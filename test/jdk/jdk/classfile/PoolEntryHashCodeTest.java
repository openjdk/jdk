/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8352794
 * @summary Test equal PoolEntry and BootstrapMethodEntry objects have equal hash codes.
 * @run junit ${test.main.class}
 */

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.TypeDescriptor;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;
import static java.lang.constant.ConstantDescs.CD_Boolean;
import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_Collection;
import static java.lang.constant.ConstantDescs.CD_Exception;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_MethodType;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.DEFAULT_NAME;
import static java.lang.constant.ConstantDescs.FALSE;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PoolEntryHashCodeTest {

    @Test
    void testHashCodeContractAcrossConstantPools() {
        ConstantPoolBuilder pool1 = ConstantPoolBuilder.of();
        ConstantPoolBuilder pool2 = ConstantPoolBuilder.of();

        // Prefill the pools with some rubbish entries to offset
        // the indices so that the index-based hash codes differ
        prefillWithGarbage(pool1);

        testEntry(Utf8Entry.class, pool1, pool2, pool -> pool.utf8Entry("Test Utf8Entry"));
        testEntry(IntegerEntry.class, pool1, pool2, pool -> pool.intEntry(12345));
        testEntry(FloatEntry.class, pool1, pool2, pool -> pool.floatEntry(12345f));
        testEntry(LongEntry.class, pool1, pool2, pool -> pool.longEntry(12345L));
        testEntry(DoubleEntry.class, pool1, pool2, pool -> pool.doubleEntry(12345d));

        testEntry(ClassEntry.class, pool1, pool2, pool -> pool.classEntry(CD_Object));
        testEntry(StringEntry.class, pool1, pool2, pool -> pool.stringEntry("Test String"));

        testEntry(FieldRefEntry.class, pool1, pool2, pool -> pool.fieldRefEntry(CD_Boolean, "TRUE", CD_Boolean));
        testEntry(MethodRefEntry.class, pool1, pool2, pool -> pool.methodRefEntry(CD_Exception, INIT_NAME, MTD_void));
        testEntry(InterfaceMethodRefEntry.class, pool1, pool2,
                pool -> pool.interfaceMethodRefEntry(CD_Collection, "isEmpty", MethodTypeDesc.of(CD_boolean)));
        testEntry(NameAndTypeEntry.class, pool1, pool2, pool -> pool.nameAndTypeEntry("hoge", MethodTypeDesc.of(CD_Object)));
        testEntry(MethodHandleEntry.class, pool1, pool2, pool -> pool.methodHandleEntry(BSM_INVOKE));
        testEntry(MethodTypeEntry.class, pool1, pool2, pool -> pool.methodTypeEntry(MethodTypeDesc.of(CD_String)));

        testEntry(ConstantDynamicEntry.class, pool1, pool2, pool -> pool.constantDynamicEntry(FALSE));
        testEntry(InvokeDynamicEntry.class, pool1, pool2, pool -> pool.invokeDynamicEntry(
                DynamicCallSiteDesc.of(
                        ConstantDescs.ofCallsiteBootstrap(
                                ClassDesc.of(StringConcatFactory.class.getName()),
                                "makeConcat",
                                CD_CallSite),
                        MethodTypeDesc.of(CD_String, CD_Object, CD_Object, CD_Object))));

        testEntry(ModuleEntry.class, pool1, pool2, pool -> pool.moduleEntry(ModuleDesc.of("java.base")));
        testEntry(PackageEntry.class, pool1, pool2,
                pool -> pool.packageEntry(PackageDesc.ofInternalName("java/lang")));

        testEntry(BootstrapMethodEntry.class, pool1, pool2, pool -> pool.bsmEntry(
                ConstantDescs.ofCallsiteBootstrap(
                        ClassDesc.of(LambdaMetafactory.class.getName()),
                        "metafactory",
                        CD_CallSite,
                        CD_MethodType,
                        CD_MethodHandle,
                        CD_MethodType),
                List.of(
                        MethodTypeDesc.of(CD_Object, CD_Object),
                        MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.VIRTUAL,
                                CD_Object,
                                "hashCode",
                                MethodTypeDesc.of(CD_int)),
                        MethodTypeDesc.of(CD_int, CD_Object))));
    }

    private static <T> void testEntry(Class<T> type,
                                      ConstantPoolBuilder pool1,
                                      ConstantPoolBuilder pool2,
                                      Function<? super ConstantPoolBuilder, ? extends T> factory) {
        T entry1 = factory.apply(pool1);
        T entry2 = factory.apply(pool2);

        assertEquals(entry1, entry2, type::getName);
        assertEquals(entry1.hashCode(), entry2.hashCode(), type::getName);
    }

    private static void prefillWithGarbage(ConstantPoolBuilder pool) {
        for (int i = 0; i < 10; i++) {
            pool.utf8Entry("ignore: " + i);
        }

        pool.bsmEntry(
                MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC,
                        ClassDesc.of(DEFAULT_NAME),
                        DEFAULT_NAME,
                        MethodTypeDesc.of(CD_Object, CD_MethodHandles_Lookup, CD_String, ClassDesc.of(TypeDescriptor.class.getName()))
                ),
                List.of()
        );
    }
}
