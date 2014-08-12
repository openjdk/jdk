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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class UnaryExpression extends Expression {
    Expression right;

    /**
     * Constructor
     */
    UnaryExpression(int op, long where, Type type, Expression right) {
        super(op, where, type);
        this.right = right;
    }

    /**
     * Order the expression based on precedence
     */
    public Expression order() {
        if (precedence() > right.precedence()) {
            UnaryExpression e = (UnaryExpression)right;
            right = e.right;
            e.right = order();
            return e;
        }
        return this;
    }

    /**
     * Select the type of the expression
     */
    void selectType(Environment env, Context ctx, int tm) {
        throw new CompilerError("selectType: " + opNames[op]);
    }

    /**
     * Check a unary expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        vset = right.checkValue(env, ctx, vset, exp);

        int tm = right.type.getTypeMask();
        selectType(env, ctx, tm);
        if (((tm & TM_ERROR) == 0) && type.isType(TC_ERROR)) {
            env.error(where, "invalid.arg", opNames[op]);
        }
        return vset;
    }

    /**
     * Check if constant
     */
    public boolean isConstant() {
        switch (op) {
        case POS:
        case NEG:
        case BITNOT:
        case NOT:
        case EXPR:
        case CONVERT: // generated inside of CastExpression
            return right.isConstant();
        }
        return false;
    }

    /**
     * Evaluate
     */
    Expression eval(int a) {
        return this;
    }
    Expression eval(long a) {
        return this;
    }
    Expression eval(float a) {
        return this;
    }
    Expression eval(double a) {
        return this;
    }
    Expression eval(boolean a) {
        return this;
    }
    Expression eval(String a) {
        return this;
    }
    Expression eval() {
        switch (right.op) {
          case BYTEVAL:
          case CHARVAL:
          case SHORTVAL:
          case INTVAL:
            return eval(((IntegerExpression)right).value);
          case LONGVAL:
            return eval(((LongExpression)right).value);
          case FLOATVAL:
            return eval(((FloatExpression)right).value);
          case DOUBLEVAL:
            return eval(((DoubleExpression)right).value);
          case BOOLEANVAL:
            return eval(((BooleanExpression)right).value);
          case STRINGVAL:
            return eval(((StringExpression)right).value);
        }
        return this;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return right.inline(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
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
            // I am not positive that this catch is ever reached.
            //
            // env.error(where, "arithmetic.exception");
            return this;
        }
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        UnaryExpression e = (UnaryExpression)clone();
        if (right != null) {
            e.right = right.copyInline(ctx);
        }
        return e;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1 + right.costInline(thresh, env, ctx);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        right.print(out);
        out.print(")");
    }
}
