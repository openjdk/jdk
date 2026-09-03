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
 * @bug 8379812
 * @summary ServiceLoader should reject invalid service types
 */

import java.lang.invoke.MethodType;
import java.lang.module.ModuleLayer;
import java.util.ServiceLoader;

public class ArrayServiceTypeTest {

    public static void main(String[] args) {
        assertFails(() -> ServiceLoader.loadInstalled(
                MethodType.genericMethodType(1, true).parameterType(1)));
        assertFails(() -> ServiceLoader.loadInstalled(Object[].class));
        assertFails(() -> ServiceLoader.load(Object[].class));
        assertFails(() -> ServiceLoader.load(Object[].class,
                ArrayServiceTypeTest.class.getClassLoader()));
        assertFails(() -> ServiceLoader.load(ModuleLayer.boot(), Object[].class));
        assertFails(() -> ServiceLoader.load(int.class));
    }

    private static void assertFails(Runnable action) {
        try {
            action.run();
            throw new RuntimeException("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("not a valid service type")) {
                throw new RuntimeException("unexpected message: " + e.getMessage(), e);
            }
        }
    }
}
