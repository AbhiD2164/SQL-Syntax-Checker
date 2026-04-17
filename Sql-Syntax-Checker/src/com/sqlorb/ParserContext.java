package com.sqlorb;

import java.util.List;

/**
 * ParserContext
 * ------------------------------------------------------------
 * Maintains parsing state and token navigation utilities.
 *
 * Future-ready for:
 *  - Structured syntax exceptions
 *  - Error recovery
 *  - Suggested fixes
 */
public class ParserContext {

    private final List<Token> tokens;
    private int current = 0;

    public ParserContext(List<Token> tokens) {
        this.tokens = tokens;
    }

    // =====================================================
    // NAVIGATION
    // =====================================================
    public Token peek() {
        return current < tokens.size()
            ? tokens.get(current)
            : new Token(TokenType.EOF, "", current);
    }

    public Token peekNext() {
        return (current + 1 < tokens.size())
            ? tokens.get(current + 1)
            : new Token(TokenType.EOF, "", current + 1);
    }

    public void advance() {
        if (current < tokens.size()) current++;
    }

    // =====================================================
    // MATCHING
    // =====================================================

    /**
     * Strict match.
     * Throws syntax error if token does not match.
     */
    public void match(TokenType expected) {
        if (peek().type == expected) {
            advance();
        } else {
            throw new RuntimeException(
                "Syntax Error at position " + peek().position +
                ": Expected " + expected +
                " but found '" + peek().value + "'"
            );
        }
    }
    public void match(TokenType expected, String desc) {
        Token token = peek();
        if (token.type != expected) {
            throw new RuntimeException(
                "Syntax Error at position " + token.position +
                ": Expected " + desc + " but found '" + token.value + "'"
            );
        }
        advance();
    }

    /**
     * Optional match.
     * Useful for commas, optional keywords, etc.
     */
    public boolean matchIf(TokenType expected) {
        if (peek().type == expected) {
            advance();
            return true;
        }
        return false;
    }
    public boolean isEOF() {
        return current >= tokens.size() || peek().type == TokenType.EOF;
    }
}
