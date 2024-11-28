/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.OptionalDataException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import sun.reflect.ReflectionFactory;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.TestNG;

/*
 * @test
 * @bug 8137058 8164908 8168980 8275137 8333796
 * @summary Basic test for the unsupported ReflectionFactory
 * @modules jdk.unsupported
 * @run testng ReflectionFactoryTest
 */

public class ReflectionFactoryTest {

    // Initialized by init()
    static ReflectionFactory factory;

    @DataProvider(name = "ClassConstructors")
    static Object[][] classConstructors() {
        return new Object[][] {
                {Object.class},
                {Foo.class},
                {Bar.class},
        };
    }

    @BeforeClass
    static void init() {
        factory = ReflectionFactory.getReflectionFactory();
    }

    /**
     * Test that the correct Constructor is selected and run.
     * @param type type of object to create
     * @throws NoSuchMethodException - error
     * @throws InstantiationException - error
     * @throws IllegalAccessException - error
     * @throws InvocationTargetException - error
     */
    @Test(dataProvider="ClassConstructors")
    static void testConstructor(Class<?> type)
            throws InstantiationException, IllegalAccessException, InvocationTargetException
    {
        @SuppressWarnings("unchecked")
        Constructor<?> c = factory.newConstructorForSerialization(type);

        Object o = c.newInstance();
        Assert.assertEquals(o.getClass(), type, "Instance is wrong type");
        if (o instanceof Foo) {
            Foo foo = (Foo)o;
            foo.check();
        }
    }

    @DataProvider(name = "NonSerialConstructors")
    static Object[][] constructors() throws NoSuchMethodException {
        return new Object[][] {
                {Foo.class, Object.class.getDeclaredConstructor()},
                {Foo.class, Foo.class.getDeclaredConstructor()},
                {Baz.class, Object.class.getDeclaredConstructor()},
                {Baz.class, Foo.class.getDeclaredConstructor()},
                {Baz.class, Baz.class.getDeclaredConstructor()}
        };
    }

