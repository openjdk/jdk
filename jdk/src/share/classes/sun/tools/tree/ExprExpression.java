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
import java.util.Hashtable;

/**
 * Parenthesised expressions.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */

public
class ExprExpression extends UnaryExpression {
    /**
     * Constructor
     */
    public ExprExpression(long where, Expression right) {
        super(EXPR, where, right.type, right);
    }

    /**
     * Check a condition.  We must pass it on to our unparenthesised form.
     */
    public void checkCondition(Environment env, Context ctx, Vset vset,
                               Hashtable exp, ConditionVars cvars) {
        right.checkCondition(env, ctx, vset, exp, cvars);
        type = right.type;
    }

    /**
     * Check the expression if it appears as an lvalue.
     * We just pass it on to our unparenthesized subexpression.
     * (Part of fix for 4090372)
     */
    public Vset checkAssignOp(Environment env, Context ctx,
                              Vset vset, Hashtable exp, Expression outside) {
        vset = right.checkAssignOp(env, ctx, vset, exp, outside);
        type = right.type;
        return vset;
    }

    /**
     * Delegate to our subexpression.
     * (Part of fix for 4090372)
     */
    public FieldUpdater getUpdater(Environment env, Context ctx) {
        return right.getUpdater(env, ctx);
    }

    // Allow (x) = 9;
    //
    // I will hold off on this until I'm sure about it.  Nobody's
    // going to clammer for this one.
    //
    // public Vset checkLHS(Environment env, Context ctx,
    //     Vset vset, Hashtable exp) {
    //     vset = right.check(env, ctx, vset, exp);
    //     type = right.type;
    //     return vset;
    // }

    public boolean isNull() {
        return right.isNull();
    }

    public boolean isNonNull() {
        return right.isNonNull();
    }

    // Probably not necessary
    public Object getValue() {
        return right.getValue();
    }

    /**
     * Delegate to our subexpression.
     * See the comment in AddExpression#inlineValueSB() for
     * information about this method.
     */
    protected StringBuffer inlineValueSB(Environment env,
                                         Context ctx,
                                         StringBuffer buffer) {
        return right.inlineValueSB(env, ctx, buffer);
    }

    /**
     * Select the type of the expression
     */
    void selectType(Environment env, Context ctx, int tm) {
        type = right.type;
    }

    /**
     * Simplify
     */
    Expression simplify() {
        return right;
    }
}
