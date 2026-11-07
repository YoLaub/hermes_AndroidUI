"""Bounded Apple Mail arguments; mirrored by the macOS client contract."""
import json
import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class MailArguments(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    action: Literal['accounts', 'mailboxes', 'list', 'read', 'draft', 'mark_read', 'flag', 'move']
    account: str = Field(default='', max_length=256)
    mailbox: list[str] = Field(default_factory=list, max_length=8)
    destination: list[str] = Field(default_factory=list, max_length=8)
    message_id: int | None = Field(default=None, gt=0)
    query: str = Field(default='', max_length=200)
    offset: int = Field(default=0, ge=0, le=5000)
    limit: int = Field(default=10, ge=1, le=20)
    value: bool | None = None
    to: str = Field(default='', max_length=254)
    subject: str = Field(default='', max_length=200)
    body: str = Field(default='', max_length=8000)

    @model_validator(mode='after')
    def operation(self):
        for name in self.mailbox + self.destination:
            if not name or len(name) > 256 or any(ord(c) < 32 for c in name):
                raise ValueError('Invalid mailbox path')
        allowed = {
            'accounts': set(), 'mailboxes': {'account', 'mailbox', 'offset', 'limit'},
            'list': {'account', 'mailbox', 'query', 'offset', 'limit'},
            'read': {'account', 'mailbox', 'message_id'},
            'draft': {'to', 'subject', 'body'},
            'mark_read': {'account', 'mailbox', 'message_id', 'value'},
            'flag': {'account', 'mailbox', 'message_id', 'value'},
            'move': {'account', 'mailbox', 'message_id', 'destination'},
        }[self.action]
        if self.model_fields_set - allowed - {'action', 'command_id', 'expires_at', 'device_id', 'ttl_seconds'}:
            raise ValueError('Arguments do not match operation')
        if self.action in ('list', 'read', 'mark_read', 'flag', 'move') and not self.mailbox:
            raise ValueError('Select a mailbox first')
        if self.action in ('read', 'mark_read', 'flag', 'move') and self.message_id is None:
            raise ValueError('Message ID required')
        if self.action in ('mark_read', 'flag') and self.value is None:
            raise ValueError('Boolean value required')
        if self.action == 'move' and not self.destination:
            raise ValueError('Destination required')
        if self.action == 'draft' and (not re.fullmatch(r'[^\s@<>;,]+@[^\s@<>;,]+\.[^\s@<>;,]+', self.to)
                or '\r' in self.subject or '\n' in self.subject or not self.body.strip()):
            raise ValueError('Recipient, subject and draft body required')
        if len(json.dumps(self.model_dump(exclude_unset=True)).encode()) > 12000:
            raise ValueError('Mail arguments exceed wire limit')
        return self


class MailCommand(MailArguments):
    device_id: str = Field(min_length=1, max_length=128)
    ttl_seconds: int = Field(default=30, ge=1, le=30)
