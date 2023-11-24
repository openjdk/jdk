import java.nio.file.Path;
import java.util.function.Predicate;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;

/*
 * @test
 * @summary Verify jlink fails by default when jlinking in jmod-less mode and files have been changed
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g ModifiedFilesExitTest
 */
public class ModifiedFilesExitTest extends ModifiedFilesTest {

    public static void main(String[] args) throws Exception {
        ModifiedFilesExitTest test = new ModifiedFilesExitTest();
        test.run();
    }

    @Override
    String initialImageName() {
        return "java-base-jlink-with-mod-exit";
    }

    @Override
    void testAndAssert(Path modifiedFile, Helper helper, Path initialImage)
            throws Exception {
        CapturingHandler handler = new CapturingHandler();
        Predicate<OutputAnalyzer> exitFailPred = new Predicate<>() {

            @Override
            public boolean test(OutputAnalyzer t) {
                return t.getExitValue() != 0; // expect failure
            }
        };
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(initialImage)
                                .name("java-base-jlink-with-mod-exit-target")
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .build(), handler, exitFailPred);
        OutputAnalyzer analyzer = handler.analyzer();
        if (analyzer.getExitValue() == 0) {
            throw new AssertionError("Expected jlink to fail due to modified file!");
        }
        analyzer.stdoutShouldContain(modifiedFile.toString() + " has been modified");
        // Verify the error message is reasonable
        analyzer.stdoutShouldNotContain("jdk.tools.jlink.internal.RunImageLinkException");
        analyzer.stdoutShouldNotContain("java.lang.IllegalArgumentException");
    }

}
