package com.androids3demo;

import android.app.Application;
import android.content.Context;

/**
 * Created by akash on 3/22/15.
 */
public class AWSApplication extends Application
{
    private static AWSApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    static public AWSApplication getAppContext(){
        return instance;
    }
}
