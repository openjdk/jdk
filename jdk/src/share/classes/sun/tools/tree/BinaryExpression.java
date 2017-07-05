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
import sun.tools.asm.Label;
import sun.tools.asm.Assembler;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class BinaryExpression extends UnaryExpression {
    Expression left;

    /**
     * Constructor
     */
    BinaryExpression(int op, long where, Type type, Expression left, Expression right) {
        super(op, where, type, right);
        this.left = left;
    }

    /**
     * Order the expression based on precedence
     */
    public Expression order() {
        if (precedence() > left.precedence()) {
            UnaryExpression e = (UnaryExpression)left;
            left = e.right;
            e.right = order();
            return e;
        }
        return this;
    }

    /**
     * Check a binary expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        vset = left.checkValue(env, ctx, vset, exp);
        vset = right.checkValue(env, ctx, vset, exp);

        int tm = left.type.getTypeMask() | right.type.getTypeMask();
        if ((tm & TM_ERROR) != 0) {
            return vset;
        }
        selectType(env, ctx, tm);

        if (type.isType(TC_ERROR)) {
            env.error(where, "invalid.args", opNames[op]);
        }
        return vset;
    }

    /**
     * Check if constant
     */
    public boolean isConstant() {
        switch (op) {
        case MUL:
        case DIV:
        case REM:
        case ADD:
        case SUB:
        case LSHIFT:
        case RSHIFT:
        case URSHIFT:
        case LT:
        case LE:
        case GT:
        case GE:
        case EQ:
        case NE:
        case BITAND:
        case BITXOR:
        case BITOR:
        case AND:
        case OR:
            return left.isConstant() && right.isConstant();
        }
        return false;
    }
    /**
     * Evaluate
     */
    Expression eval(int a, int b) {
        return this;
    }
    Expression eval(long a, long b) {
        return this;
    }
    Expression eval(float a, float b) {
        return this;
    }
    Expression eval(double a, double b) {
        return this;
    }
    Expression eval(boolean a, boolean b) {
        return this;
    }
    Expression eval(String a, String b) {
        return this;
    }
    Expression eval() {
        // See also the eval() code in BinaryShiftExpression.java.
        if (left.op == right.op) {
            switch (left.op) {
              case BYTEVAL:
              case CHARVAL:
              case SHORTVAL:
              case INTVAL:
                return eval(((IntegerExpression)left).value, ((IntegerExpression)right).value);
              case LONGVAL:
                return eval(((LongExpression)left).value, ((LongExpression)right).value);
              case FLOATVAL:
                return eval(((FloatExpression)left).value, ((FloatExpression)right).value);
              case DOUBLEVAL:
                return eval(((DoubleExpression)left).value, ((DoubleExpression)right).value);
              case BOOLEANVAL:
                return eval(((BooleanExpression)left).value, ((BooleanExpression)right).value);
              case STRINGVAL:
                return eval(((StringExpression)left).value, ((StringExpression)right).value);
            }
        }
        return this;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        left = left.inline(env, ctx);
        right = right.inline(env, ctx);
        return (left == null) ? right : new CommaExpression(where, left, right);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        left = left.inlineValue(env, ctx);
        right = right.inlineValue(env, ctx);
        try {
            return eval().simplify();
        } catch (ArithmeticException e) {
            // Got rid of this error message.  It isn't illegal to
            // have a program which does a constant division by
            // zero.  We return `this' to make the compiler to
            // generate code here.
            // (bugs 4019304, 4089107).
            //
            // env.error(where, "arithmetic.exception");
            return this;
        }
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        BinaryExpression e = (BinaryExpression)clone();
        if (left != null) {
            e.left = left.copyInline(ctx);
        }
        if (right != null) {
            e.right = right.copyInline(ctx);
        }
        return e;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1 + ((left != null) ? left.costInline(thresh, env, ctx) : 0) +
                   ((right != null) ? right.costInline(thresh, env, ctx) : 0);
    }

    /**
     * Code
     */
    void codeOperation(Environment env, Context ctx, Assembler asm) {
        throw new CompilerError("codeOperation: " + opNames[op]);
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        if (type.isType(TC_BOOLEAN)) {
            Label l1 = new Label();
            Label l2 = new Label();

            codeBranch(env, ctx, asm, l1, true);
            asm.add(true, where, opc_ldc, 0);
            asm.add(true, where, opc_goto, l2);
            asm.add(l1);
            asm.add(true, where, opc_ldc, 1);
            asm.add(l2);
        } else {
            left.codeValue(env, ctx, asm);
            right.codeValue(env, ctx, asm);
            codeOperation(env, ctx, asm);
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        if (left != null) {
            left.print(out);
        } else {
            out.print("<null>");
        }
        out.print(" ");
        if (right != null) {
            right.print(out);
        } else {
            out.print("<null>");
        }
        out.print(")");
    }
}
