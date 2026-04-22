package com.sqlorb;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/validate", new ValidationHandler());

        server.setExecutor(null);
        server.start();
        
        System.out.println("🚀 Server started!"); 
        System.out.println("   Waiting for requests at http://localhost:" + port + "/validate");
    }

    static class ValidationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // --- CORS HEADERS ---
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            String sqlQuery = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            String responseMessage;
            int statusCode;

            try {
                Lexer lexer = new Lexer(sqlQuery);
                List<Token> tokens = lexer.tokenize();
                
                SQLParser parser = new SQLParser(tokens);
                parser.parse();
                
                responseMessage = "{" +
                        "\"status\": \"success\", " +
                        "\"message\": \"✅ Valid Syntax!\"" +
                        "}";
                statusCode = 200;
                
            } catch (Exception e) {
                // --- FIX: SAFE JSON CONSTRUCTION ---
                String rawError = e.getMessage();
                if (rawError == null) rawError = "Unknown Error (" + e.getClass().getSimpleName() + ")";

                // 1. Escape quotes to prevent breaking JSON string
                // 2. Escape newlines to prevent invalid JSON format
                String cleanError = rawError
                        .replace("\"", "'")
                        .replace("\n", "\\n")
                        .replace("\r", ""); 

                String mainError = cleanError;
                String suggestion = "";

                if (cleanError.contains("Suggested fix:")) {
                    String[] parts = cleanError.split("Suggested fix:");
                    mainError = parts[0].trim();
                    if (parts.length > 1) {
                        suggestion = parts[1].trim();
                    }
                }

                responseMessage = "{" +
                        "\"status\": \"error\", " +
                        "\"message\": \"" + mainError + "\", " +
                        "\"suggestion\": \"" + suggestion + "\"" +
                        "}";
                statusCode = 400;
            }

            byte[] responseBytes = responseMessage.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}