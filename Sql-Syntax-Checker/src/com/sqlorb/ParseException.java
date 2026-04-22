package com.sqlorb;

public class ParseException extends RuntimeException {

    private final String suggestion;

    public ParseException(String message) {
        super(message);
        this.suggestion = null;
    }

    public ParseException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
