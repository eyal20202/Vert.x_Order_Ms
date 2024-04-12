package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class OrderVerticle extends AbstractVerticle {

  private static final String ORDER_DATA_FILE = "orderData.json";
  private Map<String, JsonArray> userOrders = new HashMap<>();
  private EventBus eventBus;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    eventBus = vertx.eventBus();

    eventBus.consumer("addOrder", message -> {
      JsonObject orderData = new JsonObject(message.body().toString());
      addOrder(orderData);
      message.reply("Order Added Successfully");
    });

    eventBus.consumer("getOrders", message -> {
      JsonArray userOrderArray = userOrders.getOrDefault("username", new JsonArray());
      message.reply(userOrderArray.encode());
    });

    startPromise.complete();
  }

  private void addOrder(JsonObject orderData) {
    String username = orderData.getString("username");
    JsonArray orders = userOrders.computeIfAbsent(username, k -> new JsonArray());
    orders.add(orderData.put("orderId", userOrders.size() + 1).put("date", LocalDateTime.now().toString()));
    saveOrders();
  }

  private void saveOrders() {
    try {
      JsonArray allOrders = new JsonArray();
      for (JsonArray userOrder : userOrders.values()) {
        allOrders.addAll(userOrder);
      }
      Files.writeString(Paths.get(ORDER_DATA_FILE), allOrders.encode());
    } catch (Exception e) {
      System.err.println("Error saving orders to file: " + e.getMessage());
    }
  }
}
