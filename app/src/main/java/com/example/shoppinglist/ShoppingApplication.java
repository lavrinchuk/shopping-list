package com.example.shoppinglist;

import com.example.shoppinglist.data.ShoppingRepository;
import com.example.shoppinglist.data.ShoppingDatabase;

import android.app.Application;

public class ShoppingApplication extends Application {
    private ShoppingRepository repository;

    ShoppingRepository repository() {
        if (repository == null) {
            repository = createRepository();
        }
        return repository;
    }

    ShoppingRepository createRepository() {
        return new ShoppingRepository(new ShoppingDatabase(this));
    }
}
