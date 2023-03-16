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

import org.testng.annotations.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;
import static org.testng.Assert.assertSame;

/*
 * @test
 * @compile ConstantDescsTest.java
 * @run testng ConstantDescsTest
 * @summary unit tests for java.lang.constant.ConstantDescs
 */
public class ConstantDescsTest {

    /**
     * Checks that ConstantDescs descriptor fields resolve to the right
     * constants.
     * @throws ReflectiveOperationException if the test fails
     */
    @Test
    public void validateAllFields() throws ReflectiveOperationException {
        // Use a minimally-trusted lookup
        var lookup = MethodHandles.publicLookup();

        assertSame(CD_Object.resolveConstantDesc(lookup), Object.class);
        assertSame(CD_String.resolveConstantDesc(lookup), String.class);
        assertSame(CD_Class.resolveConstantDesc(lookup), Class.class);
        assertSame(CD_Number.resolveConstantDesc(lookup), Number.class);
        assertSame(CD_Integer.resolveConstantDesc(lookup), Integer.class);
        assertSame(CD_Long.resolveConstantDesc(lookup), Long.class);
        assertSame(CD_Float.resolveConstantDesc(lookup), Float.class);
        assertSame(CD_Double.resolveConstantDesc(lookup), Double.class);
        assertSame(CD_Short.resolveConstantDesc(lookup), Short.class);
        assertSame(CD_Byte.resolveConstantDesc(lookup), Byte.class);
        assertSame(CD_Character.resolveConstantDesc(lookup), Character.class);
        assertSame(CD_Boolean.resolveConstantDesc(lookup), Boolean.class);
        assertSame(CD_Void.resolveConstantDesc(lookup), Void.class);
        assertSame(CD_Exception.resolveConstantDesc(lookup), Exception.class);
        assertSame(CD_Throwable.resolveConstantDesc(lookup), Throwable.class);
        assertSame(CD_Enum.resolveConstantDesc(lookup), Enum.class);
        assertSame(CD_VarHandle.resolveConstantDesc(lookup), VarHandle.class);
        assertSame(CD_MethodHandles.resolveConstantDesc(lookup), MethodHandles.class);
        assertSame(CD_MethodHandles_Lookup.resolveConstantDesc(lookup), MethodHandles.Lookup.class);
        assertSame(CD_MethodHandle.resolveConstantDesc(lookup), MethodHandle.class);
        assertSame(CD_MethodType.resolveConstantDesc(lookup), MethodType.class);
        assertSame(CD_CallSite.resolveConstantDesc(lookup), CallSite.class);
        assertSame(CD_Collection.resolveConstantDesc(lookup), Collection.class);
        assertSame(CD_List.resolveConstantDesc(lookup), List.class);
        assertSame(CD_Set.resolveConstantDesc(lookup), Set.class);
        assertSame(CD_Map.resolveConstantDesc(lookup), Map.class);
        assertSame(CD_ConstantDesc.resolveConstantDesc(lookup), ConstantDesc.class);
        assertSame(CD_ClassDesc.resolveConstantDesc(lookup), ClassDesc.class);
        assertSame(CD_EnumDesc.resolveConstantDesc(lookup), Enum.EnumDesc.class);
        assertSame(CD_MethodTypeDesc.resolveConstantDesc(lookup), MethodTypeDesc.class);
        assertSame(CD_MethodHandleDesc.resolveConstantDesc(lookup), MethodHandleDesc.class);
        assertSame(CD_DirectMethodHandleDesc.resolveConstantDesc(lookup), DirectMethodHandleDesc.class);
        assertSame(CD_VarHandleDesc.resolveConstantDesc(lookup), VarHandle.VarHandleDesc.class);
        assertSame(CD_MethodHandleDesc_Kind.resolveConstantDesc(lookup), DirectMethodHandleDesc.Kind.class);
        assertSame(CD_DynamicConstantDesc.resolveConstantDesc(lookup), DynamicConstantDesc.class);
        assertSame(CD_DynamicCallSiteDesc.resolveConstantDesc(lookup), DynamicCallSiteDesc.class);
        assertSame(CD_ConstantBootstraps.resolveConstantDesc(lookup), ConstantBootstraps.class);
        assertSame(CD_int.resolveConstantDesc(lookup), int.class);
        assertSame(CD_long.resolveConstantDesc(lookup), long.class);
        assertSame(CD_float.resolveConstantDesc(lookup), float.class);
        assertSame(CD_double.resolveConstantDesc(lookup), double.class);
        assertSame(CD_short.resolveConstantDesc(lookup), short.class);
        assertSame(CD_byte.resolveConstantDesc(lookup), byte.class);
        assertSame(CD_char.resolveConstantDesc(lookup), char.class);
        assertSame(CD_boolean.resolveConstantDesc(lookup), boolean.class);
        assertSame(CD_void.resolveConstantDesc(lookup), void.class);
        assertSame(NULL.resolveConstantDesc(lookup), null);
        assertSame(TRUE.resolveConstantDesc(lookup), Boolean.TRUE);
        assertSame(FALSE.resolveConstantDesc(lookup), Boolean.FALSE);
    }

    /**
     * Ensures all public static final descriptor fields in ConstantDescs
     * are resolvable.
     * @throws ReflectiveOperationException if the test fails
     */
    @Test
    public void checkFieldsResolvable() throws ReflectiveOperationException {
        // minimally trusted lookup
        var lookup = MethodHandles.publicLookup();
        var fields = Stream.of(ConstantDescs.class.getFields())
                .filter(f -> f.accessFlags().contains(AccessFlag.STATIC)
                        && ConstantDesc.class.isAssignableFrom(f.getType()))
                .toArray(Field[]::new);
        for (var field : fields) {
            var desc = (ConstantDesc) field.get(null);
            desc.resolveConstantDesc(lookup);
        }
    }
}
