package com.shunyalink.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Replace a link's tags. Null or empty removes them all. */
public record UpdateTagsRequest(
        @Size(max = LinkTags.MAX_TAGS, message = "A link can have at most " + LinkTags.MAX_TAGS + " tags")
        Set<@NotBlank(message = "A tag cannot be blank")
            @Size(max = LinkTags.MAX_LENGTH, message = "A tag can be at most " + LinkTags.MAX_LENGTH + " characters")
            String> tags) {
}
