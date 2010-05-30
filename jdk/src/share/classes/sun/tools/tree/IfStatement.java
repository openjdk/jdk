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
class IfStatement extends Statement {
    Expression cond;
    Statement ifTrue;
    Statement ifFalse;

    /**
     * Constructor
     */
    public IfStatement(long where, Expression cond, Statement ifTrue, Statement ifFalse) {
        super(IF, where);
        this.cond = cond;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        CheckContext newctx = new CheckContext(ctx, this);
        // Vset vsExtra = vset.copy();  // See comment below.
        ConditionVars cvars =
              cond.checkCondition(env, newctx, reach(env, vset), exp);
        cond = convert(env, newctx, Type.tBoolean, cond);
        // The following code, now deleted, was apparently an erroneous attempt
        // at providing better error diagnostics.  The comment read: 'If either
        // the true clause or the false clause is unreachable, do a reasonable
        // check on the child anyway.'
        //    Vset vsTrue  = cvars.vsTrue.isDeadEnd() ? vsExtra : cvars.vsTrue;
        //    Vset vsFalse = cvars.vsFalse.isDeadEnd() ? vsExtra : cvars.vsFalse;
        // Unfortunately, this violates the rules laid out in the JLS, and leads to
        // blatantly incorrect results.  For example, 'i' will not be recognized
        // as definitely assigned following the statement 'if (true) i = 1;'.
        // It is best to slavishly follow the JLS here.  A cleverer approach could
        // only correctly issue warnings, as JLS 16.2.6 is quite explicit, and it
        // is OK for a dead branch of an if-statement to omit an assignment that
        // would be required in the other branch.  A complication: This code also
        // had the effect of implementing the special-case rules for 'if-then' and
        // 'if-then-else' in JLS 14.19, "Unreachable Statements".  We now use
        // 'Vset.clearDeadEnd' to remove the dead-end status of unreachable branches
        // without affecting the definite-assignment status of the variables, thus
        // maintaining a correct implementation of JLS 16.2.6.  Fixes 4094353.
        // Note that the code below will not consider the branches unreachable if
        // the entire statement is unreachable.  This is consistent with the error
        // recovery policy that reports the only the first unreachable statement
        // along an acyclic execution path.
        Vset vsTrue  = cvars.vsTrue.clearDeadEnd();
        Vset vsFalse = cvars.vsFalse.clearDeadEnd();
        vsTrue = ifTrue.check(env, newctx, vsTrue, exp);
        if (ifFalse != null)
            vsFalse = ifFalse.check(env, newctx, vsFalse, exp);
        vset = vsTrue.join(vsFalse.join(newctx.vsBreak));
        return ctx.removeAdditionalVars(vset);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        ctx = new Context(ctx, this);
        cond = cond.inlineValue(env, ctx);

        // The compiler currently needs to perform inlining on both
        // branches of the if statement -- even if `cond' is a constant
        // true or false.  Why?  The compiler will later try to compile
        // all classes that it has seen; this includes classes that
        // appear in dead code.  If we don't inline the dead branch here
        // then the compiler will never perform inlining on any local
        // classes appearing on the dead code.  When the compiler tries
        // to compile an un-inlined local class with uplevel references,
        // it dies.  (bug 4059492)
        //
        // A better solution to this would be to walk the dead branch and
        // mark any local classes appearing therein as unneeded.  Then the
        // compilation phase could skip these classes.
        if (ifTrue != null) {
            ifTrue = ifTrue.inline(env, ctx);
        }
        if (ifFalse != null) {
            ifFalse = ifFalse.inline(env, ctx);
        }
        if (cond.equals(true)) {
            return eliminate(env, ifTrue);
        }
        if (cond.equals(false)) {
            return eliminate(env, ifFalse);
        }
        if ((ifTrue == null) && (ifFalse == null)) {
            return eliminate(env, new ExpressionStatement(where, cond).inline(env, ctx));
        }
        if (ifTrue == null) {
            cond = new NotExpression(cond.where, cond).inlineValue(env, ctx);
            return eliminate(env, new IfStatement(where, cond, ifFalse, null));
        }
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        IfStatement s = (IfStatement)clone();
        s.cond = cond.copyInline(ctx);
        if (ifTrue != null) {
            s.ifTrue = ifTrue.copyInline(ctx, valNeeded);
        }
        if (ifFalse != null) {
            s.ifFalse = ifFalse.copyInline(ctx, valNeeded);
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        int cost = 1 + cond.costInline(thresh, env, ctx);
        if (ifTrue != null) {
            cost += ifTrue.costInline(thresh, env, ctx);
        }
        if (ifFalse != null) {
            cost += ifFalse.costInline(thresh, env, ctx);
        }
        return cost;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);

        Label l1 = new Label();
        cond.codeBranch(env, newctx, asm, l1, false);
        ifTrue.code(env, newctx, asm);
        if (ifFalse != null) {
            Label l2 = new Label();
            asm.add(true, where, opc_goto, l2);
            asm.add(l1);
            ifFalse.code(env, newctx, asm);
            asm.add(l2);
        } else {
            asm.add(l1);
        }

        asm.add(newctx.breakLabel);
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("if ");
        cond.print(out);
        out.print(" ");
        ifTrue.print(out, indent);
        if (ifFalse != null) {
            out.print(" else ");
            ifFalse.print(out, indent);
        }
    }
}
