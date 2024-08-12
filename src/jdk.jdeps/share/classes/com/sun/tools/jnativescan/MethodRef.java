/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jnativescan;

import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

record MethodRef(ClassDesc owner, String name, MethodTypeDesc type) {
    public static MethodRef ofModel(MethodModel model) {
        return new MethodRef(model.parent().orElseThrow().thisClass().asSymbol(),
                model.methodName().stringValue(), model.methodTypeSymbol());
    }

    public static MethodRef ofInvokeInstruction(InvokeInstruction instruction) {
        return new MethodRef(instruction.owner().asSymbol(),
                instruction.name().stringValue(), instruction.typeSymbol());
    }

    @Override
    public String toString() {
        return JNativeScanTask.qualName(owner) + "::" + name + type.displayDescriptor();
    }
}
