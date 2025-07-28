package com.luketn.datamodel.api;

import com.luketn.datamodel.mongodb.Product;

import java.util.List;

public record ProductList(List<Product> products, int page, boolean isLastPage) { }
