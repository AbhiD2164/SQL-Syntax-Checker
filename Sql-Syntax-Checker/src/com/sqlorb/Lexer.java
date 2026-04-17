package com.sqlorb;

import java.util.*;

/**
 * Lexer
 * ------------------------------------------------------------
 * Converts raw SQL input into a stream of tokens.
 *
 * Design Goals:
 *  - Case-insensitive SQL handling
 *  - Fast keyword lookup (O(1))
 *  - Clear lexical error reporting
 *  - No grammar decisions (parser decides meaning)
 */
public class Lexer {

    private final String input;
    private int pos = 0;

    /**
     * Centralized keyword registry.
     * NOTE:
     * - ONLY true SQL keywords go here
     * - Contextual words (like WORK) should NOT be forced
     */
    private static final Map<String, TokenType> keywords = new HashMap<>();

    static {
        // =====================================================
        // DML
        // =====================================================
        keywords.put("SELECT", TokenType.SELECT);
        keywords.put("FROM", TokenType.FROM);
        keywords.put("WHERE", TokenType.WHERE);
        keywords.put("INSERT", TokenType.INSERT);
        keywords.put("INTO", TokenType.INTO);
        keywords.put("VALUES", TokenType.VALUES);
        keywords.put("UPDATE", TokenType.UPDATE);
        keywords.put("SET", TokenType.SET);
        keywords.put("DELETE", TokenType.DELETE);

        // =====================================================
        // JOIN
        // =====================================================
        keywords.put("JOIN", TokenType.JOIN);
        keywords.put("INNER", TokenType.INNER);
        keywords.put("LEFT", TokenType.LEFT);
        keywords.put("RIGHT", TokenType.RIGHT);
        keywords.put("FULL", TokenType.FULL);
        keywords.put("OUTER", TokenType.OUTER);
        keywords.put("CROSS", TokenType.CROSS);
        keywords.put("NATURAL", TokenType.NATURAL);
        keywords.put("ON", TokenType.ON);

        // =====================================================
        // SET OPERATORS
        // =====================================================
        keywords.put("UNION", TokenType.UNION);
        keywords.put("INTERSECT", TokenType.INTERSECT);
        keywords.put("EXCEPT", TokenType.EXCEPT);
        keywords.put("ALL", TokenType.ALL);

        // =====================================================
        // DDL
        // =====================================================
        keywords.put("CREATE", TokenType.CREATE);
        keywords.put("DROP", TokenType.DROP);
        keywords.put("ALTER", TokenType.ALTER);
        keywords.put("TRUNCATE", TokenType.TRUNCATE);
        keywords.put("TABLE", TokenType.TABLE);
        keywords.put("INDEX", TokenType.INDEX);
        keywords.put("VIEW", TokenType.VIEW);

        // =====================================================
        // CONSTRAINTS
        // =====================================================
        keywords.put("PRIMARY", TokenType.PRIMARY);
        keywords.put("KEY", TokenType.KEY);
        keywords.put("FOREIGN", TokenType.FOREIGN);
        keywords.put("REFERENCES", TokenType.REFERENCES);
        keywords.put("UNIQUE", TokenType.UNIQUE);
        keywords.put("DEFAULT", TokenType.DEFAULT);
        keywords.put("CHECK", TokenType.CHECK);
        keywords.put("NOT", TokenType.NOT);
        keywords.put("NULL", TokenType.NULL);
        keywords.put("AUTO_INCREMENT", TokenType.AUTO_INCREMENT);

        // =====================================================
        // ALTER HELPERS
        // =====================================================
        keywords.put("ADD", TokenType.ADD);
        keywords.put("COLUMN", TokenType.COLUMN);
        keywords.put("MODIFY", TokenType.MODIFY);
        keywords.put("RENAME", TokenType.RENAME);
        keywords.put("TO", TokenType.TO);
        keywords.put("IF", TokenType.IF);
        keywords.put("EXISTS", TokenType.EXISTS);

        // =====================================================
        // TYPES
        // =====================================================
        keywords.put("INT", TokenType.INT);
        keywords.put("INTEGER", TokenType.INTEGER);
        keywords.put("VARCHAR", TokenType.VARCHAR);
        keywords.put("BOOLEAN", TokenType.BOOLEAN);
        keywords.put("DECIMAL", TokenType.DECIMAL);
        keywords.put("DATE", TokenType.DATE);
        keywords.put("TEXT", TokenType.TEXT);
        keywords.put("BLOB", TokenType.BLOB);

        // =====================================================
        // LOGIC
        // =====================================================
        keywords.put("AND", TokenType.AND);
        keywords.put("OR", TokenType.OR);
        keywords.put("IS", TokenType.IS);
        keywords.put("LIKE", TokenType.LIKE);
        keywords.put("IN", TokenType.IN);
        keywords.put("BETWEEN", TokenType.BETWEEN);

        // =====================================================
        // ORDER / GROUP
        // =====================================================
        keywords.put("ORDER", TokenType.ORDER_BY);
        keywords.put("GROUP", TokenType.GROUP_BY);
        keywords.put("HAVING", TokenType.HAVING);
        keywords.put("BY", TokenType.IDENTIFIER); // parser validates
        keywords.put("ASC", TokenType.ASC);
        keywords.put("DESC", TokenType.DESC);
        keywords.put("AS", TokenType.AS);
        keywords.put("DISTINCT", TokenType.DISTINCT);
        keywords.put("LIMIT", TokenType.LIMIT);
        keywords.put("OFFSET", TokenType.OFFSET);

        // =====================================================
        // VALUES & TCL
        // =====================================================
        keywords.put("TRUE", TokenType.TRUE);
        keywords.put("FALSE", TokenType.FALSE);
        keywords.put("SAVEPOINT", TokenType.SAVEPOINT);
        keywords.put("OPTION", TokenType.OPTION);
        keywords.put("COMMIT", TokenType.COMMIT);
        keywords.put("ROLLBACK", TokenType.ROLLBACK);
    }

