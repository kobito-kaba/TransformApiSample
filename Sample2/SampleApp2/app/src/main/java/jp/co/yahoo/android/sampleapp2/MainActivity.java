package jp.co.yahoo.android.sampleapp2;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import jp.co.yahoo.android.hotpatchandroidlib.ScriptDownloader;
import jp.co.yahoo.android.hotpatchandroidlib.ScriptRepository;
import jp.co.yahoo.android.hotpatchandroidlib.ScriptRunner;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int current = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.decrementButton).setOnClickListener(this);
        findViewById(R.id.incrementButton).setOnClickListener(this);
        findViewById(R.id.downloadButton).setOnClickListener(this);
        findViewById(R.id.removeButton).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.incrementButton:
                int res1 = increment();
                Log.d("script", String.valueOf(res1));
                break;
            case R.id.decrementButton:
                int res2 = decrement();
                Log.d("script", String.valueOf(res2));
                break;
            case R.id.downloadButton:
                downloadScripts();
                break;
            case R.id.removeButton:
                removeScripts();
                break;
        }
    }

    private void showNumber(int number) {
        TextView numberText = (TextView)findViewById(R.id.numberText);
        numberText.setText(String.valueOf(number));
    }

    private int increment() {
        showNumber(++current);
        return current;
    }

    private Integer decrement() {
        showNumber(--current);
        return current;
    }

    private void downloadScripts() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                return ScriptDownloader.downloadScripts();
            }

            @Override
            protected void onPostExecute(Boolean result) {
                String message;
                if (result)  message = "success";
                else message = "failed";
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }

    private void removeScripts() {
        ScriptRepository.getInstance().removeAll(this);
    }

}
