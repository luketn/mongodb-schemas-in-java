package com.luketn.dataaccess.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class MongoDBProvider {
    private static final Logger logger = LoggerFactory.getLogger(MongoDBProvider.class);

    private final String connectionString;
    private final String databaseName;

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public MongoDBProvider(
            @Value("${mongodb-schema.dataaccess.mongodb.connection-string}") String connectionString,
            @Value("${mongodb-schema.dataaccess.mongodb.database-name}") String databaseName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;

        this.mongoClient = null;
        this.mongoDatabase = null;
    }

    public MongoDatabase getMongoDatabase() {
        init();
        if (mongoDatabase == null) {
            throw new IllegalStateException("MongoDB connection is not initialized.");
        }
        return mongoDatabase;
    }

    private void init() {
        if (mongoClient == null || mongoDatabase == null) {
            synchronized (MongoDBProvider.class) {
                if (mongoClient == null || mongoDatabase == null) {
                    long startTime = System.currentTimeMillis();
                    logger.info("Connecting to MongoDB at {}", connectionString);

                    MongoClientSettings settings = createClientSettings(connectionString);
                    mongoClient = MongoClients.create(settings);
                    mongoDatabase = mongoClient.getDatabase(databaseName);

                    logger.info("Connected to MongoDB in {}ms", System.currentTimeMillis() - startTime);
                }
            }
        }
    }

    public static MongoClientSettings createClientSettings(String connectionString) {

        // Configure codec registry for POJO serialization with full flexibility
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .automatic(true)
                .conventions(Conventions.DEFAULT_CONVENTIONS)
                .build());

        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                pojoCodecRegistry);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .codecRegistry(codecRegistry)
                .build();

        return settings;
    }
}
