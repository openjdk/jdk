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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleExportInfo;
import java.lang.classfile.attribute.ModuleOpenInfo;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.JAVA_17_VERSION;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8311172 8361614
 * @summary Testing ClassFile validation of non-instruction subint (u1, u2) arguments.
 * @run junit SubIntValidationTest
 */
class SubIntValidationTest {

    @Test
    public void testBuilderFlags() {
        ClassFile.of().build(CD_Void, clb -> {
            assertThrows(IllegalArgumentException.class, () -> clb.withFlags(-1));
            assertThrows(IllegalArgumentException.class, () -> clb.withFlags(70000));
            assertThrows(IllegalArgumentException.class, () -> clb.withField("test", CD_String, -1));
            assertThrows(IllegalArgumentException.class, () -> clb.withField("test", CD_String, 70000));
            assertThrows(IllegalArgumentException.class, () -> clb.withMethod("test", MTD_void, -1, _ -> {}));
            assertThrows(IllegalArgumentException.class, () -> clb.withMethod("test", MTD_void, 70000, _ -> {}));
            clb.withField("test", CD_String, fb -> {
                assertThrows(IllegalArgumentException.class, () -> fb.withFlags(-1));
                assertThrows(IllegalArgumentException.class, () -> fb.withFlags(70000 | ACC_STATIC));
            });
            clb.withMethod("test", MTD_void, ACC_STATIC, mb -> {
                assertThrows(IllegalArgumentException.class, () -> mb.withFlags(-1));
                assertThrows(IllegalArgumentException.class, () -> mb.withFlags(70000 | ACC_STATIC));
            });
        });
    }

    @Test
    public void testClassFileVersion() {
        // Prohibited but representable major/minor
        assertDoesNotThrow(() -> ClassFileVersion.of(0, 0));
        // Non-representable major/minor
        assertDoesNotThrow(() -> ClassFileVersion.of(JAVA_17_VERSION, 42));
        assertThrows(IllegalArgumentException.class, () -> ClassFileVersion.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ClassFileVersion.of(65536, 0));
        assertThrows(IllegalArgumentException.class, () -> ClassFileVersion.of(0, -2));
        assertThrows(IllegalArgumentException.class, () -> ClassFileVersion.of(0, 65536));
        ClassFile.of().build(CD_Void, clb -> assertThrows(IllegalArgumentException.class, () -> clb.withVersion(-1, 0)));
        // Special rule without serializing to class file format
        assertEquals(ClassFile.PREVIEW_MINOR_VERSION, ClassFileVersion.of(0, -1).minorVersion());
    }

    @Test
    public void testReadMinorVersion() {
        var cf = ClassFile.of();
        var cd = ClassDesc.of("Test");
        var bytes = cf.build(cd, cb -> cb
                .withSuperclass(CD_Object)
                // old preview minor version,
                // with all bits set to 1
                .withVersion(JAVA_17_VERSION, -1)
        );

        var cm = ClassFile.of().parse(bytes);
        assertEquals(ClassFile.PREVIEW_MINOR_VERSION, cm.minorVersion());
    }

