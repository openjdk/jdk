/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn;

import java.dyn.*;
import static sun.dyn.MemberName.uncaughtException;

/**
 * Unary function composition, useful for many small plumbing jobs.
 * The invoke method takes a single reference argument, and returns a reference
 * Internally, it first calls the {@code filter} method on the argument,
 * Making up the difference between the raw method type and the
 * final method type is the responsibility of a JVM-level adapter.
 * @author jrose
 */
public class FilterOneArgument extends BoundMethodHandle {
    protected final MethodHandle filter;  // Object -> Object
    protected final MethodHandle target;  // Object -> Object

    @Override
    public String toString() {
        return target.toString();
    }

    protected Object invoke(Object argument) throws Throwable {
        Object filteredArgument = filter.invokeExact(argument);
        return target.invokeExact(filteredArgument);
    }

    private static final MethodHandle INVOKE;
    static {
        try {
            INVOKE =
                MethodHandleImpl.IMPL_LOOKUP.findVirtual(FilterOneArgument.class, "invoke",
                                                         MethodType.genericMethodType(1));
        } catch (ReflectiveOperationException ex) {
            throw uncaughtException(ex);
        }
    }

    protected FilterOneArgument(MethodHandle filter, MethodHandle target) {
        super(Access.TOKEN, INVOKE);
        this.filter = filter;
        this.target = target;
    }

    public static MethodHandle make(MethodHandle filter, MethodHandle target) {
        if (filter == null)  return target;
        if (target == null)  return filter;
        return new FilterOneArgument(filter, target);
    }

//    MethodHandle make(MethodHandle filter1, MethodHandle filter2, MethodHandle target) {
//        MethodHandle filter = make(filter1, filter2);
//        return make(filter, target);
//    }
}
