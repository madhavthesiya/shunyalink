package com.shunyalink.url;

import java.util.List;

public class ReorderRequest {
    private List<String> shortIds;

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
