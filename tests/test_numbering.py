"""Permit numbers, and the gapless counter behind them."""

import threading
from datetime import date

import pytest

from jeepdolly.errors import Conflict, Impossible, Unreadable
from jeepdolly.permits.numbering import Counter, Numbering, PermitNumber, Series

APRIL = date(2025, 4, 8)


def test_a_number_prints_short_enough_to_read_over_the_telephone():
    number = PermitNumber(2025, Series.SINGLE, 4471)
    assert str(number) == "25-P-004471"
    assert repr(number) == "PermitNumber(25-P-004471)"


def test_a_number_reads_back_from_what_was_written_on_the_fax():
    number = PermitNumber.parse("25-S-000012")
    assert number.year == 2025
    assert number.series is Series.SUPERLOAD
    assert number.sequence == 12


def test_something_that_is_not_a_number_is_refused_and_quoted_back():
    with pytest.raises(Unreadable) as refusal:
        PermitNumber.parse("permit 4471")
    assert refusal.value.facts["written"] == "permit 4471"
    with pytest.raises(Unreadable):
        PermitNumber.parse("25-P-4471")
    with pytest.raises(Unreadable):
        PermitNumber.parse("2025-P-004471")


def test_a_number_outside_what_the_office_issues_is_refused():
    with pytest.raises(Impossible):
        PermitNumber(1999, Series.SINGLE, 1)
    with pytest.raises(Impossible):
        PermitNumber(2025, Series.SINGLE, 0)
    with pytest.raises(Impossible):
        PermitNumber(2025, Series.SINGLE, 1000000)


def test_numbers_sort_by_year_then_series_then_sequence():
    numbers = [
        PermitNumber(2025, Series.SINGLE, 12),
        PermitNumber(2024, Series.SINGLE, 900),
        PermitNumber(2025, Series.ANNUAL, 3),
    ]
    assert [str(one) for one in sorted(numbers)] == ["24-P-000900", "25-A-000003", "25-P-000012"]


def test_the_counter_starts_at_one_and_never_skips():
    counter = Counter()
    taken = [counter.take(2025, Series.SINGLE) for _ in range(5)]
    assert taken == [1, 2, 3, 4, 5]
    assert counter.peek(2025, Series.SINGLE) == 6
    assert counter.issued(2025, Series.SINGLE) == 5


def test_each_series_and_each_year_counts_on_its_own():
    counter = Counter()
    counter.take(2025, Series.SINGLE)
    counter.take(2025, Series.SINGLE)
    assert counter.take(2025, Series.ANNUAL) == 1
    assert counter.take(2026, Series.SINGLE) == 1
    assert counter.issued(2025, Series.SINGLE) == 2


def test_the_counter_can_be_started_from_where_the_paper_ledger_stopped():
    counter = Counter({(2025, Series.SINGLE): 4471})
    assert counter.take(2025, Series.SINGLE) == 4471
    assert counter.take(2025, Series.SINGLE) == 4472


def test_a_full_series_refuses_rather_than_wrapping_round():
    counter = Counter({(2025, Series.SINGLE): 1000000})
    with pytest.raises(Conflict):
        counter.take(2025, Series.SINGLE)


def test_two_threads_taking_at_once_do_not_get_the_same_number():
    counter = Counter()
    taken: list[int] = []
    lock = threading.Lock()

    def grab():
        for _ in range(50):
            number = counter.take(2025, Series.SINGLE)
            with lock:
                taken.append(number)

    threads = [threading.Thread(target=grab) for _ in range(4)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    assert len(taken) == 200
    assert sorted(taken) == list(range(1, 201))


def test_the_numbering_hands_out_a_whole_number_for_a_day():
    numbering = Numbering()
    first = numbering.next_number(APRIL)
    second = numbering.next_number(APRIL, Series.SUPERLOAD)
    assert str(first) == "25-P-000001"
    assert str(second) == "25-S-000001"
    assert numbering.issued_in(2025) == 1


def test_a_replacement_takes_a_number_out_of_its_own_series():
    numbering = Numbering()
    original = numbering.next_number(APRIL)
    replacement = numbering.replacement_for(original, APRIL)
    assert replacement.series is Series.REPLACEMENT
    assert str(replacement) == "25-R-000001"


def test_a_replacement_of_a_replacement_is_refused():
    numbering = Numbering()
    original = numbering.next_number(APRIL)
    replacement = numbering.replacement_for(original, APRIL)
    with pytest.raises(Impossible):
        numbering.replacement_for(replacement, APRIL)
