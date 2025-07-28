package com.luketn.api;

import com.luketn.datamodel.api.ProductList;
import com.luketn.datamodel.mongodb.Product;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;


@Path("/product")
public class ProductApi {
    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductList listProducts(@QueryParam("page") int page) {
        return new ProductList(
                List.of(
                        new Product("1", "Product A", "Description A", BigDecimal.valueOf(10.99)),
                        new Product("2", "Product B", "Description B", BigDecimal.valueOf(20.99)),
                        new Product("3", null, "Description C", BigDecimal.valueOf(30.99))
                ),
                page,
                1
        );
    }
}
