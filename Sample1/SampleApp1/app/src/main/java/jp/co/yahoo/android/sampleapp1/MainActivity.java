package jp.co.yahoo.android.sampleapp1;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import jp.co.yahoo.sample.logger.annotations.Logging;

@Logging("MainActivityTag")
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int current = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.decrementButton).setOnClickListener(this);
        findViewById(R.id.incrementButton).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.incrementButton) increment();
        else decrement();
    }

    private void showNumber(int number) {
        TextView numberText = (TextView)findViewById(R.id.numberText);
        numberText.setText(String.valueOf(number));
    }

    private void increment() {
        showNumber(++current);
    }

    private void decrement() {
        showNumber(--current);
    }

}
