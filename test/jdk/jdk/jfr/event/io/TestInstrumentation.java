/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.security.ProtectionDomain;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.IllegalClassFormatException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;

/*
 * @test
 * @summary Test that will instrument the same classes that JFR will also instrument.
 * @key jfr
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules java.instrument
 *          jdk.jartool/sun.tools.jar
 *          jdk.jfr
 * @enablePreview
 * @comment update --enable-preview in launchTest() too
 *
 * @run main/othervm jdk.jfr.event.io.TestInstrumentation
 */

// Test that will instrument the same classes that JFR will also instrument.
//
// The methods that will be instrumented, for example java.io.RandomAccessFile.write,
// will add the following code at the start of the method:
// InstrumentationCallback.callback("<classname>::<methodname>");
//
// The class InstrumentationCallback will log all keys added by the callback() function.
//
// With this instrumentation in place, we will run some existing jfr.io tests
// to verify that our instrumentation has not broken the JFR instrumentation.
//
// After the tests have been run, we verify that the callback() function have been
// called from all instrumented classes and methods. This will verify that JFR has not
// broken our instrumentation.
//
// To use instrumentation, the test must be run in a new java process with
// the -javaagent option.
// We must also create two jars:
// TestInstrumentation.jar: The javaagent for the instrumentation.
// InstrumentationCallback.jar: This is a separate jar with the instrumentation
// callback() function. It is in a separate jar because it must be added to
// the bootclasspath to be called from java.io classes.
//
// The test contains 3 parts:
// Setup part that will create jars and launch the new test instance.
// Agent part that contains the instrumentation code.
// The actual test part is in the TestMain class.
//
public class TestInstrumentation implements ClassFileTransformer {

    private static Instrumentation instrumentation = null;
    private static TestInstrumentation testTransformer = null;

    // All methods that will be instrumented.
    private static final Set<MethodKey> instrMethodKeys = Stream.of(
        "java/io/RandomAccessFile::seek::(J)V",
        "java/io/RandomAccessFile::read::()I",
        "java/io/RandomAccessFile::read::([B)I",
        "java/io/RandomAccessFile::write::([B)V",
        "java/io/RandomAccessFile::write::(I)V",
        "java/io/RandomAccessFile::close::()V",
        "java/io/FileInputStream::read::([BII)I",
        "java/io/FileInputStream::read::([B)I",
        "java/io/FileInputStream::read::()I",
        "java/io/FileOutputStream::write::(I)V",
        "java/io/FileOutputStream::write::([B)V",
        "java/io/FileOutputStream::write::([BII)V",
        "java/net/Socket$SocketInputStream::read::()I",
        "java/net/Socket$SocketInputStream::read::([BII)I",
        "java/net/Socket$SocketInputStream::close::()V",
        "java/net/Socket$SocketOutputStream::write::(I)V",
        "java/net/Socket$SocketOutputStream::write::([BII)V",
        "java/net/Socket$SocketOutputStream::close::()V",
        "java/nio/channels/FileChannel::read::([Ljava/nio/ByteBuffer;)J",
        "java/nio/channels/FileChannel::write::([Ljava/nio/ByteBuffer;)J",
        "java/nio/channels/SocketChannel::open::()Ljava/nio/channels/SocketChannel;",
        "java/nio/channels/SocketChannel::open::(Ljava/net/SocketAddress;)Ljava/nio/channels/SocketChannel;",
        "java/nio/channels/SocketChannel::read::([Ljava/nio/ByteBuffer;)J",
        "java/nio/channels/SocketChannel::write::([Ljava/nio/ByteBuffer;)J",
        "sun/nio/ch/FileChannelImpl::read::(Ljava/nio/ByteBuffer;)I",
        "sun/nio/ch/FileChannelImpl::write::(Ljava/nio/ByteBuffer;)I"
    ).map(s -> {
        String[] a = s.split("::");
        return new MethodKey(a[0], a[1], a[2]);
    }).collect(Collectors.toUnmodifiableSet());

