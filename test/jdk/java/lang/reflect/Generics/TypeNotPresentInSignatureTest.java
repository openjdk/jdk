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
 * @summary Test behaviors with Signature attribute with any absent
 *          class or interface
 * @library /test/lib
 * @modules java.base/jdk.internal.classfile.components
 * @compile MalformedSignatureTest.java
 * @comment reuses Sample classes from MalformedSignatureTest
 * @run junit TypeNotPresentInSignatureTest
 */

import jdk.internal.classfile.components.ClassRemapper;
import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.TypeVariable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TypeNotPresentInSignatureTest {

    static Class<?> sampleClass, sampleRecord;

    @BeforeAll
    static void setup() throws Exception {
        var compiledDir = Path.of(System.getProperty("test.classes"));
        var cf = ClassFile.of();

        var reDesc = ClassDesc.of("java.lang.RuntimeException");
        var fix = ClassRemapper.of(Map.of(reDesc, ClassDesc.of("does.not.Exist")));
        var f2 = ClassTransform.transformingMethods((mb, me) -> {
            if (me instanceof ExceptionsAttribute) {
                mb.with(ExceptionsAttribute.ofSymbols(reDesc));
            } else {
                mb.with(me);
            }
        });

        var plainBytes = cf.transformClass(cf.parse(compiledDir.resolve("SampleClass.class")), fix);
        plainBytes = cf.transformClass(cf.parse(plainBytes), f2);
        sampleClass = ByteCodeLoader.load("SampleClass", plainBytes);
        var recordBytes = cf.transformClass(cf.parse(compiledDir.resolve("SampleRecord.class")), fix);
        recordBytes = cf.transformClass(cf.parse(recordBytes), f2);
        sampleRecord = ByteCodeLoader.load("SampleRecord", recordBytes);
    }


    @Test
    void testClass() {
        assertEquals(ArrayList.class, sampleClass.getSuperclass());
        assertArrayEquals(new Class<?>[] {Predicate.class}, sampleClass.getInterfaces());
        assertThrows(TypeNotPresentException.class, sampleClass::getGenericSuperclass);
        assertThrows(TypeNotPresentException.class, sampleClass::getGenericInterfaces);
    }

    @Test
    void testField() throws ReflectiveOperationException {
        var field = sampleClass.getDeclaredField("field");
        assertEquals(Optional.class, field.getType());
        assertThrows(TypeNotPresentException.class, field::getGenericType);
    }

    @Test
    void testConstructor() throws ReflectiveOperationException {
        var constructor = sampleClass.getDeclaredConstructor(Optional.class);
        assertArrayEquals(new Class<?>[] {Optional.class}, constructor.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, constructor.getExceptionTypes());
        assertThrows(TypeNotPresentException.class, constructor::getGenericParameterTypes);
        var typeVar = (TypeVariable<?>) constructor.getGenericExceptionTypes()[0];
        assertThrows(TypeNotPresentException.class, typeVar::getBounds);
    }

    @Test
    void testMethod() throws ReflectiveOperationException {
        var method = sampleClass.getDeclaredMethod("method", Optional.class);
        assertEquals(Optional.class, method.getReturnType());
        assertArrayEquals(new Class<?>[] {Optional.class}, method.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, method.getExceptionTypes());
        assertThrows(TypeNotPresentException.class, method::getGenericReturnType);
        assertThrows(TypeNotPresentException.class, method::getGenericParameterTypes);
        var typeVar = (TypeVariable<?>) method.getGenericExceptionTypes()[0];
        assertThrows(TypeNotPresentException.class, typeVar::getBounds);
    }

    @Test
    void testRecordComponent() {
        var rcs = sampleRecord.getRecordComponents();
        assertNotNull(rcs);
        assertEquals(1, rcs.length);
        var rc = rcs[0];
        assertNotNull(rc);

        assertEquals(Optional.class, rc.getType());
        assertEquals("Ljava/util/Optional<Ldoes/not/Exist;>;", rc.getGenericSignature());
        assertThrows(TypeNotPresentException.class, rc::getGenericType);
    }
}