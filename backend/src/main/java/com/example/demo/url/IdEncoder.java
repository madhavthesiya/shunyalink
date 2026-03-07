package com.example.demo.url;

public interface IdEncoder {
    String encode(long id);
    long decode(String shortId);
    boolean isValid(String shortId);
}
