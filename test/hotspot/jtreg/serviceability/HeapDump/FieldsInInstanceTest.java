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

/*
 * @test
 * @bug 8317692
 * @summary Verifies heap dump contains all fields of an instance
 * @library /test/lib
 * @run driver FieldsInInstanceTest
 */
class FieldsInInstanceTarg extends LingeredApp {

    public static void main(String[] args) {
        B b = new B();
        NoFields2 nf = new NoFields2();
        NoParentFields npf = new NoParentFields();
        OnlyParentFields opf = new OnlyParentFields();
        DirectParentNoFields dpnf = new DirectParentNoFields();
        LingeredApp.main(args);
        Reference.reachabilityFence(b);
        Reference.reachabilityFence(nf);
        Reference.reachabilityFence(npf);
        Reference.reachabilityFence(opf);
        Reference.reachabilityFence(dpnf);
    }

    interface I {
        int i = -10;
    }
    static abstract class A implements I {
        static boolean b;
        int a = 3;
        String s = "Field";
    }
    static class B extends A {
        static String f = null;
        int a = 7;
        double s = 0.5d;
    }

    // no fields:
    interface I1 {
    }
    static class NoFields1 {
    }
    static class NoFields2 extends NoFields1 implements I1 {
    }

    // no parent fields
    static class NoParentFields extends NoFields1 implements I1 {
        int i1 = 1;
        int i2 = 2;
    }

    // only parent fields
    static class Parent1 {
        int i3 = 3;
    }
    static class OnlyParentFields extends Parent1 {
    }

    // in between parent with no fields
    static class DirectParentNoFields extends OnlyParentFields {
        int i = 17;
    }
}

public class FieldsInInstanceTest {

    public static void main(String[] args) throws Exception {
        File dumpFile = new File("Myheapdump.hprof");
        createDump(dumpFile, args);
        verifyDump(dumpFile);
    }

    private static void createDump(File dumpFile, String[] extraOptions) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new FieldsInInstanceTarg();

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

            List<JavaThing> bFields = getFields(snapshot, FieldsInInstanceTarg.B.class);
            // B has 2 instance fields, A has 2 instance fields
            Asserts.assertEquals(bFields.size(), 4);
            // JavaObject reverses the order of fields, so fields of B are at the end.
            // Order is only specified for supertypes, so we check if values are *anywhere* in their range
            // by using the toString output.
            String asString = bFields.subList(2, 4).toString();
            Asserts.assertTrue(asString.contains("0.5"), "value for field B.s not found");
            Asserts.assertTrue(asString.contains("7"), "value for field B.a not found");
            asString = bFields.subList(0, 2).toString();
            Asserts.assertTrue(asString.contains("3"), "value for field A.a not found");
            Asserts.assertTrue(asString.contains("Field"), "value for field A.s not found");

            Asserts.assertEquals(getFields(snapshot, FieldsInInstanceTarg.NoFields2.class).size(), 0);

            Asserts.assertEquals(getFields(snapshot, FieldsInInstanceTarg.NoParentFields.class).size(), 2);

            Asserts.assertEquals(getFields(snapshot, FieldsInInstanceTarg.OnlyParentFields.class).size(), 1);

            Asserts.assertEquals(getFields(snapshot, FieldsInInstanceTarg.DirectParentNoFields.class).size(), 2);
        }
    }

    private static List<JavaThing> getFields(Snapshot snapshot, Class<?> clazz) {
        JavaObject javaObject = (JavaObject) snapshot.findClass(clazz.getName()).getInstances(false).nextElement();
        List<JavaThing> fields = Arrays.asList(javaObject.getFields());
        log("Fields for " + clazz + " (including superclasses): " + fields);
        return fields;
    }

    private static void log(Object s) {
        System.out.println(s);
    }

}