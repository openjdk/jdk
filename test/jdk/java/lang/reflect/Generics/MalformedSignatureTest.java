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
 * @summary Test behaviors with malformed Signature attribute
 * @library /test/lib
 * @run junit MalformedSignatureTest
 */

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.reflect.GenericSignatureFormatError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MalformedSignatureTest {

    private static final String DUMMY_SIGNATURE_TEXT = "Not a signature";
    static Class<?> sampleClass, sampleRecord;

    @BeforeAll
    static void setup() throws Exception {
        var compiledDir = Path.of(System.getProperty("test.classes"));
        var cf = ClassFile.of();

        var badSignatureTransform = new ClassTransform() {
            private SignatureAttribute badSignature;

            @Override
            public void atStart(ClassBuilder builder) {
                badSignature = SignatureAttribute.of(builder.constantPool().utf8Entry(DUMMY_SIGNATURE_TEXT));
            }

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                switch (element) {
                    case SignatureAttribute sig -> {
                    } // dropping
                    case FieldModel f -> builder
                            .transformField(f, FieldTransform.dropping(SignatureAttribute.class::isInstance)
                                    .andThen(FieldTransform.endHandler(fb -> fb.with(badSignature))));
                    case MethodModel m -> builder
                            .transformMethod(m, MethodTransform.dropping(SignatureAttribute.class::isInstance)
                                    .andThen(MethodTransform.endHandler(fb -> fb.with(badSignature))));
                    case RecordAttribute rec -> builder.with(RecordAttribute.of(rec.components().stream().map(comp ->
                                    RecordComponentInfo.of(comp.name(), comp.descriptor(), Stream.concat(
                                                    Stream.of(badSignature), comp.attributes().stream()
                                                            .filter(Predicate.not(SignatureAttribute.class::isInstance)))
                                            .toList()))
                            .toList()));
                    default -> builder.with(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                builder.with(badSignature);
            }
        };

        var plainBytes = cf.transformClass(cf.parse(compiledDir.resolve("SampleClass.class")), badSignatureTransform);
        sampleClass = ByteCodeLoader.load("SampleClass", plainBytes);
        var recordBytes = cf.transformClass(cf.parse(compiledDir.resolve("SampleRecord.class")), badSignatureTransform);
        sampleRecord = ByteCodeLoader.load("SampleRecord", recordBytes);
    }

    @Test
    void testClass() {
        assertEquals(ArrayList.class, sampleClass.getSuperclass());
        assertArrayEquals(new Class<?>[] {Predicate.class}, sampleClass.getInterfaces());
        assertThrows(GenericSignatureFormatError.class, sampleClass::getGenericSuperclass);
        assertThrows(GenericSignatureFormatError.class, sampleClass::getGenericInterfaces);
    }

    @Test
    void testField() throws ReflectiveOperationException {
        var field = sampleClass.getDeclaredField("field");
        assertEquals(Optional.class, field.getType());
        assertThrows(GenericSignatureFormatError.class, field::getGenericType);
    }

    @Test
    void testConstructor() throws ReflectiveOperationException {
        var constructor = sampleClass.getDeclaredConstructors()[0];
        assertArrayEquals(new Class<?>[] {Optional.class}, constructor.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, constructor.getExceptionTypes());
        assertThrows(GenericSignatureFormatError.class, constructor::getGenericParameterTypes);
        assertThrows(GenericSignatureFormatError.class, constructor::getGenericExceptionTypes);
    }

    @Test
    void testMethod() throws ReflectiveOperationException {
        var method = sampleClass.getDeclaredMethods()[0];
        assertEquals(Optional.class, method.getReturnType());
        assertArrayEquals(new Class<?>[] {Optional.class}, method.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, method.getExceptionTypes());
        assertThrows(GenericSignatureFormatError.class, method::getGenericReturnType);
        assertThrows(GenericSignatureFormatError.class, method::getGenericParameterTypes);
        assertThrows(GenericSignatureFormatError.class, method::getGenericExceptionTypes);
    }

    @Test
    void testRecordComponent() {
        var rcs = sampleRecord.getRecordComponents();
        assertNotNull(rcs);
        assertEquals(1, rcs.length);
        var rc = rcs[0];
        assertNotNull(rc);

        assertEquals(Optional.class, rc.getType());
        assertEquals(DUMMY_SIGNATURE_TEXT, rc.getGenericSignature());
        assertThrows(GenericSignatureFormatError.class, rc::getGenericType);
    }
}

// Sample classes shared with TypeNotPresentInSignatureTest
abstract class SampleClass extends ArrayList<RuntimeException> implements Predicate<RuntimeException> { // class
    Optional<RuntimeException> field; // field

    <T extends RuntimeException> SampleClass(Optional<RuntimeException> param) throws T {
    } // constructor

    <T extends RuntimeException> Optional<RuntimeException> method(Optional<RuntimeException> param) throws T {
        return null;
    } // method
}

record SampleRecord(Optional<RuntimeException> component) {
}
