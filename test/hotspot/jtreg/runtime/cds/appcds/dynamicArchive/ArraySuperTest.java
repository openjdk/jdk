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
 * @bug 8304147
 * @summary make sure dynamic archive does not archive array classes with incorrect values in
 *          Array::_secondary_supers
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build ArraySuperTest jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar ArraySuperApp.jar ArraySuperApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ArraySuperTest
 */

import java.util.function.Predicate;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ArraySuperTest extends DynamicArchiveTestBase {

    public static void main(String[] args) throws Exception {
        runTest(ArraySuperTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("ArraySuperApp.jar");
        String mainClass = ArraySuperApp.class.getName();

        dump(topArchiveName, "-cp", appJar, mainClass).assertNormalExit();
        run(topArchiveName, "-cp", appJar, "-Xshare:off", mainClass, "withDynamicArchive").assertNormalExit();
        run(topArchiveName, "-cp", appJar, mainClass, "withDynamicArchive").assertNormalExit();
    }
}

class ArraySuperApp implements Predicate {
    static volatile Object array;
    public boolean test(Object o) {
        return true;
    }
    static void main(String args[]) {
        array = new ArraySuperApp[1];
        if (args.length > 0) {
            Predicate[] p = new Predicate[0];
            System.out.println(p.getClass().isInstance(array));
            p = (Predicate[])array;
            p[0] = new ArraySuperApp();
            System.out.println("All tests passed");
        }
    }
}
