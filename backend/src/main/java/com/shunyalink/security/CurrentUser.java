package com.shunyalink.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the signed-in user's ID into a controller method: {@code @CurrentUser Long userId}.
 *
 * <p>Required by default: without a signed-in user the request ends with 401 before the method runs. Use
 * {@code @CurrentUser(required = false)} on endpoints that also serve anonymous visitors; the ID is then null.
 * Replaces the principal-casting code that was copied into each controller.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
    boolean required() default true;
}
