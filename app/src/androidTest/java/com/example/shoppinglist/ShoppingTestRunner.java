package com.example.shoppinglist;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;

/** Never launch the production Application/database from instrumented tests. */
public class ShoppingTestRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader loader, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(loader, TestShoppingApplication.class.getName(), context);
    }
}
