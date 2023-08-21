/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8313961
 * @summary Checks that the search for magic methods does not depend on the
 * specification of Class.getDeclared[Field|Method]() when it chooses the
 * reflective object to return.
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.constantpool
 */

package java.io;

import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.TypeKind;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.reflect.AccessFlag.PUBLIC;
import static jdk.internal.classfile.Classfile.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultiMagicTest {

    /*
     * This test class generated, loads, and instantiate a Serializable object
     * to check the fix explained in the JBS issue.
     *
     * The generated classes cannot be described in Java, because it does not
     * admit multiple fields with the same name but different types, nor does it
     * admit multiple methods with the same name and same parameter types but
     * different return types.
     * The pseudo-Java classes are the following
     *
    public class SuperMultiMagic implements Serializable {
        public SuperMultiMagic() {
        }
        public Integer writeReplace() {
            return null;
        }
        public Object writeReplace() {
            return null;
        }
    }

    public class MultiMagic extends SuperMultiMagic {
        private static final int serialPersistentFields = 0;
        private static final ObjectStreamField[] serialPersistentFields =
            new ObjectStreamField[0];

        public MultiMagic() {
        }
        private int writeObject(ObjectOutputStream oos) {
            return 0;
        }
        private void writeObject(ObjectOutputStream oos) {
        }
        public Integer writeReplace() {
            return null;
        }
    }
     *
     */

    private static final String SUPER_MULTI_MAGIC_CLS_NAME = "SuperMultiMagic";
    private static final String MULTI_MAGIC_CLS_NAME = "MultiMagic";

    private static void addSuperConstructor(ClassBuilder cb) {
        cb.withMethod(INIT_NAME,
            MTD_void,
            ACC_PUBLIC,
            mb -> mb.withCode(
                b -> b.aload(0)
                    .invokespecial(CD_Object, INIT_NAME, MTD_void)
                    .return_()));
    }

    private static void addClassInit(ClassBuilder cb) {
        cb.withMethod(CLASS_INIT_NAME,
            MTD_void,
            ACC_STATIC,
            mb -> mb.withCode(
                b -> b.iconst_0()
                    .putstatic(cb.constantPool().fieldRefEntry(
                        ClassDesc.of("MultiMagic"),
                        "serialPersistentFields",
                        CD_int))

                    .iconst_0()
                    .anewarray(ClassDesc.ofDescriptor(
                        ObjectStreamField.class.descriptorString()))
                    .putstatic(cb.constantPool().fieldRefEntry(
                        ClassDesc.of("MultiMagic"),
                        "serialPersistentFields",
                        ClassDesc.ofDescriptor(
                            ObjectStreamField[].class.descriptorString())))

                    .return_()));
    }

    private static void addConstructor(ClassBuilder cb) {
        cb.withMethod(INIT_NAME,
            MTD_void,
            ACC_PUBLIC,
            mb -> mb.withCode(
                b -> b.aload(0)
                    .invokespecial(ClassDesc.of(
                        SUPER_MULTI_MAGIC_CLS_NAME), INIT_NAME, MTD_void)
                    .return_()));
    }

    private static void addOtherSerialPersistentFields(ClassBuilder cb) {
        cb.withField("serialPersistentFields",
            CD_int,
            ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
    }

    private static void addMagicSerialPersistentFields(ClassBuilder cb) {
        cb.withField("serialPersistentFields",
            ClassDesc.ofDescriptor(ObjectStreamField[].class.descriptorString()),
            ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
    }

    private static void addOtherWriteObject(ClassBuilder cb) {
        cb.withMethod("writeObject",
            MethodTypeDesc.of(CD_int, ClassDesc.of(
                ObjectOutputStream.class.getName())),
            ACC_PRIVATE,
            mb -> mb.withCode(
                b -> b.iconst_0()
                    .returnInstruction(TypeKind.IntType)));
    }

    private static void addMagicWriteObject(ClassBuilder cb) {
        cb.withMethod("writeObject",
            MethodTypeDesc.of(CD_void, ClassDesc.of(
                ObjectOutputStream.class.getName())),
            ACC_PRIVATE,
            mb -> mb.withCode(CodeBuilder::return_));
    }

    private static void addOtherWriteReplace(ClassBuilder cb) {
        cb.withMethod("writeReplace",
            MethodTypeDesc.of(CD_Integer),
            ACC_PUBLIC,
            mb -> mb.withCode(
                b -> b.aconst_null()
                    .returnInstruction(TypeKind.ReferenceType)));
    }

    private static void addMagicWriteReplace(ClassBuilder cb) {
        cb.withMethod("writeReplace",
            MethodTypeDesc.of(CD_Object),
            ACC_PUBLIC,
            mb -> mb.withCode(
                b -> b.aconst_null()
                    .returnInstruction(TypeKind.ReferenceType)));
    }

    private static Object generateLoadInstantiate()
        throws ReflectiveOperationException {
        byte[] superClassFile = Classfile.of()
            .build(ClassDesc.of(SUPER_MULTI_MAGIC_CLS_NAME),
            cb -> {
                cb.withFlags(PUBLIC)
                    .withInterfaceSymbols(List.of(ClassDesc.of(
                        Serializable.class.getName())));
                addSuperConstructor(cb);
                addOtherWriteReplace(cb);
                addMagicWriteReplace(cb);
            });
        byte[] classFile = Classfile.of().build(ClassDesc.of(MULTI_MAGIC_CLS_NAME),
            cb -> {
                cb.withFlags(PUBLIC)
                    .withSuperclass(ClassDesc.of(SUPER_MULTI_MAGIC_CLS_NAME));
                addOtherSerialPersistentFields(cb);
                addMagicSerialPersistentFields(cb);
                addClassInit(cb);
                addConstructor(cb);
                addOtherWriteObject(cb);
                addMagicWriteObject(cb);
                addOtherWriteReplace(cb);
            });

        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) {
                if (SUPER_MULTI_MAGIC_CLS_NAME.equals(name)) {
                    return super.defineClass(name, superClassFile, 0, superClassFile.length);
                }
                if (MULTI_MAGIC_CLS_NAME.equals(name)) {
                    return super.defineClass(name, classFile, 0, classFile.length);
                }
                throw new AssertionError();
            }
        };
        return loader.loadClass(MULTI_MAGIC_CLS_NAME)
            .getConstructor(new Class<?>[0])
            .newInstance();
    }

    @Test
    public void test() throws ReflectiveOperationException {
        Object multiMagicInstance = generateLoadInstantiate();
        Class<?> multiMagicClass = multiMagicInstance.getClass();
        ObjectStreamClass osc = ObjectStreamClass.lookup(multiMagicClass);

        assertEquals(ObjectStreamField[].class, osc.getFields().getClass());
        assertEquals(0, osc.getFields().length);
        assertTrue(osc.hasWriteObjectMethod());
        assertTrue(osc.hasWriteReplaceMethod());
    }

}
