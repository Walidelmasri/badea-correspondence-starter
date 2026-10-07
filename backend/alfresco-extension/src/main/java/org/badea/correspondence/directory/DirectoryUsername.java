package org.badea.correspondence.directory;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized username used to bind an authenticated Alfresco user
 * to the BADEA employee directory.
 *
 * <p>Alfresco exposes the AD account name (for example {@code adil.amin}),
 * while the employee directory may store the same account as a UPN-like
 * value (for example {@code adil.amin@badea.org}). The domain identity is
 * the normalized account name, not an email address.</p>
 */
public record DirectoryUsername(String value) {

    public DirectoryUsername {
        Objects.requireNonNull(value, "Directory username must not be null.");

        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                "Directory username must not be blank."
            );
        }

        int atIndex = value.indexOf('@');

        if (atIndex >= 0) {
            if (atIndex == 0 || atIndex != value.lastIndexOf('@')) {
                throw new IllegalArgumentException(
                    "Directory username has an invalid format."
                );
            }

            value = value.substring(0, atIndex);
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                "Directory username must contain an account name."
            );
        }
    }
}