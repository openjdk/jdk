/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.classfile.impl;

import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.constant.ClassDesc;

public final class BoundLocalVariable
        extends AbstractBoundLocalVariable
        implements LocalVariableInfo,
                   LocalVariable {

    public BoundLocalVariable(CodeImpl code, int offset) {
        super(code, offset);
    }

    @Override
    public Utf8Entry type() {
        return secondaryEntry();
    }

    @Override
    public ClassDesc typeSymbol() {
        return Util.fieldTypeSymbol(type());
    }

    @Override
    public void writeTo(DirectCodeBuilder writer) {
        writer.addLocalVariable(this);
    }

    @Override
    public String toString() {
        return String.format("LocalVariable[name=%s, slot=%d, type=%s]", name().stringValue(), slot(), type().stringValue());
    }
}
