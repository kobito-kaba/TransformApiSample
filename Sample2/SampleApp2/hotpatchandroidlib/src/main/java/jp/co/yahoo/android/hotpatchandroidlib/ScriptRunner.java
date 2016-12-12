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

    public static void init() throws IOException {
        ContextFactory.initGlobal(new UnsafeContextFactory());
    }

    public Object run(String signature, Class returnType, Object instance, Object ... args) {

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

            rhino.evaluateString(scope, script, "JavaScript", 1, null);

            Function function = (Function)scope.get("apply", scope);

            Object result = function.call(rhino, scope, scope, functionParams);

            if (result instanceof Undefined) {
                return result;
            } else {
                return Context.jsToJava(result, returnType);
            }
        } finally {
            Context.exit();
        }
    }

    /**
     * privateなメソッドやフィールドにもアクセスできるようにしたContextFactory
     */
    private static class UnsafeContextFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            return featureIndex == Context.FEATURE_ENHANCED_JAVA_ACCESS
                    || super.hasFeature(cx, featureIndex);
        }
    }

}
