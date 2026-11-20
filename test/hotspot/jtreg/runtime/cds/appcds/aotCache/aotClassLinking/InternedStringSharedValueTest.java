/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test the handling of AOTStreamedHeapWriter for an interned Strings in AOT heap that shares
 *          its value array with other non-interned Strings
 * @bug 8392112
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build InternedStringSharedValueTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar InternedStringSharedValueTestApp Helper
 * @run driver InternedStringSharedValueTest AOT
 */

import java.lang.invoke.MethodType;
import java.io.Serializable;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class InternedStringSharedValueTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = InternedStringSharedValueTestApp.class.getName();

    public static void main(String[] args) throws Exception {
        new Tester().run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-XX:AOTInitTestClass=" + mainClass,
                    "-XX:-UseCompressedOops", // choose AOTStreamedHeapWriter
                    "-XX:-AOTReplayTraining", // need this before JDK-8393448 is fixed.
                };
            } else {
                return new String[] {
                    "-XX:-UseCompressedOops", // choose AOTStreamedHeapWriter
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            out.shouldHaveExitValue(0);
            out.shouldNotContain("Unexpected exception:");
        }
    }
}

class Helper {
    volatile static String tail = "App";
}

// This class is forced to be AOT-initilized using -XX:AOTInitTestClass
class InternedStringSharedValueTestApp {
    // These fields are scanned by AOTHeap::copy_and_rescan_aot_inited_mirror(),
    // and dfs_index are assigned to them in AOTStreamedHeapWriter::order_source_objs().
    // Having the 3 fields in this order should ensure that both functions will
    // process a non-interned String first, before processing the interned String.
    static final String nonInternedA;
    static final String interned;
    static final String nonInternedB;

    static {
        String s = "InternedStringSharedValueTest" + Helper.tail;
        interned = s.intern();
        nonInternedA = new String(interned);
        nonInternedB = new String(interned);
    }

    public static void main(String[] args) {
        if (nonInternedA.intern() == nonInternedA) {
            throw new RuntimeException("The nonInternedA string should not be interned");
        }
        if (nonInternedB.intern() == nonInternedB) {
            throw new RuntimeException("The nonInternedB string should not be interned");
        }
    }
}

