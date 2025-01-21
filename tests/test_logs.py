"""Log lines the office can grep, and the values that never appear in one."""

import io
import logging

import pytest

from jeepdolly import logs


def test_a_line_leads_with_the_event_and_then_key_and_value():
    written = logs.line("permit.issued", number="25-P-004471", usdot=1204471)
    assert written == "permit.issued number=25-P-004471 usdot=1204471"


def test_the_fields_keep_the_order_they_were_given_in():
    written = logs.line("route.checked", origin="waterville", destination="pittsfield")
    assert written.index("origin") < written.index("destination")


def test_a_value_with_a_space_in_it_is_quoted_and_one_without_is_not():
    written = logs.line("carrier.suspended", name="Bickford Heavy Haul", usdot=1204471)
    assert 'name="Bickford Heavy Haul"' in written
    assert "usdot=1204471" in written


def test_a_quote_inside_a_value_does_not_break_the_line():
    written = logs.line("permit.voided", reason='the customer said "no"')
    assert written.count('"') == 2
    assert "'no'" in written


def test_an_empty_value_is_written_as_empty_quotes():
    assert logs.line("permit.issued", detail="") == 'permit.issued detail=""'
    assert logs.line("permit.issued", detail=None) == 'permit.issued detail=""'


def test_anything_that_looks_like_a_secret_is_never_written():
    written = logs.line("key.issued", person="Dana", api_key="abc123", digest="deadbeef")
    assert "abc123" not in written
    assert "deadbeef" not in written
    assert written.count("***") == 2


def test_a_line_with_no_event_is_a_mistake_in_the_caller():
    with pytest.raises(ValueError):
        logs.line("   ")


def test_a_line_with_no_fields_is_just_the_event():
    assert logs.line("service.started") == "service.started"


def test_the_office_logger_writes_where_it_was_pointed():
    stream = io.StringIO()
    logs.configure(stream=stream)
    logs.event("permit.issued", number="25-P-004471")
    assert "permit.issued number=25-P-004471" in stream.getvalue()
    assert "INFO" in stream.getvalue()


def test_trouble_and_failure_go_out_at_their_own_levels():
    stream = io.StringIO()
    logs.configure(stream=stream)
    logs.trouble("route.blocked", segment="US2-1")
    logs.failure("database.unreachable", url="sqlite")
    written = stream.getvalue()
    assert "WARNING route.blocked segment=US2-1" in written
    assert "ERROR database.unreachable" in written


def test_configuring_twice_does_not_write_every_line_twice():
    stream = io.StringIO()
    logs.configure(stream=stream)
    logs.configure(stream=stream)
    logs.event("service.started")
    assert stream.getvalue().count("service.started") == 1


def test_the_level_keeps_the_quiet_lines_out():
    stream = io.StringIO()
    logs.configure(level=logging.WARNING, stream=stream)
    logs.event("permit.issued", number="25-P-004471")
    logs.trouble("route.blocked", segment="US2-1")
    assert "permit.issued" not in stream.getvalue()
    assert "route.blocked" in stream.getvalue()
