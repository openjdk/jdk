/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib/
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.ReadUncompressedOopTest
 * @run main ClassFileInstaller
 *     sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 *     jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-UseCompressedOops
 *     compiler.jvmci.compilerToVM.ReadUncompressedOopTest
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+UseCompressedOops
 *     compiler.jvmci.compilerToVM.ReadUncompressedOopTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class ReadUncompressedOopTest {

    public static void main(String args[])  {
        new ReadUncompressedOopTest().runTest();
    }

    private void runTest()  {
        long ptr = getPtr();
        System.out.printf("calling readUncompressedOop(0x%x)%n", ptr);
        Asserts.assertEQ(getClass(),
                CompilerToVMHelper.readUncompressedOop(ptr),
                String.format("unexpected class returned for 0x%x", ptr));
    }

    private static final Unsafe UNSAFE = Utils.getUnsafe();
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Class<?> CLASS = ReadUncompressedOopTest.class;
    private static final long PTR = WB.getObjectAddress(CLASS);

    private static long getPtr() {
        Field field;
        try {
            field = CLASS.getDeclaredField("PTR");
        } catch (NoSuchFieldException nsfe) {
            throw new Error("TESTBUG : " + nsfe, nsfe);
        }
        Object base = UNSAFE.staticFieldBase(field);
        return WB.getObjectAddress(base) + UNSAFE.staticFieldOffset(field);
    }
}

