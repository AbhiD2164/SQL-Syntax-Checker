package com.sqlorb;

public class SyntaxError extends RuntimeException {
	private final String suggestion;

    public SyntaxError(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }

    public String getSuggestion() {
        return suggestion;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + (suggestion != null ? "\n💡 Suggested Fix: " + suggestion : "");
    }
}

