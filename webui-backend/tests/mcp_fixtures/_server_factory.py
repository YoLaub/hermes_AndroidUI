"""Build a real MCP server with whichever server API the installed SDK ships.

mcp 1.x exposes ``mcp.server.fastmcp.FastMCP``; mcp 2.x exposes
``mcp.server.mcpserver.MCPServer``. Both register tools with ``@server.tool()``.
"""
import os
import asyncio


def build_server(name: str):
    try:
        from mcp.server.fastmcp import FastMCP
        server = FastMCP(name)
    except ImportError:
        from mcp.server.mcpserver import MCPServer
        server = MCPServer(name)

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
        """Block for the given number of seconds, then answer."""
        await asyncio.sleep(seconds)
        return f"{prefix}slept"

    return server
