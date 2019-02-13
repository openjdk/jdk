/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


/*
 * @test TestAnonSymbolLeak
 * @bug 8218755
 * @requires vm.opt.final.ClassUnloading
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.org.objectweb.asm
 *          java.management
 * @library /test/lib /runtime/testlibrary
 * @build p1.AnonSymbolLeak
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -Xmn8m -XX:+UnlockDiagnosticVMOptions -Xlog:class+unload -XX:+WhiteBoxAPI TestAnonSymbolLeak
 */

import java.lang.reflect.Method;
import sun.hotspot.WhiteBox;
import jdk.test.lib.Asserts;

public class TestAnonSymbolLeak {
    static String className = "p1.AnonSymbolLeak";

    private static class ClassUnloadTestMain {
        public static void test() throws Exception {
            // Load the AnonSymbolLeak class in a new class loader, run it, which loads
            // an unsafe anonymous class in the same package p1.  Then unload it.
            // Then test that the refcount of the symbol created for the prepended name doesn't leak.
            ClassLoader cl = ClassUnloadCommon.newClassLoader();
            Class<?> c = cl.loadClass(className);
            c.getMethod("test").invoke(null);
            cl = null; c = null;
            ClassUnloadCommon.triggerUnloading();
        }
    }

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String... args) throws Exception {
        String oldName = "AnonClass";
        String prependedName = "p1/AnonClass";
        ClassUnloadCommon.failIf(wb.isClassAlive(className), "should not be loaded");
        int countBeforeOld = wb.getSymbolRefcount(oldName);
        int countBefore = wb.getSymbolRefcount(prependedName);
        ClassUnloadTestMain.test();
        ClassUnloadCommon.failIf(wb.isClassAlive(className), "should be unloaded");
        int countAfterOld = wb.getSymbolRefcount(oldName);
        int countAfter = wb.getSymbolRefcount(prependedName);
        Asserts.assertEquals(countBeforeOld, countAfterOld); // no leaks to the old name
        System.out.println("count before and after " + countBeforeOld + " " + countAfterOld);
        Asserts.assertEquals(countBefore, countAfter);       // no leaks to the prepended name
        System.out.println("count before and after " + countBefore + " " + countAfter);
    }
}
