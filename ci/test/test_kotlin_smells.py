from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_kotlin_smells import (  # noqa: E402
    CODE_EMPTY_CATCH,
    CODE_GLOBAL_SCOPE,
    CODE_NOT_NULL_ASSERTION,
    CODE_PRINT_STACK_TRACE,
    ERROR_CODES,
    kotlin_smells,
    mask_non_code,
)
from check_repo_hygiene import added_line_numbers, kotlin_smell_diagnostics  # noqa: E402


def codes(text: str) -> list[tuple[int, str]]:
    return [(smell.line, smell.code) for smell in kotlin_smells(text)]


class MaskingTest(unittest.TestCase):
    def test_comments_and_strings_are_blanked_but_newlines_survive(self) -> None:
        source = 'val a = "text" // trailing\n/* block\nstill block */ val b = 1\n'

        masked = mask_non_code(source)

        self.assertEqual(len(masked), len(source))
        self.assertEqual(masked.count("\n"), source.count("\n"))
        self.assertNotIn("text", masked)
        self.assertNotIn("trailing", masked)
        self.assertNotIn("block", masked)
        self.assertIn("val a = ", masked)
        self.assertIn(" val b = 1", masked)

    def test_nested_block_comments_are_one_comment(self) -> None:
        masked = mask_non_code("/* outer /* inner */ still comment */ val a = 1")

        self.assertNotIn("comment", masked)
        self.assertIn("val a = 1", masked)

    def test_raw_strings_span_lines_and_hide_their_content(self) -> None:
        source = 'val js = """\n  try { f(); } catch (e) {}\n  if (a !== b) { x!! }\n"""\nval real = 1\n'

        masked = mask_non_code(source)

        self.assertNotIn("catch", masked)
        self.assertNotIn("!!", masked)
        self.assertIn("val real = 1", masked)

    def test_escaped_quotes_do_not_end_a_string_early(self) -> None:
        masked = mask_non_code(r'val a = "quote \" catch (e: Exception) {}" ; val b = 2')

        self.assertNotIn("catch", masked)
        self.assertIn("val b = 2", masked)


class EmptyCatchTest(unittest.TestCase):
    def test_single_line_empty_catch_is_reported(self) -> None:
        source = "fun f() {\n    try {\n        g()\n    } catch (e: Exception) {}\n}\n"

        self.assertEqual(codes(source), [(4, CODE_EMPTY_CATCH)])

    def test_comment_only_catch_is_reported(self) -> None:
        source = "try {\n    g()\n} catch (e: IOException) {\n    // nothing to do\n}\n"

        self.assertEqual(codes(source), [(3, CODE_EMPTY_CATCH)])

    def test_catch_that_handles_the_failure_is_not_reported(self) -> None:
        source = (
            "try {\n"
            "    g()\n"
            "} catch (e: IOException) {\n"
            "    AppLogger.e(TAG, \"failed\", e)\n"
            "    throw e\n"
            "}\n"
        )

        self.assertEqual(codes(source), [])

    def test_catch_inside_injected_javascript_is_not_kotlin(self) -> None:
        # WebViewHandler injects JavaScript through a raw string; that JS has its own empty catch.
        source = (
            "val js =\n"
            "    \"\"\"\n"
            "    try { window.fetch(url); } catch (e) {}\n"
            "    \"\"\".trimIndent()\n"
            "webView.evaluateJavascript(js, null)\n"
        )

        self.assertEqual(codes(source), [])

    def test_run_catching_and_try_blocks_are_not_catch_blocks(self) -> None:
        source = "val result = runCatching { parse(it) }\ntry {\n    f()\n} finally {\n    close()\n}\n"

        self.assertEqual(codes(source), [])


class NotNullAssertionTest(unittest.TestCase):
    def test_assertion_is_reported_with_its_line(self) -> None:
        source = "fun f() {\n    val name = preferences.getString(KEY, null)!!\n    return name\n}\n"

        self.assertEqual(codes(source), [(2, CODE_NOT_NULL_ASSERTION)])

    def test_inequality_and_negation_are_not_assertions(self) -> None:
        source = "if (a != b && !flag) {\n    return\n}\n"

        self.assertEqual(codes(source), [])

    def test_assertion_inside_a_string_or_comment_is_ignored(self) -> None:
        source = 'val text = "use !! carefully" // !! not code\n/* !! */\n'

        self.assertEqual(codes(source), [])


class AddedLineScopeTest(unittest.TestCase):
    """Only lines the candidate adds are in scope; the tree already has hundreds of findings."""

    def test_added_line_numbers_follow_the_hunk_headers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            def git(*args: str) -> subprocess.CompletedProcess[str]:
                return subprocess.run(
                    ["git", *args], cwd=repository, check=True, capture_output=True, text=True
                )
            git("init", "-b", "main")
            git("config", "user.name", "CI Test")
            git("config", "user.email", "ci@example.com")
            source = repository / "Sample.kt"
            source.write_text(
                "package sample\n\nfun existing(): String {\n    return legacy()!!\n}\n",
                encoding="utf-8",
            )
            git("add", "Sample.kt")
            git("commit", "-m", "base")
            base = git("rev-parse", "HEAD").stdout.strip()

            source.write_text(
                "package sample\n\nfun existing(): String {\n    return legacy()!!\n}\n\n"
                "fun added(): String {\n    return other()!!\n}\n",
                encoding="utf-8",
            )
            git("add", "Sample.kt")
            git("commit", "-m", "candidate")
            candidate = git("rev-parse", "HEAD").stdout.strip()

            # added_line_numbers shells out to git in the working directory, like the rest of
            # check_repo_hygiene, so the temporary repository has to be the cwd.
            previous_directory = Path.cwd()
            try:
                os.chdir(repository)
                added = added_line_numbers(base, candidate)
            finally:
                os.chdir(previous_directory)

            self.assertEqual({"Sample.kt"}, set(added))
            self.assertEqual({6, 7, 8, 9}, added["Sample.kt"])
            # The pre-existing `!!` on line 4 is not part of the candidate.
            smells = {smell.line for smell in kotlin_smells(source.read_text(encoding="utf-8"))}
            self.assertEqual({4, 8}, smells)
            self.assertEqual({8}, smells & added["Sample.kt"])


