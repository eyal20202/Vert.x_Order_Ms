package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RestVerticle extends AbstractVerticle {

    private static final String USER_CREDENTIALS_FILE = "userCredentials.json";
    private static final String ORDER_DATA_FILE = "orderData.json";
    private static final Map<String, String> loggedInUsers = new ConcurrentHashMap<>();
    private EventBus eventBus;

    @Override
    public void start(Promise<Void> startPromise) {
        eventBus = vertx.eventBus();

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.post("/login").handler(this::handleLogin);
        router.post("/logout").handler(this::handleLogout);
        router.post("/addOrder").handler(this::handleAddOrder);
        router.get("/getOrders").handler(this::handleGetOrders);

        server.requestHandler(router).listen(8080, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                System.out.println("HTTP server started on port 8080");
            } else {
                startPromise.fail(http.cause());
            }
        });
    }

    private void handleLogin(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.body().asJsonObject();
        String username = requestBody.getString("username");
        String password = requestBody.getString("password");

        if (isValidCredentials(username, password)) {
            String sessionID = java.util.UUID.randomUUID().toString();
            loggedInUsers.put(username, sessionID);
            routingContext.response().addCookie(Cookie.cookie("sessionID", sessionID));
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");
            response.end(new JsonObject().put("success", true).put("sessionID", sessionID).encode());
        } else {
            routingContext.response().setStatusCode(401).end("Username and password not valid");
        }
    }

    private boolean isValidCredentials(String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_CREDENTIALS_FILE))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            JsonArray credentialsArray = new JsonArray(jsonBuilder.toString());
            for (Object obj : credentialsArray) {
                JsonObject userObject = (JsonObject) obj;
                if (userObject.getString("username").equals(username) && userObject.getString("password").equals(password)) {
                    return true;
                }
            }
            return false; // Credentials not found
        } catch (Exception e) {
            System.err.println("Error reading user credentials: " + e.getMessage());
            return false;
        }
    }

    private void handleLogout(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.getBodyAsJson();
        String username = requestBody.getString("username");
        loggedInUsers.remove(username);
        routingContext.response().end("Logout Successful");
    }

    private void handleAddOrder(RoutingContext routingContext) {
        JsonObject orderData = routingContext.getBodyAsJson();
        String username = getUsernameFromSession(routingContext);
        if (username != null) {
            orderData.put("username", username);
            eventBus.request("addOrder", orderData.encode(), reply -> {
                if (reply.succeeded()) {
                    routingContext.response().end("Order Added Successfully");
                } else {
                    routingContext.response().setStatusCode(500).end("Failed to Add Order");
                }
            });
        } else {
            routingContext.response().setStatusCode(401).end("User not authenticated");
        }
    }

    private String getUsernameFromSession(RoutingContext routingContext) {
        String sessionID = routingContext.getCookie("sessionID") != null ? routingContext.getCookie("sessionID").getValue() : null;
        for (Map.Entry<String, String> entry : loggedInUsers.entrySet()) {
            if (entry.getValue().equals(sessionID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void handleGetOrders(RoutingContext routingContext) {
        try {
            String orderData = Files.readString(Paths.get(ORDER_DATA_FILE));
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(orderData);
        } catch (Exception e) {
            System.err.println("Error reading order data: " + e.getMessage());
            routingContext.response().setStatusCode(500).end("Failed to Get Orders");
        }
    }
}
