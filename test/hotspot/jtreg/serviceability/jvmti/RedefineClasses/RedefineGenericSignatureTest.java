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
 * @summary Verifies class redefinition correctly updates generic_signature and source_file_name attributes
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.instrument
 * @library /test/lib
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar --add-opens=java.base/java.lang=ALL-UNNAMED RedefineGenericSignatureTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.List;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class GenericSignatureTester {
    public GenericSignatureTarget<List<String>> method1() {
        return null;
    }
}

class GenericSignatureTarget<T extends List<?>>  {
    public GenericSignatureTarget<T> foo() { return null; }
    public static void throwException() { throw new RuntimeException(); }
}

public class RedefineGenericSignatureTest {
    private static final String newTargetClassSource =
            "class GenericSignatureTarget<T> {\n" +
            "    public GenericSignatureTarget<T> foo() { return null; }\n" +
            "    public static void throwException() { throw new RuntimeException(); }\n" +
            "}\n";

    public static void main (String[] args) throws Throwable {
        RedefineGenericSignatureTest test = new RedefineGenericSignatureTest();
        test.runTest();
    }

    private final static String sourceFileName = "RedefineGenericSignatureTest.java";
    private final static String sourceFileNameNew = "RedefineGenericSignatureTestNew.java";
    // expected signature of GenericSignatureTester.method1 return type
    private final static String expectedRetType = "GenericSignatureTarget<java.util.List<java.lang.String>>";
    // expected generic signature of the original GenericSignatureTarget
    private final static String expectedSigOld = "<T::Ljava/util/List<*>;>Ljava/lang/Object;";
    // expected generic signature of the redefined GenericSignatureTarget
    private final static String expectedSigNew = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

    private static void log(Object o) {
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

    private byte[] getNewClassBytes() {
        byte[] bytecode = InMemoryJavaCompiler.compile(GenericSignatureTarget.class.getName(), newTargetClassSource);

        ClassWriter cw = new ClassWriter(0);
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(new ClassVisitor(Opcodes.ASM7, cw) {
            private boolean sourceSet = false;
            @Override
            public void visitSource(String source, String debug) {
                sourceSet = true;
                log("Changing source: \"" + source + "\" -> \"" + sourceFileNameNew + "\"");
                super.visitSource(sourceFileNameNew, debug);
            }

            @Override
            public void visitEnd() {
                if (!sourceSet) {
                    log("Set source: \"" + sourceFileNameNew + "\"");
                    super.visitSource(sourceFileNameNew, null);
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    private void runTest() throws Throwable {
        Class targetClass = GenericSignatureTarget.class;

        String oldSig = getTargetGenSig();
        log("old target class sig: \"" + oldSig + "\"");

        byte[] oldClassBytes = targetClass.getResourceAsStream(targetClass.getName() + ".class").readAllBytes();
        printDisassembled("Old " + targetClass.getName(), targetClass, oldClassBytes);

        log("Redefining " + targetClass.getName() + " class");
        byte[] newClassBytes = getNewClassBytes();
        printDisassembled("New " + targetClass.getName(), targetClass, newClassBytes);
        RedefineClassHelper.redefineClass(targetClass, newClassBytes);

        String newSig = getTargetGenSig();
        log("new target class sig: \"" + newSig + "\"");

        String newRetType = getTesterRetType();
        log("new tester ret type: \"" + newRetType + "\"");

        String newSrcFileName = getTargetSourceFilename();
        log("new source file name: \"" + newSrcFileName + "\"");

        Asserts.assertStringsEqual(expectedSigOld, oldSig, "wrong old generic signature");
        Asserts.assertStringsEqual(expectedSigNew, newSig, "wrong new generic signature");
        Asserts.assertStringsEqual(expectedRetType, newRetType, "wrong ret type");
        Asserts.assertStringsEqual(sourceFileNameNew, newSrcFileName, "wrong new source file name");
    }
}
