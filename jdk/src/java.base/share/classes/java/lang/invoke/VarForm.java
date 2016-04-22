/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.VarHandle.AccessMode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A var handle form containing a set of member name, one for each operation.
 * Each member characterizes a static method.
 */
class VarForm {

    // Holds VarForm for VarHandle implementation classes
    private static final ClassValue<VarForm> VFORMS
            = new ClassValue<>() {
        @Override
        protected VarForm computeValue(Class<?> impl) {
            return new VarForm(linkFromStatic(impl));
        }
    };

    final @Stable MemberName[] table;

    VarForm(MemberName[] table) {
        this.table = table;
    }

    /**
     * Creates a var form given an VarHandle implementation class.
     * Each signature polymorphic method is linked to a static method of the
     * same name on the implementation class or a super class.
     */
    static VarForm createFromStatic(Class<? extends VarHandle> impl) {
        return VFORMS.get(impl);
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