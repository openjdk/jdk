/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import sun.hotspot.WhiteBox;
import jdk.internal.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;

/*
 * @test TestAnonymousClassUnloading
 * @bug 8054402
 * @summary "Tests unloading of anonymous classes."
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 * @compile TestAnonymousClassUnloading.java
 * @run main ClassFileInstaller TestAnonymousClassUnloading
 *                              sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation TestAnonymousClassUnloading
 */
public class TestAnonymousClassUnloading {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static int COMP_LEVEL_SIMPLE = 1;
    private static int COMP_LEVEL_FULL_OPTIMIZATION = 4;

    /**
     * We override hashCode here to be able to access this implementation
     * via an Object reference (we cannot cast to TestAnonymousClassUnloading).
     */
    @Override
    public int hashCode() {
        return 42;
    }

    /**
     * Does some work by using the anonymousClass.
     * @param anonymousClass Class performing some work (will be unloaded)
     */
    static private void doWork(Class<?> anonymousClass) throws InstantiationException, IllegalAccessException {
        // Create a new instance
        Object anon = anonymousClass.newInstance();
        // We would like to call a method of anonymousClass here but we cannot cast because the class
        // was loaded by a different class loader. One solution would be to use reflection but since
        // we want C2 to implement the call as an IC we call Object::hashCode() here which actually
        // calls anonymousClass::hashCode(). C2 will then implement this call as an IC.
        if (anon.hashCode() != 42) {
            new RuntimeException("Work not done");
        }
    }

    /**
     * Makes sure that method is compiled by forcing compilation if not yet compiled.
     * @param m Method to be checked
     */
    static private void makeSureIsCompiled(Method m) {
        // Make sure background compilation is disabled
        if (WHITE_BOX.getBooleanVMFlag("BackgroundCompilation")) {
            throw new RuntimeException("Background compilation enabled");
        }

        // Check if already compiled
        if (!WHITE_BOX.isMethodCompiled(m)) {
            // If not, try to compile it with C2
            if(!WHITE_BOX.enqueueMethodForCompilation(m, COMP_LEVEL_FULL_OPTIMIZATION)) {
                // C2 compiler not available, try to compile with C1
                WHITE_BOX.enqueueMethodForCompilation(m, COMP_LEVEL_SIMPLE);
            }
            // Because background compilation is disabled, method should now be compiled
            if(!WHITE_BOX.isMethodCompiled(m)) {
                throw new RuntimeException(m + " not compiled");
            }
        }
    }

    /**
     * This test creates stale Klass* metadata referenced by a compiled IC.
     *
     * The following steps are performed:
     * (1) An anonymous version of TestAnonymousClassUnloading is loaded by a custom class loader
     * (2) The method doWork that calls a method of the anonymous class is compiled. The call
     *     is implemented as an IC referencing Klass* metadata of the anonymous class.
     * (3) Unloading of the anonymous class is enforced. The IC now references dead metadata.
     */
    static public void main(String[] args) throws Exception {
        // (1) Load an anonymous version of this class using the corresponding Unsafe method
        URL classUrl = TestAnonymousClassUnloading.class.getResource("TestAnonymousClassUnloading.class");
        URLConnection connection = classUrl.openConnection();

        int length = connection.getContentLength();
        byte[] classBytes = connection.getInputStream().readAllBytes();
        if (length != -1 && classBytes.length != length) {
            throw new IOException("Expected:" + length + ", actual: " + classBytes.length);
        }

        Class<?> anonymousClass = UNSAFE.defineAnonymousClass(TestAnonymousClassUnloading.class, classBytes, null);

        // (2) Make sure all paths of doWork are profiled and compiled
        for (int i = 0; i < 100000; ++i) {
            doWork(anonymousClass);
        }

        // Make sure doWork is compiled now
        Method doWork = TestAnonymousClassUnloading.class.getDeclaredMethod("doWork", Class.class);
        makeSureIsCompiled(doWork);

        // (3) Throw away reference to anonymousClass to allow unloading
        anonymousClass = null;

        // Force garbage collection to trigger unloading of anonymousClass
        // Dead metadata reference to anonymousClass triggers JDK-8054402
        WHITE_BOX.fullGC();
    }
}
