/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

class TestUnloadedInlineTypeField {
    static class MyValue3 {
        int foo;
    }

    static class MyValue3Holder {
        MyValue3 v;
    }

    static class MyValue10 {
        int foo;
    }

    static class MyValue10Holder {
        MyValue10 v1;
        MyValue10 v2;
    }

    static class MyValue13 {
        int foo;
    }

    static class MyValue13Holder {
        MyValue13 v;
    }

    static class MyValue14 {
        int foo;
    }

    static class MyValue14Holder {
        MyValue14 v;
    }

    static class MyValue15 {
        int foo;
    }

    static class MyValue15Holder {
        MyValue15 v;
    }

    static value class MyValue16 {
        int foo = 42;
    }

    static value class MyValue17 {
        int foo = 42;
    }
}

public class GetUnresolvedInlineFieldWrongSignature {

    public static void test13(Object holder) {
        if (holder != null) {
            // Don't use MyValue13Holder in the signature, it might trigger class loading
            ((TestUnloadedInlineTypeField.MyValue13Holder)holder).v = new TestUnloadedInlineTypeField.MyValue13();
        }
    }

    public static void test15(Object holder) {
        if (holder != null) {
            // Don't use MyValue15Holder in the signature, it might trigger class loading
            ((TestUnloadedInlineTypeField.MyValue15Holder)holder).v = new TestUnloadedInlineTypeField.MyValue15();
        }
    }

    public static Object test16(boolean warmup) {
        if (!warmup) {
            return new TestUnloadedInlineTypeField.MyValue16();
        } else {
            return null;
        }
    }

    public static Object test17(boolean warmup) {
        if (!warmup) {
            return new TestUnloadedInlineTypeField.MyValue17();
        } else {
            return null;
        }
    }
}
