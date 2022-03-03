/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8282241
 * @summary Verifies class retransformation correctly updates generic_signature and source_file_name attributes
 *
 * @library /test/lib
 * @run compile -g RetransformGenericSignatureTest.java
 * @run shell MakeJAR.sh retransformAgent
 *
 * @run main/othervm -javaagent:retransformAgent.jar --add-opens=java.base/java.lang=ALL-UNNAMED RetransformGenericSignatureTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.List;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.ClassTransformer;


class GenericSignatureTester {
    public GenericSignatureTarget<List<String>> method1() {
        return null;
    }
}

// See ClassTransformer.transform(int) comment for @1 tag explanations.
// @1 uncomment class GenericSignatureTarget<T> {
class GenericSignatureTarget<T extends List<?>>  { // @1 commentout
    public GenericSignatureTarget<T> method1(GenericSignatureTarget<?> a) {
        // the following line changes CP and this causes mapping during CP merge
        // @1 uncomment System.out.println(a);
        return null;
    }

    public static void throwException() {
        throw new RuntimeException();
    }
}

public class RetransformGenericSignatureTest extends ATransformerManagementTestCase {

    public RetransformGenericSignatureTest() {
        super("RetransformGenericSignatureTest");
    }

    public static void main (String[] args) throws Throwable {
        ATestCaseScaffold test = new RetransformGenericSignatureTest();
        test.runTest();
    }

    private final Transformer transformer = new Transformer();
    private final static String sourceFileName = "RetransformGenericSignatureTest.java";
    private final static String sourceFileNameNew = "RetransformGenericSignatureTestNew.java";
    // expected signature of GenericSignatureTester.method1 return type
    private final static String expectedRetType = "GenericSignatureTarget<java.util.List<java.lang.String>>";
    // expected generic signature of the original GenericSignatureTarget
    private final static String expectedSigOld = "<T::Ljava/util/List<*>;>Ljava/lang/Object;";
    // expected generic signature of the retransformed GenericSignatureTarget
    private final static String expectedSigNew = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

    private void log(Object o) {
        System.out.println(o);
    }

    private String getTargetGenSig() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandles.Lookup classLookup = MethodHandles.privateLookupIn(Class.class, lookup);
        MethodHandle getGenericSignature0 = classLookup.findVirtual(
                Class.class, "getGenericSignature0", MethodType.methodType(String.class));
        Object genericSignature = getGenericSignature0.invoke(GenericSignatureTarget.class);
        return String.valueOf(genericSignature);
    }

    private String getTesterRetType() throws Throwable {
        Type type = GenericSignatureTester.class.getDeclaredMethod("method1").getGenericReturnType();
        return String.valueOf(type);
    }

    private String getTargetSourceFilename() {
        try {
            GenericSignatureTarget.throwException();
        } catch (RuntimeException ex) {
            return ex.getStackTrace()[0].getFileName();
        }
        return "Cannot get source file name";
    }

    // Retransforms the class using provided class bytes;
    // Returns class bytes passed to the transformer.
    private byte[] retransform(Class cls, byte[] classBytes) throws Throwable {
        transformer.targetClassName = cls.getName();
        transformer.seenClassBytes = null;
        transformer.newClassBytes = classBytes;
        fInst.retransformClasses(cls);
        assertTrue(transformer.targetClassName + " was not seen by transform()",
                transformer.seenClassBytes != null);
        return transformer.seenClassBytes;
    }

    // Prints dissassembled class bytes.
    private void printDisassembled(String description, Class cls, byte[] bytes) throws Exception {
        log(description + " -------------------");

        File f = new File(cls.getSimpleName()+".class");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }
        JDKToolLauncher javap = JDKToolLauncher.create("javap")
                .addToolArg("-verbose")
                .addToolArg("-p")       // Shows all classes and members.
                //.addToolArg("-c")       // Prints out disassembled code
                .addToolArg("-s")       // Prints internal type signatures.
                .addToolArg(f.toString());
        ProcessBuilder pb = new ProcessBuilder(javap.getCommand());
        OutputAnalyzer out = ProcessTools.executeProcess(pb);
        out.shouldHaveExitValue(0);
        try {
            Files.delete(f.toPath());
        } catch (Exception ex) {
            // ignore
        }
        out.asLines().forEach(s -> log(s));
        log("==========================================");
        Files.deleteIfExists(f.toPath());
    }

    private void retransformTargetClass() throws Throwable {
        Class targetClass = GenericSignatureTarget.class;
        log("Retransforming " + targetClass.getName() + " class");
        String origSource = Files.readString(Paths.get(System.getProperty("test.src")).resolve(sourceFileName));
        // replace main class name to avoid compilation errors
        String newSource = origSource.replaceAll(
                "RetransformGenericSignatureTest", "RetransformGenericSignatureTestNew");

        String transformedClassFile = ClassTransformer.fromString(newSource)
                .setFileName(sourceFileNameNew)
                .transform(1, targetClass.getName(), "-g");
        byte[] classBytes = Files.readAllBytes(Paths.get(transformedClassFile));
        byte[] oldClassBytes = retransform(targetClass, classBytes);

        printDisassembled("Old " + targetClass.getName(), targetClass, oldClassBytes);
        printDisassembled("New " + targetClass.getName(), targetClass, classBytes);
    }

    protected final void doRunTest() throws Throwable {
        beVerbose();

        String oldSig = getTargetGenSig();
        log("old target class sig: \"" + oldSig + "\"");

        addTransformerToManager(fInst, transformer, true);

        retransformTargetClass();

        String newSig = getTargetGenSig();
        log("new target class sig: \"" + newSig + "\"");

        String newRetType = getTesterRetType();
        log("new tester ret type: \"" + newRetType + "\"");

        String newSrcFileName = getTargetSourceFilename();
        log("new source file name: \"" + newSrcFileName + "\"");

        assertEquals("wrong old generic signature", expectedSigOld, oldSig);
        assertEquals("wrong new generic signature", expectedSigNew, newSig);
        assertEquals("wrong ret type", expectedRetType, newRetType);
        assertEquals("wrong new source file name", sourceFileNameNew, newSrcFileName);
    }

    public class Transformer implements ClassFileTransformer {
        public Transformer() {
        }

        public String toString() {
            return Transformer.this.getClass().getName();
        }

        String targetClassName;
        byte[] seenClassBytes;
        byte[] newClassBytes;

        public byte[] transform(ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            if (className.equals(targetClassName)) {
                log(this + ".transform() sees '" + className
                        + "' of " + classfileBuffer.length + " bytes.");
                seenClassBytes = classfileBuffer;
                if (newClassBytes != null) {
                    log(this + ".transform() sets new classbytes for '" + className
                            + "' of " + newClassBytes.length + " bytes.");
                }
                return newClassBytes;
            }

            return null;
        }
    }
}
