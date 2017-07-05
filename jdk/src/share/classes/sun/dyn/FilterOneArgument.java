/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.dyn;

import java.dyn.JavaMethodHandle;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;

/**
 * Unary function composition, useful for many small plumbing jobs.
 * The invoke method takes a single reference argument, and returns a reference
 * Internally, it first calls the {@code filter} method on the argument,
 * Making up the difference between the raw method type and the
 * final method type is the responsibility of a JVM-level adapter.
 * @author jrose
 */
public class FilterOneArgument extends JavaMethodHandle {
    protected final MethodHandle filter;  // Object -> Object
    protected final MethodHandle target;  // Object -> Object

    protected Object entryPoint(Object argument) {
        Object filteredArgument = filter.<Object>invoke(argument);
        return target.<Object>invoke(filteredArgument);
    }

    private static final MethodHandle entryPoint =
        MethodHandleImpl.IMPL_LOOKUP.findVirtual(FilterOneArgument.class, "entryPoint", MethodType.makeGeneric(1));

    protected FilterOneArgument(MethodHandle filter, MethodHandle target) {
        super(entryPoint);
        this.filter = filter;
        this.target = target;
    }

    public static MethodHandle make(MethodHandle filter, MethodHandle target) {
        if (filter == null)  return target;
        if (target == null)  return filter;
        return new FilterOneArgument(filter, target);
    }

    public String toString() {
        return filter + "|>" + target;
    }

//    MethodHandle make(MethodHandle filter1, MethodHandle filter2, MethodHandle target) {
//        MethodHandle filter = make(filter1, filter2);
//        return make(filter, target);
//    }
}
