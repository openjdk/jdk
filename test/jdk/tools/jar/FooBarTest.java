import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import sun.tools.jar.ValidatorAccess;
import static java.nio.charset.StandardCharsets.US_ASCII;

/*
 * @test
 * @modules jdk.jartool/sun.tools.jar
 * @build jdk.jartool/sun.tools.jar.ValidatorAccess
 * @run main/othervm FooBarTest
 */
public class FooBarTest {

    public static void main(final String[] args) throws Exception {
        final Path file = Files.createTempFile(Path.of("."), "test", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("foo-entry"));
            zos.write("hello".getBytes(US_ASCII));
            zos.closeEntry();
        }
        try (final ZipFile zf = new ZipFile(file.toFile())) {
            ValidatorAccess.doSomething(zf);
        }
        System.out.println("test complete");
    }
}
