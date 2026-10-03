"""Silent stdio MCP server: nothing on stdout/stderr except the protocol."""
import logging
import sys

sys.path.insert(0, __import__("os").path.dirname(__file__))
from _server_factory import build_server  # noqa: E402

logging.disable(logging.CRITICAL)

if __name__ == "__main__":
    build_server("fixture-stdio").run(transport="stdio")
