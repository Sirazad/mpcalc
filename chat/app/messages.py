"""Every text the player can see from this service, Hungarian (default) and English, chosen by Accept-Language
like the backend. Both catalogs must have the same keys (a test checks it). {name} marks an argument."""
from __future__ import annotations

HU: dict[str, str] = {
    "not_logged_in": "Nem vagy bejelentkezve.",
    "not_your_character": "Ez a karakter nem található, vagy nem a tiéd.",
    "message_required": "Írj be egy kérdést.",
    "message_too_long": "A kérdés túl hosszú (legfeljebb {limit} karakter).",
    "history_invalid": "A beszélgetés előzménye érvénytelen. Indíts új beszélgetést.",
    "rate_limited": "Túl sok kérdés rövid idő alatt. Próbáld újra {seconds} másodperc múlva.",
    "busy": "Még dolgozom az előző kérdéseden. Várd meg a választ.",
    "unavailable": "A csevegés most nem érhető el. Próbáld újra később.",
    "model_busy": "A csevegés most túlterhelt. Próbáld újra egy perc múlva.",
    "tools_unavailable": "A számoló szolgáltatás most nem érhető el. Próbáld újra később.",
    "backend_unavailable": "A szerver most nem érhető el. Próbáld újra később.",
    "refused": "Erre a kérdésre nem tudok válaszolni.",
    "cut_off": "A válasz félbemaradt, mert elérte a hosszkorlátot. Kérdezz rövidebben, vagy szűkítsd a kérdést.",
    "too_many_steps": "A kérdés túl sok lépést igényelt. Fogalmazd meg pontosabban, vagy bontsd részekre.",
    "status_list_available_mosaics": "Az elérhető mozaikok megnézése…",
    "status_calculate_spell": "Számolok…",
    "status_find_best_spell": "A legjobb változat keresése…",
    "status_tool": "Adatot kérek a számoló szolgáltatástól…",
}

EN: dict[str, str] = {
    "not_logged_in": "You are not logged in.",
    "not_your_character": "This character was not found, or it is not yours.",
    "message_required": "Type a question.",
    "message_too_long": "The question is too long (at most {limit} characters).",
    "history_invalid": "The conversation history is invalid. Start a new conversation.",
    "rate_limited": "Too many questions in a short time. Try again in {seconds} seconds.",
    "busy": "I am still working on your previous question. Wait for the answer.",
    "unavailable": "The chat is not available now. Try again later.",
    "model_busy": "The chat is overloaded now. Try again in a minute.",
    "tools_unavailable": "The calculation service is not available now. Try again later.",
    "backend_unavailable": "The server is not available now. Try again later.",
    "refused": "I cannot answer this question.",
    "cut_off": "The answer was cut off because it reached the length limit. Ask a shorter question or narrow it down.",
    "too_many_steps": "The question needed too many steps. Make it more precise, or split it up.",
    "status_list_available_mosaics": "Looking at the available mosaics…",
    "status_calculate_spell": "Calculating…",
    "status_find_best_spell": "Searching for the best option…",
    "status_tool": "Asking the calculation service…",
}

CATALOGS = {"hu": HU, "en": EN}


def pick_language(accept_language: str | None) -> str:
    """Hungarian unless the header asks for English first (the page always asks for Hungarian)."""
    if accept_language:
        for part in accept_language.split(","):
            tag = part.split(";")[0].strip().lower()
            if tag.startswith("hu"):
                return "hu"
            if tag.startswith("en"):
                return "en"
    return "hu"


def text(key: str, lang: str = "hu", **args: object) -> str:
    """The text of a key; a missing key is a programming error, never shown as the key."""
    return CATALOGS.get(lang, HU)[key].format(**args)
