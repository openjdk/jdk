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
        s.test(s::f_lambda);
        s.test(s::f_anon);
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

    Supplier<F> f_lambda(int x) {
        return new Supplier<F>() {
            @Override
            public F get() {
                return () -> x;
            }
        };
    }

    Supplier<F> f_anon(int x) {
        return new Supplier<F>() {
            @Override
            public F get() {
                return new F() {
                    public int getValue() {
                        return x;
                    }
                };
            }
        };
    }
}
