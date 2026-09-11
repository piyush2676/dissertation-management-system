package com.dms.search;

import java.util.List;

/**
 * What a search turned up, grouped by the kind of thing found.
 *
 * <p>Every hit carries the link the user is allowed to open. Nothing reaches this
 * record that the searcher may not already see -- scoping happens in the query,
 * not by filtering afterwards.
 */
public record SearchResults(String query, List<Group> groups) {

    public record Group(String label, List<Hit> hits) {
        public boolean isEmpty() {
            return hits.isEmpty();
        }
    }

    public record Hit(String title, String subtitle, String link, String badge) {
        public boolean hasBadge() {
            return badge != null && !badge.isBlank();
        }
    }

    public static SearchResults empty(String query) {
        return new SearchResults(query, List.of());
    }

    public int total() {
        return groups.stream().mapToInt(g -> g.hits().size()).sum();
    }

    public boolean isEmpty() {
        return total() == 0;
    }
}
