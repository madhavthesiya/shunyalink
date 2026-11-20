package com.shunyalink.url;

/** Whether a link may redirect. Stored as text; the database allows exactly these values (V19). */
public enum LinkStatus {
    /** Redirects normally. */
    ACTIVE,
    /** Found unsafe after it was created: visitors see a warning page, and the destination is never revealed. */
    FLAGGED
}
