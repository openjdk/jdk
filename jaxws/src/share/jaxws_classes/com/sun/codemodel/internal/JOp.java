/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;


/**
 * JClass for generating expressions containing operators
 */

abstract public class JOp {

    private JOp() {
    }


    /**
     * Determine whether the top level of an expression involves an
     * operator.
     */
    static boolean hasTopOp(JExpression e) {
        return (e instanceof UnaryOp) || (e instanceof BinaryOp);
    }

    /* -- Unary operators -- */

    static private class UnaryOp extends JExpressionImpl {

        protected String op;
        protected JExpression e;
        protected boolean opFirst = true;

        UnaryOp(String op, JExpression e) {
            this.op = op;
            this.e = e;
        }

        UnaryOp(JExpression e, String op) {
            this.op = op;
            this.e = e;
            opFirst = false;
        }

        public void generate(JFormatter f) {
            if (opFirst)
                f.p('(').p(op).g(e).p(')');
            else
                f.p('(').g(e).p(op).p(')');
        }

    }

    public static JExpression minus(JExpression e) {
        return new UnaryOp("-", e);
    }

    /**
     * Logical not <tt>'!x'</tt>.
     */
    public static JExpression not(JExpression e) {
        if (e == JExpr.TRUE) return JExpr.FALSE;
        if (e == JExpr.FALSE) return JExpr.TRUE;
        return new UnaryOp("!", e);
    }

    public static JExpression complement(JExpression e) {
        return new UnaryOp("~", e);
    }

    static private class TightUnaryOp extends UnaryOp {

        TightUnaryOp(JExpression e, String op) {
            super(e, op);
        }

        public void generate(JFormatter f) {
            if (opFirst)
                f.p(op).g(e);
            else
                f.g(e).p(op);
        }

    }

    public static JExpression incr(JExpression e) {
        return new TightUnaryOp(e, "++");
    }

    public static JExpression decr(JExpression e) {
        return new TightUnaryOp(e, "--");
    }


    /* -- Binary operators -- */

    static private class BinaryOp extends JExpressionImpl {

        String op;
        JExpression left;
        JGenerable right;

        BinaryOp(String op, JExpression left, JGenerable right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }

        public void generate(JFormatter f) {
            f.p('(').g(left).p(op).g(right).p(')');
        }

    }

    public static JExpression plus(JExpression left, JExpression right) {
        return new BinaryOp("+", left, right);
    }

    public static JExpression minus(JExpression left, JExpression right) {
        return new BinaryOp("-", left, right);
    }

    public static JExpression mul(JExpression left, JExpression right) {
        return new BinaryOp("*", left, right);
    }

    public static JExpression div(JExpression left, JExpression right) {
        return new BinaryOp("/", left, right);
    }

    public static JExpression mod(JExpression left, JExpression right) {
        return new BinaryOp("%", left, right);
    }

    public static JExpression shl(JExpression left, JExpression right) {
        return new BinaryOp("<<", left, right);
    }

    public static JExpression shr(JExpression left, JExpression right) {
        return new BinaryOp(">>", left, right);
    }

    public static JExpression shrz(JExpression left, JExpression right) {
        return new BinaryOp(">>>", left, right);
    }

    public static JExpression band(JExpression left, JExpression right) {
        return new BinaryOp("&", left, right);
    }

    public static JExpression bor(JExpression left, JExpression right) {
        return new BinaryOp("|", left, right);
    }

    public static JExpression cand(JExpression left, JExpression right) {
        if (left == JExpr.TRUE) return right;
        if (right == JExpr.TRUE) return left;
        if (left == JExpr.FALSE) return left;    // JExpr.FALSE
        if (right == JExpr.FALSE) return right;   // JExpr.FALSE
        return new BinaryOp("&&", left, right);
    }

    public static JExpression cor(JExpression left, JExpression right) {
        if (left == JExpr.TRUE) return left;    // JExpr.TRUE
        if (right == JExpr.TRUE) return right;   // JExpr.FALSE
        if (left == JExpr.FALSE) return right;
        if (right == JExpr.FALSE) return left;
        return new BinaryOp("||", left, right);
    }

    public static JExpression xor(JExpression left, JExpression right) {
        return new BinaryOp("^", left, right);
    }

    public static JExpression lt(JExpression left, JExpression right) {
        return new BinaryOp("<", left, right);
    }

    public static JExpression lte(JExpression left, JExpression right) {
        return new BinaryOp("<=", left, right);
    }

    public static JExpression gt(JExpression left, JExpression right) {
        return new BinaryOp(">", left, right);
    }

    public static JExpression gte(JExpression left, JExpression right) {
        return new BinaryOp(">=", left, right);
    }

    public static JExpression eq(JExpression left, JExpression right) {
        return new BinaryOp("==", left, right);
    }

    public static JExpression ne(JExpression left, JExpression right) {
        return new BinaryOp("!=", left, right);
    }

    public static JExpression _instanceof(JExpression left, JType right) {
        return new BinaryOp("instanceof", left, right);
    }

    /* -- Ternary operators -- */

    static private class TernaryOp extends JExpressionImpl {

        String op1;
        String op2;
        JExpression e1;
        JExpression e2;
        JExpression e3;

        TernaryOp(String op1, String op2,
                  JExpression e1, JExpression e2, JExpression e3) {
            this.e1 = e1;
            this.op1 = op1;
            this.e2 = e2;
            this.op2 = op2;
            this.e3 = e3;
        }

        public void generate(JFormatter f) {
            f.p('(').g(e1).p(op1).g(e2).p(op2).g(e3).p(')');
        }

    }

    public static JExpression cond(JExpression cond,
                                   JExpression ifTrue, JExpression ifFalse) {
        return new TernaryOp("?", ":", cond, ifTrue, ifFalse);
    }

}
