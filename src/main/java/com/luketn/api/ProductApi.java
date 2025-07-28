package com.luketn.api;

import com.luketn.dataaccess.mongodb.ProductDataAccess;
import com.luketn.datamodel.api.ProductList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/product")
public class ProductApi {
    private final ProductDataAccess productDataAccess;

    public ProductApi(ProductDataAccess productDataAccess) {
        this.productDataAccess = productDataAccess;
    }

    @GetMapping( "/list")
    public ProductList listProducts(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        return new ProductList(productDataAccess.listProducts(page));
    }
}
