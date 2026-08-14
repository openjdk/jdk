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

/**
 * @test
 * @bug 8389450
 * @summary Test that loading and unloading value classes does not leak code cache memory.
 * @requires vm.flagless & vm.opt.final.ClassUnloading
 * @enablePreview
 * @run main/othervm -XX:ReservedCodeCacheSize=32m ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.io.InputStream;

public class TestValueClassCodeCacheLeak {
    public static value class TemporaryValue { }

    static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) {
            return defineClass("compiler.valhalla.inlinetypes.TestValueClassCodeCacheLeak$TemporaryValue", bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        InputStream in = TestValueClassCodeCacheLeak.class.getResourceAsStream("TestValueClassCodeCacheLeak$TemporaryValue.class");
        byte[] bytes = in.readAllBytes();

        // Create a class loader, load value class and throw the class loader away
        // to trigger class unloading. This should not leak (code cache) memory.
        for (int i = 0; i < 5000; i++) {
            new Loader().define(bytes);
        }
    }
}

