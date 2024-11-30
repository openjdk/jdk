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
 * @summary Basic tests for prohibited magic serialPersistentFields
 * @library /test/lib
 * @enablePreview
 * @run testng SerialPersistentFieldsTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.FieldModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Checks that the serialPersistentFields declaration is effectively ignored.
 */
public class SerialPersistentFieldsTest {

    ClassLoader serializableRecordLoader;

    /**
     * Generates the serializable record classes used by the test. First creates
     * the initial bytecode for the record class using javac, then adds the
     * prohibited serialization magic field. Effectively, for example:
     *
     *   record R () implements Serializable {
     *       private static final ObjectStreamField[] serialPersistentFields = {
     *           new ObjectStreamField("s", String.class),
     *           new ObjectStreamField("i", int.class),
     *           new ObjectStreamField("l", long.class),
     *       };
     *   }
     */
    @BeforeTest
    public void setup() {
        {  // R1
            byte[] byteCode = InMemoryJavaCompiler.compile("R1",
                    "public record R1 () implements java.io.Serializable { }");
            ObjectStreamField[] serialPersistentFields = {
                    new ObjectStreamField("s", String.class),
                    new ObjectStreamField("i", int.class),
                    new ObjectStreamField("l", long.class),
                    new ObjectStreamField("d", double.class)
            };
            byteCode = addSerialPersistentFields(byteCode, serialPersistentFields);
            serializableRecordLoader = new ByteCodeLoader("R1", byteCode, SerialPersistentFieldsTest.class.getClassLoader());
        }
        {  // R2
            byte[] byteCode = InMemoryJavaCompiler.compile("R2",
                    "public record R2 (int x) implements java.io.Serializable { }");
            ObjectStreamField[] serialPersistentFields = {
                    new ObjectStreamField("s", String.class)
            };
            byteCode = addSerialPersistentFields(byteCode, serialPersistentFields);
            serializableRecordLoader = new ByteCodeLoader("R2", byteCode, serializableRecordLoader);
        }
        {  // R3
            byte[] byteCode = InMemoryJavaCompiler.compile("R3",
                    "public record R3 (int x, int y) implements java.io.Serializable { }");
            ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
            byteCode = addSerialPersistentFields(byteCode, serialPersistentFields);
            serializableRecordLoader = new ByteCodeLoader("R3", byteCode, serializableRecordLoader);
        }
        {  // R4
            byte[] byteCode = InMemoryJavaCompiler.compile("R4",
                    "import java.io.Serializable;" +
                    "public record R4<U extends Serializable,V extends Serializable>(U u, V v) implements Serializable { }");
            ObjectStreamField[] serialPersistentFields = {
                    new ObjectStreamField("v", String.class)
            };
            byteCode = addSerialPersistentFields(byteCode, serialPersistentFields);
            serializableRecordLoader = new ByteCodeLoader("R4", byteCode, serializableRecordLoader);
        }
        {  // R5  -- Externalizable
            byte[] byteCode = InMemoryJavaCompiler.compile("R5",
                    "import java.io.*;" +
                    "public record R5 (int x) implements Externalizable {" +
                    "    @Override public void writeExternal(ObjectOutput out) {\n" +
                    "        throw new AssertionError(\"should not reach here\");\n" +
                    "    }\n" +
                    "    @Override public void readExternal(ObjectInput in) {\n" +
                    "        throw new AssertionError(\"should not reach here\");\n" +
                    "    }  }");
            ObjectStreamField[] serialPersistentFields = {
                    new ObjectStreamField("v", String.class)
            };
            byteCode = addSerialPersistentFields(byteCode, serialPersistentFields);
            serializableRecordLoader = new ByteCodeLoader("R5", byteCode, serializableRecordLoader);
        }
    }

