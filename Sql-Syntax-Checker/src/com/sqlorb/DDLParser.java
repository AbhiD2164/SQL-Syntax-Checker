package com.sqlorb;

/**
 * DDLParser v2 - ANSI SQL with smart error suggestions
 * =========================================================
 * Supports:
 *  - CREATE TABLE / INDEX (with IF NOT EXISTS, UNIQUE)
 *  - DROP TABLE / INDEX (with IF EXISTS)
 *  - ALTER TABLE (ADD, DROP, RENAME)
 *  - TRUNCATE TABLE
 *  - Column-level & table-level constraints
 *  - Multi-column foreign keys
 *  - CHECK constraints
 *  - Index types (USING BTREE/HASH)
 *  - Batch/multi-statement parsing
 *  - Smart error suggestions
 */

public class DDLParser {

    private final ParserContext ctx;

    public DDLParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Entry point: parses multiple DDL statements separated by semicolons
     */
    public void parseAll() {
        while (!ctx.isEOF()) {
            parse();
            if (ctx.peek().type == TokenType.SEMICOLON) ctx.advance();
        }
    }

    /**
     * Parse a single DDL statement
     */
    public void parse() {
        TokenType type = ctx.peek().type;
        switch (type) {
            case CREATE: parseCreate(); break;
            case DROP: parseDrop(); break;
            case ALTER: parseAlter(); break;
            case TRUNCATE: parseTruncate(); break;
            default: error(
                "Unexpected start token '" + ctx.peek().value + "'",
                "DDL statement should start with CREATE, DROP, ALTER, or TRUNCATE"
            );
        }
    }

    // =====================================================
    // CREATE
    // =====================================================
    private void parseCreate() {
        match(TokenType.CREATE, "CREATE keyword");

        if (ctx.peek().type == TokenType.TABLE) parseCreateTable();
        else if (ctx.peek().type == TokenType.INDEX || ctx.peek().type == TokenType.UNIQUE)
            parseCreateIndex();
        else error(
            "Expected TABLE or INDEX after CREATE",
            "Use CREATE TABLE or CREATE INDEX"
        );
    }

    private void parseCreateTable() {
        match(TokenType.TABLE, "TABLE keyword");

        if (ctx.peek().type == TokenType.IF) {
            match(TokenType.IF, "IF keyword");
            match(TokenType.NOT, "NOT keyword");
            match(TokenType.EXISTS, "EXISTS keyword");
        }

        match(TokenType.IDENTIFIER, "table name");
        match(TokenType.LPAREN, "'(' to start column/constraint list");

        parseColumnDefinition();

        while (true) {
            if (ctx.matchIf(TokenType.COMMA)) {
                // Could be column or table-level constraint
                if (isConstraintStart(ctx.peek().type)) parseTableConstraint();
                else parseColumnDefinition();
            } else if (ctx.peek().type == TokenType.IDENTIFIER) {
                error(
                    "Missing comma before '" + ctx.peek().value + "'",
                    "Insert a comma (,) between column names or constraints"
                );
            } else break;
        }

        match(TokenType.RPAREN, "')' to end table definition");
    }

    private void parseColumnDefinition() {
        match(TokenType.IDENTIFIER, "column name");
        parseDataType();

        while (isConstraintStart(ctx.peek().type)) parseConstraint();
    }

    private void parseTableConstraint() {
        TokenType type = ctx.peek().type;

        // ---------------- PRIMARY KEY ----------------
        if (type == TokenType.PRIMARY) {
            // Consume PRIMARY and KEY separately
            ctx.match(TokenType.PRIMARY, "PRIMARY");
            ctx.match(TokenType.KEY, "KEY");
            match(TokenType.LPAREN, "'(' after PRIMARY KEY");
            parseIdentifierList();
            match(TokenType.RPAREN, "')' after PRIMARY KEY columns");
        }
        else if (type == TokenType.PRIMARY_KEY) {
            // If lexer combines PRIMARY_KEY into one token
            ctx.advance();
            match(TokenType.LPAREN, "'(' after PRIMARY KEY");
            parseIdentifierList();
            match(TokenType.RPAREN, "')' after PRIMARY KEY columns");
        }

        // ---------------- UNIQUE ----------------
        else if (type == TokenType.UNIQUE) {
            ctx.advance(); // consume UNIQUE
            match(TokenType.LPAREN, "'(' after UNIQUE");
            parseIdentifierList();
            match(TokenType.RPAREN, "')' after UNIQUE columns");
        }

        // ---------------- FOREIGN KEY ----------------
        else if (type == TokenType.FOREIGN) {
            ctx.match(TokenType.FOREIGN, "FOREIGN");
            ctx.match(TokenType.KEY, "KEY");
            match(TokenType.LPAREN, "'(' after FOREIGN KEY");
            parseIdentifierList();
            match(TokenType.RPAREN, "')' after FOREIGN KEY columns");
            parseReferences();
        }
        else if (type == TokenType.FOREIGN_KEY) {
            // if lexer combined into one token
            ctx.advance();
            match(TokenType.LPAREN, "'(' after FOREIGN KEY");
            parseIdentifierList();
            match(TokenType.RPAREN, "')' after FOREIGN KEY columns");
            parseReferences();
        }

        // ---------------- CHECK ----------------
        else if (type == TokenType.CHECK) {
            ctx.advance();
            match(TokenType.LPAREN, "'(' after CHECK");
            parseCheckExpression();
            match(TokenType.RPAREN, "')' after CHECK expression");
        }

        else {
            error(
                "Unknown table-level constraint '" + ctx.peek().value + "'",
                "Use PRIMARY KEY, UNIQUE, FOREIGN KEY, or CHECK"
            );
        }
    }


