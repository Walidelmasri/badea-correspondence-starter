package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoundedMailboxPageCollectorTest {
    @Test
    void returnsOnlyRequestedPageWithAccurateTotalAndStableSort() {
        for (int offset = 0; offset <= 110; offset += 10) {
            var selection = new BoundedMailboxPageCollector<>(offset, 10,
                Comparator.<Integer>naturalOrder());
            for (int number = 100; number >= 1; number--) selection.add(number);
            assertEquals(100, selection.total());
            assertEquals(IntStream.rangeClosed(offset + 1, Math.min(offset + 10, 100))
                .boxed().toList(), selection.page());
        }
    }

    @Test
    void ordersNewestFirstAndUsesSecondaryKeyOnTies() {
        record Item(int modified, String id) { }
        var selection = new BoundedMailboxPageCollector<>(1, 2,
            Comparator.comparingInt(Item::modified).reversed().thenComparing(Item::id));
        selection.add(new Item(5, "z"));
        selection.add(new Item(1, "a"));
        selection.add(new Item(5, "a"));
        selection.add(new Item(5, "b"));
        assertEquals(List.of(new Item(5, "b"), new Item(5, "z")), selection.page());
        assertEquals(4, selection.total());
    }

    @Test
    void doesNotInventItemsForEmptyOrOutOfRangePages() {
        var selection = new BoundedMailboxPageCollector<>(20, 10, Comparator.<Integer>naturalOrder());
        for (int item = 0; item < 3; item++) selection.add(item);
        assertEquals(List.of(), selection.page());
        assertEquals(3, selection.total());
        assertThrows(IllegalArgumentException.class,
            () -> new BoundedMailboxPageCollector<>(-1, 10, Comparator.<Integer>naturalOrder()));
    }
}