    /** Constructs a new instance of given named record, with the given args. */
    Object newRecord(String name, Class<?>[] pTypes, Object[] args) {
        try {
            Class<?> c = Class.forName(name, true, serializableRecordLoader);
            assert c.isRecord();
            assert c.getRecordComponents() != null;
            return c.getConstructor(pTypes).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    Object newR1() {
        return newRecord("R1", null, null);
    }
    Object newR2(int x) {
        return newRecord("R2", new Class[]{int.class}, new Object[]{x});
    }
    Object newR3(int x, int y) {
        return newRecord("R3", new Class[]{int.class, int.class}, new Object[]{x, y});
    }
    Object newR4(Serializable u, Serializable v) {
        return newRecord("R4", new Class[]{Serializable.class, Serializable.class}, new Object[]{u,v});
    }
    Object newR5(int x) {
        return newRecord("R5", new Class[]{int.class}, new Object[]{x});
    }

    @DataProvider(name = "recordInstances")
    public Object[][] recordInstances() {
        return new Object[][] {
            new Object[] { newR1()                                },
            new Object[] { newR2(5)                               },
            new Object[] { newR3(7, 8)                            },
            new Object[] { newR4("str", BigDecimal.valueOf(4567)) },
            new Object[] { newR5(9)                               },
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

    // -- machinery for augmenting a record class with prohibited serial field --

    static byte[] addSerialPersistentFields(byte[] classBytes,
                                            ObjectStreamField[] spf) {
        var cf = ClassFile.of();
        var model = cf.parse(classBytes);
        return cf.transformClass(model, new SerialPersistentFieldsVisitor(model.thisClass().asSymbol(), spf));
    }

    /** A visitor that adds a serialPersistentFields field, and assigns it in clinit. */
    static final class SerialPersistentFieldsVisitor implements ClassTransform {
        static final String FIELD_NAME = "serialPersistentFields";
        static final ClassDesc CD_ObjectStreamField = ObjectStreamField.class.describeConstable().orElseThrow();
        static final ClassDesc FIELD_DESC = CD_ObjectStreamField.arrayType();
        final ObjectStreamField[] spf;
        final ClassDesc className;
        SerialPersistentFieldsVisitor(ClassDesc className, ObjectStreamField[] spf) {
            this.className = className;
            this.spf = spf;
        }

        @Override
        public void accept(ClassBuilder builder, ClassElement element) {
            if (element instanceof FieldModel fieldModel) {
                var name = fieldModel.fieldName().stringValue();
                assert !name.equals(FIELD_NAME) : "Unexpected " + FIELD_NAME + " field";
                builder.accept(fieldModel);
            } else {
                builder.accept(element);
            }
        }

        @Override
        public void atEnd(ClassBuilder builder) {
            builder.withField(FIELD_NAME, FIELD_DESC, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
            builder.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
                cob.bipush(spf.length);
                cob.anewarray(CD_ObjectStreamField);

                for (int i = 0; i < spf.length; i++) {
                    ObjectStreamField osf = spf[i];
                    cob.dup();
                    cob.bipush(i);
                    cob.new_(CD_ObjectStreamField);
                    cob.dup();
                    cob.loadConstant(osf.getName());
                    if (osf.isPrimitive()) {
                        cob.loadConstant(DynamicConstantDesc.ofNamed(
                                ConstantDescs.BSM_PRIMITIVE_CLASS, String.valueOf(osf.getTypeCode()), CD_Class));
                    } else {
                        // Currently Classfile API cannot encode primitive classdescs as condy
                        cob.loadConstant(osf.getType().describeConstable().orElseThrow());
                    }
                    cob.invokespecial(CD_ObjectStreamField, INIT_NAME, MethodTypeDesc.of(CD_void, CD_String, CD_Class));
                    cob.aastore();
                }

                cob.putstatic(className, FIELD_NAME, FIELD_DESC);
                cob.return_();
            });
        }
    }

    // -- infra sanity --

    /** Checks to ensure correct operation of the test's generation logic. */
    @Test(dataProvider = "recordInstances")
    public void wellFormedGeneratedClasses(Object obj) throws Exception {
        out.println("\n---");
        out.println(obj);
        Field f = obj.getClass().getDeclaredField("serialPersistentFields");
        assertTrue((f.getModifiers() & Modifier.PRIVATE) != 0);
        assertTrue((f.getModifiers() & Modifier.STATIC) != 0);
        assertTrue((f.getModifiers() & Modifier.FINAL) != 0);
        f.setAccessible(true);
        ObjectStreamField[] fv = (ObjectStreamField[])f.get(obj);
        assertTrue(fv != null, "Unexpected null value");
        assertTrue(fv.length >= 0, "Unexpected negative length:" + fv.length);
    }
}
