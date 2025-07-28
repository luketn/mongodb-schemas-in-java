package com.luketn.dataaccess.mongodb;

import com.luketn.datamodel.mongodb.Product;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductDataAccess {
    private static final int pageSize = 10;

    private final MongoDBProvider mongoDBProvider;

    public ProductDataAccess(MongoDBProvider mongoDBProvider) {
        this.mongoDBProvider = mongoDBProvider;
    }

    public List<Product> listProducts(int page) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<Product> collection = database.getCollection("products", Product.class);
        return collection.find().skip(page * pageSize).limit(pageSize).into(new ArrayList<>());
    }

}
