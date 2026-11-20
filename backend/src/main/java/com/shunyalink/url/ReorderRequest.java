package com.shunyalink.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ReorderRequest {
    /** The owner's links in their new order. Bounded, so one request can't ask for an unbounded number of updates. */
    @NotNull(message = "shortIds is required")
    @Size(max = 1000, message = "At most 1000 links can be reordered at once")
    private List<@NotBlank String> shortIds;

    public ReorderRequest() {
    }

    public ReorderRequest(List<String> shortIds) {
        this.shortIds = shortIds;
    }

    public List<String> getShortIds() {
        return shortIds;
    }

    public void setShortIds(List<String> shortIds) {
        this.shortIds = shortIds;
    }
}
