package com.scholarship.service;

/** Builds page bodies shaped like the real ones the extractors read. */
final class TestPages {

    private TestPages() {
    }

    /** One programme cycle inside a Buddy4Study brand page. */
    static String item(long id, String title, String deadline, String launch, String session, String eligibility) {
        return "{\"scholarshipId\":" + id + ",\"title\":\"" + title + "\",\"deadline\":" + (deadline == null ? "null" : "\"" + deadline + "\"")
                + ",\"applicationLaunchDate\":" + (launch == null ? "null" : "\"" + launch + "\"") + ",\"session\":\"" + session + "\""
                + ",\"eligibility\":\"" + eligibility + "\",\"benefits\":\"<p>Up to <b>INR 50,000</b></p>\",\"requiredDocument\":\"<ul><li>Marksheet</li><li>ID proof</li></ul>\""
                + ",\"applyLink\":\"https://forms.example.org/apply/" + id + "\"}";
    }

    static String brandPage(String... items) {
        return "<html><head><title>Brand</title></head><body><script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"pageProps\":{\"scholarship\":{\"brandPage\":{\"name\":\"Brand\",\"scholarships\":[" + String.join(",", items)
                + "]}}}}}</script></body></html>";
    }

    static String emptyShell() {
        return "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{\"pageProps\":{\"scholarship\":{\"scholarship\":null}}}}</script></body></html>";
    }

    static PageFetcher.Page ok(String html) {
        return new PageFetcher.Page(true, 200, org.jsoup.Jsoup.parse(html).text(), null, html);
    }
}
