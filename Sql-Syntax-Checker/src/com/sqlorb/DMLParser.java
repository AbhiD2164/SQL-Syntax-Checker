package com.sqlorb;

/**
 * DMLParser with Suggested Fixes
 * ============================================================================
 * SQL DML syntax parser (syntax validation only) + suggested corrections.
 *
 * Existing features remain intact; added capability:
 *  - Detect common user mistakes (like missing commas, FROM, or parentheses)
 *  - Provide a corrected SQL snippet as suggestion
 *
 * NOTE:
 *  - Parser still validates SYNTAX ONLY.
 *  - Suggested fixes are heuristic-based; do not perform semantic validation.
 */
public class DMLParser {

    private final ParserContext ctx;

    public DMLParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================================
    // ENTRY POINT
    // =========================================================================
    public void parse() {
        switch (ctx.peek().type) {
            case INSERT -> parseInsert();
            case UPDATE -> parseUpdate();
            case DELETE -> parseDelete();
            default -> error("Expected INSERT, UPDATE, or DELETE");
        }
    }

    // =========================================================================
    // INSERT
    // =========================================================================
    private void parseInsert() {
        ctx.match(TokenType.INSERT);
        ctx.match(TokenType.INTO);
        ctx.match(TokenType.IDENTIFIER); // table

        boolean emptyColumnList = false;

        // Optional column list
        if (ctx.matchIf(TokenType.LPAREN)) {
            if (ctx.peek().type == TokenType.RPAREN) {
                emptyColumnList = true;
                ctx.advance();
            } else {
                parseIdentifierListWithFix(); // <-- enhanced
                ctx.match(TokenType.RPAREN);
            }
        }

        // DEFAULT VALUES
        if (ctx.matchIf(TokenType.DEFAULT)) {
            ctx.match(TokenType.VALUES);
            return;
        }

        // INSERT ... SELECT
        if (ctx.peek().type == TokenType.SELECT) {
            parseSelect();
            return;
        }

        // VALUES (...), allow empty column rows if columns list empty
        ctx.match(TokenType.VALUES);
        parseRowList(emptyColumnList);
    }

    private void parseRowList(boolean allowEmptyRow) {
        parseRow(allowEmptyRow);
        while (ctx.matchIf(TokenType.COMMA)) {
            parseRow(allowEmptyRow);
        }
    }

