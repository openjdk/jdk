/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8031321
 * @library /testlibrary /testlibrary/whitebox /compiler/whitebox ..
 * @build LZcntTestL
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UseCountLeadingZerosInstruction LZcntTestL
 */

import com.oracle.java.testlibrary.Platform;

import java.lang.reflect.Method;

public class LZcntTestL extends LZcntTestI {

    protected LZcntTestL(Method method) {
        super(method);
        isLongOperation = true;
        if (Platform.isX64()) {
            instrMask = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF};
            instrPattern = new byte[]{(byte) 0xF3, (byte) 0x00, (byte) 0x0F, (byte) 0xBD};
        }
    }

    public static void main(String[] args) throws Exception {
        // j.l.Integer and Long should be loaded to allow a compilation of the methods that use their methods
        System.out.println("classes java.lang.Long should be loaded. Proof: " + Long.class);
        BmiIntrinsicBase.verifyTestCase(LZcntTestL::new, TestLzcntL.LzcntLExpr.class.getDeclaredMethods());
    }
}
