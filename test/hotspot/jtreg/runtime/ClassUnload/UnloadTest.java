/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test UnloadTest
 * @bug 8210559 8297740
 * @requires vm.opt.final.ClassUnloading
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library classes
 * @build jdk.test.whitebox.WhiteBox test.Empty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmn8m -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xlog:class+unload=debug UnloadTest
 */
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.classloader.ClassUnloadCommon;

import java.lang.reflect.Array;
import java.lang.ref.Reference;
import java.util.List;
import java.util.Set;

/**
 * Test that verifies that liveness of classes is correctly tracked.
 *
 * The test creates a class loader, uses the loader to load a class and creates
 * an instance related to that class.
 * 1. Then, it nulls out references to the class loader, triggers class
 *    unloading and verifies the class is *not* unloaded.
 * 2. Next, it nulls out references to the instance, triggers class unloading
 *    and verifies the class is unloaded.
 */
public class UnloadTest {

    public static void main(String... args) throws Exception {
        test_unload_instance_klass();
        test_unload_obj_array_klass();
    }

    private static void test_unload_instance_klass() throws Exception {
        final String className = "test.Empty";
        final WhiteBox wb = WhiteBox.getWhiteBox();

        ClassUnloadCommon.failIf(wb.isClassAlive(className), "is not expected to be alive yet");

        ClassLoader cl = ClassUnloadCommon.newClassLoader();
        Object o = cl.loadClass(className).newInstance();

        ClassUnloadCommon.failIf(!wb.isClassAlive(className), "should be live here");

        String loaderName = cl.getName();
        int loadedRefcount = wb.getSymbolRefcount(loaderName);
        System.out.println("Refcount of symbol " + loaderName + " is " + loadedRefcount);

        cl = null;
        ClassUnloadCommon.triggerUnloading();

        ClassUnloadCommon.failIf(!wb.isClassAlive(className), "should still be live");

        // Don't let `o` get prematurely reclaimed by the GC.
        Reference.reachabilityFence(o);
        o = null;
        ClassUnloadCommon.triggerUnloading();


        ClassUnloadCommon.failIf(wb.isClassAlive(className), "should have been unloaded");

        int unloadedRefcount = wb.getSymbolRefcount(loaderName);
        System.out.println("Refcount of symbol " + loaderName + " is " + unloadedRefcount);
        ClassUnloadCommon.failIf(unloadedRefcount != (loadedRefcount - 1), "Refcount must be decremented");
    }

    private static void test_unload_obj_array_klass() throws Exception {
        final WhiteBox wb = WhiteBox.getWhiteBox();

        ClassLoader cl = ClassUnloadCommon.newClassLoader();
        Object o = Array.newInstance(cl.loadClass("test.Empty"), 1);
        final String className = o.getClass().getName();

        ClassUnloadCommon.failIf(!wb.isClassAlive(className), "should be live here");

        String loaderName = cl.getName();
        int loadedRefcount = wb.getSymbolRefcount(loaderName);
        System.out.println("Refcount of symbol " + loaderName + " is " + loadedRefcount);

        cl = null;
        ClassUnloadCommon.triggerUnloading();
        ClassUnloadCommon.failIf(!wb.isClassAlive(className), "should still be live");

        // Don't let `o` get prematurely reclaimed by the GC.
        Reference.reachabilityFence(o);
        o = null;

        Set<String> aliveClasses = ClassUnloadCommon.triggerUnloading(List.of(className));
        ClassUnloadCommon.failIf(!aliveClasses.isEmpty(), "should have been unloaded: " + aliveClasses);

        int unloadedRefcount = wb.getSymbolRefcount(loaderName);
        System.out.println("Refcount of symbol " + loaderName + " is " + unloadedRefcount);
        ClassUnloadCommon.failIf(unloadedRefcount != (loadedRefcount - 1), "Refcount must be decremented");
    }
}
