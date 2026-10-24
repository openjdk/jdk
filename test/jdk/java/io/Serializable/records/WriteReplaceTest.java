/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8246774
 * @summary Basic tests for writeReplace
 * @run junit WriteReplaceTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import static java.lang.System.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WriteReplaceTest {

    record R1 () implements Serializable {
        Object writeReplace() { return new Replacement(); }

        private static class Replacement implements Serializable {
            private Object readResolve() { return new R1(); }
        }
    }

    record R2 (int x, int y) implements Serializable {
        Object writeReplace() { return new Ser(x, y); }

        private static class Ser implements Serializable {
            private final int x;
            private final int y;
            Ser(int x, int y) { this.x = x; this.y = y; }
            private Object readResolve() { return new R2(x, y); }
        }
    }

    record R3 (R1 r1, R2 r2) implements Serializable { }

    record R4 (long l) implements Serializable {
        Object writeReplace() { return new Replacement(l); }

        static class Replacement implements Serializable {
            long l;
            Replacement(long l) { this.l = l; }
        }
    }

    public Object[][] recordObjects() {
        return new Object[][] {
            new Object[] { new R1(),                       R1.class             },
            new Object[] { new R2(1, 2),                   R2.class             },
            new Object[] { new R3(new R1(), new R2(3,4)),  R3.class             },
            new Object[] { new R4(4L),                     R4.Replacement.class },
        };
    }

    @ParameterizedTest
    @MethodSource("recordObjects")
    public void testSerialize(Object objectToSerialize, Class<?> expectedType)
        throws Exception
    {
        out.println("\n---");
        out.println("serializing : " + objectToSerialize);
        Object deserializedObj = serializeDeserialize(objectToSerialize);
        out.println("deserialized: " + deserializedObj);
        if (objectToSerialize.getClass().equals(expectedType))
            assertEquals(objectToSerialize, deserializedObj);
        else
            assertEquals(expectedType, deserializedObj.getClass());
    }

    // -- null replacement

    record R10 () implements Serializable {
        Object writeReplace() { return null; }
    }

    @Test
    public void testNull() throws Exception {
        out.println("\n---");
        Object objectToSerialize = new R10();
        out.println("serializing : " + objectToSerialize);
        Object deserializedObj = serializeDeserialize(objectToSerialize);
        out.println("deserialized: " + deserializedObj);
        assertEquals(null, deserializedObj);
    }

    // --- infra

    static <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] streamBytes)
        throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
        ObjectInputStream ois  = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }

    static <T> T serializeDeserialize(T obj)
        throws IOException, ClassNotFoundException
    {
        return deserialize(serialize(obj));
    }
}
