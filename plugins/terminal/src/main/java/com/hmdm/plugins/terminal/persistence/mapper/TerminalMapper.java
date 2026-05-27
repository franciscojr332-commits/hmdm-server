package com.hmdm.plugins.terminal.persistence.mapper;

import com.hmdm.plugins.terminal.persistence.domain.TerminalCommand;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSession;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSnippet;
import com.hmdm.plugins.terminal.rest.json.TerminalDeviceStatus;
import com.hmdm.plugins.terminal.rest.json.TerminalOutputEntry;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface TerminalMapper {

    // ─── Sessions ─────────────────────────────────────────────────────
    @Insert("INSERT INTO plugin_terminal_sessions (customerid, userid, startedat, label) " +
            "VALUES (#{customerId}, #{userId}, #{startedAt}, #{label})")
    @SelectKey(statement = "SELECT currval('plugin_terminal_sessions_id_seq')",
            keyColumn = "id", keyProperty = "id", before = false, resultType = int.class)
    void insertSession(TerminalSession session);

    @Update("UPDATE plugin_terminal_sessions SET endedat = #{endedAt} WHERE id = #{id}")
    void endSession(@Param("id") int sessionId, @Param("endedAt") long endedAt);

    @Select("SELECT id, customerid AS customerId, userid AS userId, startedat AS startedAt, " +
            "       endedat AS endedAt, label " +
            "FROM plugin_terminal_sessions WHERE id = #{id}")
    TerminalSession getSessionById(@Param("id") int sessionId);

    // ─── Commands history ─────────────────────────────────────────────
    @Insert("INSERT INTO plugin_terminal_commands " +
            "(sessionid, deviceid, command, messagetype, messageid, sentat, status) " +
            "VALUES (#{sessionId}, #{deviceId}, #{command}, #{messageType}, #{messageId}, #{sentAt}, #{status})")
    @SelectKey(statement = "SELECT currval('plugin_terminal_commands_id_seq')",
            keyColumn = "id", keyProperty = "id", before = false, resultType = long.class)
    void insertCommand(TerminalCommand cmd);

    @Update("UPDATE plugin_terminal_commands SET status = #{status}, completedat = #{completedAt} " +
            "WHERE messageid = #{messageId}")
    void updateCommandStatus(@Param("messageId") int messageId,
                              @Param("status") String status,
                              @Param("completedAt") long completedAt);

    @Select("SELECT id, sessionid AS sessionId, deviceid AS deviceId, command, " +
            "       messagetype AS messageType, messageid AS messageId, " +
            "       sentat AS sentAt, completedat AS completedAt, status " +
            "FROM plugin_terminal_commands " +
            "WHERE sessionid = #{sessionId} " +
            "ORDER BY sentat ASC")
    List<TerminalCommand> getCommandsBySession(@Param("sessionId") int sessionId);

    @Select("<script>" +
            "SELECT c.id, c.sessionid AS sessionId, c.deviceid AS deviceId, c.command, " +
            "       c.messagetype AS messageType, c.messageid AS messageId, " +
            "       c.sentat AS sentAt, c.completedat AS completedAt, c.status, " +
            "       d.number AS deviceNumber " +
            "FROM plugin_terminal_commands c " +
            "JOIN plugin_terminal_sessions s ON s.id = c.sessionid " +
            "LEFT JOIN devices d ON d.id = c.deviceid " +
            "WHERE s.customerid = #{customerId} " +
            "  <if test=\"userId != null\">AND s.userid = #{userId}</if>" +
            "  <if test=\"deviceId != null\">AND c.deviceid = #{deviceId}</if>" +
            "  <if test=\"sinceMs != null\">AND c.sentat &gt;= #{sinceMs}</if>" +
            "  <if test=\"search != null and search != ''\">AND c.command ILIKE '%' || #{search} || '%'</if>" +
            "ORDER BY c.sentat DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<java.util.Map<String, Object>> searchHistory(@Param("customerId") int customerId,
                                                       @Param("userId") Integer userId,
                                                       @Param("deviceId") Integer deviceId,
                                                       @Param("sinceMs") Long sinceMs,
                                                       @Param("search") String search,
                                                       @Param("limit") int limit);

    @Delete("DELETE FROM plugin_terminal_commands WHERE sentat &lt; #{cutoffMs}")
    int purgeOldCommands(@Param("cutoffMs") long cutoffMs);

    @Delete("DELETE FROM plugin_terminal_sessions WHERE endedat IS NOT NULL AND endedat &lt; #{cutoffMs}")
    int purgeOldSessions(@Param("cutoffMs") long cutoffMs);

    // ─── Snippets ─────────────────────────────────────────────────────
    @Select("SELECT id, customerid AS customerId, category, label, commands, " +
            "       messagetype AS messageType, payloadtemplate AS payloadTemplate, " +
            "       destructive, sortorder AS sortOrder, createdat AS createdAt, createdby AS createdBy " +
            "FROM plugin_terminal_snippets " +
            "WHERE customerid IS NULL OR customerid = #{customerId} " +
            "ORDER BY category, sortorder, label")
    List<TerminalSnippet> getSnippets(@Param("customerId") int customerId);

    @Insert("INSERT INTO plugin_terminal_snippets " +
            "(customerid, category, label, commands, messagetype, payloadtemplate, destructive, sortorder, createdat, createdby) " +
            "VALUES (#{customerId}, #{category}, #{label}, #{commands}, #{messageType}, #{payloadTemplate}, " +
            "        #{destructive}, #{sortOrder}, #{createdAt}, #{createdBy})")
    @SelectKey(statement = "SELECT currval('plugin_terminal_snippets_id_seq')",
            keyColumn = "id", keyProperty = "id", before = false, resultType = int.class)
    void insertSnippet(TerminalSnippet snippet);

    @Update("UPDATE plugin_terminal_snippets " +
            "SET category = #{category}, label = #{label}, commands = #{commands}, " +
            "    messagetype = #{messageType}, payloadtemplate = #{payloadTemplate}, " +
            "    destructive = #{destructive}, sortorder = #{sortOrder} " +
            "WHERE id = #{id} AND (customerid IS NULL OR customerid = #{customerId})")
    void updateSnippet(TerminalSnippet snippet);

    @Delete("DELETE FROM plugin_terminal_snippets " +
            "WHERE id = #{id} AND customerid = #{customerId}")
    void deleteSnippet(@Param("id") int id, @Param("customerId") int customerId);

    // ─── Command status realtime ───────────────────────────────────────
    @Select("<script>" +
            "SELECT pm.id AS messageId, pm.deviceid AS deviceId, pm.messagetype AS messageType, " +
            "       pp.status AS deliveryStatus, " +
            "       pp.createtime AS enqueuedAt, " +
            "       pp.sendtime AS sentTime, " +
            "       pp.delivered_ack_at AS deliveredAt, " +
            "       pp.executed_ack_at AS executedAt, " +
            "       pp.failed_at AS failedAt, " +
            "       pp.failure_code AS failureCode " +
            "FROM pushmessages pm " +
            "LEFT JOIN pendingpushes pp ON pp.messageid = pm.id " +
            "WHERE pm.id IN " +
            "<foreach collection=\"messageIds\" item=\"id\" open=\"(\" close=\")\" separator=\",\">#{id}</foreach>" +
            "</script>")
    List<java.util.Map<String, Object>> getMessagesStatus(@Param("messageIds") List<Integer> messageIds);

    // ─── Favorites ─────────────────────────────────────────────────────
    @Select("SELECT snippetid FROM plugin_terminal_snippet_favorites WHERE userid = #{userId}")
    List<Integer> getFavoriteSnippetIds(@Param("userId") int userId);

    @Insert("INSERT INTO plugin_terminal_snippet_favorites (userid, snippetid) " +
            "VALUES (#{userId}, #{snippetId}) " +
            "ON CONFLICT (userid, snippetid) DO NOTHING")
    void addFavorite(@Param("userId") int userId, @Param("snippetId") int snippetId);

    @Delete("DELETE FROM plugin_terminal_snippet_favorites " +
            "WHERE userid = #{userId} AND snippetid = #{snippetId}")
    void removeFavorite(@Param("userId") int userId, @Param("snippetId") int snippetId);

    // ─── Output stream (read from plugin_devicelog_log) ───────────────
    @Select("<script>" +
            "SELECT l.id, l.deviceid AS deviceId, d.number AS deviceNumber, " +
            "       l.createtime AS ts, l.severity, l.message, " +
            "       CASE " +
            "         WHEN l.message LIKE 'Executed a command:%' THEN 'exec_result' " +
            "         WHEN l.message LIKE 'Got Push Message%' THEN 'push_received' " +
            "         WHEN l.message LIKE 'Silently install%' OR l.message LIKE 'Silently uninstall%' THEN 'app_op' " +
            "         WHEN l.severity = 'ERROR' THEN 'error' " +
            "         ELSE 'log' " +
            "       END AS kind " +
            "FROM plugin_devicelog_log l " +
            "JOIN devices d ON d.id = l.deviceid " +
            "WHERE l.createtime &gt; #{sinceMs} " +
            "  <if test=\"deviceIds != null and deviceIds.size() &gt; 0\">" +
            "    AND l.deviceid IN " +
            "    <foreach collection=\"deviceIds\" item=\"id\" open=\"(\" close=\")\" separator=\",\">#{id}</foreach>" +
            "  </if>" +
            "  AND l.severity &lt;&gt; 'VERBOSE' " +
            "ORDER BY l.createtime ASC " +
            "LIMIT #{maxRows}" +
            "</script>")
    List<TerminalOutputEntry> getOutputSince(@Param("sinceMs") long sinceMs,
                                              @Param("deviceIds") List<Integer> deviceIds,
                                              @Param("maxRows") int maxRows);

    // ─── Device status for picker ─────────────────────────────────────
    @Select("<script>" +
            "SELECT d.id, d.number, d.description, " +
            "       d.configurationid AS configurationId, " +
            "       c.name AS configurationName, " +
            "       (SELECT MAX(l.createtime) FROM plugin_devicelog_log l WHERE l.deviceid = d.id) AS lastPollMs, " +
            "       (SELECT COUNT(*) FROM pendingpushes pp " +
            "          JOIN pushmessages pm ON pm.id = pp.messageid " +
            "          WHERE pm.deviceid = d.id AND pp.status = 0) AS pendingCount " +
            "FROM devices d " +
            "LEFT JOIN configurations c ON c.id = d.configurationid " +
            "WHERE d.customerid = #{customerId} " +
            "  <if test=\"configurationId != null\">AND d.configurationid = #{configurationId}</if>" +
            "ORDER BY d.number" +
            "</script>")
    List<TerminalDeviceStatus> getDevices(@Param("customerId") int customerId,
                                           @Param("configurationId") Integer configurationId);
}
