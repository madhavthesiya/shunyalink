package com.shunyalink.url;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The categories a link can have: the ones the AI is asked to choose from, and the ones the frontend draws. There used
 * to be two lists, 7 in the prompt and 13 accepted, so 6 values had no icon and could never come from the prompt.
 */
public enum LinkCategory {
    VIDEO, GITHUB, DOCUMENTATION, SOCIAL_MEDIA, PORTFOLIO, BLOG, GENERAL;

    /** "[VIDEO, GITHUB, …, GENERAL]", for the AI prompt, so the prompt can't drift from this list. */
    public static String promptList() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ", "[", "]"));
    }

    /** The category an AI answer names, or GENERAL for anything else (including nothing). */
    public static LinkCategory parse(String answer) {
        if (answer == null) {
            return GENERAL;
        }
        String name = answer.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(category -> category.name().equals(name)).findFirst().orElse(GENERAL);
    }
}
