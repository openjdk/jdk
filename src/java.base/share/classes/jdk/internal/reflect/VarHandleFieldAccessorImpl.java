/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

abstract class VarHandleFieldAccessorImpl extends FieldAccessorImpl {
    protected final boolean isReadOnly;
    private final VarHandle varHandle;
    private @Stable final VHInvoker accessor;
    private @Stable VHInvoker fastAccessor;
    private int numAccesses;

    protected VarHandleFieldAccessorImpl(Field field, VarHandle varHandle, boolean isReadOnly) {
        super(field);
        this.isReadOnly = isReadOnly;
        this.varHandle = varHandle;
        this.accessor = new VHInvokerDelegate(varHandle);
    }

    protected void ensureObj(Object o) {
        // for compatibility, check the receiver object first
        // throw NullPointerException if o is null
            if (!field.getDeclaringClass().isAssignableFrom(o.getClass())) {
            throwSetIllegalArgumentException(o);
        }
    }

    @ForceInline
    final VHInvoker accessor() {
        var accessor = fastAccessor;
        if (accessor != null) {
            return accessor;
        }
        return slowAccessor();
    }

    @DontInline
    final VHInvoker slowAccessor() {
        var accessor = this.accessor;
        if (++numAccesses > ReflectionFactory.inflationThreshold()) {
            this.fastAccessor = accessor = MethodHandleAccessorFactory.newVarHandleAccessor(field, varHandle);
        }
        return accessor;
    }

    /**
     * IllegalArgumentException because Field::get on the specified object, which
     * is not an instance of the class or interface declaring the underlying method
     */
    protected IllegalArgumentException newGetIllegalArgumentException(Class<?> type) {
        return new IllegalArgumentException(getMessage(true, type.getName()));
    }

    /**
     * IllegalArgumentException because Field::set on the specified object, which
     * is not an instance of the class or interface declaring the underlying method
     */
    protected IllegalArgumentException newSetIllegalArgumentException(Class<?> type) {
        return new IllegalArgumentException(getMessage(false, type.getName()));
    }


}
