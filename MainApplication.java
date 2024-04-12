import com.example.OrderVerticle;
import com.example.RestVerticle;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import io.vertx.core.Vertx;

import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.io.InputStream;

public class MainApplication {
    public static void main(String[] args) {
//        System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
//        System.setProperty("hazelcast.local.localPort", "5701");
        // Create Hazelcast cluster manager
        Config hazelcastConfig = loadConfigFromFile("/cluster.xml");
        VertxOptions options = new VertxOptions()
                .setClusterManager(new HazelcastClusterManager(hazelcastConfig));

        // Create clustered Vert.x instance
        Vertx.clusteredVertx(options, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                System.out.println("Vert.x cluster started successfully");

                // Deploy verticles here
                vertx.deployVerticle(new RestVerticle());
                vertx.deployVerticle(new OrderVerticle());
            } else {
                System.err.println("Failed to start Vert.x cluster: " + result.cause());
            }
        });
    }
    private static Config loadConfigFromFile(String filePath) {
        try (InputStream inputStream = MainApplication.class.getClassLoader().getResourceAsStream(filePath)) {
            XmlConfigBuilder configBuilder = new XmlConfigBuilder(inputStream);
            return configBuilder.build();
        } catch (Exception e) {
            System.err.println("Failed to load Hazelcast configuration from file: " + e.getMessage());
            return new Config(); // Return default configuration if file loading fails
        }
    }
}
