from __future__ import annotations

import re
from datetime import date
from typing import Sequence


_EFFECTIVE_DATE_PATTERN = re.compile(
    r"\[\s*시행일\s*:\s*(\d{4})\s*\.\s*(\d{1,2})\s*\.\s*(\d{1,2})\s*\.?\s*\]"
)


def explicit_effective_dates(content: str) -> tuple[date, ...]:
    """Extract explicit Korean statute effective-date markers from a chunk."""
    parsed: list[date] = []
    for year, month, day in _EFFECTIVE_DATE_PATTERN.findall(content):
        try:
            parsed.append(date(int(year), int(month), int(day)))
        except ValueError:
            continue
    return tuple(parsed)


def is_content_effective(content: str, as_of: date) -> bool:
    """Fail closed for a chunk explicitly marked as effective after ``as_of``.

    Document-level dates remain authoritative for ordinary content. This guard handles
    official consolidated statute PDFs that include a separately chunked future text
    in the same source document as the currently effective text.
    """
    return all(effective_date <= as_of for effective_date in explicit_effective_dates(content))


def effective_dates_for_chunks(
    contents: Sequence[str],
    section_paths: Sequence[tuple[str, ...]],
    chunk_orders: Sequence[int] | None = None,
) -> tuple[date | None, ...]:
    """Propagate an end-of-provision marker to preceding split chunks.

    Consolidated Korean statute PDFs can repeat the current and future version of the
    same article. A future marker is often printed only in the last chunk. Circled
    paragraph one marks the start of a repeated multi-chunk provision.
    """
    orders = tuple(range(len(contents))) if chunk_orders is None else tuple(chunk_orders)
    if len(contents) != len(section_paths) or len(contents) != len(orders):
        raise ValueError("content and section path counts must match")
    effective_dates: list[date | None] = [None] * len(contents)
    for marker_index, content in enumerate(contents):
        dates = explicit_effective_dates(content)
        if not dates:
            continue
        effective_date = max(dates)
        start_index = marker_index
        if not content.lstrip().startswith("①"):
            previous = marker_index - 1
            while previous >= 0:
                if section_paths[previous] != section_paths[marker_index]:
                    break
                if orders[previous] + 1 != orders[previous + 1]:
                    break
                if contents[previous].lstrip().startswith("①"):
                    start_index = previous
                    break
                previous -= 1
        for index in range(start_index, marker_index + 1):
            effective_dates[index] = effective_date
    return tuple(effective_dates)
