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
 * @bug 8388318
 * @summary Test serialization of value classes
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @library /test/lib
 * @compile ValueSerializationTest.java
 * @build jdk.test.lib.helpers.StrictInit jdk.test.lib.helpers.StrictProcessor
 * @comment run the StrictProcessor over the classes that use \@StrictInit to
 *          generate classfiles with STRICT_INIT access flags for its annotated fields
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             ValueSerializationTest$IdentityStrictPoint
 * @run junit/othervm -DdeserializerOnBootclasspath=false ${test.main.class}
 * @run junit/bootclasspath/othervm -DdeserializerOnBootclasspath=true ${test.main.class}
 */

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

import jdk.internal.value.Deserializer;
import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.io.ObjectStreamConstants.SC_EXTERNALIZABLE;
import static java.io.ObjectStreamConstants.SC_SERIALIZABLE;
import static java.io.ObjectStreamConstants.STREAM_MAGIC;
import static java.io.ObjectStreamConstants.STREAM_VERSION;
import static java.io.ObjectStreamConstants.TC_CLASSDESC;
import static java.io.ObjectStreamConstants.TC_ENDBLOCKDATA;
import static java.io.ObjectStreamConstants.TC_NULL;
import static java.io.ObjectStreamConstants.TC_OBJECT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValueSerializationTest {

    private static final boolean DESERIALIZER_ON_BOOTCLASSPATH = Boolean.getBoolean("deserializerOnBootclasspath");
    private static final Class<NotSerializableException> NSE = NotSerializableException.class;
    private static final Class<InvalidClassException> ICE = InvalidClassException.class;

    @BeforeAll
    static void setup() {
        System.out.println("deserializerOnBootclasspath: " + DESERIALIZER_ON_BOOTCLASSPATH);
    }

    static Stream<Arguments> serializationFailingInstances() {
        return DESERIALIZER_ON_BOOTCLASSPATH ? serializationAlwaysFailingInstances()
                : Stream.concat(serializationAlwaysFailingInstances(),
                                // Unrecognized deserializer leads to ICE
                                deserializerInstances().map(a -> Arguments.of(a, ICE)));
    }

    static Stream<Arguments> serializationAlwaysFailingInstances() {
        return Stream.of(
                Arguments.of(
                        new NonSerializableValue(10, 100),
                        NSE
                ),

                Arguments.of(
                        new ValueWithNoDeserializer(10, 100),
                        ICE
                ),

                Arguments.of(
                        new IdentityStrictPoint(),
                        ICE
                ),

                Arguments.of(
                        new StrictInSuper(),
                        ICE
                ),

                Arguments.of(
                        new StrictInTwiceSuper(),
                        ICE
                ),

                Arguments.of(
                        new StrictInAbstractValueSuper(),
                        ICE
                ),

                // an array of non-serializable value objects
                Arguments.of(
                        new NonSerializableValue[]{
                                new NonSerializableValue(1, 5)
                        },
                        NSE
                ),

                Arguments.of(
                        new Object[]{
                                new NonSerializableValue(3, 7)
                        },
                        NSE
                ),

                Arguments.of(
                        new ExternalizableValue(12, 102),
                        ICE
                ),

                Arguments.of(
                        new ExternalizableValue[]{
                                new ExternalizableValue(3, 7),
                                new ExternalizableValue(2, 8)
                        },
                        ICE
                ),

                Arguments.of(
                        new Object[]{
                                new ExternalizableValue(13, 17),
                                new ExternalizableValue(14, 18)
                        },
                        ICE
                )
        );
    }

    /*
     * Verifies that the given obj that isn't expected to be serializable
     * throws the expected exception from ObjectOutputStream.writeObject()
     */
    @ParameterizedTest
    @MethodSource("serializationFailingInstances")
    void testSerializationFails(Object obj, Class<? extends Exception> expectedException)
            throws Exception {
        // expect serialization to fail
        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            assertThrows(expectedException, () -> oos.writeObject(obj));
        }
    }

    static Stream<Object> deserializerInstances() {
        return Stream.of(
                new ValueWithDeserializer(11, 101),

                new ValueWithDeserializer[]{
                        new ValueWithDeserializer(1, 5),
                        new ValueWithDeserializer(2, 6)
                },

                new Object[]{
                        new ValueWithDeserializer(3, 7),
                        new ValueWithDeserializer(4, 8)
                }
        );
    }

    static Stream<Object> serializingInstances() {
        return DESERIALIZER_ON_BOOTCLASSPATH ? Stream.concat(deserializerInstances(), alwaysSerializingInstances())
                : alwaysSerializingInstances();
    }

    static Stream<Object> alwaysSerializingInstances() {
        return Stream.of(
                new ValueWriteReplaceWithIdentity(45),

                new ValueWriteReplaceWithIdentity[]{
                        new ValueWriteReplaceWithIdentity(46)
                },

                new ExtValueWithIdentityReplacement("hello"),

                new ExtValueWithIdentityReplacement[]{
                        new ExtValueWithIdentityReplacement("there")
                },

                new CustomNumberWithIdentity(16, 42),

                new CustomNumberWithIdentity[] {
                        new CustomNumberWithIdentity(-6, 77),
                        new CustomNumberWithIdentity(54, -79),
                }
        );
    }

    /*
     * Verifies that a value object that implements java.io.Serializable and is associated with
     * the JDK internal jdk.internal.value.Deserializer can be serialized and deserialized through
     * the use of ObjectOutputStream.writeObject() and ObjectInputStream.readObject() successfully.
     * The deserialized object is then compared with the given obj to verify that they are equal.
     */
    @ParameterizedTest
    @MethodSource("serializingInstances")
    void testSerDeserSucceeds(Object obj) throws IOException, ClassNotFoundException {
        // serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        byte[] bytes = baos.toByteArray();
        Object actual;
        // deserialize
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            actual = ois.readObject();
        }
        // compare the deserialized with the original
        if (obj.getClass().isArray()) {
            assertArrayEquals((Object[]) obj, (Object[]) actual);
        } else {
            assertEquals(obj, actual);
        }
    }

    static Stream<Arguments> classes() {
        return Stream.of(
                Arguments.of(
                        ExtValueWithIdentityReplacement.class,
                        SC_EXTERNALIZABLE,
                        ICE
                ),

                Arguments.of(
                        ExtValueWithIdentityReplacement.class,
                        SC_SERIALIZABLE,
                        ICE
                ),

                Arguments.of(
                        IdentityStrictPoint.class,
                        SC_SERIALIZABLE,
                        ICE
                ),

                Arguments.of(
                        StrictInSuper.class,
                        SC_SERIALIZABLE,
                        ICE
                ),

                Arguments.of(
                        StrictInTwiceSuper.class,
                        SC_SERIALIZABLE,
                        ICE
                ),

                Arguments.of(
                        StrictInAbstractValueSuper.class,
                        SC_SERIALIZABLE,
                        ICE
                ),

                Arguments.of(
                        ValueWithDeserializer.class,
                        SC_EXTERNALIZABLE,
                        ICE
                ),

                Arguments.of(
                        ValueWithDeserializer.class,
                        SC_SERIALIZABLE,
                        DESERIALIZER_ON_BOOTCLASSPATH ? null : ICE
                ),

                Arguments.of(
                        CustomNumberWithIdentity.class,
                        SC_SERIALIZABLE,
                        null
                )
        );
    }

    /*
     * A byte stream is generated containing a reference to the given class
     * with the given flags and a serial version UID determined in the test method.
     * The byte stream is then read using ObjectInputStream.readObject() and the test verifies
     * that if an exception is expected to be thrown then it is thrown, or if the deserialization
     * is expected to complete normally, then it verifies that no exception is thrown.
     */
    @ParameterizedTest
    @MethodSource("classes")
    void testDeser(Class<?> clazz, byte flags, Class<? extends Exception> expectedException)
            throws Exception {
        ObjectStreamClass clsDesc = ObjectStreamClass.lookup(clazz);
        long uid = clsDesc == null ? 0L : clsDesc.getSerialVersionUID();
        byte[] serialBytes = byteStreamFor(clazz.getName(), uid, flags);
        // deserialize
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialBytes))) {
            if (expectedException != null) {
                assertThrows(expectedException, () -> ois.readObject());
            } else {
                ois.readObject();
            }
        }
    }

    // Generate a byte stream containing a reference to the named class with the SVID and flags.
    private static byte[] byteStreamFor(String className, long uid, byte flags) throws Exception {
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

    /**
     * A concrete value class that doesn't implement Serializable (or Externalizable) interface
     */
    public static value class NonSerializableValue {
        public int x;
        public int y;

        public NonSerializableValue(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[NonSerializableValue x=" + x + " y=" + y + "]";
        }
    }

    /**
     * An identity class with strict initialized instance fields
     */
    public static class IdentityStrictPoint implements Serializable {
        static {
            for (var f : IdentityStrictPoint.class.getDeclaredFields()) {
                assertTrue(f.isStrictInit(), "missing strict init on field: " + f.getName());
            }
        }

        @StrictInit
        public int x;
        @StrictInit
        public int y;

        public IdentityStrictPoint() {
            x = 3;
            y = 5;
            super();
        }

        @Override
        public String toString() {
            return "[IdentityStrictPoint x=" + x + " y=" + y + "]";
        }
    }

    /**
     * A concrete value class that implements java.io.Serializable and doesn't have any
     * jdk.internal.value.Deserializer on its constructor.
     */
    public static value class ValueWithNoDeserializer implements Serializable {
        public int x;
        public int y;

        // Note: Must NOT have @Deserializer annotation
        public ValueWithNoDeserializer(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[ValueWithNoDeserializer x=" + x + " y=" + y + "]";
        }
    }

    /**
     * A concrete value class which implements java.io.Externalizable and doesn't
     * implement writeReplace().
     */
    static value class ExternalizableValue implements Externalizable {
        public int x;
        public int y;

        public ExternalizableValue() {
            this.x = 0;
            this.y = 0;
        }

        ExternalizableValue(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void readExternal(ObjectInput in) {
            // concrete value class isn't expected to be deserializable, so we don't
            // expect this method to be invoked during deserialization.
            throw new AssertionError("not expected to be invoked on " + this);
        }

        @Override
        public void writeExternal(ObjectOutput out) {
            // concrete value class isn't expected to be serializable, so we don't
            // expect this method to be invoked during serialization.
            throw new AssertionError("not expected to be invoked on " + this);
        }

        @Override
        public String toString() {
            return "[ExternalizableValue x=" + x + " y=" + y + "]";
        }
    }


    /**
     * A concrete value class which implements java.io.Serializable and has a
     * jdk.internal.value.Deserializer associated with its constructor.
     * It may be serialized only if it is on the boot class path.
     */
    static value class ValueWithDeserializer implements Serializable {
        public int x;
        public int y;

        @Deserializer({"x", "y"})
        private ValueWithDeserializer(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[ValueWithDeserializer x=" + x + " y=" + y + "]";
        }
    }

    /**
     * A concrete value class which implements java.io.Serializable
     * and implements the writeReplace() method to return an identity
     * object.
     */
    static value class ValueWriteReplaceWithIdentity implements Serializable {
        public int x;

        ValueWriteReplaceWithIdentity(int x) {
            this.x = x;
        }

        @Serial
        Object writeReplace() throws ObjectStreamException {
            return new IdentityRecord(x);
        }

        @Serial
        private void readObject(ObjectInputStream s) throws InvalidObjectException {
            // the writeReplace() implementation of this class, when the serialization side
            // is preparing to write this object to the stream, has replaced this object
            // with an instance of a different class, so we don't expect deserialization
            // to invoke this method.
            throw new AssertionError("not expected to be invoked on " + this);
        }

        @Override
        public String toString() {
            return "[ValueWriteReplaceWithIdentity x=" + x + "]";
        }

        private record IdentityRecord(int x) implements Serializable {
            @Serial
            Object readResolve() throws ObjectStreamException {
                return new ValueWriteReplaceWithIdentity(x);
            }
        }
    }

    /**
     * A concrete value class which implements java.io.Externalizable and implements
     * the writeReplace() method to return an identity object.
     */
    static value class ExtValueWithIdentityReplacement implements Externalizable {
        public String s;

        ExtValueWithIdentityReplacement(String s) {
            this.s = s;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ExtValueWithIdentityReplacement foo && s.equals(foo.s);
        }

        @Serial
        Object writeReplace() throws ObjectStreamException {
            return new IdentityRecord(s);
        }

        private record IdentityRecord(String s) implements Serializable {
            @Serial
            Object readResolve() throws ObjectStreamException {
                return new ExtValueWithIdentityReplacement(s);
            }
        }

        @Override
        public void readExternal(ObjectInput in) {
            // the writeReplace() implementation of this class, when the serialization side
            // is preparing to write this object to the stream, has replaced this object
            // with an instance of a different class, so we don't expect deserialization
            // to invoke this method.
            throw new AssertionError("not expected to be invoked on " + this);
        }

        @Override
        public void writeExternal(ObjectOutput out) {
            // the writeReplace() implementation of this class, when the serialization side
            // is preparing to write this object to the stream, has replaced this object
            // with an instance of a different class, so we don't expect this method to
            // play any role during serialization.
            throw new AssertionError("not expected to be invoked on " + this);
        }

        @Override
        public String toString() {
            return "[ExtValueWithIdentityReplacement s=" + s + "]";
        }
    }

    /**
     * A plain identity class that does not declare any strictly initialized
     * instance field but its immediate superclasses, which implement Serializable,
     * does.
     */
    public static class StrictInSuper extends IdentityStrictPoint {
        // Declares no field, inherits strictly-initialized instance fields
        // IdentityStrictPoint.x and y
        public StrictInSuper() {
            super();
        }

        @Override
        public String toString() {
            return "[StrictInSuper x=" + x + " y=" + y + "]";
        }
    }

    /**
     * A plain identity class that does not declare any strictly initialized
     * instance field but one of its non-immediate superclasses that implement
     * Serializable does.
     */
    public static class StrictInTwiceSuper extends StrictInSuper {
        // Declares no field, inherits strictly-initialized instance fields
        // IdentityStrictPoint.x and y
        public StrictInTwiceSuper() {
            super();
        }

        @Override
        public String toString() {
            return "[StrictInTwiceSuper x=" + x + " y=" + y + "]";
        }
    }

    /**
     * An abstract value class that declares instance fields. Such fields are
     * always strictly initialized, making none of their subclasses serializable
     * without jdk.internal.value.Deserializer.
     */
    public abstract static value class HasFieldAbstractValue implements Serializable {
        public int x;
        public int y;

        public HasFieldAbstractValue(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[HasFieldAbstractValue x=" + x + " y=" + y + "]";
        }
    }

    /**
     * An identity class that inherits strictly-initialized instance fields from
     * its abstract value superclass that is serializable.  Therefore, this class
     * is not serializable without jdk.internal.value.Deserializer.
     */
    public static class StrictInAbstractValueSuper extends HasFieldAbstractValue {
        public StrictInAbstractValueSuper() {
            super(42, -3);
        }

        @Override
        public String toString() {
            return "[StrictInAbstractValueSuper x=" + x + " y=" + y + "]";
        }
    }

    /**
     * An identity class that does not inherit any strictly-initialized instance
     * field from its abstract value superclass that is serializable, in this
     * case the migrated java.lang.Number.  This class is accepted by default
     * serialization.
     */
    public static class CustomNumberWithIdentity extends Number {
        public int x;
        public int y;

        public CustomNumberWithIdentity(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int intValue() {
            return (int) longValue();
        }

        @Override
        public long longValue() {
            return (long) x << 32 | y;
        }

        @Override
        public float floatValue() {
            return longValue();
        }

        @Override
        public double doubleValue() {
            return longValue();
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof CustomNumberWithIdentity that))
                return false;

            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return result;
        }

        @Override
        public String toString() {
            return "[CustomNumberWithIdentity x=" + x + " y=" + y + "]";
        }
    }
}