    private void parseRow(boolean allowEmptyRow) {
        ctx.match(TokenType.LPAREN);

        if (ctx.peek().type == TokenType.RPAREN) {
            if (!allowEmptyRow) {
                errorWithFix("Empty VALUES row not allowed",
                        "Add values inside parentheses, e.g., (1, 'abc')");
            }
            ctx.advance();
            return;
        }

        parseExpressionList();
        ctx.match(TokenType.RPAREN);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================
    private void parseUpdate() {
        ctx.match(TokenType.UPDATE);
        ctx.match(TokenType.IDENTIFIER); // table
        ctx.match(TokenType.SET);

        do {
            parseQualifiedIdentifier();
            if (!ctx.matchIf(TokenType.EQUALS)) {
                errorWithFix("Expected '=' in SET clause",
                        "Add '=' between column and value, e.g., name = 'John'");
            }
            parseExpression();
        } while (ctx.matchIf(TokenType.COMMA));

        if (ctx.matchIf(TokenType.WHERE)) {
            parseCondition();
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================
    private void parseDelete() {
        ctx.match(TokenType.DELETE);
        ctx.match(TokenType.FROM);
        if (ctx.peek().type != TokenType.IDENTIFIER) {
            errorWithFix("Missing table name in DELETE",
                    "Add table name after FROM, e.g., DELETE FROM users");
        }
        ctx.match(TokenType.IDENTIFIER);

        if (ctx.matchIf(TokenType.WHERE)) {
            parseCondition();
        }
    }

    // =========================================================================
    // CONDITIONS (WHERE)
    // =========================================================================
    private void parseCondition() {
        parseLogicalTerm();
        while (ctx.peek().type == TokenType.OR) {
            ctx.advance();
            parseLogicalTerm();
        }
    }

    private void parseLogicalTerm() {
        parseLogicalFactor();
        while (ctx.peek().type == TokenType.AND) {
            ctx.advance();
            parseLogicalFactor();
        }
    }

    private void parseLogicalFactor() {
        if (ctx.matchIf(TokenType.LPAREN)) {
            parseCondition();
            if (!ctx.matchIf(TokenType.RPAREN)) {
                errorWithFix("Unmatched '(' in WHERE clause",
                        "Add ')' at the end of the condition");
            }
            return;
        }

        parsePredicate();
    }

    private void parsePredicate() {
        if (ctx.matchIf(TokenType.EXISTS)) {
            ctx.match(TokenType.LPAREN);
            parseSelect();
            ctx.match(TokenType.RPAREN);
            return;
        }

        parseExpression();

        if (ctx.matchIf(TokenType.IN)) {
            ctx.match(TokenType.LPAREN);
            if (ctx.peek().type == TokenType.SELECT) {
                parseSelect();
            } else {
                parseExpressionList();
            }
            ctx.match(TokenType.RPAREN);
            return;
        }

        if (!isComparison(ctx.peek().type)) {
            errorWithFix("Expected comparison operator",
                    "Add a comparison operator (=, !=, <, <=, >, >=) after expression");
        }
        ctx.advance();
        parseExpression();
    }

    // =========================================================================
    // EXPRESSIONS
    // =========================================================================
    private void parseExpression() { parseAdditive(); }
    private void parseAdditive() {
        parseMultiplicative();
        while (ctx.peek().type == TokenType.PLUS ||
               ctx.peek().type == TokenType.MINUS) {
            ctx.advance();
            parseMultiplicative();
        }
    }
    private void parseMultiplicative() {
        parseUnary();
        while (ctx.peek().type == TokenType.STAR ||
               ctx.peek().type == TokenType.SLASH) {
            ctx.advance();
            parseUnary();
        }
    }
    private void parseUnary() {
        if (ctx.peek().type == TokenType.PLUS || ctx.peek().type == TokenType.MINUS) {
            ctx.advance();
        }
        parsePrimary();
    }

    private void parsePrimary() {
        TokenType t = ctx.peek().type;

        if (ctx.matchIf(TokenType.LPAREN)) {
            if (ctx.peek().type == TokenType.SELECT) parseSelect();
            else parseExpression();
            ctx.match(TokenType.RPAREN);
            return;
        }

        if (t == TokenType.NUMBER || t == TokenType.STRING || t == TokenType.TRUE ||
            t == TokenType.FALSE || t == TokenType.NULL || t == TokenType.DEFAULT) {
            ctx.advance();
            return;
        }

        if (t == TokenType.IDENTIFIER) {
            parseQualifiedIdentifier();
            if (ctx.matchIf(TokenType.LPAREN)) {
                if (ctx.peek().type != TokenType.RPAREN) parseExpressionList();
                ctx.match(TokenType.RPAREN);
            }
            return;
        }

        errorWithFix("Invalid expression",
                "Check syntax near '" + ctx.peek().value + "'");
    }

    // =========================================================================
    // SELECT (minimal)
    // =========================================================================
    private void parseSelect() {
        ctx.match(TokenType.SELECT);
        if (!ctx.matchIf(TokenType.STAR)) parseExpressionList();

        if (ctx.peek().type != TokenType.FROM) {
            errorWithFix("Missing FROM clause",
                    "Add FROM <table>, e.g., SELECT name FROM users");
        }
        ctx.match(TokenType.FROM);
        ctx.match(TokenType.IDENTIFIER);

        if (ctx.matchIf(TokenType.WHERE)) parseCondition();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private void parseExpressionList() {
        parseExpression();
        while (ctx.matchIf(TokenType.COMMA)) parseExpression();
    }

    private void parseIdentifierListWithFix() {
        parseQualifiedIdentifier();
        while (ctx.peek().type != TokenType.RPAREN && ctx.peek().type != TokenType.EOF) {
            if (!ctx.matchIf(TokenType.COMMA)) {
                // Detected missing comma between identifiers
                errorWithFix("Missing comma between columns",
                        "Add commas between identifiers, e.g., col1, col2");
            }
            parseQualifiedIdentifier();
        }
    }

    private void parseIdentifierList() {
        parseQualifiedIdentifier();
        while (ctx.matchIf(TokenType.COMMA)) parseQualifiedIdentifier();
    }

    private void parseQualifiedIdentifier() {
        ctx.match(TokenType.IDENTIFIER);
        while (ctx.matchIf(TokenType.DOT)) ctx.match(TokenType.IDENTIFIER);
    }

    private boolean isComparison(TokenType t) {
        return t == TokenType.EQUALS || t == TokenType.NOT_EQUALS ||
               t == TokenType.GT || t == TokenType.GE ||
               t == TokenType.LT || t == TokenType.LE;
    }

    // =========================================================================
    // ERROR WITH SUGGESTED FIX
    // =========================================================================
    private void errorWithFix(String msg, String suggestion) {
        throw new RuntimeException(
            "Syntax Error near '" + ctx.peek().value + "': " + msg +
            ". Suggested fix: " + suggestion
        );
    }

    private void error(String msg) {
        throw new RuntimeException(
            "Syntax Error near '" + ctx.peek().value + "': " + msg
        );
    }
}
