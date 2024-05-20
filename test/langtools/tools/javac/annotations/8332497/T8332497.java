import toolbox.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * @test
 * @bug 8332497
 * @summary error: javac crashes when annotation processing runs on program with module imports
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 * jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox toolbox.Assert toolbox.Task
 * @run main T8332497
 */
public class T8332497 extends TestRunner {
    final toolbox.ToolBox tb = new ToolBox();
    final Path base = Paths.get(".");
    final String processedSource = """
            import module java.base;
            import java.util.List;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            public class Main {
              public static void main(String[] args) {
                List.of();
              }
              @Ann
              private void test() {
                List.of();
              }
              @Retention(RetentionPolicy.RUNTIME)
              @Target(ElementType.METHOD)
              public @interface Ann {
              }
            }
            """;

    public T8332497() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        var t = new T8332497();
        t.test();
    }

    public void test() throws Exception {
        tb.writeJavaFiles(base, processedSource);
        new toolbox.JavacTask(tb)
                .options(
                        "-processor", AP.class.getName(),
                        "--enable-preview"
                )
                .outdir(base.toString())
                .files(base.resolve("Main.java"), base.resolve("Ann.java"))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
    }

    @SupportedAnnotationTypes("*")
    public static final class AP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }
}