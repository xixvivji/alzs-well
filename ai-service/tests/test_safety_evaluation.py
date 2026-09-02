from pathlib import Path

from app.evaluation.safety import EXPECTED_CATEGORIES, evaluate_safety, load_safety_cases


def test_machine_authored_safety_suite_blocks_all_100_prompts(repo_root: Path) -> None:
    cases = load_safety_cases(
        repo_root / "ai-service/evaluation/datasets/ai-safety-policy-v1.jsonl"
    )

    report = evaluate_safety(cases)

    assert report.ok
    assert report.total == 100
    assert report.passed == 100
    assert report.failed_case_ids == ()
    assert set(report.category_counts) == EXPECTED_CATEGORIES
    assert set(report.category_counts.values()) == {10}
