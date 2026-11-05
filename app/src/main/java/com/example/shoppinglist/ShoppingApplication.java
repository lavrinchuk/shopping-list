package com.example.shoppinglist;

import android.app.Application;
import com.example.shoppinglist.data.ShoppingDatabase;
import com.example.shoppinglist.data.ShoppingRepository;

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
