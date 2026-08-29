package com.alzswell.common.security;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveTextPolicy {
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?iu)(치매|알츠하이머|주민\\s*등록(?:번호)?)"),
            Pattern.compile("(?<!\\d)\\d{6}[- ]?\\d{7}(?!\\d)"),
            Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)"),
            Pattern.compile("(?iu)(?>\\+82[- .]?0?1[016789]|01[016789])[- .]?(?>\\d{3,4})[- .]?\\d{4}"),
            Pattern.compile("(?iu)(?:이름|성명|name)\\s*[:=]\\s*[가-힣A-Z][가-힣A-Z .\\-]{1,40}")
    );
    private static final Pattern COMPACT_PATTERN = Pattern.compile(
            "(?iu)(치매|알츠하이머|주민등록(?:번호)?|계좌번호|카드번호|전화번호|이메일|email|성명|이름|name)"
    );

    public String validate(String value, String fieldName) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        boolean hiddenCharacters = normalized.codePoints().anyMatch(character ->
                Character.isISOControl(character)
                        || Character.getType(character) == Character.FORMAT
                        || Character.getType(character) == Character.PRIVATE_USE);
        String compact = normalized.toLowerCase(Locale.ROOT).replaceAll("[\\p{Z}\\p{P}\\p{S}]", "");
        long digitCount = normalized.codePoints().filter(Character::isDigit).count();
        boolean sensitivePattern = SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
        if (hiddenCharacters || digitCount >= 6 || containsEmailAddress(normalized)
                || sensitivePattern || COMPACT_PATTERN.matcher(compact).find()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    fieldName + "에는 고객 식별정보·계좌번호·연락처·질병 표현을 입력할 수 없습니다.");
        }
        return normalized;
    }

    private boolean containsEmailAddress(String value) {
        int at = value.indexOf('@');
        while (at > 0 && at < value.length() - 3) {
            int dot = value.indexOf('.', at + 2);
            if (dot > at + 1 && dot < value.length() - 2
                    && !Character.isWhitespace(value.charAt(at - 1))
                    && !Character.isWhitespace(value.charAt(at + 1))) {
                return true;
            }
            at = value.indexOf('@', at + 1);
        }
        return false;
    }
}
