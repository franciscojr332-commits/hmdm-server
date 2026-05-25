/*
 * HMDM-EVOLUTION F1.3: MyBatis mapper for command audit events.
 */

package com.hmdm.notification.persistence.mapper;

import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommandAuditMapper {

    @Insert("INSERT INTO mdm_command_audit (message_id, event_type, from_state, to_state, event_at, actor, details, ip_address) " +
            "VALUES (#{messageId}, #{eventType}, #{fromState}, #{toState}, #{eventAt}, #{actor}, CAST(#{details} AS JSONB), CAST(#{ipAddress} AS INET))")
    void insertEvent(CommandAuditEvent event);

    @Select("SELECT id, message_id AS messageId, event_type AS eventType, from_state AS fromState, " +
            "to_state AS toState, event_at AS eventAt, actor, details::TEXT AS details, ip_address::TEXT AS ipAddress " +
            "FROM mdm_command_audit WHERE message_id = #{messageId} ORDER BY event_at ASC")
    List<CommandAuditEvent> findByMessageId(@Param("messageId") int messageId);

    @Select("SELECT COUNT(*) FROM mdm_command_audit WHERE event_at >= #{since}")
    long countSince(@Param("since") long since);
}
