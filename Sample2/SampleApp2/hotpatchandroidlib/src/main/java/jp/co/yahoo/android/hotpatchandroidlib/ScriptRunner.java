package jp.co.yahoo.android.hotpatchandroidlib;


import android.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.IOException;

public class ScriptRunner {

    private static final String RHINO_LOG = "var log = Packages.jp.co.yahoo.android.hotpatchandroidlib.ScriptRunner.log;";

    public static void init() throws IOException {
        ContextFactory.initGlobal(new UnsafeContextFactory());
    }

    private static void log(String msg) {
        Log.d("ScriptRunner", msg);
    }

    public Object run(String signature, Class returnType, Object instance, Object ... args) {

        log(signature + " started");

        String script = ScriptRepository.getInstance().findScript(signature);

        Object[] functionParams = new Object[]{};

        Context rhino = Context.enter();

        rhino.setOptimizationLevel(-1);

        try {
            Scriptable scope = rhino.initStandardObjects();

            ScriptableObject.putProperty(scope, "instance", Context.javaToJS(instance, scope));

            for (int i = 0; i < args.length; i++) {
                ScriptableObject.putProperty(scope, "arg" + i, Context.javaToJS(args[i], scope));
            }

            rhino.evaluateString(scope, RHINO_LOG + script, "JavaScript", 1, null);

            Function function = (Function)scope.get("apply", scope);

            Object result = (Object)function.call(rhino, scope, scope, functionParams);

            if (result instanceof Undefined) {
                return result;
            } else {
                return Context.jsToJava(result, returnType);
            }
        } finally {
            Context.exit();
            log(signature + " exit");
        }
    }

    /**
     * privateなメソッドやフィールドにもアクセスできるようにしたContextFactory
     */
    private static class UnsafeContextFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            return featureIndex == 13 || super.hasFeature(cx, featureIndex);
        }
    }

}
