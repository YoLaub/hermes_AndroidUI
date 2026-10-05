"""
Hermes WebUI -- where a screenshot returned by an MCP tool goes.

The worker returns a screenshot as Hermes's multimodal envelope. Whether the main model may be handed the
image depends on the model, so this applies Hermes's own routing rule (the one its `computer_use` tool
uses): a model that can read images receives the envelope; any other receives a text description made by
the auxiliary vision model. A model that cannot read images is never handed the raw image.

The auxiliary path needs an image file (`vision_analyze_tool` takes a path or a URL): it is written to a
private temporary directory (0700, file 0600) and removed as soon as the analysis returns, or fails.
"""

import json
import logging
import os
import re
import tempfile
from typing import Any, Callable, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)

Image = Tuple[str, str]  # (mime type, base64 data)

_DATA_URI = re.compile(r"data:(image/(?:jpeg|png|webp|gif));base64,([A-Za-z0-9+/]+={0,2})")
_EXTENSIONS = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp", "image/gif": ".gif"}


def main_route_from_cfg(cfg: Optional[Dict[str, Any]]) -> Tuple[str, str]:
    """(provider, model) of the profile's main model, from its own config.yaml."""
    model_cfg = (cfg or {}).get("model") if isinstance(cfg, dict) else None
    if isinstance(model_cfg, str):
        return "", model_cfg.strip()
    if isinstance(model_cfg, dict):
        provider = str(model_cfg.get("provider") or "").strip().lower()
        model = str(model_cfg.get("default") or "").strip()
        return provider, model
    return "", ""


def _is_envelope(result: Any) -> bool:
    return isinstance(result, dict) and result.get("_multimodal") is True and isinstance(result.get("content"), list)


def _images_of(envelope: Dict[str, Any]) -> List[Image]:
    images: List[Image] = []
    for part in envelope["content"]:
        if isinstance(part, dict) and part.get("type") == "image_url":
            url = str((part.get("image_url") or {}).get("url") or "")
            m = _DATA_URI.fullmatch(url)
            if m:
                images.append((m.group(1), m.group(2)))
    return images


def _text_of(envelope: Dict[str, Any]) -> str:
    return "\n".join(
        str(p.get("text", "")) for p in envelope["content"] if isinstance(p, dict) and p.get("type") == "text"
    )


def _default_should_route(provider: str, model: str, cfg: Optional[Dict[str, Any]]) -> bool:
    from tools.computer_use.vision_routing import should_route_capture_to_aux_vision

    return bool(should_route_capture_to_aux_vision(provider, model, cfg))


def _default_describe(images: List[Image], prompt: str) -> Optional[str]:
    """Ask the auxiliary vision model to describe each image. Files live only for the duration of the call."""
    from model_tools import _run_async
    from tools.vision_tools import vision_analyze_tool

    analyses: List[str] = []
    with tempfile.TemporaryDirectory(prefix="hermes-shot-") as directory:  # mode 0700, removed on exit
        import base64

        for index, (mime, data) in enumerate(images, start=1):
            path = os.path.join(directory, f"screenshot_{index}{_EXTENSIONS.get(mime, '.jpg')}")
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as handle:
                handle.write(base64.b64decode(data))
            raw = _run_async(vision_analyze_tool(path, prompt))
            text = ""
            if isinstance(raw, str):
                try:
                    parsed = json.loads(raw)
                    text = str(parsed.get("analysis") or "").strip() if isinstance(parsed, dict) else raw.strip()
                except (TypeError, ValueError):
                    text = raw.strip()
            if text:
                analyses.append(text)
    if not analyses:
        return None
    if len(analyses) == 1:
        return analyses[0]
    return "\n".join(f"Image {i}: {a}" for i, a in enumerate(analyses, start=1))


def route_screenshot_result(
    result: Any,
    cfg: Optional[Dict[str, Any]],
    *,
    should_route: Optional[Callable[[str, str, Any], bool]] = None,
    describe: Optional[Callable[[List[Image], str], Optional[str]]] = None,
) -> Any:
    """Return [result] as is, or as text when the main model cannot read the images it carries."""
    if not _is_envelope(result):
        return result
    images = _images_of(result)
    if not images:
        return result

    provider, model = main_route_from_cfg(cfg)
    try:
        to_auxiliary = (should_route or _default_should_route)(provider, model, cfg)
    except Exception as exc:  # noqa: BLE001 - same as Hermes: if the rule cannot run, keep the native path
        logger.debug("screenshot routing decision unavailable (%s): sending the image to the main model", type(exc).__name__)
        return result
    if not to_auxiliary:
        return result

    text = _text_of(result)
    prompt = (
        "Describe what is visible in this phone app screenshot in concise but specific terms: the app and screen, "
        "the layout, labelled buttons and fields, and any prominent text the user would need to know about. "
        "Do not invent details that are not visible.\n\nElement list for cross-reference:\n" + text
    )
    try:
        analysis = (describe or _default_describe)(images, prompt)
    except Exception as exc:  # noqa: BLE001 - the model must still get something usable
        logger.warning("auxiliary vision analysis failed (%s)", type(exc).__name__)
        analysis = None

    if not analysis:
        return (
            f"{text}\n\n(vision indisponible : le modèle de vision auxiliaire n'a pas pu analyser la capture, "
            "elle n'est pas transmise au modèle principal. Les éléments listés ci-dessus restent utilisables.)"
        )
    return f"{text}\n\nDescription de la capture (modèle de vision auxiliaire) :\n{analysis}"
