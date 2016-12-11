package jp.co.yahoo.android.hotpatchandroidlib;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class ScriptDownloader {
    private static final String PORT = ":8080";
    // Androidエミュレータから見たときの、PCのローカルホスト
    private static final String BASE_URL = "http://10.0.2.2" + PORT;
    private static final String FILE_DOWNLOAD_URL = BASE_URL + "/patches/%s";
    private static final String FILE_LIST_URL = BASE_URL + "/patches/";

    private ScriptDownloader() {
    }

    public static boolean downloadScripts() {
        String[] scriptFiles = downloadFileList();

        if (scriptFiles == null) return false;

        for(String scriptFile : scriptFiles) {
            String script = downloadFile(scriptFile);
            if (script == null) return false;

            ScriptRepository.getInstance().addScript(scriptFile, script);
        }

        return true;
    }

    private static String downloadFile(String fileName) {
        try {
            return request(String.format(FILE_DOWNLOAD_URL, fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String[] downloadFileList() {
        String[] files = null;

        try {
            String response = request(FILE_LIST_URL);
            JSONArray responseJson = new JSONArray(response);

            int length = responseJson.length();
            files = new String[length];
            for(int i = 0; i < length; i++) {
                files[i] = responseJson.getString(i);
            }

        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        return files;
    }

    private static String request(String urlString) throws IOException {
        BufferedInputStream in = null;
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            in = new BufferedInputStream(connection.getInputStream());
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            int len;
            while((len = in.read(buffer, 0, buffer.length)) != -1) {
                bao.write(buffer, 0, len);
            }
            bao.flush();

            return new String(bao.toByteArray(), "UTF-8");
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
