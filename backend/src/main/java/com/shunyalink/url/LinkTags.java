package com.shunyalink.url;

/**
 * Limits for a link's tags, shared by creating and editing a link. Before they existed, a request could attach
 * thousands of tags, or a tag longer than the 255-character column, which failed inside the database.
 */
final class LinkTags {
    static final int MAX_TAGS = 20;
    static final int MAX_LENGTH = 50;

    private LinkTags() {
    }
}
