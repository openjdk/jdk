/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025636
 * @library /test/lib/
 * @modules jdk.compiler
 * @enablePreview
 * @compile LambdaStackTrace.java
 * @run main LambdaStackTrace
 * @summary Synthetic frames should be hidden in exceptions
 */

import jdk.test.lib.compiler.CompilerUtils;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.classfile.ClassFile.*;


public class LambdaStackTrace {

    static Path CLASSES = Path.of(System.getProperty("test.classes", "."));

    public static void main(String[] args) throws Exception {
        testBasic();
        testBridgeMethods();
    }

    /**
     * Test the simple case
     */
    private static void testBasic() throws Exception {
        try {
            Runnable r = () -> {
                throw new RuntimeException();
            };
            r.run();
        } catch (Exception ex) {
            // Before 8025636 the stacktrace would look like:
            //  at LambdaStackTrace.lambda$main$0(LambdaStackTrace.java:37)
            //  at LambdaStackTrace$$Lambda/1937396743.run(<Unknown>:1000000)
            //  at LambdaStackTrace.testBasic(LambdaStackTrace.java:40)
            //  at ...
            //
            // We are verifying that the middle frame above is gone.

            verifyFrames(ex.getStackTrace(),
                    "LambdaStackTrace\\..*",
                    "LambdaStackTrace.testBasic");
        }
    }

    /**
     * Test the more complicated case with bridge methods.
     *
     * We set up the following interfaces:
     *
     * interface Maker {
     *   Object make();
     * }
     * interface StringMaker extends Maker {
     *   String make();
     * }
     *
     * And we will use them like so:
     *
     * StringMaker sm = () -> { throw new RuntimeException(); };
     * sm.make();
     * ((Maker)m).make();
     *
     * The first call is a "normal" interface call, the second will use a
     * bridge method. In both cases the generated lambda frame should
     * be removed from the stack trace.
     */
    private static void testBridgeMethods() throws Exception {
        // setup
        generateInterfaces();
        compileCaller();

        // test
        StackTraceElement[] frames = call("Caller", "callStringMaker");
        verifyFrames(frames,
                "Caller\\..*",
                "Caller.callStringMaker");

        frames = call("Caller", "callMaker");
        verifyFrames(frames,
                "Caller\\..*",
                "Caller.callMaker");
    }

    private static void generateInterfaces() throws IOException {
        // We can't let javac compile these interfaces because in > 1.8 it will insert
        // bridge methods into the interfaces - we want code that looks like <= 1.7,
        // so we generate it.
        Files.write(CLASSES.resolve("Maker.class"), generateMaker());
        Files.write(CLASSES.resolve("StringMaker.class"), generateStringMaker());
    }

    private static byte[] generateMaker() {
        // interface Maker {
        //   Object make();
        // }
        return ClassFile.of().build(ClassDesc.of("Maker"), clb -> clb
                .withVersion(JAVA_7_VERSION, 0)
                .withFlags(ACC_INTERFACE | ACC_ABSTRACT)
                .withSuperclass(CD_Object)
                .withMethod("make", MethodTypeDesc.of(CD_Object),
                        ACC_PUBLIC | ACC_ABSTRACT, mb -> {})
        );
    }

    private static byte[] generateStringMaker() {
        // interface StringMaker extends Maker {
        //   String make();
        // }
        return ClassFile.of().build(ClassDesc.of("StringMaker"), clb -> clb
                .withVersion(JAVA_7_VERSION, 0)
                .withFlags(ACC_INTERFACE | ACC_ABSTRACT)
                .withSuperclass(CD_Object)
                .withInterfaceSymbols(ClassDesc.of("Maker"))
                .withMethod("make", MethodTypeDesc.of(CD_String),
                        ACC_PUBLIC | ACC_ABSTRACT, mb -> {})
        );
    }


    static void emitCode(Path file) throws IOException {
        ArrayList<String> scratch = new ArrayList<>();
        scratch.add("public class Caller {");
        scratch.add("    public static void callStringMaker() {");
        scratch.add("        StringMaker sm = () -> { throw new RuntimeException(); };");
        scratch.add("        sm.make();");
        scratch.add("    }");
        scratch.add("    public static void callMaker() {");
        scratch.add("        StringMaker sm = () -> { throw new RuntimeException(); };");
        scratch.add("        ((Maker) sm).make();");  // <-- This will call the bridge method
        scratch.add("    }");
        scratch.add("}");

        Files.write(file, scratch, Charset.defaultCharset());
    }

    static void compileCaller() throws IOException {
        Path src = Path.of("src");
        Files.createDirectories(src);

        Path caller = src.resolve("Caller.java");
        emitCode(caller);
        CompilerUtils.compile(src, CLASSES, "-cp", CLASSES.toAbsolutePath().toString());
    }

    private static void verifyFrames(StackTraceElement[] stack, String... patterns) throws Exception {
        for (int i = 0; i < patterns.length; i++) {
            String cm = stack[i].getClassName() + "." + stack[i].getMethodName();
            if (!cm.matches(patterns[i])) {
                System.err.println("Actual trace did not match expected trace at frame " + i);
                System.err.println("Expected frame patterns:");
                for (int j = 0; j < patterns.length; j++) {
                    System.err.println("  " + j + ": " + patterns[j]);
                }
                System.err.println("Actual frames:");
                for (int j = 0; j < patterns.length; j++) {
                    System.err.println("  " + j + ": " + stack[j]);
                }
                throw new Exception("Incorrect stack frames found");
            }
        }
    }

    private static StackTraceElement[] call(String clazz, String method) throws Exception {
        Class<?> c = Class.forName(clazz);
        try {
            Method m = c.getDeclaredMethod(method);
            m.invoke(null);
        } catch(InvocationTargetException ex) {
            return ex.getTargetException().getStackTrace();
        }
        throw new Exception("Expected exception to be thrown");
    }
}
