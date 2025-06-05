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
 * @bug 8336492
 * @summary Regression in lambda serialization
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.*;

public class SerializedLambdaInLocalClass {

    public static void main(String[] args) {
        SerializedLambdaInLocalClass s = new SerializedLambdaInLocalClass();
        s.test(s::f_lambda_in_anon);
        s.test(s::f_lambda_in_local);
        s.test(s::f_lambda_in_lambda);
    }

    void test(IntFunction<Supplier<F>> fSupplier) {
        try {
            F f = fSupplier.apply(42).get();
            var baos = new ByteArrayOutputStream();
            // write
            try (var oos = new ObjectOutputStream(baos)) {
                oos.writeObject(f);
            }
            byte[] bytes = baos.toByteArray();
            var bais = new ByteArrayInputStream(bytes);
            // read
            try (var ois = new ObjectInputStream(bais)) {
                F f2 = (F)ois.readObject();
                if (f2.getValue() != f.getValue()) {
                    throw new AssertionError(String.format("Found: %d, expected %d", f2.getValue(), f.getValue()));
                }
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new AssertionError(ex);
        }
    }

    interface F extends Serializable {
        int getValue();
    }

    Supplier<F> f_lambda_in_anon(int x) {
        return new Supplier<F>() {
            @Override
            public F get() {
                return () -> x;
            }
        };
    }

    Supplier<F> f_lambda_in_local(int x) {
        class FSupplier implements Supplier<F> {
            @Override
            public F get() {
                return () -> x;
            }
        }
        return new FSupplier();
    }

    Supplier<F> f_lambda_in_lambda(int x) {
        return () -> () -> x;
    }
}
