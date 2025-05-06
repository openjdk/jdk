package sun.tools.jar;

import java.io.IOException;
import java.util.zip.ZipFile;

public class ValidatorAccess {

    public static boolean doSomething(final ZipFile zipFile) throws IOException {
        final Main main = new Main(System.out, System.err, "jar-test-program");
        final boolean ret = Validator.validate(main, zipFile);
        System.out.println("temporary system.out to demo the usage");
        return ret;
    }
}
