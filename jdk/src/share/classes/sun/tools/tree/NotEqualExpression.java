/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.Assembler;
import sun.tools.asm.Label;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class NotEqualExpression extends BinaryEqualityExpression {
    /**
     * constructor
     */
    public NotEqualExpression(long where, Expression left, Expression right) {
        super(NE, where, left, right);
    }

    /**
     * Evaluate
     */
    Expression eval(int a, int b) {
        return new BooleanExpression(where, a != b);
    }
    Expression eval(long a, long b) {
        return new BooleanExpression(where, a != b);
    }
    Expression eval(float a, float b) {
        return new BooleanExpression(where, a != b);
    }
    Expression eval(double a, double b) {
        return new BooleanExpression(where, a != b);
    }
    Expression eval(boolean a, boolean b) {
        return new BooleanExpression(where, a != b);
    }

    /**
     * Simplify
     */
    Expression simplify() {
        if (left.isConstant() && !right.isConstant()) {
            return new NotEqualExpression(where, right, left);
        }
        return this;
    }

    /**
     * Code
     */
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        left.codeValue(env, ctx, asm);
        switch (left.type.getTypeCode()) {
          case TC_BOOLEAN:
          case TC_INT:
            if (!right.equals(0)) {
                right.codeValue(env, ctx, asm);
                asm.add(where, whenTrue ? opc_if_icmpne : opc_if_icmpeq, lbl, whenTrue);
                return;
            }
            break;
          case TC_LONG:
            right.codeValue(env, ctx, asm);
            asm.add(where, opc_lcmp);
            break;
          case TC_FLOAT:
            right.codeValue(env, ctx, asm);
            asm.add(where, opc_fcmpl);
            break;
          case TC_DOUBLE:
            right.codeValue(env, ctx, asm);
            asm.add(where, opc_dcmpl);
            break;
          case TC_ARRAY:
          case TC_CLASS:
          case TC_NULL:
            if (right.equals(0)) {
                asm.add(where, whenTrue ? opc_ifnonnull : opc_ifnull, lbl, whenTrue);
            } else {
                right.codeValue(env, ctx, asm);
                asm.add(where, whenTrue ? opc_if_acmpne : opc_if_acmpeq, lbl, whenTrue);
            }
            return;
          default:
            throw new CompilerError("Unexpected Type");
        }
        asm.add(where, whenTrue ? opc_ifne : opc_ifeq, lbl, whenTrue);
    }
}
