package com.linkvault.app;

import android.app.Application;

import com.linkvault.app.di.AppContainer;

public class App extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer getContainer() {
        return container;
    }
}
