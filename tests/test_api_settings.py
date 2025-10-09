"""How this office is configured, and what happens when it is configured differently."""

import pytest
from starlette.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.config import OfficeRules, Settings
from jeepdolly.persistence.books import enforcement_from
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, keyed_client, keys_for

NEAR = "2025-12-15"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def app_with(factory, rules, key=None, role=Role.SUPERVISOR):
    """A client over one database with one set of office rules.

    The key is passed in when a test wants two of these at once. Issuing a
    key reissues the whole set, so building a second client the easy way
    stops the first one working, which is a bad half hour.
    """
    made = TestClient(create_app(factory, Settings(rules=rules)))
    made.headers[HEADER] = key or keys_for(factory)[role]
    return made


def test_the_settings_page_says_how_the_office_is_set_up(client):
    found = client.get("/settings").json()
    assert found["free_copies"] == 3
    assert found["renewal_days"] == 30
    assert found["citation_grace"] == 30
    assert found["line_survey_years"] == 5


def test_the_settings_page_carries_the_printed_lines(client):
    found = client.get("/settings").json()
    assert found["lines"][0] == "free copies       3"
    assert len(found["lines"]) == 4


def test_the_settings_page_wants_a_key(factory):
    plain = TestClient(create_app(factory))
    assert plain.get("/settings").status_code in (401, 403)


def test_health_still_wants_no_key(factory):
    plain = TestClient(create_app(factory))
    assert plain.get("/health").status_code == 200


def test_a_changed_rule_shows_on_the_page(factory):
    client = app_with(factory, OfficeRules(free_copies=5, renewal_days=45))
    found = client.get("/settings").json()
    assert found["free_copies"] == 5
    assert found["renewal_days"] == 45


def test_the_renewal_window_defaults_to_the_office_rule(factory):
    from test_api_renewals import an_annual

    an_annual(factory)
    key = keys_for(factory)[Role.SUPERVISOR]
    tight = app_with(factory, OfficeRules(renewal_days=1), key)
    wide = app_with(factory, OfficeRules(renewal_days=60), key)
    assert tight.get(f"/renewals?on={NEAR}").json() == []
    assert wide.get(f"/renewals?on={NEAR}").json() != []


def test_the_window_can_still_be_asked_for_explicitly(factory):
    from test_api_renewals import an_annual

    an_annual(factory)
    tight = app_with(factory, OfficeRules(renewal_days=1))
    assert tight.get(f"/renewals?on={NEAR}&within=60").json() != []


def test_the_letters_follow_the_same_window(factory):
    from test_api_renewals import an_annual

    an_annual(factory)
    wide = app_with(factory, OfficeRules(renewal_days=60))
    assert wide.get(f"/renewals/letters?on={NEAR}").json() != []


def test_a_shorter_grace_holds_a_carrier_sooner(factory):
    from datetime import date

    from jeepdolly.enforcement.penalty import Offence
    from jeepdolly.services import enforcement

    with UnitOfWork(factory) as work:
        enforcement.file_citation(
            work,
            number="E-25-0417",
            usdot=1204471,
            officer="Sgt A Thibodeau",
            offence=Offence.NO_PERMIT,
            place="US201-2",
            written=date(2025, 4, 8),
            filed=date(2025, 4, 8),
            who="M Cyr",
        )
        work.commit()
        generous = enforcement_from(work.db, grace=30)
        tight = enforcement_from(work.db, grace=0)
    on = date(2025, 5, 10)
    assert generous.hold(1204471, on) is None
    assert tight.hold(1204471, on) is not None


def test_the_default_grace_is_the_one_the_module_owns(factory):
    from jeepdolly.enforcement.standing import GRACE_DAYS

    assert OfficeRules().citation_grace == GRACE_DAYS
