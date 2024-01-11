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

import org.testng.annotations.DataProvider;
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
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @compile ConstantDescsTest.java
 * @run testng ConstantDescsTest
 * @summary unit tests for java.lang.constant.ConstantDescs
 */
public class ConstantDescsTest {

    @DataProvider(name = "validateFields")
    public Object[][] knownFieldsData() {
        return new Object[][]{
                {CD_Object, Object.class},
                {CD_String, String.class},
                {CD_Class, Class.class},
                {CD_Number, Number.class},
                {CD_Integer, Integer.class},
                {CD_Long, Long.class},
                {CD_Float, Float.class},
                {CD_Double, Double.class},
                {CD_Short, Short.class},
                {CD_Byte, Byte.class},
                {CD_Character, Character.class},
                {CD_Boolean, Boolean.class},
                {CD_Void, Void.class},
                {CD_Exception, Exception.class},
                {CD_Throwable, Throwable.class},
                {CD_Enum, Enum.class},
                {CD_VarHandle, VarHandle.class},
                {CD_MethodHandles, MethodHandles.class},
                {CD_MethodHandles_Lookup, MethodHandles.Lookup.class},
                {CD_MethodHandle, MethodHandle.class},
                {CD_MethodType, MethodType.class},
                {CD_CallSite, CallSite.class},
                {CD_Collection, Collection.class},
                {CD_List, List.class},
                {CD_Set, Set.class},
                {CD_Map, Map.class},
                {CD_ConstantDesc, ConstantDesc.class},
                {CD_ClassDesc, ClassDesc.class},
                {CD_EnumDesc, Enum.EnumDesc.class},
                {CD_MethodTypeDesc, MethodTypeDesc.class},
                {CD_MethodHandleDesc, MethodHandleDesc.class},
                {CD_DirectMethodHandleDesc, DirectMethodHandleDesc.class},
                {CD_VarHandleDesc, VarHandle.VarHandleDesc.class},
                {CD_MethodHandleDesc_Kind, DirectMethodHandleDesc.Kind.class},
                {CD_DynamicConstantDesc, DynamicConstantDesc.class},
                {CD_DynamicCallSiteDesc, DynamicCallSiteDesc.class},
                {CD_ConstantBootstraps, ConstantBootstraps.class},
                {CD_int, int.class},
                {CD_long, long.class},
                {CD_float, float.class},
                {CD_double, double.class},
                {CD_short, short.class},
                {CD_byte, byte.class},
                {CD_char, char.class},
                {CD_boolean, boolean.class},
                {CD_void, void.class},
                {NULL, null},
                {TRUE, Boolean.TRUE},
                {FALSE, Boolean.FALSE},
        };
    }

    /**
     * Checks that ConstantDescs descriptor fields resolve to the right
     * constants.
     * @throws ReflectiveOperationException if the test fails
     */
    @Test(dataProvider = "validateFields")
    public void validateFields(ConstantDesc desc, Object value) throws ReflectiveOperationException {
        // Use a minimally-trusted lookup
        assertEquals(desc.resolveConstantDesc(MethodHandles.publicLookup()), value);
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
