package org.badea.correspondence.infrastructure.oracle;

import java.sql.SQLException;
import java.util.Objects;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * Creates the Oracle UCP data source used exclusively by the
 * BADEA employee-directory integration.
 *
 * <p>Connection settings are supplied externally. This class does not
 * read environment variables, Spring properties, or committed
 * configuration files.</p>
 */
public final class OracleEmployeeDirectoryDataSourceFactory {

    private static final String CONNECTION_FACTORY_CLASS =
        "oracle.jdbc.pool.OracleDataSource";

    private OracleEmployeeDirectoryDataSourceFactory() {
    }

    public static PoolDataSource create(
        String jdbcUrl,
        String username,
        String password
    ) {
        String normalizedJdbcUrl = requireText(
            jdbcUrl,
            "Oracle JDBC URL"
        );

        String normalizedUsername = requireText(
            username,
            "Oracle username"
        );

        String validatedPassword = requirePassword(password);

        if (!normalizedJdbcUrl.startsWith("jdbc:oracle:")) {
            throw new IllegalArgumentException(
                "Oracle JDBC URL must start with 'jdbc:oracle:'."
            );
        }

        try {
            PoolDataSource dataSource =
                PoolDataSourceFactory.getPoolDataSource();

            dataSource.setConnectionFactoryClassName(
                CONNECTION_FACTORY_CLASS
            );

            dataSource.setURL(normalizedJdbcUrl);
            dataSource.setUser(normalizedUsername);

            /*
             * Do not trim or otherwise modify passwords.
             * The supplied secret must reach Oracle exactly as provided.
             */
            dataSource.setPassword(validatedPassword);

            return dataSource;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to configure Oracle employee-directory "
                    + "data source.",
                exception
            );
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        Objects.requireNonNull(
            value,
            fieldName + " must not be null."
        );

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank."
            );
        }

        return normalized;
    }

    private static String requirePassword(String password) {
        Objects.requireNonNull(
            password,
            "Oracle password must not be null."
        );

        if (password.isEmpty()) {
            throw new IllegalArgumentException(
                "Oracle password must not be empty."
            );
        }

        return password;
    }
}