    public Lexer(String input) {
        this.input = input;
    }

    // =====================================================
    // TOKENIZATION
    // =====================================================
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < input.length()) {
            char current = input.charAt(pos);

            // Skip whitespace
            if (Character.isWhitespace(current)) {
                pos++;
                continue;
            }

            // Comments
            if (current == '-' && peekNext() == '-') {
                while (pos < input.length() && input.charAt(pos) != '\n') pos++;
                continue;
            }

            if (current == '/' && peekNext() == '*') {
                pos += 2;
                while (pos + 1 < input.length() &&
                       !(input.charAt(pos) == '*' && input.charAt(pos + 1) == '/')) {
                    pos++;
                }
                pos += 2;
                continue;
            }

            // Multi-char operators
            if (pos + 1 < input.length()) {
                String op = input.substring(pos, pos + 2);
                if (op.equals(">=")) { add(tokens, TokenType.GE, op); pos += 2; continue; }
                if (op.equals("<=")) { add(tokens, TokenType.LE, op); pos += 2; continue; }
                if (op.equals("!=") || op.equals("<>")) {
                    add(tokens, TokenType.NOT_EQUALS, op);
                    pos += 2;
                    continue;
                }
            }

            // Single symbols
            switch (current) {
            case ',': add(tokens, TokenType.COMMA, ","); pos++; continue;
            case ';': add(tokens, TokenType.SEMICOLON, ";"); pos++; continue;
            case '(': add(tokens, TokenType.LPAREN, "("); pos++; continue;
            case ')': add(tokens, TokenType.RPAREN, ")"); pos++; continue;
            case '*': add(tokens, TokenType.STAR, "*"); pos++; continue;
            case '+': add(tokens, TokenType.PLUS, "+"); pos++; continue;     
            case '-': add(tokens, TokenType.MINUS, "-"); pos++; continue;    
            case '/': add(tokens, TokenType.SLASH, "/"); pos++; continue;    
            case '%': add(tokens, TokenType.PERCENT, "%"); pos++; continue;  
            case '=': add(tokens, TokenType.EQUALS, "="); pos++; continue;
            case '>': add(tokens, TokenType.GT, ">"); pos++; continue;
            case '<': add(tokens, TokenType.LT, "<"); pos++; continue;
            case '.': add(tokens, TokenType.DOT, "."); pos++; continue;
        }
            // Strings
            if (current == '\'') {
                tokens.add(readString());
                continue;
            }

            // Numbers
            if (Character.isDigit(current)) {
                tokens.add(readNumber());
                continue;
            }

            // Identifiers / Keywords
            if (Character.isLetter(current) || current == '_') {
                tokens.add(readIdentifier());
                continue;
            }

            throw new RuntimeException(
                "Lexical Error: Invalid character '" + current +
                "' at position " + pos
            );
        }

        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void add(List<Token> tokens, TokenType type, String value) {
        tokens.add(new Token(type, value, pos));
    }

    private char peekNext() {
        return (pos + 1 < input.length()) ? input.charAt(pos + 1) : '\0';
    }

    private Token readString() {
        int start = pos++;
        StringBuilder sb = new StringBuilder();

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\'') {
                pos++;
                return new Token(TokenType.STRING, sb.toString(), start);
            }
            sb.append(c);
            pos++;
        }

        throw new RuntimeException("Syntax Error: Unterminated string literal");
    }

    private Token readNumber() {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        return new Token(TokenType.NUMBER, input.substring(start, pos), start);
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < input.length() &&
              (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }

        String text = input.substring(start, pos);
        TokenType type = keywords.getOrDefault(text.toUpperCase(), TokenType.IDENTIFIER);
        return new Token(type, text, start);
    }
}
