/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents an inlined {@link Constant} value.
 */
public class ConstantValue extends Value {

    private final Constant constant;

    public ConstantValue(ValueKind<?> kind, Constant constant) {
        super(kind);
        this.constant = constant;
    }

    public Constant getConstant() {
        return constant;
    }

    public boolean isJavaConstant() {
        return constant instanceof JavaConstant;
    }

    public JavaConstant getJavaConstant() {
        return (JavaConstant) constant;
    }

    @Override
    public String toString() {
        return constant.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantValue) {
            ConstantValue other = (ConstantValue) obj;
            return super.equals(other) && this.constant.equals(other.constant);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return constant.hashCode() + super.hashCode();
    }
}
