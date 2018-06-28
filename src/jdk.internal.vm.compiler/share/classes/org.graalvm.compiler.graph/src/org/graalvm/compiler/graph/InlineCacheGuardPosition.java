/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.graph;

import java.util.Objects;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InlineCacheGuardPosition extends NodeSourcePosition {
    private final ResolvedJavaType dispatchedType;
    private final ResolvedJavaMethod concreteMethod;
    private final int hashCode;

    public InlineCacheGuardPosition(NodeSourcePosition callStack, ResolvedJavaType dispatchedType, ResolvedJavaMethod targetMethod) {
        super(callStack.getCaller(), callStack.getMethod(), callStack.getBCI());
        this.concreteMethod = targetMethod;
        this.dispatchedType = dispatchedType;
        this.hashCode = super.hashCode() + 7 * ((dispatchedType == null) ? 0 : dispatchedType.hashCode()) + 31 * targetMethod.hashCode();
    }

    public ResolvedJavaType getDispatchedType() {
        return dispatchedType;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return concreteMethod;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            InlineCacheGuardPosition that = (InlineCacheGuardPosition) obj;
            if (hashCode != that.hashCode) {
                return false;
            }
            if (this.getBCI() == that.getBCI() && Objects.equals(this.getMethod(), that.getMethod()) && Objects.equals(this.getCaller(), that.getCaller()) &&
                            Objects.equals(this.concreteMethod, that.concreteMethod) && Objects.equals(this.dispatchedType, that.dispatchedType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "dispatchedType=" + (dispatchedType == null ? "null" : dispatchedType.getName()) + " target_method=" + concreteMethod.getName() + " target_method_class=" +
                        concreteMethod.getDeclaringClass().getName() + " position=" + super.toString();
    }
}
