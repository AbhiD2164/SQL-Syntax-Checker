package com.sqlorb;

import java.util.List;

/**
 * DQLParser is a syntax-only SQL parser for SELECT queries.
 * It validates queries without executing them.
 * 
 * Features:
 * - SELECT statements with DISTINCT
 * - Nested subqueries in FROM, WHERE, IN, and functions
 * - JOINs (INNER, LEFT, RIGHT, FULL, CROSS, NATURAL, OUTER)
 * - WHERE conditions with AND, OR, comparison operators
 * - GROUP BY, HAVING
 * - ORDER BY with ASC/DESC
 * - LIMIT clause
 * - Functions and CASE expressions
 * - Detailed error messages with suggestions
 */
public class DQLParser {

    private final ParserContext ctx;

    public DQLParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================
    // ENTRY POINT: parseQuery()
    // =========================================================
    // Starts parsing a SELECT query. Handles optional top-level
    // set operators (UNION, INTERSECT, EXCEPT) and final semicolon.
    public void parseQuery() {
        parseQuery(false); // top-level query, not subquery
    }

    /**
     * parseQuery(boolean isSubquery)
     * @param isSubquery true if this query is nested inside parentheses or IN
     */
    private void parseQuery(boolean isSubquery) {
        if (ctx.peek().type != TokenType.SELECT) {
            throw new RuntimeException(
                "Syntax Error: Query must start with SELECT.\n" +
                "Suggested fix: Start your query with 'SELECT <columns> FROM <table>'."
            );
        }

        parseSelect();

        // Handle set operators (UNION, INTERSECT, EXCEPT)
        while (isSetOperator(ctx.peek().type)) {
            TokenType op = ctx.peek().type;
            ctx.advance();

            // Optional UNION ALL
            if (op == TokenType.UNION && ctx.peek().type == TokenType.ALL) ctx.advance();

            if (ctx.peek().type != TokenType.SELECT) {
                throw new RuntimeException(
                    "Syntax Error: '" + op + "' must be followed by a SELECT.\n" +
                    "Suggested fix: Add a full 'SELECT ...' query after '" + op + "'."
                );
            }
            parseSelect();
        }

        // Optional semicolon
        if (ctx.peek().type == TokenType.SEMICOLON) ctx.advance();

        // Only enforce EOF if not a subquery
        if (!isSubquery && ctx.peek().type != TokenType.EOF) {
            throw new RuntimeException(
                "Syntax Error: Unexpected text '" + ctx.peek().value + "' at end of query.\n" +
                "Suggested fix: Remove extra text or check your query."
            );
        }
    }

    // =========================================================
    // SELECT Clause
    // =========================================================
    public void parseSelect() {
        require(TokenType.SELECT, "Ensure the query starts with 'SELECT'.");

        if (ctx.peek().type == TokenType.DISTINCT) ctx.advance();

        parseColumns();

        if (ctx.peek().type == TokenType.WHERE) {
            throw new RuntimeException(
                "Error: WHERE clause cannot appear before FROM.\n" +
                "Suggested fix: Move 'WHERE ...' after 'FROM <table>'."
            );
        }

        require(TokenType.FROM, "Add 'FROM' keyword after your column list.");
        parseTableOrSubquery();

        while (isJoinStart(ctx.peek().type)) parseJoin();

        if (ctx.peek().type == TokenType.WHERE) {
            ctx.advance();
            parseCondition();
        }

        if (ctx.peek().type == TokenType.GROUP_BY) {
            ctx.advance();
            parseGroupByList();

            if (ctx.peek().type == TokenType.HAVING) {
                ctx.advance();
                parseCondition();
            }
        }

        if (ctx.peek().type == TokenType.ORDER_BY) {
            ctx.advance();
            parseOrderByList();
        }

        if (ctx.peek().type == TokenType.LIMIT) {
            ctx.advance();
            require(TokenType.NUMBER, "Provide a number for the LIMIT (e.g., LIMIT 10).");
        }
    }

