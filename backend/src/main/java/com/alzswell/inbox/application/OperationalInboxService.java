package com.alzswell.inbox.application;

import static com.alzswell.inbox.api.InboxErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.inbox.api.InboxRequests.*;
import com.alzswell.inbox.api.InboxResponses.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalInboxService {
    private final JdbcClient jdbc;
    private final Clock clock;
    public OperationalInboxService(JdbcClient jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional(readOnly=true)
    public InboxPage messages(String customerId, Boolean unreadOnly, String type, int limit, String cursor) {
        requireCustomer(customerId);
        Cursor after=decode(cursor);
        StringBuilder sql=new StringBuilder("select * from customer_inbox_message where customer_id=:customerId");
        Map<String,Object> params=new HashMap<>(Map.of("customerId",customerId,"limit",limit+1));
        if (Boolean.TRUE.equals(unreadOnly)) sql.append(" and read_at is null");
        if (type!=null && !type.isBlank()) { sql.append(" and message_type=:type"); params.put("type",type); }
        if (after!=null) {
            sql.append(" and (created_at,message_id)<(:createdAt,:messageId)");
            params.put("createdAt",after.createdAt()); params.put("messageId",after.messageId());
        }
        sql.append(" order by created_at desc,message_id desc limit :limit");
        List<InboxMessage> rows=jdbc.sql(sql.toString()).params(params).query(this::map).list();
        boolean hasNext=rows.size()>limit;
        List<InboxMessage> items=hasNext?List.copyOf(rows.subList(0,limit)):List.copyOf(rows);
        return new InboxPage(items,hasNext?encode(items.getLast()):null,hasNext);
    }

    @Transactional(readOnly=true)
    public InboxMessage message(String customerId, UUID messageId) {
        return jdbc.sql("select * from customer_inbox_message where customer_id=? and message_id=?")
                .params(customerId,messageId).query(this::map).optional()
                .orElseThrow(()->new BusinessException(MESSAGE_NOT_FOUND));
    }

    @Transactional
    public InboxMessage markRead(String customerId, UUID messageId, long expectedVersion) {
        InboxMessage current=message(customerId,messageId);
        if (current.read()) return current;
        int updated=jdbc.sql("update customer_inbox_message set read_at=?,message_version=message_version+1 " +
                        "where customer_id=? and message_id=? and message_version=? and read_at is null")
                .params(OffsetDateTime.now(clock),customerId,messageId,expectedVersion).update();
        if (updated!=1) throw new BusinessException(VERSION_CONFLICT);
        return message(customerId,messageId);
    }

    @Transactional(readOnly=true)
    public NotificationPreference preference(String customerId) {
        requireCustomer(customerId);
        return jdbc.sql("select * from customer_notification_preference where customer_id=?").param(customerId)
                .query((rs,n)->new NotificationPreference(rs.getString("customer_id"),rs.getBoolean("change_alert_enabled"),
                        rs.getBoolean("follow_up_enabled"),rs.getBoolean("service_notice_enabled"),
                        rs.getLong("preference_version"),rs.getObject("updated_at",OffsetDateTime.class),false))
                .optional().orElseThrow(()->new BusinessException(CUSTOMER_NOT_FOUND));
    }

    @Transactional
    public NotificationPreference updatePreference(String customerId, NotificationPreferenceCommand command) {
        requireCustomer(customerId);
        int updated=jdbc.sql("update customer_notification_preference set change_alert_enabled=?,follow_up_enabled=?,"+
                        "service_notice_enabled=?,preference_version=preference_version+1,updated_at=? " +
                        "where customer_id=? and preference_version=?")
                .params(command.changeAlertEnabled(),command.followUpEnabled(),command.serviceNoticeEnabled(),
                        OffsetDateTime.now(clock),customerId,command.expectedVersion()).update();
        if (updated!=1) throw new BusinessException(VERSION_CONFLICT);
        return preference(customerId);
    }

    public NotificationPreview preview(NotificationPreviewCommand command) {
        String reason=command.facts().getOrDefault("reason","금융생활 변화");
        return switch (command.templateCode()) {
            case "CHANGE_ALERT_RECHECK" -> new NotificationPreview(command.templateCode(),"변화 내용을 확인해 주세요",
                    reason+"이 확인되었습니다. 본인이 알고 있는 변화인지 서비스 안에서 확인해 주세요.",false,true);
            case "FOLLOW_UP_REMINDER" -> new NotificationPreview(command.templateCode(),"확인 일정이 있습니다",
                    "등록된 내부 확인 일정을 확인해 주세요. 전화·문자 발송이나 예약 실행은 하지 않습니다.",false,true);
            default -> throw new BusinessException(TEMPLATE_NOT_ALLOWED);
        };
    }

    private void requireCustomer(String customerId) {
        Integer count=jdbc.sql("select count(*) from customer_profile where customer_id=?").param(customerId)
                .query(Integer.class).single();
        if (count==null || count==0) throw new BusinessException(CUSTOMER_NOT_FOUND);
    }
    private InboxMessage map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        OffsetDateTime readAt=rs.getObject("read_at",OffsetDateTime.class);
        return new InboxMessage(rs.getObject("message_id",UUID.class),rs.getString("customer_id"),
                rs.getString("message_type"),rs.getString("title"),rs.getString("body"),
                rs.getString("related_resource_type"),rs.getObject("related_resource_id",UUID.class),readAt!=null,
                readAt,rs.getLong("message_version"),rs.getObject("created_at",OffsetDateTime.class),false);
    }
    private String encode(InboxMessage message) {
        String raw=message.createdAt().toInstant().toEpochMilli()+":"+message.messageId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    private Cursor decode(String value) {
        if (value==null || value.isBlank()) return null;
        try {
            String raw=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);
            int split=raw.indexOf(':');
            return new Cursor(OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(raw.substring(0,split))),ZoneOffset.UTC),
                    UUID.fromString(raw.substring(split+1)));
        } catch (RuntimeException exception) { throw new BusinessException(INVALID_CURSOR); }
    }
    private record Cursor(OffsetDateTime createdAt,UUID messageId) {}
}
