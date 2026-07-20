package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.GZIPInputStream;

/**
 * V21 — load the global LOINC master catalog (lab_service_catalog) on boot.
 *
 * A Java Flyway migration rather than a SQL one because the data is ~60k rows:
 * as raw INSERTs it is a ~24 MB migration that Flyway would checksum on every
 * boot and every clone would carry. Instead the rows ship as a 3.7 MB gzipped
 * CSV resource (db/data/lab_service_catalog.csv.gz) and stream in through
 * PostgreSQL COPY — 9x smaller, loads in ~1s. The resource is produced from the
 * licensed LOINC release by db/seeds/gen_lab_service_catalog.py.
 *
 * Runs automatically and exactly once per database (Flyway never re-runs a
 * successful versioned migration), so the catalog is populated by simply
 * deploying — no hand-run seed step. Flyway wraps this in a transaction, so a
 * mid-COPY failure rolls back cleanly and leaves the table empty for a retry.
 *
 * The count guard makes it safe on a database that was already hand-populated
 * (e.g. a dev box): if rows exist, it no-ops instead of double-loading. COPY
 * uses NULL '' so blank CSV fields become NULL for the nullable columns.
 */
public class V21__load_lab_service_catalog extends BaseJavaMigration {

    private static final String RESOURCE = "/db/data/lab_service_catalog.csv.gz";

    private static final String COPY_SQL =
            "COPY lab_service_catalog "
          + "(loinc_code, test_code, name, aliases, category, discipline, "
          + " specimen_kind, default_unit, value_type, is_panel) "
          + "FROM STDIN WITH (FORMAT csv, NULL '')";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM lab_service_catalog")) {
            rs.next();
            if (rs.getLong(1) > 0) {
                return;  // already populated — nothing to do
            }
        }

        InputStream resource = getClass().getResourceAsStream(RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Missing classpath resource " + RESOURCE
                    + " — regenerate it with db/seeds/gen_lab_service_catalog.py");
        }

        CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
        try (GZIPInputStream gz = new GZIPInputStream(resource)) {
            long rows = copyManager.copyIn(COPY_SQL, gz);
            System.out.println("V21: loaded " + rows + " lab_service_catalog rows");
        }
    }
}
