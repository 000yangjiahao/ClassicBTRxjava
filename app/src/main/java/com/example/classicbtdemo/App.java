package com.example.classicbtdemo;

import android.app.Application;
import android.util.Log;

import java.io.IOException;
import java.net.SocketException;

import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RxJavaPlugins.setErrorHandler(e -> {
            Throwable throwable = e;
            if (e instanceof UndeliverableException) {
                throwable = e.getCause();
            }
            if ((throwable instanceof IOException) || (throwable instanceof SocketException)) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (throwable instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((throwable instanceof NullPointerException) || (throwable instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
                return;
            }
            if (throwable instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
                return;
            }
            Log.w("Undeliverable exception received, not sure what to do", throwable);
        });
    }
}
