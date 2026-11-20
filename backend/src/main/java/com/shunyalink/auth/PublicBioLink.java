package com.shunyalink.auth;

import com.shunyalink.url.UrlStatsResponse;

/**
 * A link as a public bio page shows it: an allowlist, not the owner's full stats. Visitors follow the short URL,
 * where a password (and any block) is enforced, so the destination is never part of a public response.
 */
public record PublicBioLink(String shortId, String title, String category) {

    public static PublicBioLink of(UrlStatsResponse link) {
        return new PublicBioLink(link.getShortId(), link.getTitle(), link.getCategory());
    }
}