    // Set of all classes targeted for instrumentation.
    private static Set<ClassDesc> instrClassesTarget = null;

    // Set of all classes where instrumentation has been completed.
    private static Set<ClassDesc> instrClassesDone = null;

    static {
        // Split class names from InstrMethodKeys.
        instrClassesTarget = new HashSet<>();
        instrClassesDone = new HashSet<>();
        for (MethodKey key : instrMethodKeys) {
            instrClassesTarget.add(key.owner());
        }
    }

    private static void log(String msg) {
        System.out.println("TestTransformation: " + msg);
    }


    ////////////////////////////////////////////////////////////////////
    // This is the actual test part.
    // A batch of jfr io tests will be run twice with a
    // retransfromClasses() in between. After each test batch we verify
    // that all callbacks have been called.
    ////////////////////////////////////////////////////////////////////

    public static class TestMain {

        private enum TransformStatus { Transformed, Retransformed, Removed }

        public static void main(String[] args) throws Throwable {
            runAllTests(TransformStatus.Transformed);

            // Retransform all classes and then repeat tests
            Set<Class<?>> classes = new HashSet<>();
            for (ClassDesc className : instrClassesTarget) {
                var desc = className.descriptorString();
                Class<?> clazz = Class.forName(desc.substring(1, desc.length() - 1).replace('/', '.'));
                classes.add(clazz);
                log("Will retransform " + clazz.getName());
            }
            instrumentation.retransformClasses(classes.toArray(new Class<?>[0]));

            // Clear all callback keys so we don't read keys from the previous test run.
            InstrumentationCallback.clear();
            runAllTests(TransformStatus.Retransformed);

            // Remove my test transformer and run tests again. Should not get any callbacks.
            instrumentation.removeTransformer(testTransformer);
            instrumentation.retransformClasses(classes.toArray(new Class<?>[0]));
            InstrumentationCallback.clear();
            runAllTests(TransformStatus.Removed);
        }

        // This is not all available jfr io tests, but a reasonable selection.
        public static void runAllTests(TransformStatus status) throws Throwable {
            log("runAllTests, TransformStatus: " + status);
            try {
                String[] noArgs = new String[0];
                TestRandomAccessFileEvents.main(noArgs);
                TestSocketEvents.main(noArgs);
                TestSocketChannelEvents.main(noArgs);
                TestFileChannelEvents.main(noArgs);
                TestFileStreamEvents.main(noArgs);
                TestDisabledEvents.main(noArgs);

                // Verify that all expected callbacks have been called.
                Set<String> callbackKeys = InstrumentationCallback.getKeysCopy();
                for (MethodKey key : instrMethodKeys) {
                    boolean gotCallback = callbackKeys.contains(key.toString());
                    boolean expectsCallback = isClassInstrumented(status, key);
                    String msg = String.format("status:%s, key:%s, expects:%b", status, key, expectsCallback);
                    if (gotCallback != expectsCallback) {
                        throw new Exception("Wrong callback() for " + msg);
                    } else {
                        log("Correct callback() for " + msg);
                    }
                }
            } catch (Throwable t) {
                log("Test failed in phase " + status);
                t.printStackTrace();
                throw t;
            }
        }

        private static boolean isClassInstrumented(TransformStatus status, MethodKey key) throws Throwable {
            switch (status) {
            case Retransformed:
                return true;
            case Removed:
                return false;
            case Transformed:
                var className = key.owner();
                return instrClassesDone.contains(className);
            }
            throw new Exception("Test error: Unknown TransformStatus: " + status);
        }
    }


