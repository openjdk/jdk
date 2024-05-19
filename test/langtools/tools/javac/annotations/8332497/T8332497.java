import toolbox.*;

import javax.annotation.processing.Processor;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    final String processorName = "AP";
    final Path base = Paths.get(".");
    final String processedSource = """
            import module java.base;
            import java.util.List;
            public class Main {
              public static void main(String[] args) {
                List.of();
              }
              @Ann
              private void test() {
                List.of();
              }
            }
            """;

    final String annotationSource = """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface Ann {
            }
            """;

    final String processorSource = """
            import module java.base;
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.annotation.processing.SupportedSourceVersion;
            import javax.lang.model.element.TypeElement;
            import javax.lang.model.SourceVersion;
            @SupportedAnnotationTypes("*")
            @SupportedSourceVersion(SourceVersion.RELEASE_23)
            public class AP extends AbstractProcessor {
                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    return false;
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
        var procJarPath = createProcessorJarFile();
        tb.writeJavaFiles(base, processedSource);
        tb.writeJavaFiles(base, annotationSource);

        new toolbox.JavacTask(tb)
                .classpath(procJarPath)
                .options(
                        "-processorpath", procJarPath.toString()
                )
                .outdir(base.toString())
                .files(base.resolve("Main.java"), base.resolve("Ann.java"))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
    }

    private Path createProcessorJarFile() throws Exception {
        var apDir = base;

        toolbox.JarTask jarTask = new toolbox.JarTask(tb, processorName + ".jar");

        // write out META-INF/services file for the processor
        var servicesFile =
                apDir.resolve("META-INF")
                        .resolve("services")
                        .resolve(Processor.class.getCanonicalName());
        tb.writeFile(servicesFile, processorName);

        // write out processor source file
        tb.writeJavaFiles(apDir, processorSource);

        // Compile the processor
        new toolbox.JavacTask(tb)
                .files(processorName + ".java")
                .run(Task.Expect.SUCCESS)
                .writeAll();

        // Create jar file
        jarTask.files(servicesFile.toString(), apDir.resolve(processorName + ".class").toString()).run();
        return base.resolve(processorName + ".jar");
    }
}