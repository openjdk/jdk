/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282080
 * @summary Check that serializable lambdas referring to j.l.Object methods work.
 * @compile SerializableObjectMethods.java
 * @run main SerializableObjectMethods
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableObjectMethods {

    interface I1 extends Serializable {}

    interface I2 extends I1 {

        @Override
        public int hashCode();

    }

    interface F<T, R> extends Serializable {

        R apply(T t);
    }

    public static void main(String[] args) throws Exception {
        new SerializableObjectMethods().run();
    }

    void run() throws IOException, ClassNotFoundException {
        saveLoad((F<I1, Integer>) I1::hashCode).apply(new I1() {});
        saveLoad((F<I2, Integer>) I2::hashCode).apply(new I2() {});
    }

    <T, R> F<T, R> saveLoad(F<T, R> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try ( ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(value);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            return (F<T, R>) ois.readObject();
        }
    }
}