    ////////////////////////////////////////////////////////////////////
    // This is the setup part. It will create needed jars and
    // launch a new java instance that will run the internal class TestMain.
    // This setup step is needed because we must use a javaagent jar to
    // transform classes.
    ////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Throwable {
        buildJar("TestInstrumentation", true);
        buildJar("InstrumentationCallback", false);
        launchTest();
    }

    private static void buildJar(String jarName, boolean withManifest) throws Throwable {
        final String slash = File.separator;
        final String packageName = "jdk/jfr/event/io".replace("/", slash);
        System.out.println("buildJar packageName: " + packageName);

        String testClasses = System.getProperty("test.classes", "?");
        String testSrc = System.getProperty("test.src", "?");
        String jarPath = testClasses + slash + jarName + ".jar";
        String manifestPath = testSrc + slash + jarName + ".mf";
        String className = packageName + slash + jarName + ".class";

        String[] args = null;
        if (withManifest) {
            args = new String[] {"-cfm", jarPath, manifestPath, "-C", testClasses, className};
        } else {
            args = new String[] {"-cf", jarPath, "-C", testClasses, className};
        }

        log("Running jar " + Arrays.toString(args));
        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(args)) {
            throw new Exception("jar failed: args=" + Arrays.toString(args));
        }
    }

    // Launch the test instance. Will run the internal class TestMain.
    private static void launchTest() throws Throwable {
        final String slash = File.separator;

        // Need to add jdk/lib/tools.jar to classpath.
        String classpath =
            System.getProperty("test.class.path", "") + File.pathSeparator +
            System.getProperty("test.jdk", ".") + slash + "lib" + slash + "tools.jar";
        String testClassDir = System.getProperty("test.classes", "") + slash;

        String[] args = {
            "-Xbootclasspath/a:" + testClassDir + "InstrumentationCallback.jar",
            "--enable-preview",
            "-classpath", classpath,
            "-javaagent:" + testClassDir + "TestInstrumentation.jar",
            "jdk.jfr.event.io.TestInstrumentation$TestMain" };
        OutputAnalyzer output = ProcessTools.executeTestJava(args);
        output.shouldHaveExitValue(0);
    }


    ////////////////////////////////////////////////////////////////////
    // This is the java agent part. Used to transform classes.
    //
    // Each transformed method will add this call:
    // InstrumentationCallback.callback("<classname>::<methodname>");
    ////////////////////////////////////////////////////////////////////

    public static void premain(String args, Instrumentation inst) throws Exception {
        instrumentation = inst;
        testTransformer = new TestInstrumentation();
        inst.addTransformer(testTransformer, true);
    }

    public byte[] transform(
            ClassLoader classLoader, String className, Class<?> classBeingRedefined,
            ProtectionDomain pd, byte[] bytes) throws IllegalClassFormatException {
        // Check if this class should be instrumented.
        ClassDesc target = ClassDesc.ofInternalName(className);
        if (!instrClassesTarget.contains(target)) {
            return null;
        }

        boolean isRedefinition = classBeingRedefined != null;
        log("instrument class(" + className + ") " + (isRedefinition ? "redef" : "load"));

        instrClassesDone.add(target);
        var cf = ClassFile.of();
        return cf.transform(cf.parse(bytes), (clb, ce) -> {
            MethodKey key;
            if (ce instanceof MethodModel mm && instrMethodKeys.contains(key = new MethodKey(
                    target, mm.methodName().stringValue(), mm.methodTypeSymbol()))) {
                clb.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    private static final MethodTypeDesc MTD_callback = MethodTypeDesc.of(CD_void, CD_String);
                    private static final ClassDesc CD_InstrumentationCallback = InstrumentationCallback.class
                            .describeConstable().orElseThrow();

                    @Override
                    public void atStart(CodeBuilder cb) {
                        cb.loadConstant(key.toString());
                        cb.invokestatic(CD_InstrumentationCallback, "callback", MTD_callback);
                        log("instrumented " + key);
                    }

                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        cb.with(ce);
                    }
                }));
            } else {
                clb.with(ce);
            }
        });
    }

    public record MethodKey(ClassDesc owner, String name, MethodTypeDesc desc) {
        public MethodKey(String className, String methodName, String signature) {
            this(ClassDesc.ofInternalName(className), methodName, MethodTypeDesc.ofDescriptor(signature));
        }

        @Override
        public String toString() {
            var ownerDesc = owner.descriptorString();
            return ownerDesc.substring(1, ownerDesc.length() - 1) + "::" + name + "::" + desc.descriptorString();
        }
    }
}
