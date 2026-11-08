package com.shunyalink.url;

/** Snapshot of the link whose authorization was checked, even if its alias is subsequently reused. */
public record ResolvedLink(long urlId, String longUrl) { }
