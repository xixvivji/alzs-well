package com.alzswell.knowledge.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;

/** 공용 지식 계약의 NFC → RFC 8785 JSON → SHA-256 청크 식별자 계산기. */
public final class KnowledgeChunkId {
    private static final char[] HEX="0123456789abcdef".toCharArray();

    private KnowledgeChunkId() {}

    public static Result compute(String documentId,String versionLabel,List<String> sectionPath,int chunkOrder,
            String textHash,String chunkerVersion) {
        StringBuilder canonical=new StringBuilder(192);
        canonical.append('[');
        appendString(canonical,nfc(documentId));
        canonical.append(',');
        appendString(canonical,nfc(versionLabel));
        canonical.append(",[");
        for(int index=0;index<sectionPath.size();index++) {
            if(index>0) canonical.append(',');
            appendString(canonical,nfc(sectionPath.get(index)));
        }
        canonical.append("],").append(chunkOrder).append(',');
        appendString(canonical,nfc(textHash));
        canonical.append(',');
        appendString(canonical,nfc(chunkerVersion));
        canonical.append(']');
        String canonicalJson=canonical.toString();
        return new Result(canonicalJson,"chk_"+sha256(canonicalJson.getBytes(StandardCharsets.UTF_8)));
    }

    private static String nfc(String value) {
        if(value==null) throw new IllegalArgumentException("chunk ID string values cannot be null");
        return Normalizer.normalize(value,Normalizer.Form.NFC);
    }

    /* RFC 8785 §3.2.2.2. 이 계약은 문자열·정수·배열만 사용하므로 별도 객체-key 정렬은 필요 없다. */
    private static void appendString(StringBuilder target,String value) {
        target.append('"');
        for(int index=0;index<value.length();) {
            char current=value.charAt(index++);
            switch(current) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\t' -> target.append("\\t");
                case '\n' -> target.append("\\n");
                case '\f' -> target.append("\\f");
                case '\r' -> target.append("\\r");
                default -> {
                    if(current<=0x1f) {
                        target.append("\\u00").append(HEX[(current>>>4)&0x0f]).append(HEX[current&0x0f]);
                    } else if(Character.isHighSurrogate(current)) {
                        if(index>=value.length()||!Character.isLowSurrogate(value.charAt(index))) {
                            throw new IllegalArgumentException("chunk ID strings must contain valid Unicode");
                        }
                        target.append(current).append(value.charAt(index++));
                    } else if(Character.isLowSurrogate(current)) {
                        throw new IllegalArgumentException("chunk ID strings must contain valid Unicode");
                    } else {
                        target.append(current);
                    }
                }
            }
        }
        target.append('"');
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch(Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Result(String canonicalJson,String chunkId) {}
}
