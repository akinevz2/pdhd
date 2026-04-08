package ac.uk.sussex.kn253.services;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Applies lightweight, idempotent compatibility migrations for legacy SQLite
 * schemas.
 *
 * <p>
 * Some persisted local databases were created before newer embedding fields
 * were
 * introduced. Hibernate's schema-update step can then warn when creating
 * indexes
 * on columns that do not yet exist. This service patches the existing
 * {@code embedding_chunk} table so subsequent startups are clean and indexing
 * can
 * proceed reliably.
 */
@Startup
@ApplicationScoped
public class SqliteSchemaCompatibilityService {

    private static final Logger LOG = Logger.getLogger(SqliteSchemaCompatibilityService.class.getName());
    private static final String TABLE_EMBEDDING_CHUNK = "embedding_chunk";
    private static final String COLUMN_CONTENT_HASH = "content_hash";
    private static final String COLUMN_LEGACY_CONTENT_HASH = "contentHash";
    private static final String COLUMN_MODEL_NAME = "model_name";
    private static final String COLUMN_CHUNKER_VERSION = "chunker_version";

    @Inject
    DataSource dataSource;

    @PostConstruct
    void applyCompatibilityMigrations() {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, TABLE_EMBEDDING_CHUNK)) {
                return;
            }

            final Set<String> columns = columns(connection, TABLE_EMBEDDING_CHUNK);
            final boolean hasLegacyContentHash = columns.contains(COLUMN_LEGACY_CONTENT_HASH);
            final boolean hasContentHash = addColumnIfMissing(connection, columns, COLUMN_CONTENT_HASH,
                    "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, columns, COLUMN_MODEL_NAME, "TEXT NOT NULL DEFAULT 'unknown'");
            addColumnIfMissing(connection, columns, COLUMN_CHUNKER_VERSION, "TEXT NOT NULL DEFAULT 'v1'");

            if (hasLegacyContentHash && hasContentHash) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE embedding_chunk SET content_hash = contentHash WHERE (content_hash IS NULL OR content_hash = '') AND contentHash IS NOT NULL");
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX IF NOT EXISTS idx_chunk_project ON embedding_chunk (project_id)");
                statement.execute(
                        "CREATE INDEX IF NOT EXISTS idx_chunk_content_hash ON embedding_chunk (content_hash)");
            }
        } catch (final Exception e) {
            LOG.warning("SQLite schema compatibility migration skipped: " + e);
        }
    }

    private static boolean addColumnIfMissing(final Connection connection, final Set<String> columns,
            final String name, final String ddl) throws Exception {
        if (columns.contains(name)) {
            return true;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE embedding_chunk ADD COLUMN " + name + " " + ddl);
            columns.add(name);
        }
        return true;
    }

    private static boolean tableExists(final Connection connection, final String tableName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Set<String> columns(final Connection connection, final String tableName) throws Exception {
        final Set<String> names = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }
}