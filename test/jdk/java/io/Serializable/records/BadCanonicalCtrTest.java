/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary InvalidClassException is thrown when the canonical constructor
 *          cannot be found during deserialization.
 * @library /test/lib
 * @enablePreview
 * @run testng BadCanonicalCtrTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.constant.MethodTypeDesc;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.ByteCodeLoader;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Checks that an InvalidClassException is thrown when the canonical
 * constructor cannot be found during deserialization.
 */
public class BadCanonicalCtrTest {

    // ClassLoader for creating instances of the records to test with.
    ClassLoader goodRecordClassLoader;
    // ClassLoader that can be used during deserialization. Loads record
    // classes where the canonical constructor has been removed.
    ClassLoader missingCtrClassLoader;
    // ClassLoader that can be used during deserialization. Loads record
    // classes where the canonical constructor has been tampered with.
    ClassLoader nonCanonicalCtrClassLoader;

    /**
     * Generates the serializable record classes used by the test. First creates
     * the initial bytecode for the record classes using javac, then removes or
     * modifies the generated canonical constructor.
     */
    @BeforeTest
    public void setup() {
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("R1",
                    "public record R1 () implements java.io.Serializable { }");
            goodRecordClassLoader = new ByteCodeLoader("R1", byteCode, BadCanonicalCtrTest.class.getClassLoader());
            byte[] bc1 = removeConstructor(byteCode);
            missingCtrClassLoader = new ByteCodeLoader("R1", bc1, BadCanonicalCtrTest.class.getClassLoader());
            byte[] bc2 = modifyConstructor(byteCode);
            nonCanonicalCtrClassLoader = new ByteCodeLoader("R1", bc2, BadCanonicalCtrTest.class.getClassLoader());
        }
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("R2",
                    "public record R2 (int x, int y) implements java.io.Serializable { }");
            goodRecordClassLoader = new ByteCodeLoader("R2", byteCode, goodRecordClassLoader);
            byte[] bc1 = removeConstructor(byteCode);
            missingCtrClassLoader = new ByteCodeLoader("R2", bc1, missingCtrClassLoader);
            byte[] bc2 = modifyConstructor(byteCode);
            nonCanonicalCtrClassLoader = new ByteCodeLoader("R2", bc2, nonCanonicalCtrClassLoader);
        }
        {
            byte[] byteCode = InMemoryJavaCompiler.compile("R3",
                    "public record R3 (long l) implements java.io.Externalizable {" +
                    "    public void writeExternal(java.io.ObjectOutput out) { }" +
                    "    public void readExternal(java.io.ObjectInput in)    { } }");
            goodRecordClassLoader = new ByteCodeLoader("R3", byteCode, goodRecordClassLoader);
            byte[] bc1 = removeConstructor(byteCode);
            missingCtrClassLoader = new ByteCodeLoader("R3", bc1, missingCtrClassLoader);
            byte[] bc2 = modifyConstructor(byteCode);
            nonCanonicalCtrClassLoader = new ByteCodeLoader("R3", bc2, nonCanonicalCtrClassLoader);
        }
    }

    /** Constructs a new instance of record R1. */
    Object newR1() throws Exception {
        Class<?> c = Class.forName("R1", true, goodRecordClassLoader);
        assert c.isRecord();
        assert c.getRecordComponents() != null;
        return c.getConstructor().newInstance();
    }

    /** Constructs a new instance of record R2. */
    Object newR2(int x, int y) throws Exception{
        Class<?> c = Class.forName("R2", true, goodRecordClassLoader);
        assert c.isRecord();
        assert c.getRecordComponents().length == 2;
        return c.getConstructor(int.class, int.class).newInstance(x, y);
    }

    /** Constructs a new instance of record R3. */
    Object newR3(long l) throws Exception {
        Class<?> c = Class.forName("R3", true, goodRecordClassLoader);
        assert c.isRecord();
        assert c.getRecordComponents().length == 1;
        return c.getConstructor(long.class).newInstance(l);
    }

    @DataProvider(name = "recordInstances")
    public Object[][] recordInstances() throws Exception {
        return new Object[][] {
                new Object[] { newR1()        },
                new Object[] { newR2(19, 20)  },
                new Object[] { newR3(67L)     },
        };
    }

    static final Class<InvalidClassException> ICE = InvalidClassException.class;

    /**
     * Tests that InvalidClassException is thrown when no constructor is
     * present.
     */
    @Test(dataProvider = "recordInstances")
    public void missingConstructorTest(Object objToSerialize) throws Exception {
        out.println("\n---");
        out.println("serializing : " + objToSerialize);
        byte[] bytes = serialize(objToSerialize);
        out.println("deserializing");
        InvalidClassException ice = expectThrows(ICE, () -> deserialize(bytes, missingCtrClassLoader));
        out.println("caught expected ICE: " + ice);
        assertTrue(ice.getMessage().contains("record canonical constructor not found"));
    }

    /**
     * Tests that InvalidClassException is thrown when the canonical
     * constructor is not present. ( a non-canonical constructor is
     * present ).
     */
    @Test(dataProvider = "recordInstances")
    public void nonCanonicalConstructorTest(Object objToSerialize) throws Exception {
        out.println("\n---");
        out.println("serializing : " + objToSerialize);
        byte[] bytes = serialize(objToSerialize);
        out.println("deserializing");
        InvalidClassException ice = expectThrows(ICE, () -> deserialize(bytes, nonCanonicalCtrClassLoader));
        out.println("caught expected ICE: " + ice);
        assertTrue(ice.getMessage().contains("record canonical constructor not found"));
    }

    <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    <T> T deserialize(byte[] streamBytes, ClassLoader cl)
        throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
        ObjectInputStream ois  = new ObjectInputStream(bais) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
                    throws ClassNotFoundException {
                return Class.forName(desc.getName(), false, cl);
            }
        };
        return (T) ois.readObject();
    }

    // -- machinery for augmenting record class bytes --

    /**
     * Removes the constructor from the given class bytes.
     * Assumes just a single, canonical, constructor.
     */
    static byte[] removeConstructor(byte[] classBytes) {
        var cf = ClassFile.of();
        return cf.transformClass(cf.parse(classBytes), ClassTransform.dropping(ce ->
                ce instanceof MethodModel mm && mm.methodName().equalsString(INIT_NAME)));
    }

    /**
     * Modifies the descriptor of the constructor from the given class bytes.
     * Assumes just a single, canonical, constructor.
     */
    static byte[] modifyConstructor(byte[] classBytes) {
        var cf = ClassFile.of();
        return cf.transformClass(cf.parse(classBytes), ClassTransform.dropping(ce ->
                        ce instanceof MethodModel mm && mm.methodName().equalsString(INIT_NAME))
                .andThen(ClassTransform.endHandler(clb -> clb.withMethodBody(INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_Object), ACC_PUBLIC, cob -> {
                            cob.aload(0);
                            cob.invokespecial(Record.class.describeConstable().orElseThrow(),
                                    INIT_NAME, MTD_void);
                            cob.return_();
                        }))));
    }
}
