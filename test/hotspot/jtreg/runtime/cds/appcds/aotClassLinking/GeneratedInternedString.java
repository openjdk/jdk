/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test storing a dynamically generated interned string in the AOT cache
 * @bug 8356125
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build GeneratedInternedString
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar GeneratedInternedStringApp
 * @run driver GeneratedInternedString
 */

import jdk.test.lib.cds.SimpleCDSAppTester;

public class GeneratedInternedString {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("GeneratedInternedString")
            .addVmArgs("-XX:AOTInitTestClass=GeneratedInternedStringApp")
            .classpath("app.jar")
            .appCommandLine("GeneratedInternedStringApp")
            .runAOTWorkflow();
    }
}

// This class is cached in the AOT-initialized state. At the beginning of the production
// run, all of the static fields in GeneratedInternedStringApp will retain their values
// at the end of the assembly phase. GeneratedInternedStringApp::<clinit> is NOT executed in the
// production run.
class GeneratedInternedStringApp {
    static volatile int n = 0;
    static final String generatedInternedString = generate();

    public static void main(String args[]) {
        n = args.length;
        String b = generate();
        if (generatedInternedString != b) {
            throw new RuntimeException("generatedInternedString: " + System.identityHashCode(generatedInternedString)
                                       + " vs b:" + System.identityHashCode(b));
        }
    }

    static String generate() {
        System.out.println("generate() is called");
        return ("GeneratedInternedStringApp_String" + n).intern();
    }
}
