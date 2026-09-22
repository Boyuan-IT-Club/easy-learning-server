package com.earlylearning.early_learning_server;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DatabaseMigrationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void migrationCreatesCloudTablesAndDoesNotRunTwice() {
        var tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                String.class);
        assertTrue(tables.containsAll(Set.of("admin_account", "user_account", "user_license",
                "storage_cloud_file", "grammar", "course", "assessment_material", "dict_entry", "dict_gloss")));
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    @Transactional
    void usernamesAreUniqueAndCaseSensitiveAndStatusesAreRestricted() {
        String name = "Migration" + UUID.randomUUID();
        String adminInsert = "INSERT INTO admin_account (username, password_hash, status) VALUES (?, 'test-hash', ?)";
        jdbc.update(adminInsert, name, "ACTIVE");
        jdbc.update(adminInsert, name.toLowerCase(), "DISABLED");
        assertSqlError(1062, () -> jdbc.update(adminInsert, name, "ACTIVE"));
        assertSqlError(3819, () -> jdbc.update(adminInsert, name + "invalid", "active"));

        String userInsert = "INSERT INTO user_account (username, status) VALUES (?, ?)";
        jdbc.update(userInsert, name, 1);
        jdbc.update(userInsert, name.toLowerCase(), 0);
        assertSqlError(1062, () -> jdbc.update(userInsert, name, 1));
        assertSqlError(3819, () -> jdbc.update(userInsert, name + "invalid", 2));
    }

    @Test
    @Transactional
    void contentVersionsAreDistinctAndJsonMustBeValid() {
        String code = "Content" + UUID.randomUUID();
        for (String table : Set.of("course", "assessment_material")) {
            String codeColumn = table.equals("course") ? "official_course_code" : "official_material_code";
            String insert = "INSERT INTO " + table + " (" + codeColumn
                    + ", content_version, name, activity_configs_json, status) VALUES (?, ?, '测试课程', ?, ?)";
            // ActivityConfig 的业务完整性由业务模块校验；此处验证 MySQL JSON 类型及版本唯一性。
            String config = "{\"schema_version\":2,\"activities\":[]}";
            jdbc.update(insert, code, "v1", config, "ACTIVE");
            jdbc.update(insert, code, "v2", config, "DISABLED");
            jdbc.update(insert, code.toLowerCase(), "v1", config, "ACTIVE");
            assertSqlError(1062, () -> jdbc.update(insert, code, "v1", config, "ACTIVE"));
            assertSqlError(3140, () -> jdbc.update(insert, code, "invalid-json", "{", "ACTIVE"));
            assertSqlError(3819, () -> jdbc.update(insert, code, "invalid-status", config, "DRAFT"));
        }
    }

    @Test
    @Transactional
    void licensesStartUnusedAndKeepAccountReferences() {
        String value = "License" + UUID.randomUUID();
        jdbc.update("INSERT INTO user_license (activation_code_hash) VALUES (?)", value);
        var license = jdbc.queryForMap(
                "SELECT user_id, activated_at, status FROM user_license WHERE activation_code_hash = ?", value);
        assertEquals("UNUSED", license.get("status"));
        assertEquals(null, license.get("user_id"));
        assertEquals(null, license.get("activated_at"));
        assertSqlError(1062, () -> jdbc.update(
                "INSERT INTO user_license (activation_code_hash) VALUES (?)", value));
        assertSqlError(1452, () -> jdbc.update(
                "UPDATE user_license SET user_id = -1 WHERE activation_code_hash = ?", value));
        assertSqlError(3819, () -> jdbc.update(
                "UPDATE user_license SET status = 'DISABLED' WHERE activation_code_hash = ?", value));

        jdbc.update("INSERT INTO user_account (username, status) VALUES (?, 1)", value);
        Integer userId = jdbc.queryForObject("SELECT id FROM user_account WHERE username = ?", Integer.class, value);
        jdbc.update("UPDATE user_license SET user_id = ?, status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP"
                + " WHERE activation_code_hash = ?", userId, value);
        assertNotNull(jdbc.queryForObject("SELECT activated_at FROM user_license WHERE activation_code_hash = ?",
                java.sql.Timestamp.class, value));
        assertSqlError(1451, () -> jdbc.update("DELETE FROM user_account WHERE id = ?", userId));
    }

    @Test
    @Transactional
    void filesAndGrammarKeepStableCodesAndReferences() {
        String code = "File" + UUID.randomUUID();
        String fileInsert = "INSERT INTO storage_cloud_file"
                + " (file_code, object_key, file_kind, file_name, mime_type, size_bytes, status)"
                + " VALUES (?, ?, 'IMAGE', '测试.png', 'image/png', 1, 'UPLOADING')";
        jdbc.update(fileInsert, code, code);
        jdbc.update(fileInsert, code.toLowerCase(), code.toLowerCase());
        assertSqlError(1062, () -> jdbc.update(fileInsert, code, code + "other"));
        assertSqlError(1062, () -> jdbc.update(fileInsert, code + "other", code));
        Integer fileId = jdbc.queryForObject("SELECT id FROM storage_cloud_file WHERE file_code = ?", Integer.class, code);
        String grammarInsert = "INSERT INTO grammar (grammar_code, name, version, icon_file_id, status)"
                + " VALUES (?, '语法测试', 1, ?, 'ACTIVE')";
        jdbc.update(grammarInsert, code, fileId);
        jdbc.update(grammarInsert, code.toLowerCase(), fileId);
        assertSqlError(1062, () -> jdbc.update(grammarInsert, code, fileId));
        assertSqlError(1452, () -> jdbc.update(grammarInsert, code + "invalid", -1));
        assertSqlError(1451, () -> jdbc.update("DELETE FROM storage_cloud_file WHERE id = ?", fileId));
        assertSqlError(3819, () -> jdbc.update("UPDATE storage_cloud_file SET file_kind = 'BACKUP' WHERE id = ?", fileId));
        assertSqlError(3819, () -> jdbc.update("UPDATE storage_cloud_file SET status = 'TEMP' WHERE id = ?", fileId));
        assertSqlError(3819, () -> jdbc.update("UPDATE grammar SET status = 'DELETED' WHERE grammar_code = ?", code));
    }

    @Test
    @Transactional
    void dictionaryGlossesRequireExistingCourseAndEntry() {
        String code = "Dict" + UUID.randomUUID();
        String entryInsert = "INSERT INTO dict_entry (entry_code, term, pinyin, definition)"
                + " VALUES (?, '你好', 'ni hao', '测试释义')";
        jdbc.update(entryInsert, code);
        jdbc.update(entryInsert, code.toLowerCase());
        assertSqlError(1062, () -> jdbc.update(entryInsert, code));
        Integer entryId = jdbc.queryForObject("SELECT id FROM dict_entry WHERE entry_code = ?", Integer.class, code);
        jdbc.update("INSERT INTO course (official_course_code, content_version, name, activity_configs_json, status)"
                + " VALUES (?, 'v1', '测试课程', JSON_OBJECT(), 'ACTIVE')", code);
        Integer courseId = jdbc.queryForObject("SELECT id FROM course WHERE official_course_code = ?", Integer.class, code);
        String glossInsert = "INSERT INTO dict_gloss (course_id, entry_id, start_offset, end_offset) VALUES (?, ?, 0, 2)";
        jdbc.update(glossInsert, courseId, entryId);
        assertSqlError(1452, () -> jdbc.update(glossInsert, -1, entryId));
        assertSqlError(1452, () -> jdbc.update(glossInsert, courseId, -1));
        assertSqlError(1451, () -> jdbc.update("DELETE FROM course WHERE id = ?", courseId));
        assertSqlError(1451, () -> jdbc.update("DELETE FROM dict_entry WHERE id = ?", entryId));
    }

    private void assertSqlError(int expectedCode, Runnable operation) {
        // MySQL CHECK 错误 3819 的 SQLState 为 HY000，Spring 可能包装为 UncategorizedSQLException。
        // 以数据库错误码判断实际约束失败，避免依赖 Spring 的异常分类。
        var exception = assertThrows(DataAccessException.class, operation::run);
        assertTrue(exception.getMostSpecificCause() instanceof SQLException);
        assertEquals(expectedCode, ((SQLException) exception.getMostSpecificCause()).getErrorCode());
    }
}
