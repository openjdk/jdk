/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8079784
 * @summary Test that CFE with bad inner class attribute has a nice message.
 * @library /test/lib
 * @compile OuterTest1.jcod OuterTest2.jcod
 * @run main TestInnerClassAccessFlagErrorMessage
 */

import static jdk.test.lib.Asserts.*;

public class TestInnerClassAccessFlagErrorMessage {

    static String msg1 = "inner class Inner";
    static String msg2 = "anonymous inner class";

    public static void main(java.lang.String[] unused) {
        try {
            Class<?> outer = Class.forName("OuterTest1");
            fail("Should not reach here");
        } catch (ClassFormatError err) {
            System.out.println(err.getMessage());
            assertTrue(err.getMessage().contains(msg1));
        } catch (ClassNotFoundException cfne) {
            cfne.printStackTrace();
            fail("Should not reach here");
        }

        try {
            Class<?> outer = Class.forName("OuterTest2");
            fail("Should not reach here");
        } catch (ClassFormatError err) {
            System.out.println(err.getMessage());
            assertTrue(err.getMessage().contains(msg2));
        } catch (ClassNotFoundException cfne) {
            cfne.printStackTrace();
            fail("Should not reach here");
        }
    }
}
