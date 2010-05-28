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
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class AndExpression extends BinaryLogicalExpression {
    /**
     * constructor
     */
    public AndExpression(long where, Expression left, Expression right) {
        super(AND, where, left, right);
    }

    /*
     * Check an "and" expression.
     *
     * cvars is modified so that
     *    cvar.vsTrue indicates variables with a known value if
     *        both the left and right hand side are true
     *    cvars.vsFalse indicates variables with a known value
     *        either the left or right hand side is false
     */
    public void checkCondition(Environment env, Context ctx, Vset vset,
                               Hashtable exp, ConditionVars cvars) {
        // Find out when the left side is true/false
        left.checkCondition(env, ctx, vset, exp, cvars);
        left = convert(env, ctx, Type.tBoolean, left);
        Vset vsTrue = cvars.vsTrue.copy();
        Vset vsFalse = cvars.vsFalse.copy();

        // Only look at the right side if the left side is true
        right.checkCondition(env, ctx, vsTrue, exp, cvars);
        right = convert(env, ctx, Type.tBoolean, right);

        // cvars.vsTrue already reports when both returned true
        // cvars.vsFalse must be set to either the left or right side
        //    returning false
        cvars.vsFalse = cvars.vsFalse.join(vsFalse);
    }

    /**
     * Evaluate
     */
    Expression eval(boolean a, boolean b) {
        return new BooleanExpression(where, a && b);
    }

    /**
     * Simplify
     */
    Expression simplify() {
        if (left.equals(true)) {
            return right;
        }
        if (right.equals(false)) {
            // Preserve effects of left argument.
            return new CommaExpression(where, left, right).simplify();
        }
        if (right.equals(true)) {
            return left;
        }
        if (left.equals(false)) {
            return left;
        }
        return this;
    }

    /**
     * Code
     */
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        if (whenTrue) {
            Label lbl2 = new Label();
            left.codeBranch(env, ctx, asm, lbl2, false);
            right.codeBranch(env, ctx, asm, lbl, true);
            asm.add(lbl2);
        } else {
            left.codeBranch(env, ctx, asm, lbl, false);
            right.codeBranch(env, ctx, asm, lbl, false);
        }
    }
}
