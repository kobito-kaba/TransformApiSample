package jp.co.yahoo.sample.logger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * クラスにつけた場合、実装しているメソッドの冒頭に、ログ出力のコードを挿入する
 * 親クラスから継承しただけで、ファイル内にないメソッドは対象とならない
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Logging {
    // 出力するログのタグ
    String value() default "sample-logger";
}
