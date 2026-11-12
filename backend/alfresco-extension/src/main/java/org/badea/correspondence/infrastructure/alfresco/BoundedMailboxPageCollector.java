package org.badea.correspondence.infrastructure.alfresco;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/** Retains only the requested page and earlier sort candidates, never all matching summaries.
 * Exact totals still require traversing authorized repository metadata; a true indexed
 * query would need a separate authorization-preserving search projection.
 */
final class BoundedMailboxPageCollector<T> {
    private final Comparator<? super T> bestFirst;
    private final PriorityQueue<T> best;
    private final int offset;
    private final int limit;
    private final long capacity;
    private int total;

    BoundedMailboxPageCollector(int offset, int limit, Comparator<? super T> bestFirst) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("Invalid paging");
        this.offset = offset;
        this.limit = limit;
        this.capacity = (long) offset + limit;
        this.bestFirst = Objects.requireNonNull(bestFirst);
        this.best = new PriorityQueue<>(Math.max(1, Math.min(32, limit)), bestFirst.reversed());
    }

    void add(T item) {
        Objects.requireNonNull(item);
        if (total == Integer.MAX_VALUE) throw new IllegalStateException("Mailbox count overflow");
        total++;
        if (best.size() < capacity) {
            best.add(item);
        } else if (bestFirst.compare(item, best.peek()) < 0) {
            best.poll();
            best.add(item);
        }
    }

    int total() { return total; }

    List<T> page() {
        if (offset >= total) return List.of();
        List<T> sorted = new ArrayList<>(best);
        sorted.sort(bestFirst);
        return List.copyOf(sorted.subList(Math.min(offset, sorted.size()), sorted.size()));
    }
}
