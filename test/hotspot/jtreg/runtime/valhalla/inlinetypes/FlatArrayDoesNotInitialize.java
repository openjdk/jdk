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
 * @summary Test that creating an array of T does not initialize class T
 * @enablePreview
 * @compile FlatArrayDoesNotInitialize.java
 * @run main/othervm runtime.valhalla.inlinetypes.FlatArrayDoesNotInitialize
 */
package runtime.valhalla.inlinetypes;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;


public class FlatArrayDoesNotInitialize {
    static boolean initialized;

    static value class MyValue {
        static {
            initialized = true;
        }
    }

    public static void main(String[] args) {
        MyValue[] array = new MyValue[1];
        VarHandle handle = MethodHandles.arrayElementVarHandle(MyValue[].class);
        if (initialized) {
            throw new AssertionError("Should not be initialized");
        }
        if (!(boolean) handle.compareAndSet(array, 0, null, null)) {
            throw new AssertionError("CAS failed");
        }
        if (initialized) {
            throw new AssertionError("Should not be initialized");
        }
    }
}