    private void parseCreateIndex() {
        if (ctx.peek().type == TokenType.UNIQUE) ctx.advance();

        match(TokenType.INDEX, "INDEX keyword");
        match(TokenType.IDENTIFIER, "index name");
        match(TokenType.ON, "ON keyword");
        match(TokenType.IDENTIFIER, "table name");

        match(TokenType.LPAREN, "'(' to start column list");
        parseIdentifierList();
        match(TokenType.RPAREN, "')' to end column list");

        // Optional index type
        if (ctx.peek().type == TokenType.USING) {
            ctx.advance();
            if (ctx.peek().type != TokenType.BTREE && ctx.peek().type != TokenType.HASH)
                error("Invalid index type '" + ctx.peek().value + "'", "Use BTREE or HASH");
            ctx.advance();
        }
    }

    // =====================================================
    // DROP
    // =====================================================
    private void parseDrop() {
        match(TokenType.DROP, "DROP keyword");

        if (ctx.peek().type == TokenType.TABLE || ctx.peek().type == TokenType.INDEX) {
            ctx.advance();
            if (ctx.peek().type == TokenType.IF) {
                match(TokenType.IF, "IF keyword");
                match(TokenType.EXISTS, "EXISTS keyword");
            }
            match(TokenType.IDENTIFIER, "name to drop");
        } else error(
            "Expected TABLE or INDEX after DROP",
            "Use DROP TABLE or DROP INDEX"
        );
    }

    // =====================================================
    // ALTER
    // =====================================================
    private void parseAlter() {
        match(TokenType.ALTER, "ALTER keyword");
        match(TokenType.TABLE, "TABLE keyword");
        match(TokenType.IDENTIFIER, "table name");

        TokenType action = ctx.peek().type;

        if (action == TokenType.ADD) {
            ctx.advance();
            if (ctx.peek().type == TokenType.COLUMN) ctx.advance();
            parseColumnDefinition();
        } else if (action == TokenType.DROP) {
            ctx.advance();
            if (ctx.peek().type == TokenType.COLUMN) ctx.advance();
            match(TokenType.IDENTIFIER, "column name");
        } else if (action == TokenType.RENAME) {
            ctx.advance();
            if (ctx.peek().type == TokenType.TO) ctx.advance();
            match(TokenType.IDENTIFIER, "new name");
        } else error(
            "Unsupported ALTER action '" + action + "'",
            "Use ADD, DROP, or RENAME"
        );
    }

    // =====================================================
    // TRUNCATE
    // =====================================================
    private void parseTruncate() {
        match(TokenType.TRUNCATE, "TRUNCATE keyword");
        if (ctx.peek().type == TokenType.TABLE) ctx.advance();
        match(TokenType.IDENTIFIER, "table name");
    }

    // =====================================================
    // COLUMN TYPES & CONSTRAINTS
    // =====================================================
    private void parseDataType() {
        TokenType type = ctx.peek().type;
        if (!isDataType(type)) error(
            "Invalid data type '" + ctx.peek().value + "'",
            "Use valid data types like INT, VARCHAR, DECIMAL, DATE, etc."
        );

        ctx.advance();

        if (ctx.peek().type == TokenType.LPAREN) {
            ctx.advance();
            match(TokenType.NUMBER, "size/precision");
            if (ctx.peek().type == TokenType.COMMA) {
                ctx.advance();
                match(TokenType.NUMBER, "scale");
            }
            match(TokenType.RPAREN, "')' to close size/precision");
        }
    }

