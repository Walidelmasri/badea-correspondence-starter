package org.badea.correspondence.infrastructure.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import oracle.ucp.jdbc.PoolDataSource;

import org.junit.jupiter.api.Test;

class OracleEmployeeDirectoryDataSourceFactoryTest {

    @Test
    void createsOraclePoolDataSource() {
        PoolDataSource dataSource =
            OracleEmployeeDirectoryDataSourceFactory.create(
                "jdbc:oracle:thin:@//db.example:1521/BADEA",
                "STRATEGYKPI",
                "secret"
            );

        assertEquals(
            "oracle.jdbc.pool.OracleDataSource",
            dataSource.getConnectionFactoryClassName()
        );

        assertEquals(
            "jdbc:oracle:thin:@//db.example:1521/BADEA",
            dataSource.getURL()
        );

        assertEquals(
            "STRATEGYKPI",
            dataSource.getUser()
        );
    }

    @Test
    void trimsJdbcUrlAndUsername() {
        PoolDataSource dataSource =
            OracleEmployeeDirectoryDataSourceFactory.create(
                "  jdbc:oracle:thin:@//db.example:1521/BADEA  ",
                "  STRATEGYKPI  ",
                "secret"
            );

        assertEquals(
            "jdbc:oracle:thin:@//db.example:1521/BADEA",
            dataSource.getURL()
        );

        assertEquals(
            "STRATEGYKPI",
            dataSource.getUser()
        );
    }

    @Test
    void rejectsNullJdbcUrl() {
        assertThrows(
            NullPointerException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                null,
                "STRATEGYKPI",
                "secret"
            )
        );
    }

    @Test
    void rejectsBlankJdbcUrl() {
        assertThrows(
            IllegalArgumentException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                "   ",
                "STRATEGYKPI",
                "secret"
            )
        );
    }

    @Test
    void rejectsNonOracleJdbcUrl() {
        assertThrows(
            IllegalArgumentException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                "jdbc:postgresql://db.example:5432/badea",
                "STRATEGYKPI",
                "secret"
            )
        );
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(
            IllegalArgumentException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                "jdbc:oracle:thin:@//db.example:1521/BADEA",
                "   ",
                "secret"
            )
        );
    }

    @Test
    void rejectsNullPassword() {
        assertThrows(
            NullPointerException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                "jdbc:oracle:thin:@//db.example:1521/BADEA",
                "STRATEGYKPI",
                null
            )
        );
    }

    @Test
    void rejectsEmptyPassword() {
        assertThrows(
            IllegalArgumentException.class,
            () -> OracleEmployeeDirectoryDataSourceFactory.create(
                "jdbc:oracle:thin:@//db.example:1521/BADEA",
                "STRATEGYKPI",
                ""
            )
        );
    }
}