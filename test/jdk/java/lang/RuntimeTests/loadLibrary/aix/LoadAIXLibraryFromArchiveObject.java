import java.nio.file.Path;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8319516
 * @summary verify that System.loadLibrary on AIX is able to load libraries from ".a" (archive) file
 * @requires os.family == "aix"
 * @library /test/lib/
 * @build jdk.test.lib.process.ProcessTools
 * @run  main/othervm   LoadAIXLibraryFromArchiveObject
 */
public class LoadAIXLibraryFromArchiveObject {

    private static final String TEST_LIBRARY_NAME = "foobar";
    // creates a ".a" archive file in a test specific directory and then
    // launches a java application passing this directory through "-Djava.library.path".
    // the java application then attempts to load the library using System.loadLibrary()
    public static void main(final String[] args) throws Exception {
        final String javaHome = System.getProperty("java.home");
        final Path libj2pcscSo = Path.of(javaHome).resolve("lib", "libj2pcsc.so");
        if (!Files.exists(libawtSo)) {
            throw new AssertionError(libawtSo + " is missing");
        }
        final String archiveFileName = "lib" + TEST_LIBRARY_NAME + ".a";
        // copy over libj2pcsc.so as an archive file to test specific scratch dir
        final Path testNativeLibDir = Path.of("native").toAbsolutePath();
        Files.createDirectories(testNativeLibDir);
        final Path libDummyArchive = testNativeLibDir.resolve(archiveFileName);
        Files.copy(libawtSo, libDummyArchive);
        // // launch a java application which calls System.loadLibrary and is passed
        // // the directory containing the native library archive file, through
        // // -Djava.library.path
        final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                "-Djava.library.path=" + testNativeLibDir,
                LoadAIXLibraryFromArchiveObject.LoadLibraryApp.class.getName());
        ProcessTools.executeCommand(processBuilder).shouldHaveExitValue(0);
    }

    static class LoadLibraryApp {
        public static void main(final String[] args) throws Exception {
            System.out.println("attempting to load library " + TEST_LIBRARY_NAME);
            System.loadLibrary(TEST_LIBRARY_NAME);
            System.out.println(TEST_LIBRARY_NAME + " successfully loaded");
        }
    }
}