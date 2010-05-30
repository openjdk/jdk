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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ConditionalExpression extends BinaryExpression {
    Expression cond;

    /**
     * Constructor
     */
    public ConditionalExpression(long where, Expression cond, Expression left, Expression right) {
        super(COND, where, Type.tError, left, right);
        this.cond = cond;
    }

    /**
     * Order the expression based on precedence
     */
    public Expression order() {
        if (precedence() > cond.precedence()) {
            UnaryExpression e = (UnaryExpression)cond;
            cond = e.right;
            e.right = order();
            return e;
        }
        return this;
    }

    /**
     * Check the expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        ConditionVars cvars = cond.checkCondition(env, ctx, vset, exp);
        vset = left.checkValue(env, ctx, cvars.vsTrue, exp).join(
               right.checkValue(env, ctx, cvars.vsFalse, exp) );
        cond = convert(env, ctx, Type.tBoolean, cond);

        int tm = left.type.getTypeMask() | right.type.getTypeMask();
        if ((tm & TM_ERROR) != 0) {
            type = Type.tError;
            return vset;
        }
        if (left.type.equals(right.type)) {
            type = left.type;
        } else if ((tm & TM_DOUBLE) != 0) {
            type = Type.tDouble;
        } else if ((tm & TM_FLOAT) != 0) {
            type = Type.tFloat;
        } else if ((tm & TM_LONG) != 0) {
            type = Type.tLong;
        } else if ((tm & TM_REFERENCE) != 0) {
            try {
                // This is wrong.  We should be using their most common
                // ancestor, instead.
                type = env.implicitCast(right.type, left.type)
                    ? left.type : right.type;
            } catch (ClassNotFound e) {
                type = Type.tError;
            }
        } else if (((tm & TM_CHAR) != 0) && left.fitsType(env, ctx, Type.tChar) && right.fitsType(env, ctx, Type.tChar)) {
            type = Type.tChar;
        } else if (((tm & TM_SHORT) != 0) && left.fitsType(env, ctx, Type.tShort) && right.fitsType(env, ctx, Type.tShort)) {
            type = Type.tShort;
        } else if (((tm & TM_BYTE) != 0) && left.fitsType(env, ctx, Type.tByte) && right.fitsType(env, ctx, Type.tByte)) {
            type = Type.tByte;
        } else {
            type = Type.tInt;
        }

        left = convert(env, ctx, type, left);
        right = convert(env, ctx, type, right);
        return vset;
    }

    public Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        vset = cond.checkValue(env, ctx, vset, exp);
        cond = convert(env, ctx, Type.tBoolean, cond);
        return left.check(env, ctx, vset.copy(), exp).join(right.check(env, ctx, vset, exp));
    }

    /**
     * Check if constant
     */
    public boolean isConstant() {
        return cond.isConstant() && left.isConstant() && right.isConstant();
    }

    /**
     * Simplify
     */
    Expression simplify() {
        if (cond.equals(true)) {
            return left;
        }
        if (cond.equals(false)) {
            return right;
        }
        return this;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        left = left.inline(env, ctx);
        right = right.inline(env, ctx);
        if ((left == null) && (right == null)) {
            return cond.inline(env, ctx);
        }
        if (left == null) {
            left = right;
            right = null;
            cond = new NotExpression(where, cond);
        }
        cond = cond.inlineValue(env, ctx);
        return simplify();
    }

    public Expression inlineValue(Environment env, Context ctx) {
        cond = cond.inlineValue(env, ctx);
        left = left.inlineValue(env, ctx);
        right = right.inlineValue(env, ctx);
        return simplify();
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        // We need to check if right is null in case costInline()
        // is called after this expression has been inlined.
        // This call can happen, for example, in MemberDefinition#cleanup().
        // (Fix for 4069861).
        return 1 +
            cond.costInline(thresh, env, ctx) +
            left.costInline(thresh, env, ctx) +
            ((right == null) ? 0 : right.costInline(thresh, env, ctx));
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        ConditionalExpression e = (ConditionalExpression)clone();
        e.cond = cond.copyInline(ctx);
        e.left = left.copyInline(ctx);

        // If copyInline() is called after inlining is complete,
        // right could be null.
        e.right = (right == null) ? null : right.copyInline(ctx);

        return e;
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        Label l1 = new Label();
        Label l2 = new Label();

        cond.codeBranch(env, ctx, asm, l1, false);
        left.codeValue(env, ctx, asm);
        asm.add(where, opc_goto, l2);
        asm.add(l1);
        right.codeValue(env, ctx, asm);
        asm.add(l2);
    }
    public void code(Environment env, Context ctx, Assembler asm) {
        Label l1 = new Label();
        cond.codeBranch(env, ctx, asm, l1, false);
        left.code(env, ctx, asm);
        if (right != null) {
            Label l2 = new Label();
            asm.add(where, opc_goto, l2);
            asm.add(l1);
            right.code(env, ctx, asm);
            asm.add(l2);
        } else {
            asm.add(l1);
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        cond.print(out);
        out.print(" ");
        left.print(out);
        out.print(" ");
        if (right != null) {
            right.print(out);
        } else {
            out.print("<null>");
        }
        out.print(")");
    }
}
