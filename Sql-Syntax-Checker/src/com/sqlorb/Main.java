package com.sqlorb;

import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=========================================");
        System.out.println("   SQLorb Syntax Validator   ");
        System.out.println("=========================================");
        System.out.println("Supported: DQL, DML, DDL, TCL, DCL");
        System.out.println("Type 'exit' to quit.\n");

        while (true) {
            System.out.print("SQL > ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) break;
            
            try {
                // 1. Lexical Analysis (String -> Tokens)
                Lexer lexer = new Lexer(input);
                List<Token> tokens = lexer.tokenize();
                
                // 2. Syntax Analysis (Tokens -> Logic)
                // We now use the Master 'SQLParser' instead of the old 'Parser'
                SQLParser parser = new SQLParser(tokens);
                parser.parse(); 
                
                System.out.println("✅ Valid Syntax!");
                
            } catch (Exception e) {
                // Print error in red (if console supports it) or just standard error
                System.err.println("❌ " + e.getMessage());
                // Optional: Print stack trace for debugging if needed
                // e.printStackTrace(); 
            }
        }
        scanner.close();
        System.out.println("Exiting...");
    }
}