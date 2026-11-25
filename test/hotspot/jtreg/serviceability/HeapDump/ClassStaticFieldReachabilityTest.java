/*
 * Copyright (c) 2026, Google LLC. All rights reserved.
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

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.fail;

import java.io.File;
import java.lang.management.*;
import java.util.Enumeration;
import java.util.List;

import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.Root;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.process.ProcessTools;

import com.sun.management.HotSpotDiagnosticMXBean;

/*
 * @test
 * @bug 8391308
 * @summary Test GC root path to loaded class with no live instance.
 * @library /test/lib
 * @build jdk.test.lib.hprof.*
 * @build jdk.test.lib.hprof.model.*
 * @build jdk.test.lib.hprof.parser.*
 * @run main/othervm ClassStaticFieldReachabilityTest
 */

public class ClassStaticFieldReachabilityTest {

    private static class Foo {
        static Bar bar;
        static void hold(Bar o) {
            bar = o;
        }
    }

    private static class Bar {
    }

    public static void main(String[] args) throws Exception {
        Foo.hold(new Bar());

        dumpAndCheck();
    }

    private static void dumpAndCheck() throws Exception {
        List<HotSpotDiagnosticMXBean> list =
            ManagementFactory.getPlatformMXBeans(HotSpotDiagnosticMXBean.class);

        File dump = new File(ProcessTools.getProcessId() + ".hprof");
        if (dump.exists()) {
            dump.delete();
        }

        System.out.println("Creating heap dump: " + dump.getAbsolutePath());
        list.get(0).dumpHeap(dump.getAbsolutePath(), true);

        assertTrue(dump.exists() && dump.isFile(), "Failed to create heap dump");

        Snapshot snapshot = Reader.readFile(dump.getPath(), true, 0);
        snapshot.resolve(true);
        JavaClass fooClass = snapshot.findClass("ClassStaticFieldReachabilityTest$Foo");
        JavaClass barClass = snapshot.findClass("ClassStaticFieldReachabilityTest$Bar");
        Enumeration<JavaHeapObject> barObjs = barClass.getInstances(false);
        if (!barObjs.hasMoreElements()) {
            throw new Exception("No Bar instances found");
        }
        JavaHeapObject barObj = barObjs.nextElement();
        assertTrue(snapshot.rootsetReferencesTo(barObj, false).length > 0,
                   "Root set reference to instance of Bar should not be empty");
        assertTrue(snapshot.rootsetReferencesTo(fooClass, false).length > 0,
                   "Root set reference to Foo Class should not be empty");
        assertTrue(fooClass.getInstancesCount(false) == 0,
                   "Foo class instance count should be 0");

        dump.delete();
    }
}
