package com.agentic;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.Materializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

public class SimpleWebServer extends AllDirectives {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleWebServer.class);
    private final ActorSystem<Void> system;

    public SimpleWebServer(ActorSystem<Void> system) {
        this.system = system;
    }

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(
            Behaviors.empty(), 
            "SimpleWebSystem"
        );

        try {
            SimpleWebServer server = new SimpleWebServer(system);
            CompletionStage<ServerBinding> binding = server.startServer();
            
            System.out.println("🌐 Simple Web Server starting...");
            System.out.println("📱 Open your browser and go to: http://localhost:8080");
            System.out.println("⏹️  Press Ctrl+C to stop the server...");
            
            // Keep the server running
            Thread.currentThread().join();
            
            binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
                
        } catch (InterruptedException e) {
            logger.info("Server interrupted, shutting down...");
            system.terminate();
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            system.terminate();
        }
    }

    private CompletionStage<ServerBinding> startServer() {
        final Http http = Http.get(system);
        final Materializer materializer = Materializer.createMaterializer(system);

        return http.newServerAt("localhost", 8080)
                .bind(createRoute())
                .thenApply(binding -> {
                    logger.info("Server online at http://localhost:8080/");
                    return binding;
                });
    }

    private Route createRoute() {
        return concat(
            // Serve the HTML file
            pathSingleSlash(() -> 
                complete(HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(ContentTypes.TEXT_HTML_UTF8, loadStaticFile("index.html"))
                )
            ),
            
            // Simple test endpoint
            path("test", () ->
                complete(HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Web server is working!")
                )
            ),
            
            // Handle any other requests with 404
            pathPrefix("", () ->
                complete(HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Not Found"))
            )
        );
    }

    private String loadStaticFile(String filename) {
        try {
            String resourcePath = "src/main/resources/static/" + filename;
            return Files.readString(Paths.get(resourcePath));
        } catch (IOException e) {
            logger.error("Error loading static file: {}", filename, e);
            return "<html><body><h1>Error loading page</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }
} 