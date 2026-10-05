"""Build a real MCP server with whichever server API the installed SDK ships.

mcp 1.x exposes ``mcp.server.fastmcp.FastMCP``; mcp 2.x exposes
``mcp.server.mcpserver.MCPServer``. Both register tools with ``@server.tool()``.
"""
import asyncio
import base64
import os

# A minimal JPEG (1x1): enough for transport tests, never decoded.
TINY_JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////"
    "wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
)


def build_server(name: str):
    try:
        from mcp.server.fastmcp import FastMCP
        server = FastMCP(name)
    except ImportError:
        from mcp.server.mcpserver import MCPServer
        server = MCPServer(name)

    try:
        from mcp.server.fastmcp import Image
    except ImportError:
        from mcp.server.mcpserver import Image

    prefix = os.environ.get("FIXTURE_PREFIX", "")

    @server.tool()
    def echo(message: str) -> str:
        """Echo back message."""
        return f"{prefix}{message}"

    @server.tool()
    def secret_admin(message: str) -> str:
        """Tool that profiles may exclude."""
        return f"{prefix}admin:{message}"

    @server.tool()
    async def slow(seconds: float) -> str:
        """Sleep for the given number of seconds, then answer.

        Each execution appends one line to $FIXTURE_CALL_LOG so tests can count
        how many times the tool really ran (a replayed call shows up twice).
        """
        log = os.environ.get("FIXTURE_CALL_LOG")
        if log:
            with open(log, "a", encoding="utf-8") as fh:
                fh.write("slow:start\n")
        await asyncio.sleep(seconds)
        return f"{prefix}slept"

    @server.tool()
    def snap() -> list:
        """Return a caption and a tiny JPEG, like a screenshot tool."""
        return [f"{prefix}caption", Image(data=TINY_JPEG, format="jpeg")]

    @server.tool()
    def broken() -> str:
        """Always fails, so tool-level errors can be tested."""
        raise ValueError("tool exploded on purpose")

    return server
