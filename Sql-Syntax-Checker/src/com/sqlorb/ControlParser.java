package com.sqlorb;

public class ControlParser {

    private final ParserContext ctx;

    public ControlParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Entry point for control statements.
     */
    public void parse() {
        TokenType type = ctx.peek().type;

        switch (type) {
            // TCL
            case COMMIT:
            case ROLLBACK:
            case SAVEPOINT:
                parseTCL();
                break;

            // DCL
            case GRANT:
            case REVOKE:
                parseDCL();
                break;

            default:
                throw new SyntaxError(
                    "Unexpected control statement '" + ctx.peek().value + "'",
                    "Expected COMMIT, ROLLBACK, SAVEPOINT, GRANT, or REVOKE"
                );
        }
    }

    // =====================================================
    // TCL — TRANSACTION CONTROL LANGUAGE
    // =====================================================

    private void parseTCL() {
        TokenType type = ctx.peek().type;

        // -------- COMMIT --------
        if (type == TokenType.COMMIT) {
            ctx.match(TokenType.COMMIT, "COMMIT");

            if (ctx.peek().type == TokenType.WORK) {
                ctx.advance();
            }
            return;
        }

        // -------- ROLLBACK --------
        if (type == TokenType.ROLLBACK) {
            ctx.match(TokenType.ROLLBACK, "ROLLBACK");

            if (ctx.peek().type == TokenType.WORK) {
                ctx.advance();
            }

            if (ctx.peek().type == TokenType.TO) {
                ctx.advance();
                if (ctx.peek().type != TokenType.SAVEPOINT) {
                    throw new SyntaxError(
                        "Expected SAVEPOINT after TO",
                        "Add SAVEPOINT keyword after TO"
                    );
                }
                ctx.match(TokenType.SAVEPOINT, "SAVEPOINT");
                if (ctx.peek().type != TokenType.IDENTIFIER) {
                    throw new SyntaxError(
                        "Expected savepoint name",
                        "Add identifier after SAVEPOINT"
                    );
                }
                ctx.match(TokenType.IDENTIFIER, "savepoint name");
            }
            return;
        }

        // -------- SAVEPOINT --------
        if (type == TokenType.SAVEPOINT) {
            ctx.match(TokenType.SAVEPOINT, "SAVEPOINT");

            if (ctx.peek().type != TokenType.IDENTIFIER) {
                throw new SyntaxError(
                    "Expected savepoint name",
                    "Add identifier after SAVEPOINT"
                );
            }
            ctx.match(TokenType.IDENTIFIER, "savepoint name");
            return;
        }

        throw new SyntaxError("Invalid TCL statement", "Check COMMIT, ROLLBACK, SAVEPOINT syntax");
    }

    // =====================================================
    // DCL — DATA CONTROL LANGUAGE
    // =====================================================

    private void parseDCL() {
        if (ctx.peek().type == TokenType.GRANT) {
            parseGrant();
        } else if (ctx.peek().type == TokenType.REVOKE) {
            parseRevoke();
        } else {
            throw new SyntaxError(
                "Invalid DCL statement",
                "Expected GRANT or REVOKE"
            );
        }
    }

    private void parseGrant() {
        ctx.match(TokenType.GRANT, "GRANT");

        // 1. Privileges
        parsePrivileges();

        // 2. ON table
        if (ctx.peek().type != TokenType.ON) {
            throw new SyntaxError(
                "Expected ON keyword",
                "Add ON before table name"
            );
        }
        ctx.match(TokenType.ON, "ON");
        ctx.match(TokenType.IDENTIFIER, "table name");

        // 3. TO user
        if (ctx.peek().type != TokenType.TO) {
            throw new SyntaxError(
                "Expected TO keyword",
                "Add TO before user"
            );
        }
        ctx.match(TokenType.TO, "TO");
        ctx.match(TokenType.IDENTIFIER, "user name");

        // 4. Optional: WITH GRANT OPTION
        if (ctx.peek().type == TokenType.WITH) {
            ctx.advance();
            if (ctx.peek().type != TokenType.GRANT) {
                throw new SyntaxError(
                    "Expected GRANT after WITH",
                    "Add GRANT after WITH"
                );
            }
            ctx.match(TokenType.GRANT, "GRANT");
            if (ctx.peek().type != TokenType.OPTION) {
                throw new SyntaxError(
                    "Expected OPTION after GRANT",
                    "Add OPTION after GRANT"
                );
            }
            ctx.match(TokenType.OPTION, "OPTION");
        }
    }

    private void parseRevoke() {
        ctx.match(TokenType.REVOKE, "REVOKE");

        // 1. Privileges
        parsePrivileges();

        // 2. ON table
        if (ctx.peek().type != TokenType.ON) {
            throw new SyntaxError(
                "Expected ON keyword",
                "Add ON before table name"
            );
        }
        ctx.match(TokenType.ON, "ON");
        ctx.match(TokenType.IDENTIFIER, "table name");

        // 3. FROM user
        if (ctx.peek().type != TokenType.FROM) {
            throw new SyntaxError(
                "Expected FROM keyword",
                "Add FROM before user"
            );
        }
        ctx.match(TokenType.FROM, "FROM");
        ctx.match(TokenType.IDENTIFIER, "user name");
    }

    // =====================================================
    // PRIVILEGES HELPER
    // =====================================================

    private void parsePrivileges() {
        if (ctx.peek().type == TokenType.ALL) {
            ctx.advance();
            if (ctx.peek().type == TokenType.PRIVILEGES) {
                ctx.advance();
            }
            return;
        }

        do {
            if (!isPrivilege(ctx.peek().type)) {
                throw new SyntaxError(
                    "Expected privilege but found '" + ctx.peek().value + "'",
                    "Use SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, EXECUTE"
                );
            }
            ctx.advance();
        } while (ctx.matchIf(TokenType.COMMA));
    }

    private boolean isPrivilege(TokenType type) {
        return type == TokenType.SELECT ||
               type == TokenType.INSERT ||
               type == TokenType.UPDATE ||
               type == TokenType.DELETE ||
               type == TokenType.CREATE ||
               type == TokenType.DROP ||
               type == TokenType.ALTER ||
               type == TokenType.EXECUTE;
    }

}
