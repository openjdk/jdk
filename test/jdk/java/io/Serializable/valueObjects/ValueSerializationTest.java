/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test serialization of value classes
 * @enablePreview
 * @modules java.base/jdk.internal java.base/jdk.internal.value
 * @compile ValueSerializationTest.java
 * @run junit/othervm ValueSerializationTest
 */

import static java.io.ObjectStreamConstants.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.util.stream.Stream;

import jdk.internal.MigratedValueClass;
import jdk.internal.value.DeserializeConstructor;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ValueSerializationTest {

    static final Class<NotSerializableException> NSE = NotSerializableException.class;
    private static final Class<InvalidClassException> ICE = InvalidClassException.class;

    public static Stream<Arguments> doesNotImplementSerializable() {
        return Stream.of(
            Arguments.of( new NonSerializablePoint(10, 100), NSE),
            Arguments.of( new NonSerializablePointNoCons(10, 100), ICE),
            // an array of Points
            Arguments.of( new NonSerializablePoint[] {new NonSerializablePoint(1, 5)}, NSE),
            Arguments.of( Arguments.of(new NonSerializablePoint(3, 7)), NSE),
            Arguments.of( new ExternalizablePoint(12, 102), ICE),
            Arguments.of( new ExternalizablePoint[] {
                    new ExternalizablePoint(3, 7),
                    new ExternalizablePoint(2, 8) }, ICE),
            Arguments.of( new Object[] {
                    new ExternalizablePoint(13, 17),
                    new ExternalizablePoint(14, 18) }, ICE));
    }

    // value class that DOES NOT implement Serializable should throw ICE
    @ParameterizedTest
    @MethodSource("doesNotImplementSerializable")
    public void doesNotImplementSerializable(Object obj, Class expectedException) {
        assertThrows(expectedException, () -> serialize(obj));
    }

    /* Non-Serializable point. */
    public static value class NonSerializablePoint {
        public int x;
        public int y;

        public NonSerializablePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override public String toString() {
            return "[NonSerializablePoint x=" + x + " y=" + y + "]";
        }
    }

    /* Non-Serializable point, because it does not have an @DeserializeConstructor constructor. */
    public static value class NonSerializablePointNoCons implements Serializable {
        public int x;
        public int y;

        // Note: Must NOT have @DeserializeConstructor annotation
        public NonSerializablePointNoCons(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override public String toString() {
            return "[NonSerializablePointNoCons x=" + x + " y=" + y + "]";
        }
    }

    /* An Externalizable Point is not Serializable, readExternal cannot modify fields */
    static value class ExternalizablePoint implements Externalizable {
        public int x;
        public int y;
        public ExternalizablePoint() {this.x = 0; this.y = 0;}
        ExternalizablePoint(int x, int y) { this.x = x; this.y = y; }
        @Override public void readExternal(ObjectInput in) {  }
        @Override public void writeExternal(ObjectOutput out) {  }
        @Override public String toString() {
            return "[ExternalizablePoint x=" + x + " y=" + y + "]"; }
    }

    public static Stream<Arguments> implementSerializable() {
        return Stream.of(
                Arguments.of(new SerializablePoint(11, 101)),
                Arguments.of((Object)(new SerializablePoint[]{
                        new SerializablePoint(1, 5),
                        new SerializablePoint(2, 6)}),
                Arguments.of(Arguments.of(
                        new SerializablePoint(3, 7),
                        new SerializablePoint(4, 8))),
                Arguments.of(new SerializableFoo(45)),
                Arguments.of((Object)(new SerializableFoo[]{new SerializableFoo(46)})),
                Arguments.of(new ExternalizableFoo("hello")),
                Arguments.of((Object)new ExternalizableFoo[]{new ExternalizableFoo("there")})));
    }

    // value class that DOES implement Serializable is supported
    @ParameterizedTest
    @MethodSource("implementSerializable")
    public void implementSerializable(Object obj) throws IOException, ClassNotFoundException {
        byte[] bytes = serialize(obj);
        Object actual = deserialize(bytes);
        if (obj.getClass().isArray())
            Assertions.assertArrayEquals((Object[])actual, (Object[])obj);
        else
            assertEquals(actual, obj);
    }

    /* A Serializable value class Point */
    @MigratedValueClass
    static value class SerializablePoint implements Serializable {
        public int x;
        public int y;
        @DeserializeConstructor
        private SerializablePoint(int x, int y) { this.x = x; this.y = y; }

        @Override public String toString() {
            return "[SerializablePoint x=" + x + " y=" + y + "]";
        }
    }

    /* A Serializable Foo, with a serial proxy */
    static value class SerializableFoo implements Serializable {
        public int x;
        @DeserializeConstructor
        SerializableFoo(int x) { this.x = x; }

        @Serial Object writeReplace() throws ObjectStreamException {
            return new SerialFooProxy(x);
        }
        @Serial private void readObject(ObjectInputStream s) throws InvalidObjectException {
            throw new InvalidObjectException("Proxy required");
        }
        private record SerialFooProxy(int x) implements Serializable {
            @Serial Object readResolve() throws ObjectStreamException {
                return new SerializableFoo(x);
            }
        }
    }

    /* An Externalizable Foo, with a serial proxy */
    static value class ExternalizableFoo implements Externalizable {
        public String s;
        ExternalizableFoo(String s) {  this.s = s; }
        public boolean equals(Object other) {
            if (other instanceof ExternalizableFoo foo) {
                return s.equals(foo.s);
            } else {
                return false;
            }
        }
        @Serial  Object writeReplace() throws ObjectStreamException {
            return new SerialFooProxy(s);
        }
        private record SerialFooProxy(String s) implements Serializable {
            @Serial Object readResolve() throws ObjectStreamException {
                return new ExternalizableFoo(s);
            }
        }
        @Override public void readExternal(ObjectInput in) {  }
        @Override public void writeExternal(ObjectOutput out) {  }
    }

    // Generate a byte stream containing a reference to the named class with the SVID and flags.
    private static byte[] byteStreamFor(String className, long uid, byte flags)
        throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(STREAM_MAGIC);
        dos.writeShort(STREAM_VERSION);
        dos.writeByte(TC_OBJECT);
        dos.writeByte(TC_CLASSDESC);
        dos.writeUTF(className);
        dos.writeLong(uid);
        dos.writeByte(flags);
        dos.writeShort(0);             // number of fields
        dos.writeByte(TC_ENDBLOCKDATA);   // no annotations
        dos.writeByte(TC_NULL);           // no superclasses
        dos.close();
        return baos.toByteArray();
    }

    public static Stream<Arguments> classes() {
        return Stream.of(
            Arguments.of( ExternalizableFoo.class, SC_EXTERNALIZABLE, ICE ),
            Arguments.of( ExternalizableFoo.class, SC_SERIALIZABLE, ICE ),
            Arguments.of( SerializablePoint.class, SC_EXTERNALIZABLE, ICE ),
            Arguments.of( SerializablePoint.class, SC_SERIALIZABLE, null )
        );
    }

    // value class read directly from a byte stream
    // a byte stream is generated containing a reference to the class with the flags  and SVID.
    // Reading the class from the stream verifies the exceptions thrown if there is a mismatch
    // between the stream and the local class.
    @ParameterizedTest
    @MethodSource("classes")
    public void deserialize(Class<?> cls, byte flags, Class<Exception> expected) throws Exception {
        var clsDesc = ObjectStreamClass.lookup(cls);
        long uid = clsDesc == null ? 0L : clsDesc.getSerialVersionUID();
        byte[] serialBytes = byteStreamFor(cls.getName(), uid, flags);
        if (expected == null) {
            Assertions.assertDoesNotThrow(() -> deserialize(serialBytes));
        } else {
            Assertions.assertThrows(expected, () -> deserialize(serialBytes));
        }
    }

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
}
