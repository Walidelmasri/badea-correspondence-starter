package org.badea.correspondence.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DirectoryUsernameTest {

    @Test
    void normalizesPlainUsername() {
        assertEquals(
            "adil.amin",
            new DirectoryUsername("Adil.Amin").value()
        );
    }

    @Test
    void normalizesUpnLikeDirectoryValueToAccountName() {
        assertEquals(
            "adil.amin",
            new DirectoryUsername("adil.amin@badea.org").value()
        );
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(
            "adil.amin",
            new DirectoryUsername("  adil.amin  ").value()
        );
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DirectoryUsername("   ")
        );
    }

    @Test
    void rejectsNullUsername() {
        assertThrows(
            NullPointerException.class,
            () -> new DirectoryUsername(null)
        );
    }

    @Test
    void rejectsMissingAccountName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DirectoryUsername("@badea.org")
        );
    }

    @Test
    void rejectsMultipleAtSigns() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DirectoryUsername(
                "adil.amin@badea.org@invalid"
            )
        );
    }
}