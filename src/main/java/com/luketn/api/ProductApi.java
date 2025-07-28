package com.luketn.api;

import com.luketn.datamodel.api.ProductList;
import com.luketn.datamodel.mongodb.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;


@RestController
@RequestMapping("/product")
public class ProductApi {
    @GetMapping( "/list")
    public ProductList listProducts(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        return new ProductList(
                List.of(
                        new Product("1", "Product A", "Description A", BigDecimal.valueOf(10.99)),
                        new Product("2", "Product B", "Description B", BigDecimal.valueOf(20.99)),
                        new Product("3", null, "Description C", BigDecimal.valueOf(30.99))
                ),
                page,
                true
        );
    }
}
