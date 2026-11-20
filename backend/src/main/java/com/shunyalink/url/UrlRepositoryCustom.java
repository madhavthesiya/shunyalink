package com.shunyalink.url;

import java.util.List;

/** Queries that Spring Data can't derive: implemented with plain SQL in {@link UrlRepositoryCustomImpl}. */
public interface UrlRepositoryCustom {

    /**
     * Sets each listed link's position on the user's bio page to its index in the list, in one statement. Links the
     * user doesn't own are ignored. If a short ID is listed twice, its last position wins, as before.
     *
     * @return how many links were updated
     */
    int reorder(Long userId, List<String> shortIdsInOrder);
}
