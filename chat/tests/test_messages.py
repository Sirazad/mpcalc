import re

from app import messages


def test_both_languages_have_the_same_keys_and_arguments():
    assert messages.HU.keys() == messages.EN.keys()
    for k in messages.HU:
        assert set(re.findall(r"{(\w+)}", messages.HU[k])) == set(re.findall(r"{(\w+)}", messages.EN[k])), k


def test_the_language_follows_the_header_and_defaults_to_hungarian():
    assert messages.pick_language(None) == "hu"
    assert messages.pick_language("hu-HU,hu;q=0.9") == "hu"
    assert messages.pick_language("en-US,en;q=0.9") == "en"
    assert messages.pick_language("de") == "hu"


def test_arguments_are_filled_in():
    assert "30" in messages.text("rate_limited", "hu", seconds=30)
    assert "30" in messages.text("rate_limited", "en", seconds=30)
