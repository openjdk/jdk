/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=satb-before
 * @bug 8394986
 * @summary Shenandoah performs a heap dump before a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=satb
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpBeforeFullGC
 *                   -XX:HeapDumpPath=dump -Xms64m -Xmx64m TestFullGCHeapDump 1
 */

/*
 * @test id=satb-after
 * @bug 8394986
 * @summary Shenandoah performs a heap dump after a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=satb
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpAfterFullGC
 *                   -XX:HeapDumpPath=dump -Xms64m -Xmx64m TestFullGCHeapDump 1
 */

/*
 * @test id=satb-both
 * @bug 8394986
 * @summary Shenandoah performs heap dumps before and after a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=satb
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpBeforeFullGC
 *                   -XX:+HeapDumpAfterFullGC -XX:HeapDumpPath=dump
 *                   -Xms64m -Xmx64m TestFullGCHeapDump 2
 */

/*
 * @test id=generational-before
 * @bug 8394986
 * @summary Generational Shenandoah performs a heap dump before a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpBeforeFullGC
 *                   -XX:HeapDumpPath=dump -Xms64m -Xmx64m TestFullGCHeapDump 1
 */

/*
 * @test id=generational-after
 * @bug 8394986
 * @summary Generational Shenandoah performs a heap dump after a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpAfterFullGC
 *                   -XX:HeapDumpPath=dump -Xms64m -Xmx64m TestFullGCHeapDump 1
 */

/*
 * @test id=generational-both
 * @bug 8394986
 * @summary Generational Shenandoah performs heap dumps before and after a full GC
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:+HeapDumpBeforeFullGC
 *                   -XX:+HeapDumpAfterFullGC -XX:HeapDumpPath=dump
 *                   -Xms64m -Xmx64m TestFullGCHeapDump 2
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.hprof.HprofParser;

public class TestFullGCHeapDump {
    public static void main(String[] args) throws Exception {
        final int expectedFiles = Integer.parseInt(args[0]);
        final String heapDumpPrefix = "java_pid";
        File heapDumpDirectory = new File("dump");
        boolean directoryCreated = heapDumpDirectory.mkdir();
        Asserts.assertTrue(directoryCreated, "Directory couldn't be created");

        // Create the heap dump file(s).
        System.gc();

        File[] heapDumpDirectoryFiles = heapDumpDirectory.listFiles();
        Asserts.assertNotNull(heapDumpDirectoryFiles, "Directory couldn't be listed");

        List<File> heapDumpFiles = new ArrayList<>();
        for (File file : heapDumpDirectoryFiles) {
            if (!file.isDirectory() && file.getName().startsWith(heapDumpPrefix)) {
                heapDumpFiles.add(file);
            }
        }

        Asserts.assertEquals(expectedFiles, heapDumpFiles.size(), "Heap dump files: " + heapDumpFiles);

        for (File file : heapDumpFiles) {
            HprofParser.parseAndVerify(file);
            file.delete();
        }
        heapDumpDirectory.delete();
    }
}
