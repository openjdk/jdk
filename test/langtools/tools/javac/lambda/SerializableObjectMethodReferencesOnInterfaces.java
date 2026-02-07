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
 * @bug 8374654 8208752
 * @summary test lambda deserialization for Object method references on interfaces
 * @compile/ref=SerializableObjectMethodReferencesOnInterfaces.out -XDrawDiagnostics --debug=dumpLambdaDeserializationStats SerializableObjectMethodReferencesOnInterfaces.java
 * @run main SerializableObjectMethodReferencesOnInterfaces
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableObjectMethodReferencesOnInterfaces {

    public static void main(String[] args) throws Exception {
        new Test().run();
    }

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

            serialDeserial(f1).apply(new I1() {});
            serialDeserial(f2).apply(new I2() {});
            serialDeserial(f3).apply(E.ONE);
            serialDeserial(f4).apply(new Object());
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T serialDeserial(T object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        try (ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (T) ois.readObject();
        }
    }
}
