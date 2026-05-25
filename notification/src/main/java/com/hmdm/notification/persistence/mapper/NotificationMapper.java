/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.notification.persistence.mapper;

import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.notification.rest.json.CommandStateView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>An ORM mapper for <code>Notification</code> sub-system domain objects.</p>
 *
 * @author isv
 */
public interface NotificationMapper {

    @Select("SELECT pushMessages.* " +
            "FROM pendingPushes " +
            "INNER JOIN pushMessages ON pushMessages.id = pendingPushes.messageId " +
            "INNER JOIN devices ON devices.id = pushMessages.deviceId " +
            "WHERE (devices.number = #{deviceNumber} OR devices.oldNumber = #{deviceNumber}) " +
            "AND pendingPushes.status = 0 " +
            "ORDER BY pendingPushes.createTime ASC")
    List<PushMessage> getPendingMessagesByNumber(@Param("deviceNumber") String deviceNumber);

    @Select("SELECT pushMessages.* " +
            "FROM pendingPushes " +
            "INNER JOIN pushMessages ON pushMessages.id = pendingPushes.messageId " +
            "WHERE pushMessages.deviceId = #{deviceId} " +
            "AND pendingPushes.status = 0 " +
            "ORDER BY pendingPushes.createTime ASC")
    List<PushMessage> getPendingMessagesById(@Param("deviceId") int deviceId);

    void markMessagesAsDelivered(@Param("messageIds") List<Integer> messageIds);

    @Insert("INSERT INTO pushMessages (messageType, deviceId, payload) " +
            "VALUES (#{messageType}, #{deviceId}, #{payload})")
    @SelectKey( statement = "SELECT currval('pushmessages_id_seq')", keyColumn = "id", keyProperty = "id", before = false, resultType = int.class )
    void insertPushMessage(PushMessage message);

    @Insert("INSERT INTO pendingPushes (messageId, status, createTime) " +
            "VALUES (#{messageId}, 0, EXTRACT(EPOCH FROM NOW()) * 1000)")
    void insertPendingPush(int messageId);

    @Select("SELECT status FROM pendingPushes WHERE messageId = #{messageId}")
    Integer getDeliveryStatus(@Param("messageId") int messageId);

    @Delete("DELETE FROM pushMessages " +
            "WHERE EXISTS " +
            "(" +
            " SELECT 1 " +
            " FROM pendingPushes " +
            " WHERE pendingPushes.messageId = pushMessages.id " +
            " AND (" +
            "      pendingPushes.status = 0 AND EXTRACT(EPOCH FROM NOW()) * 1000 - pendingPushes.createTime >= #{d1}" +
            "      OR " +
            "      pendingPushes.status = 1 AND NOT pendingPushes.sendTime IS NULL AND EXTRACT(EPOCH FROM NOW()) * 1000 - pendingPushes.sendTime >= #{d2}" +
            "     )" +
            ")")
    void purgeMessages(@Param("d1") long nonDeliveredMessagesLifeSpan, @Param("d2") long deliveredMessagesLifeSpan);

    // HMDM-EVOLUTION F1.5: reconciliation queries

    @Select("SELECT p.messageId " +
            "FROM pendingPushes p " +
            "WHERE p.status = 1 " +
            "  AND p.sendTime IS NOT NULL " +
            "  AND p.sendTime < #{staleBoundary} " +
            "  AND p.delivered_ack_at IS NULL " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM mdm_command_audit a " +
            "    WHERE a.message_id = p.messageId " +
            "      AND a.event_type = 'RECONCILIATION' " +
            "      AND a.to_state = 'IN_FLIGHT_STALE'" +
            "  ) " +
            "LIMIT 100")
    List<Integer> findStaleInFlightIds(@Param("staleBoundary") long staleBoundaryMs);

    @Select("SELECT p.messageId " +
            "FROM pendingPushes p " +
            "WHERE p.expires_at IS NOT NULL " +
            "  AND p.expires_at < #{now} " +
            "  AND p.executed_ack_at IS NULL " +
            "  AND p.failed_at IS NULL " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM mdm_command_audit a " +
            "    WHERE a.message_id = p.messageId " +
            "      AND a.event_type = 'STATE_TRANSITION' " +
            "      AND a.to_state = 'EXPIRED'" +
            "  ) " +
            "LIMIT 100")
    List<Integer> findExpiredIds(@Param("now") long nowMs);

    // HMDM-EVOLUTION F1.6: command tracking queries (via v_command_state view)

