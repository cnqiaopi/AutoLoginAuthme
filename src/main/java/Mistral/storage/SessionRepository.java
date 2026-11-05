package Mistral.storage;

import Mistral.model.PlayerSession;
import java.sql.SQLException;

public interface SessionRepository extends AutoCloseable {
    PlayerSession find(String playerName) throws SQLException;
    void save(String playerName, PlayerSession session) throws SQLException;
    void delete(String playerName) throws SQLException;
    @Override void close();
}