    private void parseConstraint() {
        TokenType type = ctx.peek().type;

        switch (type) {
            case PRIMARY: match(TokenType.PRIMARY, "PRIMARY"); match(TokenType.KEY, "KEY"); break;
            case PRIMARY_KEY: ctx.advance(); break;
            case NOT: match(TokenType.NOT, "NOT"); match(TokenType.NULL, "NULL"); break;
            case NOT_NULL: ctx.advance(); break;
            case UNIQUE: ctx.advance(); break;
            case AUTO_INCREMENT: ctx.advance(); break;
            case DEFAULT:
                ctx.advance();
                if (ctx.peek().type == TokenType.STRING || ctx.peek().type == TokenType.NUMBER ||
                    ctx.peek().type == TokenType.TRUE || ctx.peek().type == TokenType.FALSE ||
                    ctx.peek().type == TokenType.CURRENT_DATE || ctx.peek().type == TokenType.CURRENT_TIMESTAMP) {
                    ctx.advance();
                } else error(
                    "Expected literal value after DEFAULT",
                    "Use a string, number, TRUE/FALSE, or function like CURRENT_DATE"
                );
                break;
            case FOREIGN: match(TokenType.FOREIGN, "FOREIGN"); match(TokenType.KEY, "KEY"); parseReferences(); break;
            case FOREIGN_KEY: ctx.advance(); parseReferences(); break;
            case REFERENCES: parseReferences(); break;
            default: error(
                "Unknown constraint '" + ctx.peek().value + "'",
                "Use PRIMARY KEY, NOT NULL, UNIQUE, DEFAULT, or FOREIGN KEY"
            );
        }
    }

    private void parseReferences() {
        match(TokenType.REFERENCES, "REFERENCES keyword");
        match(TokenType.IDENTIFIER, "referenced table");
        match(TokenType.LPAREN, "'(' to start referenced columns");
        parseIdentifierList();
        match(TokenType.RPAREN, "')' to end referenced columns");
    }

    private void parseCheckExpression() {
        // Basic check: just skip tokens until RPAREN for now
        while (ctx.peek().type != TokenType.RPAREN && !ctx.isEOF()) ctx.advance();
    }

    private void parseIdentifierList() {
        match(TokenType.IDENTIFIER, "identifier");
        while (true) {
            if (ctx.matchIf(TokenType.COMMA)) match(TokenType.IDENTIFIER, "identifier");
            else if (ctx.peek().type == TokenType.IDENTIFIER) error(
                "Missing comma before '" + ctx.peek().value + "'",
                "Insert a comma (,) between identifiers"
            );
            else break;
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private boolean isDataType(TokenType type) {
        return type == TokenType.INT || type == TokenType.INTEGER || type == TokenType.VARCHAR ||
               type == TokenType.CHAR || type == TokenType.TEXT || type == TokenType.BLOB ||
               type == TokenType.BOOLEAN || type == TokenType.DATE || type == TokenType.FLOAT ||
               type == TokenType.DOUBLE || type == TokenType.DECIMAL;
    }

    private boolean isConstraintStart(TokenType type) {
        return type == TokenType.PRIMARY || type == TokenType.PRIMARY_KEY || type == TokenType.NOT ||
               type == TokenType.NOT_NULL || type == TokenType.UNIQUE || type == TokenType.DEFAULT ||
               type == TokenType.FOREIGN || type == TokenType.FOREIGN_KEY || type == TokenType.REFERENCES ||
               type == TokenType.CHECK || type == TokenType.AUTO_INCREMENT;
    }

    private void match(TokenType expected, String desc) {
        Token token = ctx.peek();
        if (token.type != expected) {
            error(
                "Expected " + desc + " but found '" + token.value + "'",
                getSuggestedFix(expected, token)
            );
        }
        ctx.advance();
    }

    private void error(String msg, String suggestion) {
        throw new SyntaxError(msg, suggestion);
    }

    private String getSuggestedFix(TokenType expected, Token actual) {
        switch (expected) {
            case COMMA: if (actual.type == TokenType.IDENTIFIER) return "Insert a comma (,) before '" + actual.value + "'"; break;
            case IDENTIFIER:
                if (actual.type == TokenType.INT || actual.type == TokenType.VARCHAR) return "Did you forget a column name before the data type?";
                break;
            case LPAREN: return "Add '(' here";
            case RPAREN: return "Add ')' here";
            case SEMICOLON: return "Add ';' at the end of the statement";
            default: return "Check syntax near '" + actual.value + "'";
        }
        return null;
    }
}
