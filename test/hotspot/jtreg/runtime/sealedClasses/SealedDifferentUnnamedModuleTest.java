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
 */

/*
 * @test
 * @bug 8345911
 * @library /test/lib
 * @compile SealedSuper.java SealedSub.java
 * @comment Copy SealedSuper.class to the currnet directory so it will be on the bootclasspath
 * @run driver jdk.test.lib.helpers.ClassFileInstaller SealedSuper
 * @run main/othervm -Xbootclasspath/a:. -Xlog:class+sealed=trace SealedDifferentUnnamedModuleTest
 */

public class SealedDifferentUnnamedModuleTest {

    public static void main(String args[]) throws Throwable {

        // Load the sealed superclass. It will be loaded by the boot loader and
        // so reside in the boot loaders un-named module.
        Class<?> c1 = Class.forName("SealedSuper");

        // Test loading a "permitted" subclass in the app classloader, which then resides
        // in the app loader's un-named module.
        // This should fail.
        try {
            Class<?> c2 = Class.forName("SealedSub");
            throw new RuntimeException("Expected IncompatibleClassChangeError exception not thrown");
        } catch (IncompatibleClassChangeError e) {
            if (!e.getMessage().equals("Failed same module check: subclass SealedSub is in module 'unnamed module' " +
                                       "with loader 'app', and sealed class SealedSuper is in module 'unnamed module' " +
                                       "with loader 'bootstrap'")) {
                throw new RuntimeException("Wrong IncompatibleClassChangeError exception thrown: " + e.getMessage());
            }
        }
    }
}