    /**
     * Tests that the given Constructor, in the hierarchy, is run.
     */
    @Test(dataProvider="NonSerialConstructors")
    static void testNonSerializableConstructor(Class<?> cl,
                                               Constructor<?> constructorToCall)
        throws ReflectiveOperationException
    {
        @SuppressWarnings("unchecked")
        Constructor<?> c = factory.newConstructorForSerialization(cl,
                                                                  constructorToCall);

        Object o = c.newInstance();
        Assert.assertEquals(o.getClass(), cl, "Instance is wrong type");

        int expectedFoo = 0;
        int expectedBaz = 0;
        if (constructorToCall.getName().equals("ReflectionFactoryTest$Foo")) {
            expectedFoo = 1;
        } else if (constructorToCall.getName().equals("ReflectionFactoryTest$Baz")) {
            expectedFoo = 1;
            expectedBaz = 4;
        }

        Assert.assertEquals(((Foo)o).foo(), expectedFoo);
        if (o instanceof Baz b) {
            Assert.assertEquals(b.baz(), expectedBaz);
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    static void testConstructorNotSuperClass() throws ReflectiveOperationException {
        factory.newConstructorForSerialization(Bar.class, Baz.class.getDeclaredConstructor());
    }

    static class Foo {
        private int foo;
        public Foo() {
            this.foo = 1;
        }

        public String toString() {
            return "foo: " + foo;
        }

        public void check() {
            int expectedFoo = 1;
            Assert.assertEquals(foo, expectedFoo, "foo() constructor not run");
        }

        public int foo() { return foo; }
    }

    static class Bar extends Foo implements Serializable {
        private int bar;
        public Bar() {
            this.bar = 1;
        }

        public String toString() {
            return super.toString() + ", bar: " + bar;
        }

        public void check() {
            super.check();
            int expectedBar = 0;
            Assert.assertEquals(bar, expectedBar, "bar() constructor not run");
        }
    }

    static class Baz extends Foo {
        private final int baz;
        public Baz() { this.baz = 4; }
        public int baz() { return baz; }
    }

    /**
     * Tests that newConstructorForExternalization returns the constructor and it can be called.
     * @throws NoSuchMethodException - error
     * @throws InstantiationException - error
     * @throws IllegalAccessException - error
     * @throws InvocationTargetException - error
     */
    @Test
    static void newConstructorForExternalization()
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> cons = factory.newConstructorForExternalization(Ext.class);
        Ext ext = (Ext)cons.newInstance();
        Assert.assertEquals(ext.ext, 1, "Constructor not run");
    }

    static class Ext implements Externalizable {
        private static final long serialVersionUID = 1L;

        int ext;

        public Ext() {
            ext = 1;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {}

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
    }

    @Test
    static void testReadWriteObjectForSerialization() throws Throwable {
        MethodHandle readObjectMethod = factory.readObjectForSerialization(Ser.class);
        Assert.assertNotNull(readObjectMethod, "readObjectMethod not found");

        MethodHandle readObjectNoDataMethod = factory.readObjectNoDataForSerialization(Ser.class);
        Assert.assertNotNull(readObjectNoDataMethod, "readObjectNoDataMethod not found");

        MethodHandle writeObjectMethod = factory.writeObjectForSerialization(Ser.class);
        Assert.assertNotNull(writeObjectMethod, "writeObjectMethod not found");

        MethodHandle readResolveMethod = factory.readResolveForSerialization(Ser.class);
        Assert.assertNotNull(readResolveMethod, "readResolveMethod not found");

        MethodHandle writeReplaceMethod = factory.writeReplaceForSerialization(Ser.class);
        Assert.assertNotNull(writeReplaceMethod, "writeReplaceMethod not found");

        byte[] data = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            Ser ser = new Ser();

            writeReplaceMethod.invoke(ser);
            Assert.assertTrue(ser.writeReplaceCalled, "writeReplace not called");
            Assert.assertFalse(ser.writeObjectCalled, "writeObject should not have been called");

            writeObjectMethod.invoke(ser, oos);
            Assert.assertTrue(ser.writeReplaceCalled, "writeReplace should have been called");
            Assert.assertTrue(ser.writeObjectCalled, "writeObject not called");
            oos.flush();
            data = baos.toByteArray();
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            Ser ser2 = new Ser();

            readObjectMethod.invoke(ser2, ois);
            Assert.assertTrue(ser2.readObjectCalled, "readObject not called");
            Assert.assertFalse(ser2.readObjectNoDataCalled, "readObjectNoData should not be called");
            Assert.assertFalse(ser2.readResolveCalled, "readResolve should not be called");

            readObjectNoDataMethod.invoke(ser2);
            Assert.assertTrue(ser2.readObjectCalled, "readObject should have been called");
            Assert.assertTrue(ser2.readObjectNoDataCalled, "readObjectNoData not called");
            Assert.assertFalse(ser2.readResolveCalled, "readResolve should not be called");

            readResolveMethod.invoke(ser2);
            Assert.assertTrue(ser2.readObjectCalled, "readObject should have been called");
            Assert.assertTrue(ser2.readObjectNoDataCalled, "readObjectNoData not called");
            Assert.assertTrue(ser2.readResolveCalled, "readResolve not called");
        }
    }

    @Test
    static void hasStaticInitializer() {
        boolean actual = factory.hasStaticInitializerForSerialization(Ser.class);
        Assert.assertTrue(actual, "hasStaticInitializerForSerialization is wrong");
    }

    static class Ser implements Serializable {
        private static final long serialVersionUID = 2L;
        static {
            // Define a static class initialization method
        }

        boolean readObjectCalled = false;
        boolean readObjectNoDataCalled = false;
        boolean writeObjectCalled = false;
        boolean readResolveCalled = false;
        boolean writeReplaceCalled = false;

        public Ser() {}

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            Assert.assertFalse(writeObjectCalled, "readObject called too many times");
            readObjectCalled = ois.readBoolean();
        }

        private void readObjectNoData() throws ObjectStreamException {
            Assert.assertFalse(readObjectNoDataCalled, "readObjectNoData called too many times");
            readObjectNoDataCalled = true;
        }

        private void writeObject(ObjectOutputStream oos) throws IOException {
            Assert.assertFalse(writeObjectCalled, "writeObject called too many times");
            writeObjectCalled = true;
            oos.writeBoolean(writeObjectCalled);
        }

        private Object writeReplace() throws ObjectStreamException {
            Assert.assertFalse(writeReplaceCalled, "writeReplace called too many times");
            writeReplaceCalled = true;
            return this;
        }

        private Object readResolve() throws ObjectStreamException {
            Assert.assertFalse(readResolveCalled, "readResolve called too many times");
            readResolveCalled = true;
            return this;
        }
    }

