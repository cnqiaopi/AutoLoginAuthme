package Mistral.storage;

import Mistral.model.PlayerSession;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSessionRepositoryTest {
    JdbcDataSource dataSource;
    JdbcSessionRepository repository;
    @BeforeEach void setup() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        repository = new JdbcSessionRepository(dataSource, () -> { });
    }
    @Test void savesReadsAndDeletesIgnoringCase() throws Exception {
        repository.save("Alice", new PlayerSession("203.0.113.1", "vanilla", 123));
        assertEquals(123, repository.find("ALICE").getAuthenticatedAt());
        repository.delete("aLiCe");
        assertNull(repository.find("Alice"));
    }
    @Test void readsAndNormalizesOldCaseSensitiveRecords() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO login_sessions VALUES ('Alice', '203.0.113.1', 'vanilla', 10), ('ALICE', '203.0.113.2', 'fabric', 20)");
        }
        assertEquals(20, repository.find("alice").getAuthenticatedAt());
        repository.save("Alice", new PlayerSession("203.0.113.3", "vanilla", 30));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM login_sessions")) { result.next(); assertEquals(1, result.getInt(1)); }
        assertEquals(30, repository.find("ALICE").getAuthenticatedAt());
    }
    @Test void failedInsertRollsBackDeletion() throws Exception {
        repository.save("Alice", new PlayerSession("203.0.113.1", "vanilla", 10));
        assertThrows(java.sql.SQLException.class, () -> repository.save("Alice", new PlayerSession("203.0.113.2", String.join("", java.util.Collections.nCopies(100, "a")), 20)));
        assertEquals(10, repository.find("Alice").getAuthenticatedAt());
    }
    @Test void schemaFailurePropagatesInsteadOfStartingPartially() {
        JdbcDataSource invalid = new JdbcDataSource();
        invalid.setURL("jdbc:h2:mem:invalid;IFEXISTS=TRUE");
        assertThrows(java.sql.SQLException.class, () -> new JdbcSessionRepository(invalid, () -> { }));
    }
}
