/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8208752
 * @summary NPE generating serializedLambdaName for nested lambda
 * @compile/ref=LambdaSerializedClassCastException.out -XDrawDiagnostics --debug=dumpLambdaDeserializationStats LambdaSerializedClassCastException.java
 * @run main LambdaSerializedClassCastException
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Function;

public class LambdaSerializedClassCastException {

    public static void main(String[] args) throws Exception {

        Function<String, String> lambda1 =
                (Function<String, String> & Serializable) Object::toString;
        Function<Object, String> lambda2 =
                (Function<Object, String> & Serializable) Object::toString;

        Function<Object, String> deserial = serialDeserial(lambda2);
        deserial.apply(new Object());
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
