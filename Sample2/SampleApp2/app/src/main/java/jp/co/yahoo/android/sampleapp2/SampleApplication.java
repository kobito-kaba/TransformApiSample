package jp.co.yahoo.android.sampleapp2;

import android.app.Application;

import java.io.IOException;

import jp.co.yahoo.android.hotpatchandroidlib.ScriptRepository;
import jp.co.yahoo.android.hotpatchandroidlib.ScriptRunner;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ScriptRunner.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ScriptRepository.getInstance().loadScripts(this);

    }
}
