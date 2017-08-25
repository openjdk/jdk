/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NodeSourcePosition extends BytecodePosition {

    /**
     * The receiver of the method this frame refers to.
     */
    private final JavaConstant receiver;
    private final int hashCode;

    public NodeSourcePosition(JavaConstant receiver, NodeSourcePosition caller, ResolvedJavaMethod method, int bci) {
        super(caller, method, bci);
        if (caller == null) {
            this.hashCode = 31 * bci + method.hashCode();
        } else {
            this.hashCode = caller.hashCode * 7 + 31 * bci + method.hashCode();
        }
        this.receiver = receiver;
        assert receiver == null || method.getDeclaringClass().isInstance(receiver);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            NodeSourcePosition that = (NodeSourcePosition) obj;
            if (hashCode != that.hashCode) {
                return false;
            }
            if (this.getBCI() == that.getBCI() && Objects.equals(this.getMethod(), that.getMethod()) && Objects.equals(this.getCaller(), that.getCaller()) &&
                            Objects.equals(this.receiver, that.receiver)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public JavaConstant getReceiver() {
        return receiver;
    }

    @Override
    public NodeSourcePosition getCaller() {
        return (NodeSourcePosition) super.getCaller();
    }

    public NodeSourcePosition addCaller(JavaConstant newCallerReceiver, NodeSourcePosition link) {
        if (getCaller() == null) {
            assert newCallerReceiver == null || receiver == null : "replacing receiver";
            return new NodeSourcePosition(newCallerReceiver, link, getMethod(), getBCI());
        } else {
            return new NodeSourcePosition(receiver, getCaller().addCaller(newCallerReceiver, link), getMethod(), getBCI());
        }
    }

    public NodeSourcePosition addCaller(NodeSourcePosition link) {
        return addCaller(null, link);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        NodeSourcePosition pos = this;
        while (pos != null) {
            MetaUtil.appendLocation(sb.append("at "), pos.getMethod(), pos.getBCI());
            if (pos.receiver != null) {
                sb.append("receiver=" + pos.receiver + " ");
            }
            pos = pos.getCaller();
            if (pos != null) {
                sb.append(CodeUtil.NEW_LINE);
            }
        }
        return sb.toString();
    }
}
