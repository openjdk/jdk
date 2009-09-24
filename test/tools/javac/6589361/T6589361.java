/**
 * @test
 * @bug     6589361
 * @summary 6589361:Failing building ct.sym file as part of the control build
 */

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import java.io.File;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import java.util.Set;
import java.util.HashSet;

public class T6589361 {
    public static void main(String [] args) throws Exception {
        JavacFileManager fm = null;
        try {
            fm = new JavacFileManager(new Context(), false, null);
            Set<JavaFileObject.Kind> set = new HashSet<JavaFileObject.Kind>();
            set.add(JavaFileObject.Kind.CLASS);
            Iterable<JavaFileObject> files = fm.list(StandardLocation.PLATFORM_CLASS_PATH, "java.lang", set, false);
            for (JavaFileObject file : files) {
                // Note: Zip/Jar entry names use '/', not File.separator, but just to be sure,
                // we normalize the filename as well.
                if (file.getName().replace(File.separatorChar, '/').contains("java/lang/Object.class")) {
                    String str = fm.inferBinaryName(StandardLocation.CLASS_PATH, file);
                    if (!str.equals("java.lang.Object")) {
                        throw new AssertionError("Error in JavacFileManager.inferBinaryName method!");
                    }
                    else {
                        return;
                    }
                }
            }
        }
        finally {
            if (fm != null) {
                fm.close();
            }
        }
        throw new AssertionError("Could not find java/lang/Object.class while compiling");
    }

}
