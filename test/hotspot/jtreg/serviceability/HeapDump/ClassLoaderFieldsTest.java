/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.JavaObject;
import jdk.test.lib.hprof.model.JavaThing;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

/*
 * @test
 * @bug 8391308
 * @summary Verifies heap dump contains java.lang.Class instance in ClassLoader
 * @library /test/lib
 * @run driver ClassLoaderFieldsTest
 */
class ClassLoaderFieldsTarg extends LingeredApp {

    static class MyLoader extends ClassLoader {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
            clb.withFlags(ACC_PUBLIC)
               .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob ->
               cob.aload(0)
                  .invokespecial(CD_Object, INIT_NAME, MTD_void)
                  .return_())
        );

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals("Test")) {
               return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) {
        MyLoader ldr = new MyLoader();
        Class<?> test;
        try {
            test = ldr.loadClass("Test");
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Test not loaded");
        }
        LingeredApp.main(args);
        Reference.reachabilityFence(ldr);
        Reference.reachabilityFence(test);
    }
}

public class ClassLoaderFieldsTest {

    public static void main(String[] args) throws Exception {
        File dumpFile = new File("Myheapdump.hprof");
        createDump(dumpFile, args);
        verifyDump(dumpFile);
    }

    private static void createDump(File dumpFile, String[] extraOptions) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new ClassLoaderFieldsTarg();

            List<String> extraVMArgs = new ArrayList<>();
            extraVMArgs.addAll(Arrays.asList(extraOptions));
            LingeredApp.startApp(theApp, extraVMArgs.toArray(new String[0]));

            //jcmd <pid> GC.heap_dump <file_path>
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jcmd")
                    .addToolArg(Long.toString(theApp.getPid()))
                    .addToolArg("GC.heap_dump")
                    .addToolArg(dumpFile.getAbsolutePath());
            Process p = ProcessTools.startProcess("jcmd", new ProcessBuilder(launcher.getCommand()));
            // If something goes wrong with heap dumping most likely we'll get crash of the target VM.
            while (!p.waitFor(5, TimeUnit.SECONDS)) {
                if (!theApp.getProcess().isAlive()) {
                    log("ERROR: target VM died, killing jcmd...");
                    p.destroyForcibly();
                    throw new Exception("Target VM died");
                }
            }

            if (p.exitValue() != 0) {
                throw new Exception("Jcmd exited with code " + p.exitValue());
            }
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static void verifyDump(File dumpFile) throws Exception {
        Asserts.assertTrue(dumpFile.exists(), "Heap dump file not found.");

        log("Reading " + dumpFile + "...");
        try (Snapshot snapshot = Reader.readFile(dumpFile.getPath(), true, 0)) {
            log("Resolving snapshot...");
            snapshot.resolve(true);
            log("Snapshot resolved.");

            JavaClass loaderClass =
                snapshot.findClass("ClassLoaderFieldsTarg$MyLoader");
            JavaClass testClass = snapshot.findClass("Test");

            Asserts.assertNotNull(loaderClass, "MyLoader class missing from dump");
            Asserts.assertNotNull(testClass, "Test class missing from dump");
            // Assert that the Class has a field for the class loader.
            Asserts.assertSame(testClass.getLoader(),
                               loaderClass.getInstances(false).nextElement());

            boolean hasClasses = Arrays.stream(loaderClass.getFields())
                                       .anyMatch(field -> field.getName().equals("classes"));
            Asserts.assertFalse(hasClasses);
        }
    }

    private static void log(Object s) {
        System.out.println(s);
    }

}
