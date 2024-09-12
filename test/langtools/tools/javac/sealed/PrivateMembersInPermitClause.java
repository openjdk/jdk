import java.nio.file.Path;

/**
 * @test
 * @bug 8338981
 * @summary Access to private classes should be permitted inside the permits clause of the enclosing top-level class
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 * jdk.compiler/com.sun.tools.javac.main
 * jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.Task
 * @run main PrivateMembersInPermitClause
 */

public class PrivateMembersInPermitClause extends toolbox.TestRunner {

    private final toolbox.ToolBox tb;

    public PrivateMembersInPermitClause() {
        super(System.err);
        tb = new toolbox.ToolBox();
    }

    public static void main(String... args) throws Exception {
        new PrivateMembersInPermitClause().runTests();
    }

    public void runTests() throws Exception {
        runTests(_ -> new Object[] {});
    }

    @Test
    public void testPrivateMembersInPermitClause() throws Exception {
        var root = Path.of("src");
        tb.writeJavaFiles(root,
            """
            sealed class S permits S.A {
                private static final class A extends S {}
            }
            """
        );

        new toolbox.JavacTask(tb)
            .files(root.resolve("S.java"))
            .run(toolbox.Task.Expect.SUCCESS);
    }
}