    // =========================================================
    // TABLE OR SUBQUERY
    // =========================================================
    private void parseTableOrSubquery() {
        if (ctx.peek().type == TokenType.LPAREN && ctx.peekNext().type == TokenType.SELECT) {
            ctx.advance(); // '('
            parseQuery(true); // <-- parse as subquery
            require(TokenType.RPAREN, "Close the subquery with ')'.");

            if (ctx.peek().type == TokenType.AS) {
                ctx.advance();
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            } else if (ctx.peek().type == TokenType.IDENTIFIER && !isClauseKeyword(ctx.peek().type)) {
                ctx.advance(); // implicit alias
            }
        } else {
            parseIdentifierWithDot();

            if (ctx.peek().type == TokenType.AS) {
                ctx.advance();
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            } else if (ctx.peek().type == TokenType.IDENTIFIER && !isClauseKeyword(ctx.peek().type)) {
                ctx.advance(); // implicit alias
            }
        }
    }

    // =========================================================
    // JOINs
    // =========================================================
    private void parseJoin() {
        boolean requiresOn = true;

        TokenType type = ctx.peek().type;
        if (type == TokenType.NATURAL || type == TokenType.CROSS) {
            requiresOn = false;
            ctx.advance();
        } else {
            ctx.advance();
            if (ctx.peek().type == TokenType.OUTER) ctx.advance();
        }

        require(TokenType.JOIN, "Complete join syntax with 'JOIN'.");
        parseTableOrSubquery();

        if (requiresOn) {
            require(TokenType.ON, "Add an 'ON' clause to define how tables connect.");
            parseCondition();
        }
    }

    // =========================================================
    // COLUMNS
    // =========================================================
    private void parseColumns() {
        if (ctx.peek().type == TokenType.STAR) {
            ctx.advance();
            return;
        }
        parseColumnList();
    }

