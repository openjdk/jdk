/*
 * Copyright 1994-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.Assembler;
import sun.tools.asm.Label;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class NotExpression extends UnaryExpression {
    /**
     * Constructor
     */
    public NotExpression(long where, Expression right) {
        super(NOT, where, Type.tBoolean, right);
    }

    /**
     * Select the type of the expression
     */
    void selectType(Environment env, Context ctx, int tm) {
        right = convert(env, ctx, Type.tBoolean, right);
    }

    /*
     * Check a "not" expression.
     *
     * cvars is modified so that
     *    cvar.vsTrue indicates variables with a known value if
     *         the expression is true.
     *    cvars.vsFalse indicates variables with a known value if
     *         the expression is false
     *
     * For "not" expressions, we look at the inside expression, and then
     * swap true and false.
     */

    public void checkCondition(Environment env, Context ctx, Vset vset,
                               Hashtable exp, ConditionVars cvars) {
        right.checkCondition(env, ctx, vset, exp, cvars);
        right = convert(env, ctx, Type.tBoolean, right);
        // swap true and false
        Vset temp = cvars.vsFalse;
        cvars.vsFalse = cvars.vsTrue;
        cvars.vsTrue = temp;
    }

    /**
     * Evaluate
     */
    Expression eval(boolean a) {
        return new BooleanExpression(where, !a);
    }

    /**
     * Simplify
     */
    Expression simplify() {
        // Check if the expression can be optimized
        switch (right.op) {
          case NOT:
            return ((NotExpression)right).right;

          case EQ:
          case NE:
          case LT:
          case LE:
          case GT:
          case GE:
            break;

          default:
            return this;
        }

        // Can't negate real comparisons
        BinaryExpression bin = (BinaryExpression)right;
        if (bin.left.type.inMask(TM_REAL)) {
            return this;
        }

        // Negate comparison
        switch (right.op) {
          case EQ:
            return new NotEqualExpression(where, bin.left, bin.right);
          case NE:
            return new EqualExpression(where, bin.left, bin.right);
          case LT:
            return new GreaterOrEqualExpression(where, bin.left, bin.right);
          case LE:
            return new GreaterExpression(where, bin.left, bin.right);
          case GT:
            return new LessOrEqualExpression(where, bin.left, bin.right);
          case GE:
            return new LessExpression(where, bin.left, bin.right);
        }
        return this;
    }

    /**
     * Code
     */
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        right.codeBranch(env, ctx, asm, lbl, !whenTrue);
    }

    /**
     * Instead of relying on the default code generation which uses
     * conditional branching, generate a simpler stream using XOR.
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        right.codeValue(env, ctx, asm);
        asm.add(where, opc_ldc, new Integer(1));
        asm.add(where, opc_ixor);
    }

}
