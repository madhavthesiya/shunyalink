package com.shunyalink.cp;

/**
 * A coding platform could not answer: a timeout, a connection failure, a 5xx or rate-limit reply, or a reply that
 * could not be read.
 *
 * <p>Failures are thrown, never returned as values, because Spring's cache stores whatever a {@code @Cacheable}
 * method returns and never what it throws. A failure therefore cannot be served from the cache for an hour, and a
 * new platform service cannot forget an {@code unless} rule. A definitive answer such as "no such user" is not a
 * failure: it is returned and cached like any other data.
 */
public class PlatformUnavailableException extends RuntimeException {

    public PlatformUnavailableException(String platform, Throwable cause) {
        super(platform + " is unavailable", cause);
    }
}
