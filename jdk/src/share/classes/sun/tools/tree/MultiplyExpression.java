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

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class MultiplyExpression extends BinaryArithmeticExpression {
    /**
     * constructor
     */
    public MultiplyExpression(long where, Expression left, Expression right) {
        super(MUL, where, left, right);
    }

    /**
     * Evaluate
     */
    Expression eval(int a, int b) {
        return new IntExpression(where, a * b);
    }
    Expression eval(long a, long b) {
        return new LongExpression(where, a * b);
    }
    Expression eval(float a, float b) {
        return new FloatExpression(where, a * b);
    }
    Expression eval(double a, double b) {
        return new DoubleExpression(where, a * b);
    }

    /**
     * Simplify
     */
    Expression simplify() {
        if (left.equals(1)) {
            return right;
        }
        if (right.equals(1)) {
            return left;
        }
        return this;
    }

    /**
     * Code
     */
    void codeOperation(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_imul + type.getTypeCodeOffset());
    }
}
