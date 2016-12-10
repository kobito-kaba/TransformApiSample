package jp.co.yahoo.sample.logger

import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.lang.reflect.Method

/**
 * Android ProjectのModuleにapplyされるプラグイン
 * Plugin<Project>を実装することで、プラグインとして扱われる
 * ビルド時に、Transform APIを使用したLoggerTransformerを登録する
 * 実際にバイトコード変換を行うのは、登録されたLoggerTransformer
 */
class Logger implements Plugin<Project>{

    @Override
    void apply(Project project) {
        // Android Applicationまたは、ライブラリであることを確認する
        def isAndroidApp = project.plugins.withType(AppPlugin)
        def isAndroidLib = project.plugins.withType(LibraryPlugin)
        if (!isAndroidApp && !isAndroidLib) {
            throw new GradleException("'com.android.application' or 'com.android.library' plugin required.")
        }

        // Android Gradle Plugin 2.0以降であることを確認する
        if (!isTransformAvailable()) {
            throw new GradleException('Logger gradle plugin only supports android gradle plugin 2.0.0 or later')
        }

        // バイトコード変換を行うTransformerを登録する
        project.android.registerTransform(new LoggerTransformer(project))
    }

    private static boolean isTransformAvailable() {
        try {
            Class transform = Class.forName('com.android.build.api.transform.Transform')
            Method transformMethod = transform.getMethod("transform", [TransformInvocation.class] as Class[])
            return transformMethod != null
        } catch (Exception ignored) {
            return false
        }
    }
}
