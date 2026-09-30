/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6857159
 * @summary local schedule failed with checkcast of Thread.currentThread()
 * @modules java.base/jdk.internal.access
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:CompileCommand=dontinline,${test.main.class}::notInlined
 *                   ${test.main.class}
 */

package compiler.c2;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

public class TestLoadKlassAntiDep {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            test();
        }
    }

    static void notInlined() { }

    static Class<?> test() {
        notInlined();

        // These intrinsics create immutable klass loads whose addresses depend on the carrier thread load
        return JLA.currentCarrierThread().getClass().getSuperclass();
    }
}
