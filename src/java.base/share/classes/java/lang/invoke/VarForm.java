/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.invoke;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.VarHandle.AccessMode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A var handle form containing a set of member name, one for each operation.
 * Each member characterizes a static method.
 */
final class VarForm {

    final boolean exact;

    final @Stable MethodType[] methodType_table;
    final @Stable MethodType[] methodType_table_exact;

    final @Stable MemberName[] memberName_table;

    VarForm(Class<?> implClass, Class<?> receiver, Class<?> value, Class<?>... intermediate) {
        this.exact = false;
        this.methodType_table = new MethodType[VarHandle.AccessType.values().length];
        this.methodType_table_exact = new MethodType[VarHandle.AccessType.values().length];
        if (receiver == null) {
            initMethodTypes(value, intermediate);
        } else {
            Class<?>[] coordinates = new Class<?>[intermediate.length + 1];
            coordinates[0] = receiver;
            System.arraycopy(intermediate, 0, coordinates, 1, intermediate.length);
            initMethodTypes(value, coordinates);
        }

        // TODO lazily calculate
        this.memberName_table = linkFromStatic(implClass);
    }

    VarForm(Class<?> value, Class<?>[] coordinates) {
        this.exact = false;
        this.methodType_table = new MethodType[VarHandle.AccessType.values().length];
        this.methodType_table_exact = new MethodType[VarHandle.AccessType.values().length];
        this.memberName_table = null;
        initMethodTypes(value, coordinates);
    }

    private VarForm(boolean exact, MethodType[] methodType_table, MethodType[] methodType_table_exact,
                    MemberName[] memberName_table, MethodType[] methodType_V_table) {
        this.exact = exact;
        this.methodType_table = methodType_table;
        this.methodType_table_exact = methodType_table_exact;
        this.memberName_table = memberName_table;
        this.methodType_V_table = methodType_V_table;
    }

    private void initMethodTypePair(int index, MethodType type) {
        methodType_table_exact[index] = type;
        methodType_table[index] = type.erase();
    }

    void initMethodTypes(Class<?> value, Class<?>... coordinates) {
        // (Receiver, <Intermediates>)Value
        initMethodTypePair(VarHandle.AccessType.GET.ordinal(), MethodType.methodType(value, coordinates));

        // (Receiver, <Intermediates>, Value)void
        initMethodTypePair(VarHandle.AccessType.SET.ordinal(),
                MethodType.methodType(void.class, coordinates).appendParameterTypes(value));

        // (Receiver, <Intermediates>, Value)Value
        initMethodTypePair(VarHandle.AccessType.GET_AND_UPDATE.ordinal(),
                MethodType.methodType(value, coordinates).appendParameterTypes(value));

        // (Receiver, <Intermediates>, Value, Value)boolean
        initMethodTypePair(VarHandle.AccessType.COMPARE_AND_SET.ordinal(),
                MethodType.methodType(boolean.class, coordinates).appendParameterTypes(value, value));

        // (Receiver, <Intermediates>, Value, Value)Value
        initMethodTypePair(VarHandle.AccessType.COMPARE_AND_EXCHANGE.ordinal(),
                MethodType.methodType(value, coordinates).appendParameterTypes(value, value));
    }

    VarForm asExact() {
        return new VarForm(true, methodType_table, methodType_table_exact, memberName_table, methodType_V_table);
    }

    VarForm asGeneric() {
        return new VarForm(false, methodType_table, methodType_table_exact, memberName_table, methodType_V_table);
    }

    @ForceInline
    final MethodType getMethodType(int type) {
        return methodType_table[type];
    }

    @ForceInline
    final MemberName getMemberName(int mode) {
        // TODO calculate lazily
        MemberName mn = memberName_table[mode];
        if (mn == null) {
            throw new UnsupportedOperationException();
        }
        return mn;
    }


    @Stable
    MethodType[] methodType_V_table;

    @ForceInline
    final MethodType[] getMethodType_V_init() {
        MethodType[] table = new MethodType[VarHandle.AccessType.values().length];
        for (int i = 0; i < methodType_table.length; i++) {
            MethodType mt = methodType_table[i];
            // TODO only adjust for sig-poly methods returning Object
            table[i] = mt.changeReturnType(void.class);
        }
        methodType_V_table = table;
        return table;
    }

    @ForceInline
    final MethodType getMethodType_V(int type) {
        MethodType[] table = methodType_V_table;
        if (table == null) {
            table = getMethodType_V_init();
        }
        return table[type];
    }


    /**
     * Link all signature polymorphic methods.
     */
    private static MemberName[] linkFromStatic(Class<?> implClass) {
        MemberName[] table = new MemberName[AccessMode.values().length];

        for (Class<?> c = implClass; c != VarHandle.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    AccessMode am = AccessMode.methodNameToAccessMode.get(m.getName());
                    if (am != null) {
                        assert table[am.ordinal()] == null;
                        table[am.ordinal()] = new MemberName(m);
                    }
                }
            }
        }
        return table;
    }
}
