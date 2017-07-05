/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.common;

import java.lang.reflect.Field;
import java.lang.reflect.Executable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;

public class CTVMUtilities {
    /*
     * A method to return HotSpotResolvedJavaMethod object using class object
     * and method as input
     */
    public static HotSpotResolvedJavaMethodImpl getResolvedMethod(Class<?> cls,
            Executable method) {
        if (!(method instanceof Method || method instanceof Constructor)) {
            throw new Error("wrong executable type " + method.getClass());
        }
        Field slotField;
        int slot;
        try {
            slotField = method.getClass().getDeclaredField("slot");
            boolean old = slotField.isAccessible();
            slotField.setAccessible(true);
            slot = slotField.getInt(method);
            slotField.setAccessible(old);
        } catch (ReflectiveOperationException e) {
            throw new Error("TEST BUG: Can't get slot field", e);
        }
        return CompilerToVMHelper.getResolvedJavaMethodAtSlot(cls, slot);
    }

    public static HotSpotResolvedJavaMethodImpl getResolvedMethod(
            Executable method) {
        return getResolvedMethod(method.getDeclaringClass(), method);
    }
}
