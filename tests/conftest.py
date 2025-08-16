"""Fixtures every test file may use without importing anything.

There are two of them and both are about the command line. Seven test files
built the same console over the same in memory database, in the same four
lines, and the fourth line reached inside the console to plant the session
factory on it. Seven copies of a reach like that is seven places to change
when the console grows a proper constructor for it.

The database fixture is here as well because the console needs one and a file
that wanted only the console had to declare it anyway. A test file that wants
a different database still declares its own and shadows this, which is what
pytest does with a fixture of the same name and is exactly the behaviour
wanted here.
"""

import pytest

from jeepdolly.cli.main import Console
from jeepdolly.config import Settings
from support import a_seeded_database


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def console(factory):
    made = Console(Settings(database_url="sqlite+pysqlite:///:memory:"))
    made._factory = factory
    return made
