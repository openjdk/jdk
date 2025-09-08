/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342090 8288590
 * @summary Infer::IncorporationBinaryOp::equals can produce side-effects
 * @compile NonDeterminismTest.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 NonDeterminismTest.java
 */

import java.util.*;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;

import static java.util.Arrays.asList;

class NonDeterminismTest {
    void test1() {
        Map<String, MemoryLayout> CANONICAL_LAYOUTS = Map.ofEntries(
                // specified canonical layouts
                Map.entry("bool", JAVA_BOOLEAN),
                Map.entry("char", JAVA_BYTE),
                Map.entry("float", JAVA_FLOAT),
                Map.entry("long long", JAVA_LONG),
                Map.entry("double", JAVA_DOUBLE),
                Map.entry("void*", ADDRESS),
                // JNI types
                Map.entry("jboolean", JAVA_BOOLEAN),
                Map.entry("jchar", JAVA_CHAR),
                Map.entry("jbyte", JAVA_BYTE),
                Map.entry("jshort", JAVA_SHORT),
                Map.entry("jint", JAVA_INT),
                Map.entry("jlong", JAVA_LONG),
                Map.entry("jfloat", JAVA_FLOAT),
                Map.entry("jdouble", JAVA_DOUBLE)
        );
    }

    class Test2 {
        interface I1<T1> {}
        interface I2<T1, T2> {}

        record R1<T1>(List<T1> T1) implements I1<T1> {}
        record R2<T1, T2>(List<T1> T1, List<T2> T2) implements I2<T1, T2> {}

        <T1> I1<T1> m1(T1 T1) {
            return new R1<>(asList(T1));
        }
        <T1, T2> I2<T1, T2> m2(T1 T1, T2 T2) {
            return new R2<>(asList(T1), asList(T2));
        }
    }
}
