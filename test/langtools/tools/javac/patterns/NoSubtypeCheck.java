/*
 * @test /nodynamiccopyright/
 * @bug 8250625
 * @summary Verify pattern matching test which is always true produces an error
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main NoSubtypeCheck
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class NoSubtypeCheck extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new NoSubtypeCheck().runTests();
    }

    NoSubtypeCheck() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSimple(Path base) throws Exception {
        record TestCase(String test, String expectedError) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("boolean b1 = o instanceof Object v1;",
                         "Test.java:3:24: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.unconditional.patterns.in.instanceof), 20, 21"),
            new TestCase("boolean b2 = o instanceof String v2;", null),
            new TestCase("boolean b3 = s instanceof Object v3;",
                         "Test.java:3:24: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.unconditional.patterns.in.instanceof), 20, 21"),
            new TestCase("boolean b4 = s instanceof String v4;",
                         "Test.java:3:24: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.unconditional.patterns.in.instanceof), 20, 21"),
            new TestCase("boolean b5 = l instanceof List<String> v5;",
                         "Test.java:3:24: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.unconditional.patterns.in.instanceof), 20, 21"),
            new TestCase("boolean b6 = l instanceof List2<String> v6;", null),
            new TestCase("boolean b7 = undef instanceof String v7;",
                         "Test.java:3:22: compiler.err.cant.resolve.location: kindname.variable, undef, , , (compiler.misc.location: kindname.class, Test, null)"),
            new TestCase("boolean b8 = o instanceof Undef v7;",
                         "Test.java:3:35: compiler.err.cant.resolve.location: kindname.class, Undef, , , (compiler.misc.location: kindname.class, Test, null)"),
        };

        for (TestCase testCase : testCases) {
            System.err.println("==running testcase: " + testCase.test);
            Path current = base.resolve(".");
            Path src = current.resolve("src");

            tb.writeJavaFiles(src, """
                                   public class Test {
                                       public static void main(Object o, String s, List<String> l) {
                                           {testCase.test}
                                       }

                                       public interface List<T> {}
                                       public interface List2<T> extends List<T> {}
                                   }
                                   """.replace("{testCase.test}", testCase.test));

            Path classes = current.resolve("classes");

            Files.createDirectories(classes);

            List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "--release", "20")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(testCase.expectedError != null ? Task.Expect.FAIL : Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

            if (testCase.expectedError != null) {
                List<String> expectedOutput = new ArrayList<>();

                expectedOutput.add(testCase.expectedError);
                expectedOutput.add("1 error");

                if (!expectedOutput.equals(log)) {
                    throw new AssertionError("Unexpected output:\n" + log);
                }
            }
        }
    }

}