    @Select("<script>" +
            "SELECT messageId, pending_id AS pendingId, deviceId, messageType, payload, " +
            "       created_at AS createdAt, sent_at AS sentAt, delivered_ack_at AS deliveredAckAt, " +
            "       executed_ack_at AS executedAckAt, failed_at AS failedAt, expires_at AS expiresAt, " +
            "       retry_count AS retryCount, max_retries AS maxRetries, " +
            "       failure_code AS failureCode, failure_message AS failureMessage, " +
            "       device_sequence_num AS deviceSequenceNum, " +
            "       correlation_id::TEXT AS correlationId, " +
            "       created_by_user_id AS createdByUserId, created_by_source AS createdBySource, " +
            "       state " +
            "FROM v_command_state " +
            "<where>" +
            "  <if test='deviceId != null'>AND deviceId = #{deviceId}</if>" +
            "  <if test='state != null'>AND state = #{state}</if>" +
            "  <if test='messageType != null'>AND messageType = #{messageType}</if>" +
            "  <if test='since != null'>AND created_at &gt;= #{since}</if>" +
            "</where>" +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<CommandStateView> findCommands(@Param("deviceId") Integer deviceId,
                                         @Param("state") String state,
                                         @Param("messageType") String messageType,
                                         @Param("since") Long since,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    @Select("SELECT messageId, pending_id AS pendingId, deviceId, messageType, payload, " +
            "       created_at AS createdAt, sent_at AS sentAt, delivered_ack_at AS deliveredAckAt, " +
            "       executed_ack_at AS executedAckAt, failed_at AS failedAt, expires_at AS expiresAt, " +
            "       retry_count AS retryCount, max_retries AS maxRetries, " +
            "       failure_code AS failureCode, failure_message AS failureMessage, " +
            "       device_sequence_num AS deviceSequenceNum, " +
            "       correlation_id::TEXT AS correlationId, " +
            "       created_by_user_id AS createdByUserId, created_by_source AS createdBySource, " +
            "       state " +
            "FROM v_command_state WHERE messageId = #{messageId}")
    CommandStateView findCommandById(@Param("messageId") int messageId);

    @Select("SELECT state, COUNT(*) AS count FROM v_command_state " +
            "<script>" +
            "  <where>" +
            "    <if test='since != null'>AND created_at &gt;= #{since}</if>" +
            "    <if test='deviceId != null'>AND deviceId = #{deviceId}</if>" +
            "  </where>" +
            "</script>" +
            " GROUP BY state ORDER BY state")
    List<java.util.Map<String, Object>> countByState(@Param("since") Long since,
                                                       @Param("deviceId") Integer deviceId);

    // HMDM-EVOLUTION F2: ACK protocol state transitions (idempotent updates)

    @Update("UPDATE pendingPushes SET delivered_ack_at = #{ackAt} " +
            "WHERE messageId = #{messageId} AND delivered_ack_at IS NULL")
    int markDelivered(@Param("messageId") int messageId, @Param("ackAt") long ackAt);

    @Update("UPDATE pendingPushes SET executed_ack_at = #{ackAt} " +
            "WHERE messageId = #{messageId} AND executed_ack_at IS NULL AND failed_at IS NULL")
    int markExecuted(@Param("messageId") int messageId, @Param("ackAt") long ackAt);

    @Update("UPDATE pendingPushes SET failed_at = #{failedAt}, failure_code = #{code}, failure_message = #{msg} " +
            "WHERE messageId = #{messageId} AND executed_ack_at IS NULL AND failed_at IS NULL")
    int markFailed(@Param("messageId") int messageId,
                    @Param("failedAt") long failedAt,
                    @Param("code") String code,
                    @Param("msg") String msg);

    @Update("UPDATE pendingPushes SET status = 0, sendTime = NULL " +
            "WHERE messageId = #{messageId} AND status = 1 AND delivered_ack_at IS NULL")
    int resetStaleInFlight(@Param("messageId") int messageId);

    @Select("SELECT deviceId FROM pushMessages WHERE id = #{messageId}")
    Integer findDeviceIdByMessageId(@Param("messageId") int messageId);

    // F2 mitigation: stale IN_FLIGHT for devices WITH agent_supports_ack=TRUE only.
    // Legacy devices stay observed-only via findStaleInFlightIds (no reset).
    @Select("SELECT p.messageId " +
            "FROM pendingPushes p " +
            "INNER JOIN pushMessages m ON m.id = p.messageId " +
            "INNER JOIN devices d ON d.id = m.deviceId " +
            "WHERE p.status = 1 " +
            "  AND p.sendTime IS NOT NULL " +
            "  AND p.sendTime < #{staleBoundary} " +
            "  AND p.delivered_ack_at IS NULL " +
            "  AND d.agent_supports_ack = TRUE " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM mdm_command_audit a " +
            "    WHERE a.message_id = p.messageId " +
            "      AND a.event_type = 'RECONCILIATION' " +
            "      AND a.to_state = 'IN_FLIGHT_STALE_RESET' " +
            "      AND a.event_at > p.sendTime " +
            "  ) " +
            "LIMIT 100")
    List<Integer> findResetableStaleInFlightIds(@Param("staleBoundary") long staleBoundaryMs);

    // Mark device agent capability — called when agent sends X-Agent-Ack-Capable header.
    @Update("UPDATE devices SET agent_supports_ack = TRUE, " +
            "agent_supports_ack_since = COALESCE(agent_supports_ack_since, #{now}) " +
            "WHERE id = #{deviceId} AND agent_supports_ack = FALSE")
    int markDeviceAckCapable(@Param("deviceId") int deviceId, @Param("now") long now);
}
