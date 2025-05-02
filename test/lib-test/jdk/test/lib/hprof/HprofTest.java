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
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.JavaObject;
import jdk.test.lib.hprof.model.JavaValueArray;
import jdk.test.lib.hprof.model.JavaThing;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;

/**
 * @test
 * @bug 8316778
 * @library /test/lib
 * @run main HprofTest
 */

class HprofTestTarg extends LingeredApp {
    // Array of primitive types
    int[] intArray = new int[2];
    // String
    String str = "test_string";

    public static void main(String[] args) {
        HprofTestTarg testObj = new HprofTestTarg();

        LingeredApp.main(args);

        Reference.reachabilityFence(testObj);
    }

}


public class HprofTest {

    public static void main(String[] args) throws Exception {
        File dumpFile = new File("Myheapdump.hprof");
        createDump(dumpFile);
        test(dumpFile);
    }

    private static void createDump(File dumpFile) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new HprofTestTarg();

            LingeredApp.startApp(theApp);

            //jcmd <pid> GC.heap_dump <file_path>
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jcmd")
                    .addToolArg(Long.toString(theApp.getPid()))
                    .addToolArg("GC.heap_dump")
                    .addToolArg(dumpFile.getAbsolutePath());
            Process p = ProcessTools.startProcess("jcmd", new ProcessBuilder(launcher.getCommand()));
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

    private static void test(File dumpFile) throws Exception {
        Asserts.assertTrue(dumpFile.exists(), "Heap dump file not found.");

        log("Reading " + dumpFile + "...");
        try (Snapshot snapshot = Reader.readFile(dumpFile.getPath(), true, 0)) {
            log("Resolving snapshot...");
            snapshot.resolve(true);
            log("Snapshot resolved.");

            JavaObject testObj = getTestObject(snapshot);
            testPrimitiveArray(testObj);
            testString(testObj);
        }

    }

    // verifies JavaValueArray.valueString does not throw
    // "invalid array element type" exception
    private static void testPrimitiveArray(JavaObject obj) {
        JavaHeapObject field = getObjectField(obj, "intArray");
        Asserts.assertTrue(field instanceof JavaValueArray);
        log("int array: " + ((JavaValueArray)field).valueString());
    }

    // verifies JavaObject.toString returns String value
    private static void testString(JavaObject obj) {
        JavaHeapObject field = getObjectField(obj, "str");
        Asserts.assertTrue(field instanceof JavaObject);
        JavaObject javaObj = (JavaObject)field;
        Asserts.assertTrue(javaObj.getClazz().isString());
        log("string: " + javaObj.toString());
        assert(javaObj.toString().contains(new HprofTestTarg().str));
    }


    private static JavaHeapObject getObjectField(JavaObject obj, String fieldName) {
        JavaThing thing = obj.getField(fieldName);
        // only non-primitive types are supported
        return (JavaHeapObject)thing;
    }

    // gets test HprofTestTarg
    private static JavaObject getTestObject(Snapshot snapshot) {
        String testClassName = HprofTestTarg.class.getName();
        JavaHeapObject testObject = getObjects(snapshot, testClassName).nextElement();
        Asserts.assertTrue(testObject instanceof JavaObject);
        return (JavaObject)testObject;
    }

    // finds all objects of the specified type
    private static Enumeration<JavaHeapObject> getObjects(Snapshot snapshot, String className) {
        log("Looking for '" + className + "' objects...");
        JavaClass jClass = snapshot.findClass(className);
        if (jClass == null) {
            throw new RuntimeException("Class '" + className + "' not found");
        }
        int instanceCount = jClass.getInstancesCount(false);
        if (instanceCount < 1) {
            throw new RuntimeException("Not instances of '" + className + "' found");
        }
        log("Found " + instanceCount + " instance(s).");
        return jClass.getInstances(false);
    }

    private static void log(Object s) {
        System.out.println(s);
    }
}
