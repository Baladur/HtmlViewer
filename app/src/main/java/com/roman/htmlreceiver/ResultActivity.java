package com.roman.htmlreceiver;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResultActivity extends Activity {

    TextView resultHeader;
    TextView resultText;
    ScrollView scroll;
    Lock asyncControl = new ReentrantLock();
    Condition readMore = asyncControl.newCondition();
    boolean processFinished;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        String site = getIntent().getStringExtra("input").toString();
        resultHeader = (TextView)findViewById(R.id.restultHeader);
        resultHeader.setText("HTML-code for " + site);
        resultText = (TextView)findViewById(R.id.resultText);
        scroll = (ScrollView)findViewById(R.id.scroll);
        scroll.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View view = (View) scroll.getChildAt(scroll.getChildCount() - 1);
                int diff = (view.getBottom() - (scroll.getHeight() + scroll.getScrollY()));
                if (diff == 0) {
                   awakeTask();
                }
            }
        });
        ProcessTask processTask = new ProcessTask();
        processTask.execute("http://" + site);
    }

    public void onBackPressed() {
        super.onBackPressed();
        processFinished = true;
        awakeTask();
    }

    private void awakeTask() {
        try {
            asyncControl.tryLock();
        } finally {
            readMore.signal();
            asyncControl.unlock();
        }
    }

    private void postResult(String res) {
        resultText.append(res);
    }

    private void postError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultText.setTextSize(20);
                resultText.setTextColor(getResources().getColor(R.color.red));
                resultText.setText(error);
            }
        });
    }

    class ProcessTask extends AsyncTask<String, List<String>, Void> {

        String site;

        @Override
        protected Void doInBackground(String... params) {
            site = params[0];

            BufferedReader reader = null;
            try {
                URL url = new URL(site);
                URLConnection connection = url.openConnection();
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), getConnectionEncoding(connection)));
                String buf;
                int i = 0;
                int batchSize = 100;
                processFinished = false;
                List<String> lines = new ArrayList<>(batchSize);
                while (!processFinished) {
                    for (i = 0; i < batchSize; i++) {
                        buf = reader.readLine();
                        if (buf == null) {
                            processFinished = true;
                            break;
                        }
                        lines.add(buf);
                    }
                    publishProgress(lines);
                    waitForJobRequest();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                postError("Check URL for mistakes.");
            } catch (IOException e) {
                e.printStackTrace();
                postError("URL might be incorrect or something wrong with Internet.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    if (reader != null)
                        reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        protected void onProgressUpdate(List<String>... lines) {
            super.onProgressUpdate(lines);
            for (int i = 0; i < lines[0].size(); i++)
                postResult(lines[0].get(i) + "\n");
            lines[0].clear();
        }

        private void waitForJobRequest() throws InterruptedException {
            asyncControl.lock();
            try {
                readMore.await();
            } finally {
                asyncControl.unlock();
            }
        }

        private String getConnectionEncoding(URLConnection connection) {
            String contentType = connection.getContentType();
            String[] values = contentType.split(";");
            String charset = "";

            for (String value : values) {
                value = value.trim();
                if (value.toLowerCase().startsWith("charset=")) {
                    charset = value.substring("charset=".length());
                }
            }
            if ("".equals(charset)) {
                charset = "UTF-8";
            }

            return charset;
        }
    }
}
