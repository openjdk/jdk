/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @library /test/lib
 * @build jdk.test.lib.ByteCodeLoader
 * @run junit/othervm -Xverify StrictStackMapsTest
 */

import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.*;

class StrictStackMapsTest {
    @Test
    void basicBranchTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fs", CD_int, ACC_STRICT_INIT)
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .iconst_0()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fsf", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fsf", CD_int))
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_()));
        runtimeVerify(className, classBytes);
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), "if -> else, then -> end");
        var elseFrame = stackMaps.entries().get(0);
        assertEquals(246, elseFrame.frameType(), "if -> else");
        assertEquals(List.of(ConstantPoolBuilder.of().nameAndTypeEntry("fsf", CD_int)), elseFrame.unsetFields());
        var mergedFrame = stackMaps.entries().get(1);
        assertEquals(246, mergedFrame.frameType(), "then -> merge");
        assertEquals(List.of(), mergedFrame.unsetFields());
    }

    @Test
    void noEarlyFrameInOldTest() {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(JAVA_26_VERSION, 0)
                .withFlags(ACC_PUBLIC | ACC_SUPER)
                .withField("fs", CD_int, ACC_STRICT_INIT) // spurious meaningless flags in 70.0
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .iconst_0()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fsf", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fsf", CD_int))
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_()));
        runtimeVerify(className, classBytes);
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), "if -> else, then -> end");
        var elseFrame = stackMaps.entries().get(0);
        assertNotEquals(246, elseFrame.frameType(), "if -> else");
        assertEquals(List.of(), elseFrame.unsetFields());
        var mergedFrame = stackMaps.entries().get(1);
        assertNotEquals(246, mergedFrame.frameType(), "then -> merge");
        assertEquals(List.of(), mergedFrame.unsetFields());
    }

    @Test
    void skipUnnecessaryUnsetFramesTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fPlain", CD_char, ACC_PRIVATE)
                .withField("fs", CD_int, ACC_STRICT_INIT)
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .iconst_0()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fPlain", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fPlain", CD_int))
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fsf", CD_int)
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_()));
        // runtimeVerify(className, classBytes); // TODO VM fix branching
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), "if -> else, then -> end");
        var elseFrame = stackMaps.entries().get(0);
        assertEquals(246, elseFrame.frameType(), "if -> else");
        assertEquals(List.of(ConstantPoolBuilder.of().nameAndTypeEntry("fsf", CD_int)), elseFrame.unsetFields());
        var mergedFrame = stackMaps.entries().get(1);
        assertNotEquals(246, mergedFrame.frameType(), "then -> end, no redundant larval");
        assertEquals(elseFrame.unsetFields(), mergedFrame.unsetFields(), "larval carries over in parsing");
    }

    // Also tests no larval_frame after ctor call
    @Test
    void clearUnsetAfterThisConstructorCallTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var fullArgsCtorDesc = MethodTypeDesc.of(CD_void, CD_int, CD_int);
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fPlain", CD_int, ACC_PRIVATE)
                .withField("fs", CD_int, ACC_STRICT_INIT)
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                // record-style ctor
                .withMethodBody(INIT_NAME, fullArgsCtorDesc, 0, cob -> cob
                        .aload(0)
                        .iload(1)
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .iload(2)
                        .putfield(classDesc, "fsf", CD_int)
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_())
                // delegates to the other ctor
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_5()
                        .iconst_0()
                        .invokespecial(classDesc, INIT_NAME, fullArgsCtorDesc)
                        .aload(0)
                        .iconst_1()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fPlain", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fPlain", CD_int))
                        .return_()));
        runtimeVerify(className, classBytes);
        var classModel = ClassFile.of().parse(classBytes);
        var delegatingCtorModel = classModel.methods().stream()
                .filter(m -> m.methodType().equalsString(MTD_void.descriptorString()))
                .findFirst().orElseThrow();
        var stackMaps = delegatingCtorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), "if -> else, then -> merge");
        var elseFrame = stackMaps.entries().get(0);
        assertNotEquals(246, elseFrame.frameType(), "if -> else, no uninitializedThis, no larval frame needed to clear unset");
        assertEquals(List.of(), elseFrame.unsetFields(), "cleared by constructor call");
        var mergeFrame = stackMaps.entries().get(1);
        assertNotEquals(246, mergeFrame.frameType(), "then -> merge");
    }

    @Test
    void allowMultiAssignTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fs", CD_int, ACC_STRICT_INIT)
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_1()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fs", CD_int), elb -> elb
                                // frame 0
                                .iconst_2()
                                .putfield(classDesc, "fsf", CD_int))
                        // frame 1
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .loadConstant(12)
                        .putfield(classDesc, "fsf", CD_int)
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_()));
        runtimeVerify(className, classBytes);
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), () -> stackMaps.entries().toString());
        var elseFrame = stackMaps.entries().get(0);
        var mergeFrame = stackMaps.entries().get(1);
        assertNotEquals(246, elseFrame.frameType(), "if -> else, no redundant larval frames");
        assertNotEquals(246, mergeFrame.frameType(), "then -> merge, no redundant larval frames");
        var cpb = ConstantPoolBuilder.of();
        assertEquals(Set.of(cpb.nameAndTypeEntry("fsf", CD_int), cpb.nameAndTypeEntry("fs", CD_int)),
                Set.copyOf(elseFrame.unsetFields()), "retains initial unsets");
        assertEquals(elseFrame.unsetFields(), mergeFrame.unsetFields(), "no unset change");
    }

    @Test
    void failOnUnsetNotClearTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fs0", CD_int, ACC_STRICT_INIT)
                .withField("fs1", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_0()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fs0", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fs1", CD_int))
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void) // unset not clear here
                        .return_())));
    }

    // Ensures stack maps are updated when fields are transformed to be strict
    @Test
    void basicTransformToStrictTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        // this class has no strict
        var classBytes = ClassFile.of().build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fs", CD_int, 0)
                .withField("fsf", CD_int, ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> cob
                        .aload(0)
                        .iconst_5()
                        .putfield(classDesc, "fs", CD_int)
                        .aload(0)
                        .iconst_0()
                        .ifThenElse(thb -> thb
                                .iconst_3()
                                .putfield(classDesc, "fsf", CD_int), elb -> elb
                                .iconst_2()
                                .putfield(classDesc, "fsf", CD_int))
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_()));

        classBytes = ClassFile.of().transformClass(ClassFile.of().parse(classBytes), ClassTransform.transformingFields((fb, fe) -> {
            if (fe instanceof AccessFlags acc) {
                fb.withFlags(acc.flagsMask() | ACC_STRICT_INIT);
            } else {
                fb.with(fe);
            }
        }));

        runtimeVerify(className, classBytes);
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(2, stackMaps.entries().size(), "if -> else, then -> merge");
        var elseFrame = stackMaps.entries().get(0);
        assertEquals(246, elseFrame.frameType(), "if -> else");
        assertEquals(List.of(ConstantPoolBuilder.of().nameAndTypeEntry("fsf", CD_int)), elseFrame.unsetFields());
        var mergedFrame = stackMaps.entries().get(1);
        assertEquals(246, mergedFrame.frameType(), "then -> merge");
        assertEquals(List.of(), mergedFrame.unsetFields());
    }

    @Test
    void explicitWriteFramesTest() throws Throwable {
        var className = "Test";
        var classDesc = ClassDesc.of(className);
        var classBytes = ClassFile.of(StackMapsOption.DROP_STACK_MAPS).build(classDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("fs", CD_int, ACC_STRICT_INIT)
                .withField("fsf", CD_int, ACC_STRICT_INIT | ACC_FINAL)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> {
                    var frames = new ArrayList<StackMapFrameInfo>();
                    cob.aload(0)
                       .iconst_0()
                       .ifThenElse(thb -> thb
                               .iconst_3()
                               .putfield(classDesc, "fsf", CD_int), elb -> {
                           // jump to else - fs, fsf unset
                           frames.add(StackMapFrameInfo.of(elb.startLabel(),
                                   List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS),
                                   List.of(),
                                   List.of(elb.constantPool().nameAndTypeEntry("fs", CD_int),
                                           elb.constantPool().nameAndTypeEntry("fsf", CD_int))));
                           elb.iconst_2()
                              .putfield(classDesc, "fsf", CD_int);
                       });
                    // merge - fs unset
                    frames.add(StackMapFrameInfo.of(cob.newBoundLabel(),
                            List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS),
                            List.of(),
                            List.of(cob.constantPool().nameAndTypeEntry("fs", CD_int))));
                    cob.aload(0)
                       .iconst_5()
                       .putfield(classDesc, "fs", CD_int)
                       .aload(0)
                       .invokespecial(CD_Object, INIT_NAME, MTD_void)
                       .iconst_1()
                       .ifThen(thb -> thb.iconst_3().pop());
                    // post larval - no uninitializedThis, empty unsets
                    frames.add(StackMapFrameInfo.of(cob.newBoundLabel(),
                            List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(classDesc)),
                            List.of()));
                    cob.return_()
                       .with(StackMapTableAttribute.of(frames));
                }));
        // runtimeVerify(className, classBytes); // TODO VM fix
        var classModel = ClassFile.of().parse(classBytes);
        var ctorModel = classModel.methods().getFirst();
        var stackMaps = ctorModel.code().orElseThrow().findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(3, stackMaps.entries().size(), "if -> else, then -> end, post larval");
        var elseFrame = stackMaps.entries().get(0);
        assertEquals(List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS), elseFrame.locals());
        // frame type for else may or may not be 246... no unset field changes but may have reorders
        assertEquals(2, elseFrame.unsetFields().size(), "if -> else");
        var cpb = ConstantPoolBuilder.of();
        assertEquals(Set.of(cpb.nameAndTypeEntry("fs", CD_int), cpb.nameAndTypeEntry("fsf", CD_int)),
                Set.copyOf(elseFrame.unsetFields()));
        var mergedFrame = stackMaps.entries().get(1);
        assertEquals(List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS), mergedFrame.locals());
        assertEquals(246, mergedFrame.frameType(), "then -> merge");
        assertEquals(List.of(cpb.nameAndTypeEntry("fs", CD_int)), mergedFrame.unsetFields());
        var postLarvalFrame = stackMaps.entries().get(2);
        assertNotEquals(246, postLarvalFrame.frameType(), "postLarval"); // no larval frame here
        assertEquals(List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(classDesc)), postLarvalFrame.locals());
        assertEquals(List.of(), postLarvalFrame.unsetFields());
    }

    private static void runtimeVerify(String className, byte[] classBytes) {
        var clazz = assertDoesNotThrow(() -> ByteCodeLoader.load(className, classBytes));
        var lookup = assertDoesNotThrow(() -> MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()));
        assertDoesNotThrow(() -> lookup.ensureInitialized(clazz)); // forces verification
        var errors = ClassFile.of().verify(classBytes);
        assertEquals(List.of(), errors, "Errors detected");
    }

    @Test
    void transformTryCatchTest() {
        byte[] bytes = InMemoryJavaCompiler.compile("TryCatchChild", """
                class TryCatchChild {
                    int x;
                    int y;
                    TryCatchChild() {
                        try {
                            x = 0;
                            int[] a = new int[1];
                            System.out.println(a[2]);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            y = 0;
                        } finally {
                            x = y = 1;
                        }
                        super();
                    }
                }
                """);
        var clazz = ClassFile.of().parse(bytes);
        var result = ClassFile.of().transformClass(clazz, new ClassTransform() {
            @Override
            public void atStart(ClassBuilder builder) {
                builder.withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION);
            }

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                switch (element) {
                    case ClassFileVersion _ -> {}
                    case FieldModel fm -> builder.transformField(fm, (fb, fe) -> {
                        if (fe instanceof AccessFlags flags) {
                            fb.withFlags(flags.flagsMask() | ACC_STRICT_INIT);
                        } else {
                            fb.with(fe);
                        }
                    });
                    default -> builder.with(element);
                }
            }
        });

        runtimeVerify("TryCatchChild", result);
    }

    @Test
    void strictAssignmentInHandlerTest() {
        var testName = "Test";
        var testDesc = ClassDesc.of(testName);
        var bytes = ClassFile.of().build(testDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("f", CD_int, ACC_STRICT_INIT)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> {
                    cob.aload(0)
                       .iconst_m1();
                    var handledBegin = cob.newBoundLabel();
                    cob.putfield(testDesc, "f", CD_int);
                    var handledEnd = cob.newBoundLabel();
                    cob.aload(0)
                       .invokespecial(CD_Object, INIT_NAME, MTD_void)
                       .return_();
                    var handler = cob.newBoundLabel(); // frame with local 0 this, f unset
                    cob.athrow();
                    cob.exceptionCatch(handledBegin, handledEnd, handler, Optional.empty());
                }));
        var parsed = ClassFile.of().parse(bytes);
        var frames = parsed.methods().getFirst().code().orElseThrow()
                .findAttribute(Attributes.stackMapTable()).orElseThrow().entries();
        assertEquals(1, frames.size());
        var frame = frames.getFirst();
        assertEquals(List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS), frame.locals());
        assertEquals(List.of(ConstantPoolBuilder.of().nameAndTypeEntry("f", CD_int)), frame.unsetFields());

        runtimeVerify(testName, bytes);
    }
}
