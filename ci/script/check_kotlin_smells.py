#!/usr/bin/env python3
"""Kotlin smell detection for the lines a candidate actually adds.

The repository carries hundreds of empty `catch` blocks and `!!` assertions and has no lint gate, so
nothing stops a pull request from adding more. Running detekt or ktlint over the whole tree would
report every pre-existing occurrence at once and would need the Android toolchain that the Python
gate deliberately does not have, so this module reports only what a diff adds and stays stdlib-only.

Detection happens on a masked copy of the source where every character belonging to a comment or a
string literal is replaced by a space (newlines are preserved, so line numbers still line up). That
matters here: the app injects JavaScript through Kotlin raw strings, and that JavaScript legitimately
contains `catch (e) {}` and `!!`, which must not be attributed to Kotlin. Masking also keeps the
prose that explains a removed pattern from looking like the pattern: several files document why they
no longer use `GlobalScope`.

Four patterns are reported:

- `empty-catch` (error) - a `catch` block with no statement, including one holding only a comment.
- `global-scope` (error) - any use of `GlobalScope`, including its import. The repository replaced
  every one of them with a cancellable `SupervisorJob() + Dispatchers.IO` scope.
- `not-null-assertion` (warning) - `!!`.
- `print-stack-trace` (warning) - `printStackTrace()`, which bypasses `AppLogger` and therefore the
  rotation, size caps and file pruning that keep logging from filling user storage.
"""

from __future__ import annotations

import re
from dataclasses import dataclass


CODE_EMPTY_CATCH = "empty-catch"
CODE_GLOBAL_SCOPE = "global-scope"
CODE_NOT_NULL_ASSERTION = "not-null-assertion"
CODE_PRINT_STACK_TRACE = "print-stack-trace"

# Codes that fail the check. The rest are reported as warnings and never fail on their own.
ERROR_CODES = frozenset({CODE_EMPTY_CATCH, CODE_GLOBAL_SCOPE})

KOTLIN_SUFFIXES = {".kt", ".kts"}

# `catch (e: Exception) { }`, `catch (_: IOException) {}`, and the same split over several lines.
# A body that holds only a comment is masked away, so it is reported too: explaining a swallowed
# exception is not handling it.
EMPTY_CATCH_RE = re.compile(r"\bcatch\b\s*(?:\([^)]*\))?\s*\{\s*\}")
NOT_NULL_ASSERTION_RE = re.compile(r"!!")
# Any use of the unstructured coroutine scope, including `import kotlinx.coroutines.GlobalScope`.
GLOBAL_SCOPE_RE = re.compile(r"\bGlobalScope\b")
# `e.printStackTrace()`; `stackTraceToString()` and friends are deliberately not matched.
PRINT_STACK_TRACE_RE = re.compile(r"\bprintStackTrace\s*\(")


@dataclass(frozen=True)
class Smell:
    code: str
    line: int
    message: str


def mask_non_code(text: str) -> str:
    """Blank out comments and string literals, keeping the length and every newline in place."""
    out: list[str] = []
    index = 0
    length = len(text)
    block_comment_depth = 0

    while index < length:
        char = text[index]
        following = text[index + 1] if index + 1 < length else ""

        if block_comment_depth:
            # Kotlin block comments nest, so `/* /* */ */` is one comment.
            if char == "/" and following == "*":
                block_comment_depth += 1
                out.append("  ")
                index += 2
            elif char == "*" and following == "/":
                block_comment_depth -= 1
                out.append("  ")
                index += 2
            else:
                out.append("\n" if char == "\n" else " ")
                index += 1
            continue

        if char == "/" and following == "/":
            while index < length and text[index] != "\n":
                out.append(" ")
                index += 1
            continue

        if char == "/" and following == "*":
            block_comment_depth = 1
            out.append("  ")
            index += 2
            continue

        if text.startswith('"""', index):
            out.append("   ")
            index += 3
            while index < length:
                if text[index] == '"':
                    # A run of quotes ends the raw string; Kotlin gives the last three to the
                    # terminator, so consume the whole run as string content.
                    end = index
                    while end < length and text[end] == '"':
                        end += 1
                    out.append(" " * (end - index))
                    index = end
                    break
                out.append("\n" if text[index] == "\n" else " ")
                index += 1
            continue

        if char == '"' or char == "'":
            quote = char
            out.append(" ")
            index += 1
            while index < length:
                current = text[index]
                if current == "\\":
                    out.append("  ")
                    index += 2
                    continue
                out.append("\n" if current == "\n" else " ")
                index += 1
                if current == quote:
                    break
            continue

        out.append(char)
        index += 1

    return "".join(out)


def line_of(masked: str, offset: int) -> int:
    return masked.count("\n", 0, offset) + 1


def kotlin_smells(text: str) -> list[Smell]:
    """Every reported smell in Kotlin source, with 1-based line numbers, ordered by line."""
    masked = mask_non_code(text)
    smells: list[Smell] = []

    for match in EMPTY_CATCH_RE.finditer(masked):
        smells.append(
            Smell(
                code=CODE_EMPTY_CATCH,
                line=line_of(masked, match.start()),
                message=(
                    "catch block is empty: log it, rethrow it, or return a value the caller can "
                    "check; a swallowed exception is undebuggable in the field"
                ),
            )
        )

    for match in NOT_NULL_ASSERTION_RE.finditer(masked):
        smells.append(
            Smell(
                code=CODE_NOT_NULL_ASSERTION,
                line=line_of(masked, match.start()),
                message=(
                    "`!!` turns a null into a crash: prefer requireNotNull(...) with a reason, "
                    "an explicit check, or a nullable type"
                ),
            )
        )

    for match in GLOBAL_SCOPE_RE.finditer(masked):
        smells.append(
            Smell(
                code=CODE_GLOBAL_SCOPE,
                line=line_of(masked, match.start()),
                message=(
                    "`GlobalScope` is unstructured and cannot be cancelled, and it swallows "
                    "exceptions: launch from a scope the owner holds, as the rest of the repository "
                    "does with SupervisorJob() + Dispatchers.IO"
                ),
            )
        )

    for match in PRINT_STACK_TRACE_RE.finditer(masked):
        smells.append(
            Smell(
                code=CODE_PRINT_STACK_TRACE,
                line=line_of(masked, match.start()),
                message=(
                    "`printStackTrace()` writes to stderr and bypasses AppLogger, so it misses the "
                    "log rotation, size caps and file pruning; log the throwable instead"
                ),
            )
        )

    smells.sort(key=lambda smell: (smell.line, smell.code))
    return smells
