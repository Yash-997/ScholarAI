package com.scholarship.service;

/** Fetches an official source page. Implementations must never throw. */
public interface PageFetcher {

    /**
     * @param ok     true only for an HTTP success with a body
     * @param status HTTP status, or null when no response was received
     * @param text   readable text of the page
     * @param error  short reason when {@code ok} is false
     * @param html   raw markup (structured adapters read embedded JSON and links from it)
     */
    record Page(boolean ok, Integer status, String text, String error, String html) {

        public Page(boolean ok, Integer status, String text, String error) {
            this(ok, status, text, error, "");
        }

        public static Page failure(Integer status, String error) {
            return new Page(false, status, "", error, "");
        }
    }

    Page fetch(String url);
}