    // LocalVarTargetInfo/TypeArgumentTarget in InstructionValidationTest for Label
    @Test
    public void testTypeAnnotations() {
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofTypeParameter(TypeAnnotation.TargetType.CLASS_TYPE_PARAMETER, 0));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofClassTypeParameter(-1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofMethodTypeParameter(300));
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofClassExtends(65535));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofClassExtends(-1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofClassExtends(65536));
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofClassTypeParameterBound(255, 255));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofClassTypeParameterBound(-1, 255));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofMethodTypeParameterBound(0, 256));
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(0));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(-1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofMethodFormalParameter(256));
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofThrows(256));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofThrows(-1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofThrows(65536));
        assertDoesNotThrow(() -> TypeAnnotation.TargetInfo.ofExceptionParameter(256));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofExceptionParameter(-1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TargetInfo.ofExceptionParameter(65536));
        assertDoesNotThrow(() -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.ARRAY, 2));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.INNER_TYPE, -1));
        assertThrows(IllegalArgumentException.class, () -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, 256));
    }

    @Test
    public void testInnerClasses() {
        assertDoesNotThrow(() -> InnerClassInfo.of(CD_Object, Optional.empty(), Optional.empty(), 65535));
        assertThrows(IllegalArgumentException.class, () -> InnerClassInfo.of(CD_Object, Optional.empty(), Optional.empty(), -1));
        assertThrows(IllegalArgumentException.class, () -> InnerClassInfo.of(ConstantPoolBuilder.of().classEntry(CD_String),
                Optional.empty(), Optional.empty(), 65536));
    }

    @Test
    public void testMethodParameter() {
        assertDoesNotThrow(() -> MethodParameterInfo.of(Optional.empty(), 65535));
        assertThrows(IllegalArgumentException.class, () -> MethodParameterInfo.of(Optional.empty(), -1));
        assertThrows(IllegalArgumentException.class, () -> MethodParameterInfo.ofParameter(Optional.empty(), 65536));
    }

    @Test
    public void testModule() {
        assertDoesNotThrow(() -> ModuleAttribute.of(ConstantPoolBuilder.of().moduleEntry(ModuleDesc.of("java.base")),
                65535, null, List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(ConstantPoolBuilder.of().moduleEntry(ModuleDesc.of("java.base")),
                -1, null, List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> ModuleAttribute.of(ConstantPoolBuilder.of().moduleEntry(ModuleDesc.of("java.base")),
                65536, null, List.of(), List.of(), List.of(), List.of(), List.of()));
        ModuleAttribute.of(ModuleDesc.of("java.base"), b -> {
            assertThrows(IllegalArgumentException.class, () -> b.moduleFlags(-1));
            assertThrows(IllegalArgumentException.class, () -> b.moduleFlags(65536));
            b.moduleFlags(0);
        });
    }

    @Test
    public void testModuleExport() {
        assertDoesNotThrow(() -> ModuleExportInfo.of(PackageDesc.of("java.lang"), 0));
        assertThrows(IllegalArgumentException.class, () -> ModuleExportInfo.of(PackageDesc.of("java.lang"), -1));
        assertThrows(IllegalArgumentException.class, () -> ModuleExportInfo.of(PackageDesc.of("java.lang"), 65536));
    }

    @Test
    public void testModuleOpen() {
        assertDoesNotThrow(() -> ModuleOpenInfo.of(PackageDesc.of("java.lang"), 0));
        assertThrows(IllegalArgumentException.class, () -> ModuleOpenInfo.of(PackageDesc.of("java.lang"), -1));
        assertThrows(IllegalArgumentException.class, () -> ModuleOpenInfo.of(PackageDesc.of("java.lang"), 65536));
    }

    @Test
    public void testModuleRequire() {
        assertDoesNotThrow(() -> ModuleRequireInfo.of(ModuleDesc.of("java.base"), 0, null));
        assertThrows(IllegalArgumentException.class, () -> ModuleRequireInfo.of(ModuleDesc.of("java.base"), -1, null));
        assertThrows(IllegalArgumentException.class, () -> ModuleRequireInfo.of(ModuleDesc.of("java.base"), 65536, null));
    }

    @Test
    public void testModuleResolution() {
        assertDoesNotThrow(() -> ModuleResolutionAttribute.of(256));
        assertThrows(IllegalArgumentException.class, () -> ModuleResolutionAttribute.of(-1));
        assertThrows(IllegalArgumentException.class, () -> ModuleResolutionAttribute.of(65536));
    }

    @Test
    public void testMethodHandleEntry() {
        ConstantPoolBuilder cp = ConstantPoolBuilder.of();
        var ref = cp.fieldRefEntry(CD_String, "a", CD_int);
        // Intentionally choose an invalid but representable refKind
        assertDoesNotThrow(() -> cp.methodHandleEntry(25, ref));
        assertThrows(IllegalArgumentException.class, () -> cp.methodHandleEntry(256, ref));
        assertThrows(IllegalArgumentException.class, () -> cp.methodHandleEntry(-1, ref));
    }
}
