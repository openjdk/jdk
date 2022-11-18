/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test Class unloading test while triggering execution of ClassLoaderStats VM operations
 * @summary Make sure class unloading occur even if ClassLoaderStats VM operations are executed
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library classes
 * @build jdk.test.whitebox.WhiteBox test.Empty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xlog:gc*,class+unload=debug UnloadTestDuringClassLoaderStatsVMOperation
 */
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.classloader.ClassUnloadCommon;

public class UnloadTestDuringClassLoaderStatsVMOperation {
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    private static String className = "test.Empty";

    public static void main(String args[]) throws Exception {
        ClassUnloadCommon.failIf(wb.isClassAlive(className), "is not expected to be alive yet");

        // Create a thread forcing ClassLoaderStats VM operations.
        Runnable task = () -> {
            while (true) {
                wb.forceClassLoaderStatsSafepoint();
            }
        };
        var clsThread = new Thread(task);
        clsThread.setDaemon(true);
        clsThread.start();

        // Load a test class and verify that it gets unloaded once we do a major collection.
        var classLoader = ClassUnloadCommon.newClassLoader();
        var loaded = classLoader.loadClass(className);
        var object = loaded.getDeclaredConstructor().newInstance();

        ClassUnloadCommon.failIf(!wb.isClassAlive(className), className + " should be loaded and live");
        System.out.println("Loaded klass: " + className);

        // Make class unloadable.
        classLoader = null;
        loaded = null;
        object = null;

        // Full/Major collection should always unload classes.
        wb.fullGC();
        ClassUnloadCommon.failIf(wb.isClassAlive(className), className + " should have been unloaded");
   }
}
