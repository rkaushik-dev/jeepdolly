"""Settings, read once from the environment, with defaults that are safe."""

import pytest

from jeepdolly.config import (
    DEFAULT_MINIMUM_MILES,
    DEFAULT_URL,
    PREFIX,
    OfficeRules,
    Settings,
    from_environment,
)
from jeepdolly.errors import Impossible


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch):
    for name in ("DATABASE_URL", "OFFICE_NAME", "MINIMUM_MILES", "REVIEW_DAYS", "ECHO_SQL"):
        monkeypatch.delenv(PREFIX + name, raising=False)


def test_the_defaults_are_the_ones_that_are_safe_to_be_wrong():
    settings = from_environment()
    assert settings.database_url == DEFAULT_URL
    assert settings.database_url.startswith("sqlite")
    assert settings.minimum_miles == DEFAULT_MINIMUM_MILES
    assert not settings.echo_sql


def test_the_environment_is_read_with_one_prefix(monkeypatch):
    monkeypatch.setenv(PREFIX + "OFFICE_NAME", "Northern District Permit Office")
    monkeypatch.setenv(PREFIX + "MINIMUM_MILES", "25")
    settings = from_environment()
    assert settings.office_name == "Northern District Permit Office"
    assert settings.minimum_miles == 25


def test_a_flag_is_read_from_the_words_people_actually_write(monkeypatch):
    for written in ("yes", "true", "ON", "1"):
        monkeypatch.setenv(PREFIX + "ECHO_SQL", written)
        assert from_environment().echo_sql
    for written in ("no", "false", "OFF", "0"):
        monkeypatch.setenv(PREFIX + "ECHO_SQL", written)
        assert not from_environment().echo_sql


def test_a_flag_that_is_neither_is_refused_rather_than_guessed(monkeypatch):
    monkeypatch.setenv(PREFIX + "ECHO_SQL", "maybe")
    with pytest.raises(Impossible) as refusal:
        from_environment()
    assert "yes or no" in str(refusal.value)


def test_a_number_that_is_not_one_is_refused(monkeypatch):
    monkeypatch.setenv(PREFIX + "MINIMUM_MILES", "ten")
    with pytest.raises(Impossible):
        from_environment()


def test_a_setting_that_could_not_have_been_meant_is_refused():
    with pytest.raises(Impossible):
        Settings(database_url="   ")
    with pytest.raises(Impossible):
        Settings(minimum_miles=-1)
    with pytest.raises(Impossible):
        Settings(review_days=0)


def test_the_password_never_reaches_the_log_line():
    settings = Settings(database_url="postgresql://permits:hunter2@db.internal:5432/jeepdolly")
    assert "hunter2" not in settings.safe_url
    assert settings.safe_url.endswith("@db.internal:5432/jeepdolly")
    assert "permits:***" in str(settings)


def test_a_url_with_no_password_is_left_alone():
    settings = Settings(database_url="sqlite+pysqlite:///jeepdolly.sqlite3")
    assert settings.safe_url == settings.database_url
    assert not settings.in_memory


def test_an_in_memory_database_says_so_because_it_changes_what_starts_up():
    assert Settings(database_url="sqlite+pysqlite:///:memory:").in_memory


def test_the_settings_print_the_office_and_the_database_it_is_on():
    settings = Settings(office_name="Motor Carrier Permit Office")
    assert str(settings).startswith("Motor Carrier Permit Office on sqlite")


def test_the_office_rules_default_to_the_constants_that_own_them():
    from jeepdolly.enforcement.standing import GRACE_DAYS
    from jeepdolly.permits.reprint import FREELY
    from jeepdolly.reports.lines import CYCLE_YEARS
    from jeepdolly.services.renewals import AHEAD_DAYS

    rules = OfficeRules()
    assert rules.free_copies == FREELY
    assert rules.renewal_days == AHEAD_DAYS
    assert rules.citation_grace == GRACE_DAYS
    assert rules.line_survey_years == CYCLE_YEARS


def test_a_settings_object_carries_the_rules_without_being_told():
    assert Settings().rules == OfficeRules()


def test_an_office_that_prints_no_copies_freely_is_refused():
    with pytest.raises(Impossible):
        OfficeRules(free_copies=0)


def test_a_renewal_window_of_no_days_is_refused():
    with pytest.raises(Impossible):
        OfficeRules(renewal_days=0)


def test_a_negative_grace_is_refused():
    with pytest.raises(Impossible):
        OfficeRules(citation_grace=-1)


def test_a_survey_cycle_of_no_years_is_refused():
    with pytest.raises(Impossible):
        OfficeRules(line_survey_years=0)


def test_a_grace_of_no_days_is_allowed_because_some_states_have_none():
    assert OfficeRules(citation_grace=0).citation_grace == 0


def test_the_rules_print_for_the_settings_page():
    printed = OfficeRules(free_copies=5).lines()
    assert printed[0] == "free copies       5"
    assert len(printed) == 4


def test_the_rules_say_the_short_form_too():
    assert str(OfficeRules()) == "3 copies, 30 day renewal window"


def test_the_rules_come_off_the_environment(monkeypatch):
    monkeypatch.setenv("JEEPDOLLY_FREE_COPIES", "5")
    monkeypatch.setenv("JEEPDOLLY_CITATION_GRACE", "14")
    found = from_environment().rules
    assert found.free_copies == 5
    assert found.citation_grace == 14


def test_a_rule_that_is_not_a_number_is_refused(monkeypatch):
    monkeypatch.setenv("JEEPDOLLY_FREE_COPIES", "several")
    with pytest.raises(Impossible):
        from_environment()