class GlobalScopeTest(unittest.TestCase):
    def test_use_and_import_are_reported_with_their_lines(self) -> None:
        source = (
            "package sample\n"
            "\n"
            "import kotlinx.coroutines.GlobalScope\n"
            "import kotlinx.coroutines.launch\n"
            "\n"
            "fun start() {\n"
            "    GlobalScope.launch { work() }\n"
            "}\n"
        )

        self.assertEqual(
            [(3, CODE_GLOBAL_SCOPE), (7, CODE_GLOBAL_SCOPE)],
            codes(source),
        )

    def test_prose_about_global_scope_is_not_a_use(self) -> None:
        source = (
            "package sample\n"
            "\n"
            "/**\n"
            " * `GlobalScope` is neither structured nor cancellable, so this scope uses\n"
            " * SupervisorJob() + Dispatchers.IO instead.\n"
            " */\n"
            "private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n"
            "\n"
            "fun start() {\n"
            "    // GlobalScope would outlive the process; scope.launch does not.\n"
            "    scope.launch { work() }\n"
            "}\n"
        )

        self.assertEqual([], codes(source))

    def test_a_cancellable_scope_is_not_reported(self) -> None:
        source = (
            "fun start() {\n"
            "    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n"
            "    scope.launch { work() }\n"
            "}\n"
        )

        self.assertEqual([], codes(source))


class PrintStackTraceTest(unittest.TestCase):
    def test_call_is_reported_with_its_line(self) -> None:
        source = (
            "fun run() {\n"
            "    try {\n"
            "        work()\n"
            "    } catch (e: Exception) {\n"
            "        e.printStackTrace()\n"
            "    }\n"
            "}\n"
        )

        self.assertEqual([(5, CODE_PRINT_STACK_TRACE)], codes(source))

    def test_logging_the_throwable_is_not_reported(self) -> None:
        source = (
            "fun run() {\n"
            "    try {\n"
            "        work()\n"
            "    } catch (e: Exception) {\n"
            "        AppLogger.e(TAG, \"work failed: ${e.message}\", e)\n"
            "    }\n"
            "}\n"
        )

        self.assertEqual([], codes(source))

    def test_stack_trace_to_string_and_prose_are_not_reported(self) -> None:
        source = (
            "fun run() {\n"
            "    val trace = failure.stackTraceToString()\n"
            "    // e.printStackTrace() would bypass AppLogger, so the trace is logged instead.\n"
            "    AppLogger.w(TAG, trace)\n"
            "}\n"
        )

        self.assertEqual([], codes(source))


class SmellSeverityTest(unittest.TestCase):
    def test_error_codes_fail_and_the_rest_only_warn(self) -> None:
        source = (
            "package sample\n"
            "\n"
            "fun broken() {\n"
            "    try {\n"
            "        work()\n"
            "    } catch (e: Exception) {\n"
            "    }\n"
            "    e.printStackTrace()\n"
            "    GlobalScope.launch { work() }\n"
            "    val value = maybe()!!\n"
            "}\n"
        )

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Sample.kt"
            path.write_text(source, encoding="utf-8")
            errors: list = []
            warnings: list = []
            kotlin_smell_diagnostics(
                path,
                Path("Sample.kt"),
                set(range(1, source.count("\n") + 1)),
                errors,
                warnings,
            )

        self.assertEqual({CODE_EMPTY_CATCH, CODE_GLOBAL_SCOPE}, set(ERROR_CODES))
        self.assertEqual(
            [(6, CODE_EMPTY_CATCH), (9, CODE_GLOBAL_SCOPE)],
            [(diagnostic.line, diagnostic.code) for diagnostic in errors],
        )
        self.assertEqual(
            [(8, CODE_PRINT_STACK_TRACE), (10, CODE_NOT_NULL_ASSERTION)],
            [(diagnostic.line, diagnostic.code) for diagnostic in warnings],
        )

    def test_smells_outside_the_added_lines_are_not_reported(self) -> None:
        source = (
            "fun existing() {\n"
            "    GlobalScope.launch { work() }\n"
            "}\n"
            "\n"
            "fun added() {\n"
            "    legacy()!!.printStackTrace()\n"
            "}\n"
        )

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Sample.kt"
            path.write_text(source, encoding="utf-8")
            errors: list = []
            warnings: list = []
            kotlin_smell_diagnostics(path, Path("Sample.kt"), {5, 6, 7}, errors, warnings)

        self.assertEqual([], [(diagnostic.line, diagnostic.code) for diagnostic in errors])
        self.assertEqual(
            [(6, CODE_NOT_NULL_ASSERTION), (6, CODE_PRINT_STACK_TRACE)],
            [(diagnostic.line, diagnostic.code) for diagnostic in warnings],
        )


if __name__ == "__main__":
    unittest.main()
