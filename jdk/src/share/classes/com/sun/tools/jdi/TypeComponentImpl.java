/*
 * Copyright 1998-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.jdi;

import com.sun.jdi.*;

import java.util.List;

abstract public class TypeComponentImpl extends MirrorImpl
    implements TypeComponent
{
    protected final long ref;
    protected final String name;
    protected final String signature;
    protected final String genericSignature;
    protected final ReferenceTypeImpl declaringType;
    private final int modifiers;

    TypeComponentImpl(VirtualMachine vm, ReferenceTypeImpl declaringType,
                      long ref,
                      String name, String signature,
                      String genericSignature, int modifiers) {
        // The generic signature is set when this is created.
        super(vm);
        this.declaringType = declaringType;
        this.ref = ref;
        this.name = name;
        this.signature = signature;
        if (genericSignature != null && genericSignature.length() != 0) {
            this.genericSignature = genericSignature;
        } else {
            this.genericSignature = null;
        }
        this.modifiers = modifiers;
    }

    public String name() {
        return name;
    }

    public String signature() {
        return signature;
    }
    public String genericSignature() {
        return genericSignature;
    }

    public int modifiers() {
        return modifiers;
    }

    public ReferenceType declaringType() {
        return declaringType;
    }

    public boolean isStatic() {
        return isModifierSet(VMModifiers.STATIC);
    }

    public boolean isFinal() {
        return isModifierSet(VMModifiers.FINAL);
    }

    public boolean isPrivate() {
        return isModifierSet(VMModifiers.PRIVATE);
    }

    public boolean isPackagePrivate() {
        return !isModifierSet(VMModifiers.PRIVATE
                              | VMModifiers.PROTECTED
                              | VMModifiers.PUBLIC);
    }

    public boolean isProtected() {
        return isModifierSet(VMModifiers.PROTECTED);
    }

    public boolean isPublic() {
        return isModifierSet(VMModifiers.PUBLIC);
    }

    public boolean isSynthetic() {
        return isModifierSet(VMModifiers.SYNTHETIC);
    }

    long ref() {
        return ref;
    }

    boolean isModifierSet(int compareBits) {
        return (modifiers & compareBits) != 0;
    }
}
