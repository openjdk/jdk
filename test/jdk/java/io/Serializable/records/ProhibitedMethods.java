/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for prohibited magic serialization methods
 * @library /test/lib
 * @enablePreview
 * @run testng ProhibitedMethods
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
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.ByteCodeLoader;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

/**
 * Checks that the various prohibited Serialization magic methods, and
 * Externalizable methods, are not invoked ( effectively ignored ) for
 * record objects.
 */
public class ProhibitedMethods {

    public interface ThrowingExternalizable extends Externalizable {
        default void writeExternal(ObjectOutput out) {
            fail("should not reach here");
        }
        default void readExternal(ObjectInput in) {
            fail("should not reach here");
        }
    }

    record Wibble () implements ThrowingExternalizable { }

    record Wobble (long l) implements ThrowingExternalizable { }

    record Wubble (Wobble wobble, Wibble wibble, String s) implements ThrowingExternalizable { }

    ClassLoader serializableRecordLoader;

    /**
     * Generates the serializable record classes used by the test. First creates
     * the initial bytecode for the record classes using javac, then adds the
     * prohibited magic serialization methods. For example, effectively generates:
     *
     *   public record Foo () implements Serializable {
     *       private void writeObject(ObjectOutputStream out)             {
     *           fail("writeObject should not be invoked");               }
     *       private void readObject(ObjectInputStream in)                {
     *           fail("readObject should not be invoked");                }
     *       private void readObjectNoData()                              {
     *           fail("readObjectNoData should not be invoked");          }
     *   }
     */
    @BeforeTest
    public void setup() {
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("Foo",
                    "public record Foo () implements java.io.Serializable { }");
            byteCode = addWriteObject(byteCode);
            byteCode = addReadObject(byteCode);
            byteCode = addReadObjectNoData(byteCode);
            serializableRecordLoader = new ByteCodeLoader("Foo", byteCode, ProhibitedMethods.class.getClassLoader());
        }
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("Bar",
                    "public record Bar (int x, int y) implements java.io.Serializable { }");
            byteCode = addWriteObject(byteCode);
            byteCode = addReadObject(byteCode);
            byteCode = addReadObjectNoData(byteCode);
            serializableRecordLoader = new ByteCodeLoader("Bar", byteCode, serializableRecordLoader);
        }
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("Baz",
                    "import java.io.Serializable;" +
                    "public record Baz<U extends Serializable,V extends Serializable>(U u, V v) implements Serializable { }");
            byteCode = addWriteObject(byteCode);
            byteCode = addReadObject(byteCode);
            byteCode = addReadObjectNoData(byteCode);
            serializableRecordLoader = new ByteCodeLoader("Baz", byteCode, serializableRecordLoader);
        }
    }

    /** Constructs a new instance of record Foo. */
    Object newFoo() {
        try {
            Class<?> c = Class.forName("Foo", true, serializableRecordLoader);
            assert c.isRecord();
            assert c.getRecordComponents() != null;
            return c.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Constructs a new instance of record Bar. */
    Object newBar(int x, int y) {
        try {
            Class<?> c = Class.forName("Bar", true, serializableRecordLoader);
            assert c.isRecord();
            assert c.getRecordComponents().length == 2;
            return c.getConstructor(int.class, int.class).newInstance(x, y);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Constructs a new instance of record Baz. */
    Object newBaz(Object u, Object v) {
        try {
            Class<?> c = Class.forName("Baz", true, serializableRecordLoader);
            assert c.isRecord();
            assert c.getRecordComponents().length == 2;
            return c.getConstructor(Serializable.class, Serializable.class).newInstance(u, v);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @DataProvider(name = "recordInstances")
    public Object[][] recordInstances() {
        return new Object[][] {
            new Object[] { newFoo()                                           },
            new Object[] { newBar(19, 20)                                     },
            new Object[] { newBaz("str", BigDecimal.valueOf(8765))            },
            new Object[] { new Wibble()                                       },
            new Object[] { new Wobble(1000L)                                  },
            new Object[] { new Wubble(new Wobble(9999L), new Wibble(), "str") },
        };
    }

    @Test(dataProvider = "recordInstances")
    public void roundTrip(Object objToSerialize) throws Exception {
        out.println("\n---");
        out.println("serializing : " + objToSerialize);
        var objDeserialized = serializeDeserialize(objToSerialize);
        out.println("deserialized: " + objDeserialized);
        assertEquals(objToSerialize, objDeserialized);
        assertEquals(objDeserialized, objToSerialize);
    }

    <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    <T> T deserialize(byte[] streamBytes)
        throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
        ObjectInputStream ois  = new ObjectInputStream(bais) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
                    throws ClassNotFoundException {
                return Class.forName(desc.getName(), false, serializableRecordLoader);
            }
        };
        return (T) ois.readObject();
    }

    <T> T serializeDeserialize(T obj)
        throws IOException, ClassNotFoundException
    {
        return deserialize(serialize(obj));
    }

    // -- machinery for augmenting record classes with prohibited serial methods --

    static final String WRITE_OBJECT_NAME = "writeObject";
    static final MethodTypeDesc WRITE_OBJECT_DESC = MethodTypeDesc.ofDescriptor("(Ljava/io/ObjectOutputStream;)V");

    static byte[] addWriteObject(byte[] classBytes) {
        return addMethod(classBytes, WRITE_OBJECT_NAME, WRITE_OBJECT_DESC);
    }

    static final String READ_OBJECT_NAME = "readObject";
    static final MethodTypeDesc READ_OBJECT_DESC = MethodTypeDesc.ofDescriptor("(Ljava/io/ObjectInputStream;)V");

    static byte[] addReadObject(byte[] classBytes) {
        return addMethod(classBytes, READ_OBJECT_NAME, READ_OBJECT_DESC);
    }

    static final String READ_OBJECT_NO_DATA_NAME = "readObjectNoData";
    static final MethodTypeDesc READ_OBJECT_NO_DATA_DESC = MethodTypeDesc.of(CD_void);

    static byte[] addReadObjectNoData(byte[] classBytes) {
        return addMethod(classBytes, READ_OBJECT_NO_DATA_NAME, READ_OBJECT_NO_DATA_DESC);
    }

    static byte[] addMethod(byte[] classBytes,
                            String name, MethodTypeDesc desc) {
        var cf = ClassFile.of();
        return cf.transform(cf.parse(classBytes), ClassTransform.endHandler(clb -> {
            clb.withMethodBody(name, desc, ACC_PRIVATE, cob -> {
                cob.constantInstruction(name + " should not be invoked");
                cob.invokestatic(Assert.class.describeConstable().orElseThrow(), "fail",
                        MethodTypeDesc.of(CD_void, CD_String));
                cob.return_();
            });
        }));
    }

    // -- infra sanity --

    static final Class<ReflectiveOperationException> ROE = ReflectiveOperationException.class;

    /** Checks to ensure correct operation of the test's generation logic. */
    @Test
    public void wellFormedGeneratedClasses() throws Exception {
        out.println("\n---");
        for (Object obj : new Object[] { newFoo(), newBar(3, 4), newBaz(1,"s") }) {
            out.println(obj);
            {   // writeObject
                Method m = obj.getClass().getDeclaredMethod("writeObject", ObjectOutputStream.class);
                assertTrue((m.getModifiers() & Modifier.PRIVATE) != 0);
                m.setAccessible(true);
                ReflectiveOperationException t = expectThrows(ROE, () ->
                        m.invoke(obj, new ObjectOutputStream(OutputStream.nullOutputStream())));
                Throwable assertionError = t.getCause();
                out.println("caught expected AssertionError: " + assertionError);
                assertTrue(assertionError instanceof AssertionError,
                           "Expected AssertionError, got:" + assertionError);
                assertEquals(assertionError.getMessage(), "writeObject should not be invoked");
            }
            {   // readObject
                Method m = obj.getClass().getDeclaredMethod("readObject", ObjectInputStream.class);
                assertTrue((m.getModifiers() & Modifier.PRIVATE) != 0);
                m.setAccessible(true);
                ReflectiveOperationException t = expectThrows(ROE, () ->
                        m.invoke(obj, new ObjectInputStream() {
                        }));
                Throwable assertionError = t.getCause();
                out.println("caught expected AssertionError: " + assertionError);
                assertTrue(assertionError instanceof AssertionError,
                           "Expected AssertionError, got:" + assertionError);
                assertEquals(assertionError.getMessage(), "readObject should not be invoked");
            }
            {   // readObjectNoData
                Method m = obj.getClass().getDeclaredMethod("readObjectNoData");
                assertTrue((m.getModifiers() & Modifier.PRIVATE) != 0);
                m.setAccessible(true);
                ReflectiveOperationException t = expectThrows(ROE, () -> m.invoke(obj));
                Throwable assertionError = t.getCause();
                out.println("caught expected AssertionError: " + assertionError);
                assertTrue(assertionError instanceof AssertionError,
                           "Expected AssertionError, got:" + assertionError);
                assertEquals(assertionError.getMessage(), "readObjectNoData should not be invoked");
            }
        }
    }
}
