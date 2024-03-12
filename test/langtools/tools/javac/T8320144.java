/*
 * @test /nodynamiccopyright/
 * @bug 8320144
 * @summary Compilation crashes when a custom annotation with invalid default value is used
 * @compile/fail/ref=T8320144.out -XDrawDiagnostics T8320144.java
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class T8320144 {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface TestAnnotation {
        public String[] excludeModules() default new String[0];
        public String[] value() default new String[] { 3 };
    }
}
