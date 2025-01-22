/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8344904
 * @summary make sure all interned strings in old classes are archived.
 * @requires vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build OldClassWithStaticString
 * @build StaticStringInOldClass
 * @run driver jdk.test.lib.helpers.ClassFileInstaller
 *                 -jar StaticStringInOldClass.jar StaticStringInOldClass StaticStringInOldClassApp OldClassWithStaticString
 * @run driver StaticStringInOldClass
 */

import java.lang.reflect.Field;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class StaticStringInOldClass {
    static final String appClass = StaticStringInOldClassApp.class.getName();
    static String[] classes = {
        appClass,
        OldClassWithStaticString.class.getName(),
    };

    public static void main(String[] args) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("StaticStringInOldClass.jar");
        OutputAnalyzer output;
        output = TestCommon.testDump(appJar, TestCommon.list(classes));
        output = TestCommon.exec(appJar, appClass);
        TestCommon.checkExec(output, "Hello");
    }
}

class StaticStringInOldClassApp {
    static String a = "xxxx123";
    public static void main(String args[]) throws Exception {
        System.out.println("Hello");
        String x = (a + "yyyy456").intern();
        Class c = OldClassWithStaticString.class;
        Field f = c.getField("s");
        String y = (String)(f.get(null));
        if (x != y) {
            throw new RuntimeException("Interned strings not equal: " +
                                       "\"" + x + "\" @ " + System.identityHashCode(x) + " vs " +
                                       "\"" + y + "\" @ " + System.identityHashCode(y));
        }
    }
}
