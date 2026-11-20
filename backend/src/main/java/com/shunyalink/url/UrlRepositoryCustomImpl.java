package com.shunyalink.url;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Data finds this class by name and merges it into {@link UrlRepository}. Reordering used to send one UPDATE
 * per link (a few hundred round trips to the database in Sydney for a long list); PostgreSQL's {@code unnest} turns
 * two arrays into rows, so a single UPDATE sets every position.
 */
class UrlRepositoryCustomImpl implements UrlRepositoryCustom {

    private static final String REORDER = """
            UPDATE urls SET order_index = v.position
            FROM unnest(?::varchar[], ?::int[]) AS v(short_id, position)
            WHERE urls.short_id = v.short_id AND urls.user_id = ?""";

    private final JdbcTemplate jdbc;

    UrlRepositoryCustomImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int reorder(Long userId, List<String> shortIdsInOrder) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < shortIdsInOrder.size(); i++) {
            positions.put(shortIdsInOrder.get(i), i); // a repeated ID keeps its last position
        }
        if (positions.isEmpty()) {
            return 0;
        }
        String[] shortIds = positions.keySet().toArray(String[]::new);
        Integer[] order = positions.values().toArray(Integer[]::new);
        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(REORDER);
            statement.setArray(1, connection.createArrayOf("varchar", shortIds));
            statement.setArray(2, connection.createArrayOf("integer", order));
            statement.setLong(3, userId);
            return statement;
        });
    }
}
