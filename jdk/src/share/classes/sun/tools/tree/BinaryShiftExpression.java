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

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class BinaryShiftExpression extends BinaryExpression {
    /**
     * constructor
     */
    public BinaryShiftExpression(int op, long where, Expression left, Expression right) {
        super(op, where, left.type, left, right);
    }

    /**
     * Evaluate the expression
     */
    Expression eval() {
        // The eval code in BinaryExpression.java only works correctly
        // for arithmetic expressions.  For shift expressions, we get cases
        // where the left and right operand may legitimately be of mixed
        // types (long and int).  This is a fix for 4082814.
        if (left.op == LONGVAL && right.op == INTVAL) {
            return eval(((LongExpression)left).value,
                        ((IntExpression)right).value);
        }

        // Delegate the rest of the cases to our parent, so as to minimize
        // impact on existing behavior.
        return super.eval();
    }

    /**
     * Select the type
     */
    void selectType(Environment env, Context ctx, int tm) {
        if (left.type == Type.tLong) {
            type = Type.tLong;
        } else if (left.type.inMask(TM_INTEGER)) {
            type = Type.tInt;
            left = convert(env, ctx, type, left);
        } else {
            type = Type.tError;
        }
        if (right.type.inMask(TM_INTEGER)) {
            right = new ConvertExpression(where, Type.tInt, right);
        } else {
            right = convert(env, ctx, Type.tInt, right);
        }
    }
}
