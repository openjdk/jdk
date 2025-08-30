/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8361635
 * @summary Testing list size validation in class file format.
 * @run junit ListValidationTest
 */

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Interfaces;
import java.lang.classfile.Label;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.util.List;
import java.util.Optional;

import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.*;
import static java.util.Collections.nCopies;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListValidationTest {
    @Test
    void testAnnotationElements() {
        var e = AnnotationElement.ofInt("dummy", 0);
        assertDoesNotThrow(() -> Annotation.of(CD_String, nCopies(65535, e)));
        assertThrows(IllegalArgumentException.class, () -> Annotation.of(CD_String, nCopies(66000, e)));
    }

    @Test
    void testAnnotationArrayValue() {
        var v = AnnotationValue.ofInt(0);
        assertDoesNotThrow(() -> AnnotationValue.ofArray(nCopies(65535, v)));
        assertThrows(IllegalArgumentException.class, () -> AnnotationValue.ofArray(nCopies(66000, v)));
    }

    @Test
    void testTypeAnnotationPath() {
        var anno = Annotation.of(CD_String);
        assertDoesNotThrow(() -> TypeAnnotation.of(TypeAnnotation.TargetInfo.ofField(), nCopies(255, TypeAnnotation.TypePathComponent.INNER_TYPE), anno));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.of(TypeAnnotation.TargetInfo.ofField(), nCopies(256, TypeAnnotation.TypePathComponent.INNER_TYPE), anno));
    }

    @Test
    void testBsmArgs() {
        var cpb = ConstantPoolBuilder.of();
        assertDoesNotThrow(() -> cpb.bsmEntry(BSM_INVOKE, nCopies(65535, 0)));
        assertThrows(IllegalArgumentException.class, () -> cpb.bsmEntry(BSM_INVOKE, nCopies(66000, 0)));
    }

    @Test
    void testInterfaces() {
        var cpb = ConstantPoolBuilder.of();
        assertDoesNotThrow(() -> Interfaces.ofSymbols(nCopies(65535, CD_Number)));
        assertThrows(IllegalArgumentException.class, () -> cpb.bsmEntry(BSM_INVOKE, nCopies(66000, 0)));
    }

    @Test
    void testStackMapFrame() {
        Label label = dummyLabel();
        assertDoesNotThrow(() -> StackMapFrameInfo.of(label,
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER),
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE)));
        assertThrows(IllegalArgumentException.class, () -> StackMapFrameInfo.of(label,
                nCopies(66000, StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER),
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE)));
        assertThrows(IllegalArgumentException.class, () -> StackMapFrameInfo.of(label,
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER),
                nCopies(66000, StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE)));
    }

    @Test
    void testTypeAnnotationLocalVarTarget() {
        Label label = dummyLabel();
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofLocalVariable(nCopies(65535, TypeAnnotation.LocalVarTargetInfo.of(label, label, 0))));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofLocalVariable(nCopies(66000, TypeAnnotation.LocalVarTargetInfo.of(label, label, 0))));
    }

    @Test
    void testExceptionsAttribute() {
        assertDoesNotThrow(() -> ExceptionsAttribute.ofSymbols(nCopies(65535, CD_Throwable)));
        assertThrows(IllegalArgumentException.class, () -> ExceptionsAttribute.ofSymbols(nCopies(66000, CD_Throwable)));
    }

    @Test
    void testStackMapTableAttribute() {
        var frame = StackMapFrameInfo.of(dummyLabel(),
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER),
                nCopies(65535, StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE));
        assertDoesNotThrow(() -> StackMapTableAttribute.of(nCopies(65535, frame)));
        assertThrows(IllegalArgumentException.class, () -> StackMapTableAttribute.of(nCopies(66000, frame)));
    }

    @Test
    void testInnerClassesAttribute() {
        var entry = InnerClassInfo.of(CD_Void, Optional.empty(), Optional.empty(), 0);
        assertDoesNotThrow(() -> InnerClassesAttribute.of(nCopies(65535, entry)));
        assertThrows(IllegalArgumentException.class, () -> InnerClassesAttribute.of(nCopies(66000, entry)));
    }

    @Test
    void testRecordAttribute() {
        var component = RecordComponentInfo.of("hello", CD_int, List.of());
        assertDoesNotThrow(() -> RecordAttribute.of(nCopies(65535, component)));
        assertThrows(IllegalArgumentException.class, () -> RecordAttribute.of(nCopies(66000, component)));
    }

    @Test
    void testMethodParametersAttribute() {
        var component = MethodParameterInfo.of(Optional.empty(), 0);
        assertDoesNotThrow(() -> MethodParametersAttribute.of(nCopies(255, component)));
        assertThrows(IllegalArgumentException.class, () -> MethodParametersAttribute.of(nCopies(300, component)));
    }

    @Test
    void testModuleHashesAttribute() {
        var hash = ModuleHashInfo.of(ModuleDesc.of("java.base"), new byte[0]);
        assertDoesNotThrow(() -> ModuleHashesAttribute.of("dummy", nCopies(65535, hash)));
        assertThrows(IllegalArgumentException.class, () -> ModuleHashesAttribute.of("dummy", nCopies(66000, hash)));
    }

    @Test
    void testModulePackagesAttribute() {
        var pkgDesc = PackageDesc.of("java.io");
        assertDoesNotThrow(() -> ModulePackagesAttribute.ofNames(nCopies(65535, pkgDesc)));
        assertThrows(IllegalArgumentException.class, () -> ModulePackagesAttribute.ofNames(nCopies(66000, pkgDesc)));
    }

    @Test
    void testPermittedSubclassesAttribute() {
        assertDoesNotThrow(() -> PermittedSubclassesAttribute.ofSymbols(nCopies(65535, CD_Collection)));
        assertThrows(IllegalArgumentException.class, () -> PermittedSubclassesAttribute.ofSymbols(nCopies(66000, CD_Collection)));
    }

    @Test
    void testNestMembersAttribute() {
        assertDoesNotThrow(() -> NestMembersAttribute.ofSymbols(nCopies(65535, CD_Collection)));
        assertThrows(IllegalArgumentException.class, () -> NestMembersAttribute.ofSymbols(nCopies(66000, CD_Collection)));
    }

    @Test
    void testCharacterRangeTableAttribute() {
        var range = CharacterRangeInfo.of(0, 0, 0, 0, 0);
        assertDoesNotThrow(() -> CharacterRangeTableAttribute.of(nCopies(65535, range)));
        assertThrows(IllegalArgumentException.class, () -> CharacterRangeTableAttribute.of(nCopies(66000, range)));
    }

    @Test
    void testLineNumberTableAttribute() {
        var lineNumber = LineNumberInfo.of(0, 0);
        assertDoesNotThrow(() -> LineNumberTableAttribute.of(nCopies(65535, lineNumber)));
        assertThrows(IllegalArgumentException.class, () -> LineNumberTableAttribute.of(nCopies(66000, lineNumber)));
    }

    @Test
    void testLocalVariableTableAttribute() {
        var utf8 = TemporaryConstantPool.INSTANCE.utf8Entry("dummy");
        var localVariable = new UnboundAttribute.UnboundLocalVariableInfo(0, 0, utf8, utf8, 0);
        assertDoesNotThrow(() -> LocalVariableTableAttribute.of(nCopies(65535, localVariable)));
        assertThrows(IllegalArgumentException.class, () -> LocalVariableTableAttribute.of(nCopies(66000, localVariable)));
    }

    @Test
    void testLocalVariableTypeTableAttribute() {
        var utf8 = TemporaryConstantPool.INSTANCE.utf8Entry("dummy");
        var localVariableType = new UnboundAttribute.UnboundLocalVariableTypeInfo(0, 0, utf8, utf8, 0);
        assertDoesNotThrow(() -> LocalVariableTypeTableAttribute.of(nCopies(65535, localVariableType)));
        assertThrows(IllegalArgumentException.class, () -> LocalVariableTypeTableAttribute.of(nCopies(66000, localVariableType)));
    }

    @Test
    void testRuntimeVisibleAnnotationsAttribute() {
        var anno = Annotation.of(CD_String);
        assertDoesNotThrow(() -> RuntimeVisibleAnnotationsAttribute.of(nCopies(65535, anno)));
        assertThrows(IllegalArgumentException.class, () -> RuntimeVisibleAnnotationsAttribute.of(nCopies(66000, anno)));
    }

    @Test
    void testRuntimeInvisibleAnnotationsAttribute() {
        var anno = Annotation.of(CD_String);
        assertDoesNotThrow(() -> RuntimeInvisibleAnnotationsAttribute.of(nCopies(65535, anno)));
        assertThrows(IllegalArgumentException.class, () -> RuntimeInvisibleAnnotationsAttribute.of(nCopies(66000, anno)));
    }

    @Test
    void testRuntimeVisibleParameterAnnotationsAttributeTopLevel() {
        assertDoesNotThrow(() -> RuntimeVisibleParameterAnnotationsAttribute.of(nCopies(255, List.of())));
        assertThrows(IllegalArgumentException.class, () -> RuntimeVisibleParameterAnnotationsAttribute.of(nCopies(256, List.of())));
    }

    @Test
    void testRuntimeInvisibleParameterAnnotationsAttributeTopLevel() {
        assertDoesNotThrow(() -> RuntimeInvisibleParameterAnnotationsAttribute.of(nCopies(255, List.of())));
        assertThrows(IllegalArgumentException.class, () -> RuntimeInvisibleParameterAnnotationsAttribute.of(nCopies(256, List.of())));
    }

    @Test
    void testRuntimeVisibleParameterAnnotationsAttributeNested() {
        var anno = Annotation.of(CD_String);
        assertDoesNotThrow(() -> RuntimeVisibleParameterAnnotationsAttribute.of(List.of(nCopies(65535, anno))));
        assertThrows(IllegalArgumentException.class, () -> RuntimeVisibleParameterAnnotationsAttribute.of(List.of(nCopies(65536, anno))));
    }

    @Test
    void testRuntimeInvisibleParameterAnnotationsAttributeNested() {
        var anno = Annotation.of(CD_String);
        assertDoesNotThrow(() -> RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(nCopies(65535, anno))));
        assertThrows(IllegalArgumentException.class, () -> RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(nCopies(65536, anno))));
    }

    @Test
    void testRuntimeVisibleTypeAnnotationsAttribute() {
        var anno = TypeAnnotation.of(TypeAnnotation.TargetInfo.ofMethodReturn(), List.of(), Annotation.of(CD_String));
        assertDoesNotThrow(() -> RuntimeVisibleTypeAnnotationsAttribute.of(nCopies(65535, anno)));
        assertThrows(IllegalArgumentException.class, () -> RuntimeVisibleTypeAnnotationsAttribute.of(nCopies(66000, anno)));
    }

    @Test
    void testRuntimeInvisibleTypeAnnotationsAttribute() {
        var anno = TypeAnnotation.of(TypeAnnotation.TargetInfo.ofMethodReturn(), List.of(), Annotation.of(CD_String));
        assertDoesNotThrow(() -> RuntimeInvisibleTypeAnnotationsAttribute.of(nCopies(65535, anno)));
        assertThrows(IllegalArgumentException.class, () -> RuntimeInvisibleTypeAnnotationsAttribute.of(nCopies(66000, anno)));
    }

    @Test
    void testModuleExportEntry() {
        var pkg = PackageDesc.of("dummy.test");
        var mod = ModuleDesc.of("the.other");
        assertDoesNotThrow(() -> ModuleExportInfo.of(pkg, 0, nCopies(65535, mod)));
        assertThrows(IllegalArgumentException.class, () -> ModuleExportInfo.of(pkg, 0, nCopies(66000, mod)));
    }

    @Test
    void testModuleOpenEntry() {
        var pkg = PackageDesc.of("dummy.test");
        var mod = ModuleDesc.of("the.other");
        assertDoesNotThrow(() -> ModuleOpenInfo.of(pkg, 0, nCopies(65535, mod)));
        assertThrows(IllegalArgumentException.class, () -> ModuleOpenInfo.of(pkg, 0, nCopies(66000, mod)));
    }

    @Test
    void testModuleProvideEntry() {
        assertDoesNotThrow(() -> ModuleProvideInfo.of(CD_Object, nCopies(65535, CD_String)));
        assertThrows(IllegalArgumentException.class, () -> ModuleProvideInfo.of(CD_Object, nCopies(66000, CD_String)));
    }

    @Test
    void testRecordComponentAttributes() {
        var attr = SyntheticAttribute.of();
        assertDoesNotThrow(() -> RecordComponentInfo.of("dummy", CD_int, nCopies(65535, attr)));
        assertThrows(IllegalArgumentException.class, () -> RecordComponentInfo.of("dummy", CD_int, nCopies(66000, attr)));
    }

    @Test
    void testModuleAttribute() {
        var md = ModuleDesc.of("java.base");
        var pkg = PackageDesc.of("java.lang");
        var require = ModuleRequireInfo.of(md, 0, null);
        var export = ModuleExportInfo.of(pkg, 0, List.of());
        var provide = ModuleProvideInfo.of(CD_Object, List.of());
        var open = ModuleOpenInfo.of(pkg, 0, List.of());
        var classEntry = TemporaryConstantPool.INSTANCE.classEntry(CD_String);
        var moduleEntry = TemporaryConstantPool.INSTANCE.moduleEntry(md);
        assertDoesNotThrow(() -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(65535, require),
                nCopies(65535, export),
                nCopies(65535, open),
                nCopies(65535, classEntry),
                nCopies(65535, provide)
        ));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(66000, require),
                nCopies(65535, export),
                nCopies(65535, open),
                nCopies(65535, classEntry),
                nCopies(65535, provide)
        ));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(65535, require),
                nCopies(66000, export),
                nCopies(65535, open),
                nCopies(65535, classEntry),
                nCopies(65535, provide)
        ));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(65535, require),
                nCopies(65535, export),
                nCopies(66000, open),
                nCopies(65535, classEntry),
                nCopies(65535, provide)
        ));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(65535, require),
                nCopies(65535, export),
                nCopies(65535, open),
                nCopies(66000, classEntry),
                nCopies(65535, provide)
        ));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(moduleEntry, 0, null,
                nCopies(65535, require),
                nCopies(65535, export),
                nCopies(65535, open),
                nCopies(65535, classEntry),
                nCopies(66000, provide)
        ));
    }

    private static Label dummyLabel() {
        Label[] capture = new Label[1];
        ClassFile.of().build(CD_Object, clb -> clb.withMethodBody("test", MTD_void, 0, cob -> {
            capture[0] = cob.startLabel();
            cob.return_();
        }));
        return capture[0];
    }
}
