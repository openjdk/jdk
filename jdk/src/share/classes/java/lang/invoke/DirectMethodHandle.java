/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.invoke.MethodHandleNatives.Constants.*;

/**
 * The flavor of method handle which emulates invokespecial or invokestatic.
 * @author jrose
 */
class DirectMethodHandle extends MethodHandle {
    //inherited oop    vmtarget;    // methodOop or virtual class/interface oop
    private final int  vmindex;     // method index within class or interface
    { vmindex = VM_INDEX_UNINITIALIZED; }  // JVM may change this

    // Constructors in this class *must* be package scoped or private.
    DirectMethodHandle(MethodType mtype, MemberName m, boolean doDispatch, Class<?> lookupClass) {
        super(mtype);

        assert(m.isMethod() || !doDispatch && m.isConstructor());
        if (!m.isResolved())
            throw new InternalError();

        MethodHandleNatives.init(this, (Object) m, doDispatch, lookupClass);
    }

    boolean isValid() {
        return (vmindex != VM_INDEX_UNINITIALIZED);
    }
}
