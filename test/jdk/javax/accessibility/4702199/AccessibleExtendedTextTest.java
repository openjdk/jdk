/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4702199
 * @summary AccessibleExtendedText and related classes for
 * missing accessibility support
 * @run main AccessibleExtendedTextTest
 */

public class AccessibleExtendedTextTest {

    public static void doTest() throws Exception {
        try {
            Class[] param = { int.class, int.class };
            Class accessibleExtendedText =
                Class.forName("javax.accessibility.AccessibleExtendedText");
            accessibleExtendedText.getDeclaredField("LINE");
            accessibleExtendedText.getDeclaredField("ATTRIBUTE_RUN");
            accessibleExtendedText.getDeclaredMethod("getTextRange", param);
            accessibleExtendedText.getDeclaredMethod("getTextSequenceAt",
                param);
            accessibleExtendedText.getDeclaredMethod("getTextSequenceAfter",
                param);
            accessibleExtendedText.getDeclaredMethod("getTextSequenceBefore",
                param);
            accessibleExtendedText.getDeclaredMethod("getTextBounds", param);
        } catch (Exception e) {
            throw new Exception(
                "Failures in Interface AccessibleExtendedText");
        }

        try {
            Class accessibleTextSequence =
                Class.forName("javax.accessibility.AccessibleTextSequence");
            accessibleTextSequence.getDeclaredField("startIndex");
            accessibleTextSequence.getDeclaredField("endIndex");
            accessibleTextSequence.getDeclaredField("text");
        } catch (Exception e) {
            throw new Exception(
                "Failures in Interface AccessibleTextSequence");
        }

        try {
            Class accessibleTextAttributeSequence = Class
                .forName("javax.accessibility.AccessibleAttributeSequence");
            accessibleTextAttributeSequence.getDeclaredField("startIndex");
            accessibleTextAttributeSequence.getDeclaredField("endIndex");
            accessibleTextAttributeSequence.getDeclaredField("attributes");
        } catch (Exception e) {
            throw new Exception(
                "Failures in Interface AccessibleAttributeSequence");
        }

        try {
            Class accessibleContext =
                Class.forName("javax.accessibility.AccessibleContext");
            accessibleContext
            .getDeclaredField("ACCESSIBLE_INVALIDATE_CHILDREN");
            accessibleContext
            .getDeclaredField("ACCESSIBLE_TEXT_ATTRIBUTES_CHANGED");
            accessibleContext
            .getDeclaredField("ACCESSIBLE_COMPONENT_BOUNDS_CHANGED");
        } catch (Exception e) {
            throw new Exception(
                "Failures in Interface AccessibleContext");
        }
        System.out.println("Test Passed");
    }

    public static void main(String[] args) throws Exception {
        doTest();
    }
}
