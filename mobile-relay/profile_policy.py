"""Which profiles may control a phone, and how.

`john` is the strict profile: the phone starts a short, bounded, consented session. `phone-agent` is a standing
profile for a phone dedicated to the agent: the relay opens its session itself, so it must only ever be granted to a
device paired for it, never to a phone that merely asks. A profile absent from this table cannot control a phone.
"""
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class ProfilePolicy:
    name: str
    standing: bool


POLICIES = {
    "john": ProfilePolicy("john", standing=False),
    "phone-agent": ProfilePolicy("phone-agent", standing=True),
}


def policy_for(profile: object) -> Optional[ProfilePolicy]:
    return POLICIES.get(str(profile or "").strip().lower())
