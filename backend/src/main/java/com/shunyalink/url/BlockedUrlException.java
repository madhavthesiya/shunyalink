package com.shunyalink.url;

import java.io.IOException;

/** The server refused to fetch a URL because it targets an internal address or isn't a plain web URL. */
public class BlockedUrlException extends IOException {

    public BlockedUrlException(String message) {
        super(message);
    }
}
