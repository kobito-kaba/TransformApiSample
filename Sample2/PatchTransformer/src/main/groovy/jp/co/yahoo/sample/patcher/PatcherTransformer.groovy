package jp.co.yahoo.sample.patcher

import com.android.SdkConstants
import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.io.FileType
import groovy.xml.MarkupBuilder
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.CtPrimitiveType
import javassist.LoaderClassPath
import javassist.Modifier
import javassist.expr.Cast
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PatcherTransformer extends Transform {

    private static final String PACKAGE = "jp.co.yahoo.android.hotpatchandroidlib"

    private Logger logger = LoggerFactory.getLogger('PatcherTransformer')

    private Project project

    public PatcherTransformer(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return 'PatcherTransformer'
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
        def outputDir = outputProvider.getContentLocation('patcher',
                getInputTypes(), getScopes(), Format.DIRECTORY)

        // プロジェクト内のクラス名の一覧を作成する
        def inputs = transformInvocation.getInputs()
        def classNames = getClassNames(inputs)

        // クラスプールを作る。修正対象を探す際に必要になる
        def mergedInputs = inputs + transformInvocation.getReferencedInputs()
        ClassPool classPool = getClassPool(mergedInputs)

        // 対象となるクラスファイルを選定し、修正を加える
        modify(classNames, classPool)

        // スコープ内のクラスをファイルに出力する
        classNames.each {
            def ctClass = classPool.getCtClass(it)
            ctClass.writeFile(outputDir.canonicalPath)
        }
    }

    /**
     * 実際に修正を加えるメソッド
     */
    private void modify(Set<String> classNames, ClassPool classPool) {
        def appClass = classPool.getCtClass("android.app.Application")

        def insertMap = new HashMap<String, String>()

        classNames.collect{ classPool.getCtClass(it) }
                // Applicationクラスとinterfaceを除いた、プロジェクト内の全クラスを対象にする
                .findAll{ !it.isInterface() && !it.subclassOf(appClass) && !it.getName().contains('$')}
                .each {

            logger.info "PatcherTransformer: ${it.getName()}"

            // 各対象クラスが実装しているメソッドを集め、
            // その内容を置き換える
            it.declaredMethods.findAll {
                // native、interface、abstractは除外
                !Modifier.isNative(it.getModifiers()) \
                        && !Modifier.isInterface(it.getModifiers()) \
                        && !Modifier.isAbstract(it.getModifiers())
            }.each {
                // メソッドのlong nameを識別子に使う
                def signature = it.longName

                // このメソッドを対象としたスクリプトがあるかをチェックするStatement
                def existScript = "${PACKAGE}.ScriptRepository.getInstance().exists(\"$signature\")"

                // スクリプトを実行するStatement
                def runScript = getRunScriptStatement(it, signature)

                // このメソッドの戻り値の型
                def returnType = it.getReturnType()

                def exe
                if (returnType == CtClass.voidType) {
                    exe = "${runScript};return;"
                } else if (returnType.isPrimitive()) {
                    //exe = "return (${returnType.getName()})$runScript;"
                    CtPrimitiveType primitiveType = (CtPrimitiveType)returnType
                    exe = "return ((${primitiveType.getWrapperName()})$runScript).${returnType.getName()}Value();"
                } else {
                    exe = "return (${returnType.getName()})$runScript;"
                }

                def checkScriptAndRun = new StringBuilder()
                checkScriptAndRun
                        .append("if ($existScript) {")
                        .append(    "$exe")
                        .append("}")

                logger.info "${checkScriptAndRun.toString()}"

                insertMap.put(signature, checkScriptAndRun.toString())

                it.insertBefore(checkScriptAndRun.toString())

            }
        }

        // output modify result to text file
        def sw = new StringWriter()
        def xml = new MarkupBuilder(sw)

        //add json values to the xml builder
        xml.resources() {
            insertMap.each {
                k, v ->
                    string(signature: "${k}", "${v}")
            }
        }

        def mappingOutputDir = new File("${project.getProjectDir().parent}", "mapping")
        if (!mappingOutputDir.exists()) {
            mappingOutputDir.mkdir()
        }

        def stringsFile = new File(mappingOutputDir, "mapping.txt")
        stringsFile.write(sw.toString())
    }

    private String getRunScriptStatement(CtMethod method, String signature) {
        // ScriptRunner#run(String signature, Class returnType, Object instance, Object ... methodArgs)
        def execute = new StringBuilder()
        execute.append( "new ${PACKAGE}.ScriptRunner()")

        execute.append(".run(\"$signature\", ${method.getReturnType().getName()}.class, this")

        // 可変長引数に、このメソッドの引数を渡していく
        def args = method.getParameterTypes()
        if (args.length > 0) {
            execute.append(", new Object[]{ \$args[0]")
            for (int i = 1; i < args.length; i++) {
                execute.append(", \$args[i]")
            }
            execute.append("}")
        } else {
            execute.append(", new Object[0]")
        }

        execute.append(")")

        return execute.toString()
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
