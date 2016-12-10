package jp.co.yahoo.sample.logger

import com.android.SdkConstants
import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.io.FileType
import javassist.ClassPool
import javassist.LoaderClassPath
import javassist.Modifier
import jp.co.yahoo.sample.logger.annotations.Logging
import org.gradle.api.Project

/**
 * {@literal @Log}のついたクラス、メソッドを探し出して、
 * ログ出力を行うコードを追加する
 */
class LoggerTransformer extends Transform {

    private Project project

    public LoggerTransformer(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return 'LoggerTransformer'
    }

    /**
     *
     */
    @Override
    Set<ContentType> getInputTypes() {
        // 修正対象はクラスのみとして、リソースは含めない
        return ImmutableSet.<ContentType> of(DefaultContentType.CLASSES)
    }

    /**
     *
     */
    @Override
    Set<Scope> getScopes() {
        // 修正対象を対象プロジェクト内のみとして、ライブラリやサブプロジェクトなどは含めないでおく
        return Sets.immutableEnumSet(Scope.PROJECT)
    }

    /**
     *
     */
    @Override
    Set<Scope> getReferencedScopes() {
        // 修正時、バイトコードを生成する際の参照には、対象プロジェクト外の諸々も含める
        return Sets.immutableEnumSet(Scope.EXTERNAL_LIBRARIES, Scope.PROJECT_LOCAL_DEPS,
                Scope.SUB_PROJECTS, Scope.SUB_PROJECTS_LOCAL_DEPS, Scope.TESTED_CODE)
    }

    /**
     * インクリメンタル実行はしない
     */
    @Override
    boolean isIncremental() {
        return false
    }

    /**
     * バイトコード修正を行う処理を、ここに記述する
     */
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        def outputProvider = transformInvocation.getOutputProvider()

        // 修正ファイルの出力先ディレクトリ
        def outputDir = outputProvider.getContentLocation('logger',
                getInputTypes(), getScopes(), Format.DIRECTORY)

        // プロジェクト内のクラス名の一覧を作成する
        def inputs = transformInvocation.getInputs()
        def classNames = getClassNames(inputs)

        // クラスプールを作る。修正対象を探す際に必要になる
        def mergedInputs = inputs + transformInvocation.getReferencedInputs()
        ClassPool classPool = getClassPool(mergedInputs)

        // 対象となるクラスファイルを選定し、修正を加える
        modify(classNames, classPool, outputDir)
    }

    /**
     * 実際に修正を加えるメソッド
     */
    private static void modify(Set<String> classNames, ClassPool classPool, File outputDir) {
        classNames.collect{ classPool.getCtClass(it) }
                .findAll{ it.hasAnnotation(Logging.class) }
                .each {
            // アノテーションからタグを得る
            def annotation = (Logging)it.getAnnotation(Logging.class)
            def tag = annotation.value()

            // 各対象クラスが実装しているメソッドを集め、
            // その冒頭に、ロギング処理を追加する
            it.declaredMethods.findAll {
                // native、interface、abstractは除外
                !Modifier.isNative(it.getModifiers()) \
                        && !Modifier.isInterface(it.getModifiers()) \
                        && !Modifier.isAbstract(it.getModifiers())
            }.each {
                // メソッド冒頭に処理を追加
                // クラスはフルパスで指定すること
                it.insertBefore("android.util.Log.v(\"${tag}\", \"${it.getLongName()}\");")
            }

            // 修正を加えたクラスをファイルに出力する
            it.writeFile(outputDir.canonicalPath)
        }
    }

    /**
     *　クラスプールを作成する
     */
    private ClassPool getClassPool(Collection<TransformInput> inputs) {
        ClassPool classPool = new ClassPool(null)
        classPool.appendSystemPath()
        classPool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()))

        inputs.each {
            it.directoryInputs.each {
                classPool.appendClassPath(it.file.absolutePath)
            }

            it.jarInputs.each {
                classPool.appendClassPath(it.file.absolutePath)
            }
        }

        project.android.bootClasspath.each {
            String path = it.absolutePath
            classPool.appendClassPath(path)
        }

        return classPool
    }

    /**
     * クラス名の一覧を作成する
     */
    static Set<String> getClassNames(Collection<TransformInput> inputs) {
        Set<String> classNames = new HashSet<String>()

        inputs.each {
            it.directoryInputs.each {
                def dirPath = it.file.absolutePath
                it.file.eachFileRecurse(FileType.FILES) {
                    if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                        // 絶対パスからパッケージ付きのクラス名を作る
                        def className =
                                it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - 6)
                                        .replace(File.separatorChar, '.' as char)
                        classNames.add(className)
                    }
                }
            }
        }
        return classNames
    }
}
