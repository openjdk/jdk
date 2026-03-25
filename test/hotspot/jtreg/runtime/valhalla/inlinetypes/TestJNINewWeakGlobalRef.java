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

package runtime.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test JNI NewWeakGlobalRef with inline types
 * @library /test/lib
 * @enablePreview
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED
 *                          runtime.valhalla.inlinetypes.TestJNINewWeakGlobalRef
 */
public class TestJNINewWeakGlobalRef {

    static value class Value {
        int i;

        public Value(int i) {
            this.i = i;
        }
    }

    native static void newWeakGlobalRef(Object target);

    static {
        System.loadLibrary("JNINewWeakGlobalRef");
    }

    public static void main(String[] args) {
        Value v = new Value(1);
        try {
            newWeakGlobalRef(v);
            Asserts.fail("Should have thrown IdentityException");
        }
        catch (IdentityException i) {
            Asserts.assertTrue(i.getMessage().contains("TestJNINewWeakGlobalRef$Value is not an identity class"),
                               "Wrong identity exception: " + i);
        }

        // Sanity check that passing null doesn't cause an error.
        newWeakGlobalRef(null);
    }
}
