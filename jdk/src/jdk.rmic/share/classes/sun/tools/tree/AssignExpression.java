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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class AssignExpression extends BinaryAssignExpression {

    private FieldUpdater updater = null;

    /**
     * Constructor
     */
    public AssignExpression(long where, Expression left, Expression right) {
        super(ASSIGN, where, left, right);
    }

    /**
     * Check an assignment expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        if (left instanceof IdentifierExpression) {
            // we don't want to mark an identifier as having a value
            // until having evaluated the right-hand side
            vset = right.checkValue(env, ctx, vset, exp);
            vset = left.checkLHS(env, ctx, vset, exp);
        } else {
            // normally left to right evaluation.
            vset = left.checkLHS(env, ctx, vset, exp);
            vset = right.checkValue(env, ctx, vset, exp);
        }
        type = left.type;
        right = convert(env, ctx, type, right);

        // Get field updater (access method) if needed, else null.
        updater = left.getAssigner(env, ctx);

        return vset;
    }

    /**
     * Inline
     */
    public Expression inlineValue(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inlineValue(env, ctx);
        // Must be 'inlineLHS' here.  But compare with similar case in
        // 'AssignOpExpression' and 'IncDecExpression', which needs 'inlineValue'.
        left = left.inlineLHS(env, ctx);
        right = right.inlineValue(env, ctx);
        if (updater != null) {
            updater = updater.inline(env, ctx);
        }
        return this;
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        if (implementation != null)
            return implementation.copyInline(ctx);
        AssignExpression e = (AssignExpression)clone();
        e.left = left.copyInline(ctx);
        e.right = right.copyInline(ctx);
        if (updater != null) {
            e.updater = updater.copyInline(ctx);
        }
        return e;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        /*----------*
        return 2 + super.costInline(thresh, env, ctx);
        *----------*/
        return (updater != null)
            // Cost of rhs expression + cost of access method call.
            // Access method call cost includes lhs cost.
            ? right.costInline(thresh, env, ctx) +
                  updater.costInline(thresh, env, ctx, false)
            // Cost of rhs expression + cost of lhs expression +
            // cost of store instruction.
            : right.costInline(thresh, env, ctx) +
                  left.costInline(thresh, env, ctx) + 2;
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        if (updater == null) {
            // Field is directly accessible.
            int depth = left.codeLValue(env, ctx, asm);
            right.codeValue(env, ctx, asm);
            codeDup(env, ctx, asm, right.type.stackSize(), depth);
            left.codeStore(env, ctx, asm);
        } else {
            // Must use access method.
            // Left operand is always a 'FieldExpression', or
            // is rewritten as one via 'implementation'.
            updater.startAssign(env, ctx, asm);
            right.codeValue(env, ctx, asm);
            updater.finishAssign(env, ctx, asm, true);
        }
    }

    public void code(Environment env, Context ctx, Assembler asm) {
        if (updater == null) {
            // Field is directly accessible.
            left.codeLValue(env, ctx, asm);
            right.codeValue(env, ctx, asm);
            left.codeStore(env, ctx, asm);
        } else {
            // Must use access method.
            // Left operand is always a 'FieldExpression', or
            // is rewritten as one via 'implementation'.
            updater.startAssign(env, ctx, asm);
            right.codeValue(env, ctx, asm);
            updater.finishAssign(env, ctx, asm, false);
        }
    }
}