    private void parseColumnList() {
        while (true) {
            parseExpression();

            if (ctx.peek().type == TokenType.AS) {
                ctx.advance();
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            }

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.advance();
                if (ctx.peek().type == TokenType.FROM) {
                    throw new RuntimeException(
                        "Syntax Error: Trailing comma before FROM.\n" +
                        "Suggested fix: Remove the last comma."
                    );
                }
                continue;
            }

            Token next = ctx.peek();
            if (next.type == TokenType.IDENTIFIER || next.type == TokenType.NUMBER || next.type == TokenType.STRING) {
                throw new RuntimeException(
                    "Error: Missing comma before '" + next.value + "'.\n" +
                    "Suggested fix: Insert a comma (,) between column names."
                );
            }
            break;
        }
    }

    // =========================================================
    // GROUP BY
    // =========================================================
    private void parseGroupByList() {
        while (true) {
            if (ctx.peek().type == TokenType.NUMBER) ctx.advance();
            else parseExpression();

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.advance();
                continue;
            }
            break;
        }
    }

    // =========================================================
    // ORDER BY
    // =========================================================
    private void parseOrderByList() {
        while (true) {
            if (ctx.peek().type == TokenType.NUMBER) ctx.advance();
            else parseExpression();

            if (ctx.peek().type == TokenType.ASC || ctx.peek().type == TokenType.DESC) ctx.advance();

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.advance();
                continue;
            }
            break;
        }
    }

    // =========================================================
    // EXPRESSIONS & CONDITIONS
    // =========================================================
    private void parseCondition() {
        parseOr();
    }

    private void parseOr() {
        parseAnd();
        while (ctx.peek().type == TokenType.OR) {
            ctx.advance();
            parseAnd();
        }
    }

    private void parseAnd() {
        parseAtomic();
        while (ctx.peek().type == TokenType.AND) {
            ctx.advance();
            parseAtomic();
        }
    }

    private void parseAtomic() {
        parseExpression();

        TokenType op = ctx.peek().type;

        if (isComparisonOperator(op)) {
            ctx.advance();
            parseExpression();
        } else if (op == TokenType.BETWEEN) {
            ctx.advance();
            parseExpression();
            require(TokenType.AND, "Use 'AND' inside a BETWEEN clause (e.g., BETWEEN 10 AND 20).");
            parseExpression();
        } else if (op == TokenType.IN) {
            ctx.advance();
            parseInList();
        } else if (op == TokenType.LIKE) {
            ctx.advance();
            parseExpression();
        } else if (op == TokenType.IS) {
            ctx.advance();
            if (ctx.peek().type == TokenType.NOT) ctx.advance();
            require(TokenType.NULL, "IS must be followed by NULL (or NOT NULL).");
        }
    }

    // =========================================================
    // EXPRESSIONS
    // =========================================================
    private void parseExpression() {
        TokenType type = ctx.peek().type;

        if (type == TokenType.IDENTIFIER) {
            if (ctx.peekNext().type == TokenType.LPAREN) parseFunctionCall();
            else parseIdentifierWithDot();
        } else if (type == TokenType.NUMBER || type == TokenType.STRING || type == TokenType.STAR || type == TokenType.NULL) {
            ctx.advance();
        } else if (type == TokenType.LPAREN) {
            ctx.advance();
            if (ctx.peek().type == TokenType.SELECT) parseQuery(true);
            else parseCondition();
            require(TokenType.RPAREN, "Unbalanced parenthesis. Add a closing ')'.");
        } else if (type == TokenType.CASE) {
            parseCaseExpression();
        } else {
            throw new RuntimeException(
                "Syntax Error: Expected a column, number, or string near '" + ctx.peek().value + "'."
            );
        }

        if (ctx.peek().type == TokenType.PLUS || ctx.peek().type == TokenType.MINUS ||
            ctx.peek().type == TokenType.STAR || ctx.peek().type == TokenType.SLASH ||
            ctx.peek().type == TokenType.PERCENT) {
            ctx.advance();
            parseExpression();
        }
    }

    private void parseFunctionCall() {
        ctx.advance(); // function name
        require(TokenType.LPAREN, "Function call must have '('.");

        if (ctx.peek().type == TokenType.SELECT) parseQuery(true);
        else if (ctx.peek().type != TokenType.RPAREN) parseColumnList();

        require(TokenType.RPAREN, "Close the function parentheses ')'.");
    }

    private void parseCaseExpression() {
        ctx.advance(); // CASE
        require(TokenType.WHEN, "CASE must have WHEN.");
        parseExpression();
        require(TokenType.THEN, "WHEN must be followed by THEN.");
        parseExpression();

        while (ctx.peek().type == TokenType.WHEN) {
            ctx.advance();
            parseExpression();
            require(TokenType.THEN, "WHEN must be followed by THEN.");
            parseExpression();
        }

        if (ctx.peek().type == TokenType.ELSE) {
            ctx.advance();
            parseExpression();
        }

        require(TokenType.END, "CASE expression must end with END.");
    }

    private void parseInList() {
        require(TokenType.LPAREN, "Start the IN list with '('.");

        if (ctx.peek().type == TokenType.SELECT) parseQuery(true);
        else {
            parseExpression();
            while (ctx.peek().type == TokenType.COMMA) {
                ctx.advance();
                parseExpression();
            }
        }

        require(TokenType.RPAREN, "Close the IN list with ')'.");
    }

    private void parseIdentifierWithDot() {
        require(TokenType.IDENTIFIER, "Expected an identifier.");
        while (ctx.peek().type == TokenType.DOT) {
            ctx.advance();
            require(TokenType.IDENTIFIER, "Expected column/table name after '.'");
        }
    }

    // =========================================================
    // UTILITIES
    // =========================================================
    private void require(TokenType expected, String suggestion) {
        if (ctx.peek().type == expected) ctx.advance();
        else throw new RuntimeException(
            "Syntax Error: Expected '" + expected + "' but found '" + ctx.peek().value + "'.\nSuggested fix: " + suggestion
        );
    }

    private boolean isClauseKeyword(TokenType t) {
        return t == TokenType.FROM || t == TokenType.WHERE || t == TokenType.GROUP_BY ||
               t == TokenType.HAVING || t == TokenType.ORDER_BY || t == TokenType.LIMIT;
    }

    private boolean isSetOperator(TokenType t) {
        return t == TokenType.UNION || t == TokenType.INTERSECT || t == TokenType.EXCEPT;
    }

    private boolean isJoinStart(TokenType t) {
        return t == TokenType.JOIN || t == TokenType.LEFT || t == TokenType.RIGHT ||
               t == TokenType.FULL || t == TokenType.INNER || t == TokenType.CROSS || t == TokenType.NATURAL;
    }

    private boolean isComparisonOperator(TokenType t) {
        return t == TokenType.EQUALS || t == TokenType.NOT_EQUALS || t == TokenType.GT ||
               t == TokenType.LT || t == TokenType.GE || t == TokenType.LE;
    }

    private TokenType peekNext() {
        return ctx.peekNext().type;
    }
}
