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
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/*
 * @test
 * @bug 8297727
 * @summary MethodHandle field access fails with bytecode compilation disabled
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames
 *     -Djava.lang.invoke.MethodHandle.COMPILE_THRESHOLD=-1
 *     ReflectionInInterpretTest
 */
public class ReflectionInInterpretTest {
    private static Integer f; // non-Object type is required to find a non-compiled form

    public static void main(String... args) throws Throwable {
        MethodHandle mh = MethodHandles.lookup().findStaticGetter(ReflectionInInterpretTest.class, "f", Integer.class);
        var _ = (Integer) mh.invokeExact();
    }
}
