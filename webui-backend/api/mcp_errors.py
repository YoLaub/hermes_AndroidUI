"""Errors shared by the WebUI process and the MCP profile workers."""


class ResultUnknownError(RuntimeError):
    """A tool call was sent but its outcome is unknown.

    The server may or may not have executed it. Callers must report it as
    unknown and never replay it automatically.
    """
