"""The package itself: what it declares, and whether every module imports.

The import test is the one that earns its place. A model that nothing imports
is a table that ``create_all`` does not build and that autogenerate offers to
drop, and the symptom of that turns up three weeks later as a missing table on
the server. Walking every module once, in one test, catches it the day it is
written.
"""

import importlib
import pkgutil
import tomllib
from pathlib import Path

import pytest

import jeepdolly
from jeepdolly.persistence.base import Base

ROOT = Path(__file__).resolve().parents[1]


def modules():
    found = []
    for one in pkgutil.walk_packages(jeepdolly.__path__, prefix="jeepdolly."):
        found.append(one.name)
    return sorted(found)


@pytest.fixture(scope="module")
def declared():
    return tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))


def test_every_module_in_the_package_imports_on_its_own():
    walked = 0
    for name in modules():
        importlib.import_module(name)
        walked += 1
    assert walked > 50


def test_the_package_declares_the_version_the_code_carries(declared):
    assert declared["project"]["version"] == jeepdolly.__version__


def test_the_console_entry_point_resolves_to_something_callable(declared):
    where = declared["project"]["scripts"]["jeepdolly"]
    module, _, name = where.partition(":")
    assert callable(getattr(importlib.import_module(module), name))


def test_the_pins_are_exact_so_two_installs_are_the_same(declared):
    for line in declared["project"]["dependencies"]:
        assert "==" in line, line
    for line in declared["project"]["optional-dependencies"]["dev"]:
        assert "==" in line, line


def test_the_runtime_does_not_depend_on_the_test_tools(declared):
    runtime = " ".join(declared["project"]["dependencies"]).lower()
    for tool in ("pytest", "ruff", "mypy", "httpx"):
        assert tool not in runtime


def test_every_table_is_reachable_from_the_models_package():
    from jeepdolly.persistence import models

    named = {one for one in dir(models) if one.endswith("Row")}
    tables = {one.class_.__name__ for one in Base.registry.mappers}
    shipped = {one for one in tables if one != "Sample"}
    assert shipped <= named


def test_the_line_length_is_the_one_the_office_reads_at(declared):
    assert declared["tool"]["ruff"]["line-length"] == 100


def test_the_tests_are_in_the_folder_the_configuration_names(declared):
    assert declared["tool"]["pytest"]["ini_options"]["testpaths"] == ["tests"]
    assert (ROOT / "tests").is_dir()
    assert len(list((ROOT / "tests").glob("test_*.py"))) > 40
