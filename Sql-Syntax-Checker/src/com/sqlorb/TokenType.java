package com.sqlorb;

public enum TokenType {
    // --- DML ---
    SELECT, FROM, WHERE, DISTINCT, AS, ORDER_BY, GROUP_BY, HAVING, LIMIT, OFFSET, FETCH, TOP,
    INSERT, INTO, VALUES, UPDATE, SET, DELETE,

    // --- DDL ---
    CREATE, ALTER, DROP, TRUNCATE, RENAME,
    DATABASE, TABLE, VIEW, INDEX, SEQUENCE, SCHEMA,
    PRIMARY_KEY, FOREIGN_KEY, REFERENCES, UNIQUE, NOT_NULL, CHECK, DEFAULT,

    // --- DATA TYPES (NEW: Required for CREATE TABLE) ---
    INT, INTEGER, VARCHAR, CHAR, BOOLEAN, DATE, FLOAT, DOUBLE, TEXT,

    // --- JOINS & SET OPS ---
    JOIN, INNER_JOIN, LEFT_JOIN, RIGHT_JOIN, FULL_JOIN, CROSS_JOIN, ON, USING,
    UNION, UNION_ALL, INTERSECT, EXCEPT, IN, EXISTS, ANY, ALL,

    // --- LOGIC & CONDITIONAL ---
    AND, OR, NOT, CASE, WHEN, THEN, ELSE, END,
    NULL, IS, TRUE, FALSE,
    ASC, DESC, BETWEEN, LIKE, ESCAPE,
    CAST,WITH, // NEW: For type conversion

    // --- AGGREGATES & FUNCTIONS ---
    COUNT, SUM, AVG, MIN, MAX,
    UPPER, LOWER, LENGTH, NOW, // NEW: Common scalar functions

    // --- TCL & DCL ---
    COMMIT, ROLLBACK, SAVEPOINT, BEGIN, TRANSACTION,
    GRANT, REVOKE,TO,

    // --- DQL---
    LEFT,RIGHT,CROSS,INNER,NATURAL,FULL,OUTER,
    
    // --- DATA ---
    IDENTIFIER, // Table/Column names like "users", "age"
    NUMBER,     // 123
    STRING,     // 'John'

    // --- ARITHMETIC ---
    PLUS, // +
    MINUS, // -
    STAR, // *
    SLASH, // /
    PERCENT, // %

    // --- COMPARISON ---
    EQUALS, // =
    NOT_EQUALS, // !=
    NOT_EQUALS_SQL, // <>
    GT, // >
    LT, // <
    GE, // >=
    LE, // <=

    // --- SYMBOLS ---
    COMMA, // ,
    SEMICOLON, // ;
    LPAREN, // (
    RPAREN, // )
    DOT,    // .

    EOF, DECIMAL, PRIMARY, FOREIGN, AUTO_INCREMENT, KEY, COLUMN, ADD, IF, BLOB, EXECUTE, OPTION, MODIFY, WORK, PRIVILEGES, ORDER, BY, CURRENT_DATE, CURRENT_TIMESTAMP, BTREE, HASH, 
}