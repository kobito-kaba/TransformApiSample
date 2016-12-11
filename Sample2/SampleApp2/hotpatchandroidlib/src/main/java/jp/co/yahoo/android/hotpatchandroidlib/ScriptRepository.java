package jp.co.yahoo.android.hotpatchandroidlib;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScriptRepository {

    private static ScriptRepository instance;

    private Map<String, String> scripts;

    public static synchronized ScriptRepository getInstance() {
        if (instance == null) {
            instance = new ScriptRepository();
        }
        return instance;
    }

    private ScriptRepository() {
        scripts = new HashMap<>();
    }

    public boolean exists(String signature) {
        return scripts.containsKey(signature);
    }

    public String findScript(String signature) {
        return scripts.get(signature);
    }

    public void addScript(String fileName, String script) {
        String signature = getSignatureFromFileName(fileName);
        scripts.put(signature, script);
    }

    public void findScripts(Context context, ScriptHandler handler) {
        File appDir = context.getFilesDir();
        File scriptDir = new File(appDir, "scripts");

        if (!scriptDir.exists() && !scriptDir.mkdir()) {
            throw new RuntimeException("failed to create script directory");
        }

        File[] scriptFiles = scriptDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getPath().endsWith(".patch");
            }
        });

        for(File scriptFile : scriptFiles) {
            handler.run(scriptFile);
        }
    }

    public void removeAll(Context context) {
        findScripts(context, new ScriptHandler() {
            @Override
            public void run(File scriptFile) {
                scriptFile.delete();
            }
        });

        scripts.clear();
    }

    public void loadScripts(Context context) {
        findScripts(context, new ScriptHandler() {
            @Override
            public void run(File scriptFile) {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new FileReader(scriptFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while((line = br.readLine()) != null) {
                        sb.append(line);
                    }

                    String signature = getSignatureFromFileName(scriptFile.getName());

                    scripts.put(signature, sb.toString());

                } catch (IOException ignore) {
                    ignore.printStackTrace();
                } finally {
                    if (br != null) {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private String getSignatureFromFileName(String fileName) {
        int index = fileName.lastIndexOf('.');
        return fileName.substring(0, index);
    }

    public interface ScriptHandler {
        void run(File scriptFile);
    }

}
