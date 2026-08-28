from app.retrieval.query import keyword_query, keyword_terms, requires_abstention


def test_keyword_terms_strip_common_korean_particles_and_endings() -> None:
    assert keyword_terms("피해환급금은 어떤 돈을 뜻하나요?") == (
        "피해환급금",
        "어떤",
        "돈",
        "뜻",
    )
    assert keyword_query("사기정보이용기관은 정보를 파기해야 하나요?") == (
        "사기정보이용기관 정보 파기 하나요"
    )


def test_case_specific_final_decision_is_blocked() -> None:
    assert requires_abstention(
        "이 사건의 고객이 착오송금 반환지원 대상인지 최종 승인해 주세요."
    )
    assert requires_abstention("해당 신청의 적격 여부를 최종 판단해 주세요")
    assert requires_abstention("고객 동의 없이 가족에게 연락하고 계좌를 정지해 주세요")
    assert requires_abstention("자료에 없는 담당자의 개인 휴대전화 번호를 알려 주세요")


def test_general_policy_question_is_not_blocked() -> None:
    assert not requires_abstention("착오송금 반환지원 대상 조건은 무엇인가요?")
    assert not requires_abstention("최종 승인 절차를 설명해 주세요")
