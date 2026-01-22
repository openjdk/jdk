/*
 * Copyright (c) 2026, Google LLC and/or its affiliates. All rights reserved.
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
 * @bug 8374654
 * @summary test lambda deserialization for Object method references on interfaces
 * @compile/ref=SerializableObjectMethodReferencesOnInterfaces.out -XDrawDiagnostics --debug=dumpLambdaDeserializationStats SerializableObjectMethodReferencesOnInterfaces.java
 */

import java.io.Serializable;

public class SerializableObjectMethodReferencesOnInterfaces {

    static class Test {
        interface I1 extends Serializable {}

        interface I2 extends I1 {
            @Override
            public int hashCode();
        }

        interface F<T, R> extends Serializable {
            R apply(T t);
        }

        enum E {
            ONE
        }

        void run() throws Exception {
            F<I1, Integer> f1 = I1::hashCode;
            F<I2, Integer> f2 = I2::hashCode;
            F<E, Integer> f3 = E::hashCode;
            F<Object, Integer> f4 = Object::hashCode;
        }
    }
}