    /**
     * Tests the constructor of OptionalDataExceptions.
     */
    @Test
    static void newOptionalDataException() {
        OptionalDataException ode = factory.newOptionalDataExceptionForSerialization(true);
        Assert.assertTrue(ode.eof, "eof wrong");
        ode = factory.newOptionalDataExceptionForSerialization(false);
        Assert.assertFalse(ode.eof, "eof wrong");

    }

    private static final String[] names = {
        "boolean_",
        "final_boolean",
        "byte_",
        "final_byte",
        "char_",
        "final_char",
        "short_",
        "final_short",
        "int_",
        "final_int",
        "long_",
        "final_long",
        "float_",
        "final_float",
        "double_",
        "final_double",
        "str",
        "final_str",
        "writeFields",
    };

    // test that the generated read/write objects are working properly
    @Test
    static void testDefaultReadWriteObject() throws Throwable {
        Ser2 ser = new Ser2((byte) 0x33, (short) 0x2244, (char) 0x5342, 0x05382716, 0xf035a73b09113bacL, 1234f, 3456.0, true, new Ser3(0x004917aa));
        ser.byte_ = (byte) 0x44;
        ser.short_ = (short) 0x3355;
        ser.char_ = (char) 0x6593;
        ser.int_ = 0x4928a299;
        ser.long_ = 0x24aa19883f4b9138L;
        ser.float_ = 4321f;
        ser.double_ = 6543.0;
        ser.boolean_ = false;
        ser.ser = new Ser3(0x70b030a0);
        // first, ensure that each field gets written
        MethodHandle writeObject = factory.defaultWriteObjectForSerialization(Ser2.class);
        Assert.assertNotNull(writeObject, "writeObject not created");
        boolean[] called = new boolean[19];
        @SuppressWarnings("removal")
        ObjectOutputStream oos = new ObjectOutputStream() {
            protected void writeObjectOverride(final Object obj) throws IOException {
                throw new IOException("Wrong method called");
            }

            public void defaultWriteObject() throws IOException {
                throw new IOException("Wrong method called");
            }

            public void writeFields() {
                called[18] = true;
            }

            public PutField putFields() {
                return new PutField() {
                    public void put(final String name, final boolean val) {
                        switch (name) {
                            case "boolean_" -> {
                                Assert.assertFalse(val);
                                called[0] = true;
                            }
                            case "final_boolean" -> {
                                Assert.assertTrue(val);
                                called[1] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final byte val) {
                        switch (name) {
                            case "byte_" -> {
                                Assert.assertEquals(val, (byte) 0x44);
                                called[2] = true;
                            }
                            case "final_byte" -> {
                                Assert.assertEquals(val, (byte) 0x33);
                                called[3] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final char val) {
                        switch (name) {
                            case "char_" -> {
                                Assert.assertEquals(val, (char) 0x6593);
                                called[4] = true;
                            }
                            case "final_char" -> {
                                Assert.assertEquals(val, (char) 0x5342);
                                called[5] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final short val) {
                        switch (name) {
                            case "short_" -> {
                                Assert.assertEquals(val, (short) 0x3355);
                                called[6] = true;
                            }
                            case "final_short" -> {
                                Assert.assertEquals(val, (short) 0x2244);
                                called[7] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final int val) {
                        switch (name) {
                            case "int_" -> {
                                Assert.assertEquals(val, 0x4928a299);
                                called[8] = true;
                            }
                            case "final_int" -> {
                                Assert.assertEquals(val, 0x05382716);
                                called[9] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final long val) {
                        switch (name) {
                            case "long_" -> {
                                Assert.assertEquals(val, 0x24aa19883f4b9138L);
                                called[10] = true;
                            }
                            case "final_long" -> {
                                Assert.assertEquals(val, 0xf035a73b09113bacL);
                                called[11] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final float val) {
                        switch (name) {
                            case "float_" -> {
                                Assert.assertEquals(val, 4321f);
                                called[12] = true;
                            }
                            case "final_float" -> {
                                Assert.assertEquals(val, 1234f);
                                called[13] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final double val) {
                        switch (name) {
                            case "double_" -> {
                                Assert.assertEquals(val, 6543.0);
                                called[14] = true;
                            }
                            case "final_double" -> {
                                Assert.assertEquals(val, 3456.0);
                                called[15] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    public void put(final String name, final Object val) {
                        switch (name) {
                            case "ser" -> {
                                Assert.assertEquals(val, new Ser3(0x70b030a0));
                                called[16] = true;
                            }
                            case "final_ser" -> {
                                Assert.assertEquals(val, new Ser3(0x004917aa));
                                called[17] = true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        }
                    }

                    @SuppressWarnings("removal")
                    public void write(final ObjectOutput out) throws IOException {
                        throw new IOException("Wrong method called");
                    }
                };
            }
        };
        writeObject.invokeExact(ser, oos);
        for (int i = 0; i < 19; i ++) {
            Assert.assertTrue(called[i], names[i]);
        }
        // now, test the read side
        MethodHandle readObject = factory.defaultReadObjectForSerialization(Ser2.class);
        Assert.assertNotNull(readObject, "readObject not created");
        @SuppressWarnings("removal")
        ObjectInputStream ois = new ObjectInputStream() {
            protected Object readObjectOverride() throws IOException {
                throw new IOException("Wrong method called");
            }

            public GetField readFields() {
                return new GetField() {
                    public ObjectStreamClass getObjectStreamClass() {
                        throw new Error("Wrong method called");
                    }

                    public boolean defaulted(final String name) throws IOException {
                        throw new IOException("Wrong method called");
                    }

                    public boolean get(final String name, final boolean val) {
                        return switch (name) {
                            case "boolean_" -> {
                                called[0] = true;
                                yield true;
                            }
                            case "final_boolean" -> {
                                called[1] = true;
                                yield true;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public byte get(final String name, final byte val) {
                        return switch (name) {
                            case "byte_" -> {
                                called[2] = true;
                                yield (byte) 0x11;
                            }
                            case "final_byte" -> {
                                called[3] = true;
                                yield (byte) 0x9f;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public char get(final String name, final char val) {
                        return switch (name) {
                            case "char_" -> {
                                called[4] = true;
                                yield (char) 0x59a2;
                            }
                            case "final_char" -> {
                                called[5] = true;
                                yield (char) 0xe0d0;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public short get(final String name, final short val) {
                        return switch (name) {
                            case "short_" -> {
                                called[6] = true;
                                yield (short) 0x0917;
                            }
                            case "final_short" -> {
                                called[7] = true;
                                yield (short) 0x110e;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public int get(final String name, final int val) {
                        return switch (name) {
                            case "int_" -> {
                                called[8] = true;
                                yield 0xd0244e19;
                            }
                            case "final_int" -> {
                                called[9] = true;
                                yield 0x011004da;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public long get(final String name, final long val) {
                        return switch (name) {
                            case "long_" -> {
                                called[10] = true;
                                yield 0x0b8101d84aa31711L;
                            }
                            case "final_long" -> {
                                called[11] = true;
                                yield 0x30558aa7189ed821L;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public float get(final String name, final float val) {
                        return switch (name) {
                            case "float_" -> {
                                called[12] = true;
                                yield 0x5.01923ap18f;
                            }
                            case "final_float" -> {
                                called[13] = true;
                                yield 0x0.882afap1f;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public double get(final String name, final double val) {
                        return switch (name) {
                            case "double_" -> {
                                called[14] = true;
                                yield 0x9.4a8fp6;
                            }
                            case "final_double" -> {
                                called[15] = true;
                                yield 0xf.881a8p4;
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }

                    public Object get(final String name, final Object val) {
                        return switch (name) {
                            case "ser" -> {
                                called[16] = true;
                                yield new Ser3(0x44cc55dd);
                            }
                            case "final_ser" -> {
                                called[17] = true;
                                yield new Ser3(0x9a8b7c6d);
                            }
                            default -> throw new Error("Unexpected field " + name);
                        };
                    }
                };
            }
        };
        // all the same methods, except for `writeFields`
        Arrays.fill(called, false);
        Constructor<?> ctor = factory.newConstructorForSerialization(Ser2.class, Object.class.getDeclaredConstructor());
        ser = (Ser2) ctor.newInstance();
        readObject.invokeExact(ser, ois);
        // excluding "writeFields", so 18 instead of 19
        for (int i = 0; i < 18; i ++) {
            Assert.assertTrue(called[i], names[i]);
        }
        Assert.assertEquals(ser.byte_, (byte)0x11);
        Assert.assertEquals(ser.final_byte, (byte)0x9f);
        Assert.assertEquals(ser.char_, (char)0x59a2);
        Assert.assertEquals(ser.final_char, (char)0xe0d0);
        Assert.assertEquals(ser.short_, (short)0x0917);
        Assert.assertEquals(ser.final_short, (short)0x110e);
        Assert.assertEquals(ser.int_, 0xd0244e19);
        Assert.assertEquals(ser.final_int, 0x011004da);
        Assert.assertEquals(ser.long_, 0x0b8101d84aa31711L);
        Assert.assertEquals(ser.final_long, 0x30558aa7189ed821L);
        Assert.assertEquals(ser.float_, 0x5.01923ap18f);
        Assert.assertEquals(ser.final_float, 0x0.882afap1f);
        Assert.assertEquals(ser.double_, 0x9.4a8fp6);
        Assert.assertEquals(ser.final_double, 0xf.881a8p4);
        Assert.assertEquals(ser.ser, new Ser3(0x44cc55dd));
        Assert.assertEquals(ser.final_ser, new Ser3(0x9a8b7c6d));
    }

    static class Ser2 implements Serializable {
        @Serial
        private static final long serialVersionUID = -2852896623833548574L;

        byte byte_;
        short short_;
        char char_;
        int int_;
        long long_;
        float float_;
        double double_;
        boolean boolean_;
        Ser3 ser;

        final byte final_byte;
        final short final_short;
        final char final_char;
        final int final_int;
        final long final_long;
        final float final_float;
        final double final_double;
        final boolean final_boolean;
        final Ser3 final_ser;

        Ser2(final byte final_byte, final short final_short, final char final_char, final int final_int,
            final long final_long, final float final_float, final double final_double,
            final boolean final_boolean, final Ser3 final_ser) {

            this.final_byte = final_byte;
            this.final_short = final_short;
            this.final_char = final_char;
            this.final_int = final_int;
            this.final_long = final_long;
            this.final_float = final_float;
            this.final_double = final_double;
            this.final_boolean = final_boolean;
            this.final_ser = final_ser;
        }
    }

    static class Ser3 implements Serializable {
        @Serial
        private static final long serialVersionUID = -1234752876749422678L;

        @Serial
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("value", int.class)
        };

        final int value;

        Ser3(final int value) {
            this.value = value;
        }

        public boolean equals(final Object obj) {
            return obj instanceof Ser3 s && value == s.value;
        }

        public int hashCode() {
            return value;
        }
    }

    static class SerInvalidFields implements Serializable {
        // this is deliberately wrong
        @SuppressWarnings({"unused", "serial"})
        @Serial
        private static final String serialPersistentFields = "Oops!";
        @Serial
        private static final long serialVersionUID = -8090960816811629489L;
    }

    static class Ext1 implements Externalizable {

        @Serial
        private static final long serialVersionUID = 7109990719266285013L;

        public void writeExternal(final ObjectOutput objectOutput) {
        }

        public void readExternal(final ObjectInput objectInput) {
        }
    }

    static class Ext2 implements Externalizable {
        public void writeExternal(final ObjectOutput objectOutput) {
        }

        public void readExternal(final ObjectInput objectInput) {
        }
    }

    record Rec1(int hello, boolean world) implements Serializable {
        @Serial
        private static final long serialVersionUID = 12349876L;
    }

    enum Enum1 {
        hello,
        world,
        ;
        private static final long serialVersionUID = 1020304050L;
    }

    interface Proxy1 {
        void hello();
    }

    static class SerialPersistentFields implements Serializable {
        @Serial
        private static final long serialVersionUID = -4947917866973382882L;
        @Serial
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("array1", Object[].class),
            new ObjectStreamField("nonExistent", String.class)
        };

        private int int1;
        private Object[] array1;
    }

    // Check our simple accessors
    @Test
    static void testAccessors() {
        Assert.assertEquals(factory.serialPersistentFields(Ser3.class), Ser3.serialPersistentFields);
        Assert.assertNotSame(factory.serialPersistentFields(Ser3.class), Ser3.serialPersistentFields);
        Assert.assertNull(factory.serialPersistentFields(SerInvalidFields.class));
    }

    // Ensure that classes with serialPersistentFields do not allow default setting/getting
    @Test
    static void testDisallowed() {
        Assert.assertNull(factory.defaultWriteObjectForSerialization(SerialPersistentFields.class));
        Assert.assertNull(factory.defaultReadObjectForSerialization(SerialPersistentFields.class));
    }

    // Main can be used to run the tests from the command line with only testng.jar.
    @SuppressWarnings("raw_types")
    @Test(enabled = false)
    public static void main(String[] args) {
        Class<?>[] testclass = {ReflectionFactoryTest.class};
        TestNG testng = new TestNG();
        testng.setTestClasses(testclass);
        testng.run();
    }
}
