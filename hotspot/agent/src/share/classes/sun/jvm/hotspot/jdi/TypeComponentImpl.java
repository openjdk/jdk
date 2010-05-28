/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.jdi;

import com.sun.jdi.*;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.InstanceKlass;

import java.util.List;

/**
 * There is no SA class that corresponds to this.  Therefore,
 * all the methods in this class which involve the SA mirror class
 * have to be implemented in the subclasses.
 */
abstract public class TypeComponentImpl extends MirrorImpl
    implements TypeComponent {

    protected final ReferenceTypeImpl declaringType;
    protected String signature;

    TypeComponentImpl(VirtualMachine vm, ReferenceTypeImpl declaringType) {
        super(vm);
        this.declaringType = declaringType;
    }

    public ReferenceType declaringType() {
        return declaringType;
    }

    public String signature() {
        return signature;
    }

    abstract public String name();
    abstract public int modifiers();
    abstract public boolean isPackagePrivate();
    abstract public boolean isPrivate();
    abstract public boolean isProtected();
    abstract public boolean isPublic();
    abstract public boolean isStatic();
    abstract public boolean isFinal();
    abstract public int hashCode();
}
