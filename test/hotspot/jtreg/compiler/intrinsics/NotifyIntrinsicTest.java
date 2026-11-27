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
 */

/*
 * @test
 * @bug 8398011
 * @summary Verify that C2 supports the Object.notify and Object.notifyAll intrinsics
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.intrinsics.NotifyIntrinsicTest
 */

package compiler.intrinsics;

import java.lang.reflect.Executable;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

public class NotifyIntrinsicTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        checkIntrinsic("notify");
        checkIntrinsic("notifyAll");
    }

    private static void checkIntrinsic(String name) throws Exception {
        Executable method = Object.class.getDeclaredMethod(name);
        if (!WHITE_BOX.isIntrinsicAvailable(method, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION)) {
            throw new RuntimeException("C2 intrinsic is not available for Object." + name + "()");
        }
    }
}
