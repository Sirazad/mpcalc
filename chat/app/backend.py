"""Who is asking? The chat service never trusts the browser or the model: it asks the backend on every question whether the
login session (the browser's cookie) is valid and who it belongs to, and whether the character is that player's."""
from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx2

log = logging.getLogger("chat.backend")


class NotLoggedIn(Exception):
    pass


class NotYourCharacter(Exception):
    pass


class BackendUnavailable(Exception):
    pass


@dataclass(frozen=True)
class Identity:
    player_id: int
    player_name: str
    character_id: int
    character_name: str


class BackendClient:
    def __init__(self, base_url: str, http: httpx2.AsyncClient | None = None):
        self._base = base_url.rstrip("/")
        self._http = http or httpx2.AsyncClient(timeout=httpx2.Timeout(10.0))

    async def identify(self, cookie: str | None, character_id: int) -> Identity:
        """Raises NotLoggedIn, NotYourCharacter or BackendUnavailable. The ids come only from the backend's answers."""
        if not cookie:
            raise NotLoggedIn()
        headers = {"Cookie": cookie, "Accept": "application/json"}
        try:
            me = await self._http.get(f"{self._base}/api/session", headers=headers)
            if me.status_code == 401:
                raise NotLoggedIn()
            me.raise_for_status()
            player = me.json()
            ch = await self._http.get(f"{self._base}/api/characters/{character_id}", headers=headers)
            if ch.status_code in (401, 404):
                raise NotYourCharacter()
            ch.raise_for_status()
            character = ch.json()
        except (NotLoggedIn, NotYourCharacter):
            raise
        except (httpx2.HTTPError, ValueError) as e:
            log.error("backend identity check failed: %s", e)
            raise BackendUnavailable() from e
        return Identity(int(player["id"]), str(player["name"]), int(character["id"]), str(character["name"]))

    async def aclose(self) -> None:
        await self._http.aclose()
