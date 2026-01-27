/*
 * @test
 * @bug 8376185
 * @summary Class retransformation on a record type annotation
 * @comment This is will rewrite the constant pool and process
 * @comment the type annotation
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 * @compile ../NamedBuffer.java
 * @compile altered/MyRecord.jcod
 * @run shell rename.sh
 * @compile MyRecord.java
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine*=debug TestRetransformRecord
 */

import java.io.File;
import java.io.FileInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TestRetransformRecord {
    static final String SRC = System.getProperty("test.src");
    static final String DEST = System.getProperty("test.classes");

    public static void main(String[] args) throws Exception {
        MyRecord.parse("foo");
        File clsfile = new File(DEST + "/altered/MyRecord.class");
        byte[] buf = null;
        try (FileInputStream str = new FileInputStream(clsfile)) {
            buf = NamedBuffer.loadBufferFromStream(str);
        }
	Instrumentation inst = RedefineClassHelper.instrumentation;
        inst.addTransformer(new IdentityTransformer("MyRecord", buf), true);
        inst.retransformClasses(MyRecord.class);
        System.out.println(MyRecord.parse("foo"));
    }
}

class IdentityTransformer implements ClassFileTransformer {
    private final String className;
    private final byte[] buffer;

    public IdentityTransformer(String className, byte[] buffer) {
        this.className = className;
        this.buffer = buffer;
    }

    @Override
    public byte[] transform(ClassLoader loader,
			    String classPath,
			    Class<?> classBeingRedefined,
			    ProtectionDomain protectionDomain,
			    byte[] classfileBuffer) {
        if (classPath != null && classPath.equals(className.replace('.', '/'))) {
            System.out.println("Transforming " + className);
            return buffer;
        }
        return null;
    }
}
