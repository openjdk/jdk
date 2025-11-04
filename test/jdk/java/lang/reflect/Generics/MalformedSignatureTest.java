/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6832374 7052898 8350704
 * @summary Test behaviors with malformed signature strings in Signature attribute.
 * @library /test/lib
 * @run junit MalformedSignatureTest
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.GenericSignatureFormatError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.constant.ConstantDescs.MTD_void;
import static org.junit.jupiter.api.Assertions.*;

class MalformedSignatureTest {

    private static final String BASIC_BAD_SIGNATURE_TEXT = "i_aM_NoT_A_Signature";
    static Class<?> sampleClass, sampleRecord;

    @BeforeAll
    static void setup() throws Exception {
        var compiledDir = Path.of(System.getProperty("test.classes"));
        var cf = ClassFile.of();

        // Transform that installs malformed signature strings to classes,
        // fields, methods, and record components.
        var badSignatureTransform = new ClassTransform() {
            private SignatureAttribute badSignature;

            @Override
            public void atStart(ClassBuilder builder) {
                badSignature = SignatureAttribute.of(builder.constantPool().utf8Entry(BASIC_BAD_SIGNATURE_TEXT));
            }

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                switch (element) {
                    case SignatureAttribute _ -> {} // dropping
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

    /**
     * Ensures the reflective generic inspection of a malformed Class throws
     * GenericSignatureFormatError while the non-generic inspection is fine.
     */
    @Test
    void testBasicClass() {
        assertEquals(ArrayList.class, sampleClass.getSuperclass());
        assertArrayEquals(new Class<?>[] {Predicate.class}, sampleClass.getInterfaces());
        var ex = assertThrows(GenericSignatureFormatError.class, sampleClass::getGenericSuperclass);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
        ex = assertThrows(GenericSignatureFormatError.class, sampleClass::getGenericInterfaces);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
    }

    /**
     * Ensures the reflective generic inspection of a malformed Field throws
     * GenericSignatureFormatError while the non-generic inspection is fine.
     */
    @Test
    void testBasicField() throws ReflectiveOperationException {
        var field = sampleClass.getDeclaredField("field");
        assertEquals(Optional.class, field.getType());
        var ex = assertThrows(GenericSignatureFormatError.class, field::getGenericType);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
    }

    /**
     * Ensures the reflective generic inspection of a malformed Constructor throws
     * GenericSignatureFormatError while the non-generic inspection is fine.
     */
    @Test
    void testBasicConstructor() throws ReflectiveOperationException {
        var constructor = sampleClass.getDeclaredConstructors()[0];
        assertArrayEquals(new Class<?>[] {Optional.class}, constructor.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, constructor.getExceptionTypes());
        var ex = assertThrows(GenericSignatureFormatError.class, constructor::getGenericParameterTypes);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
        ex = assertThrows(GenericSignatureFormatError.class, constructor::getGenericExceptionTypes);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
    }

    /**
     * Ensures the reflective generic inspection of a malformed Method throws
     * GenericSignatureFormatError while the non-generic inspection is fine.
     */
    @Test
    void testBasicMethod() throws ReflectiveOperationException {
        var method = sampleClass.getDeclaredMethods()[0];
        assertEquals(Optional.class, method.getReturnType());
        assertArrayEquals(new Class<?>[] {Optional.class}, method.getParameterTypes());
        assertArrayEquals(new Class<?>[] {RuntimeException.class}, method.getExceptionTypes());
        var ex = assertThrows(GenericSignatureFormatError.class, method::getGenericReturnType);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
        ex = assertThrows(GenericSignatureFormatError.class, method::getGenericParameterTypes);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
        ex = assertThrows(GenericSignatureFormatError.class, method::getGenericExceptionTypes);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
    }

    /**
     * Ensures the reflective generic inspection of a malformed RecordComponent throws
     * GenericSignatureFormatError while the non-generic inspection is fine.
     */
    @Test
    void testBasicRecordComponent() {
        var rcs = sampleRecord.getRecordComponents();
        assertNotNull(rcs);
        assertEquals(1, rcs.length);
        var rc = rcs[0];
        assertNotNull(rc);

        assertEquals(Optional.class, rc.getType());
        assertEquals(BASIC_BAD_SIGNATURE_TEXT, rc.getGenericSignature());
        var ex = assertThrows(GenericSignatureFormatError.class, rc::getGenericType);
        assertTrue(ex.getMessage().contains(BASIC_BAD_SIGNATURE_TEXT));
    }

    static String[] badMethodSignatures() {
        return new String[] {
                // Missing ":" after first type bound
                "<T:Lfoo/tools/nsc/symtab/Names;Lfoo/tools/nsc/symtab/Symbols;",

                // Arrays improperly indicated for exception information
                "<E:Ljava/lang/Exception;>(TE;[Ljava/lang/RuntimeException;)V^[TE;",
        };
    }

    /**
     * Ensures that particular strings are invalid as method signature strings.
     */
    @MethodSource("badMethodSignatures")
    @ParameterizedTest
    void testSignatureForMethod(String badSig) throws Throwable {
        var className = "BadSignature";
        var bytes = ClassFile.of().build(ClassDesc.of(className), clb ->
                clb.withMethod("test", MTD_void, 0, mb -> mb
                        .withCode(CodeBuilder::return_)
                        .with(SignatureAttribute.of(clb.constantPool().utf8Entry(badSig)))));

        var cl = ByteCodeLoader.load(className, bytes);
        var method = cl.getDeclaredMethod("test");
        var ex = assertThrows(GenericSignatureFormatError.class, method::getGenericParameterTypes);
        //assertTrue(ex.getMessage().contains(badSig), "Missing bad signature in error message");
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
