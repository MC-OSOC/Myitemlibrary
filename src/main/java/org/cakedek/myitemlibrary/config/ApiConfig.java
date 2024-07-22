package org.cakedek.myitemlibrary.config;

public class ApiConfig {
    private final String corsAllowOrigin;
    private final String corsAllowMethods;
    private final String corsAllowHeaders;
    private final boolean corsAllowCredentials;
    private final int corsMaxAge;
    private final String contentTypeOptions;
    private final String strictTransportSecurity;

    private ApiConfig(Builder builder) {
        this.corsAllowOrigin = builder.corsAllowOrigin;
        this.corsAllowMethods = builder.corsAllowMethods;
        this.corsAllowHeaders = builder.corsAllowHeaders;
        this.corsAllowCredentials = builder.corsAllowCredentials;
        this.corsMaxAge = builder.corsMaxAge;
        this.contentTypeOptions = builder.contentTypeOptions;
        this.strictTransportSecurity = builder.strictTransportSecurity;
    }

    public String getCorsAllowOrigin() { return corsAllowOrigin; }
    public String getCorsAllowMethods() { return corsAllowMethods; }
    public String getCorsAllowHeaders() { return corsAllowHeaders; }
    public boolean getCorsAllowCredentials() { return corsAllowCredentials; }
    public int getCorsMaxAge() { return corsMaxAge; }
    public String getContentTypeOptions() { return contentTypeOptions; }
    public String getStrictTransportSecurity() { return strictTransportSecurity; }

    public static class Builder {
        private String corsAllowOrigin = "*";
        private String corsAllowMethods = "GET,POST,PUT,DELETE,OPTIONS";
        private String corsAllowHeaders = "*";
        private boolean corsAllowCredentials = true;
        private int corsMaxAge = 1800;
        private String contentTypeOptions = "nosniff";
        private String strictTransportSecurity = "max-age=31536000; includeSubDomains";

        public Builder corsAllowOrigin(String val) { corsAllowOrigin = val; return this; }
        public Builder corsAllowMethods(String val) { corsAllowMethods = val; return this; }
        public Builder corsAllowHeaders(String val) { corsAllowHeaders = val; return this; }
        public Builder corsAllowCredentials(boolean val) { corsAllowCredentials = val; return this; }
        public Builder corsMaxAge(int val) { corsMaxAge = val; return this; }
        public Builder contentTypeOptions(String val) { contentTypeOptions = val; return this; }
        public Builder strictTransportSecurity(String val) { strictTransportSecurity = val; return this; }

        public ApiConfig build() {
            return new ApiConfig(this);
        }
    }
}