package com.shunyalink.url;

/** Just what the daily Safe Browsing re-check needs about a link that still redirects. */
public record ActiveLink(Long id, String shortId, String longUrl) {
}
