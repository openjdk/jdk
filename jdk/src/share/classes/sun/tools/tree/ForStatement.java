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
class ForStatement extends Statement {
    Statement init;
    Expression cond;
    Expression inc;
    Statement body;

    /**
     * Constructor
     */
    public ForStatement(long where, Statement init, Expression cond, Expression inc, Statement body) {
        super(FOR, where);
        this.init = init;
        this.cond = cond;
        this.inc = inc;
        this.body = body;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        vset = reach(env, vset);
        Context initctx = new Context(ctx, this);
        if (init != null) {
            vset = init.checkBlockStatement(env, initctx, vset, exp);
        }
        CheckContext newctx = new CheckContext(initctx, this);
        // remember what was unassigned on entry
        Vset vsEntry = vset.copy();
        ConditionVars cvars;
        if (cond != null) {
            cvars = cond.checkCondition(env, newctx, vset, exp);
            cond = convert(env, newctx, Type.tBoolean, cond);
        } else {
            // a missing test is equivalent to "true"
            cvars = new ConditionVars();
            cvars.vsFalse = Vset.DEAD_END;
            cvars.vsTrue = vset;
        }
        vset = body.check(env, newctx, cvars.vsTrue, exp);
        vset = vset.join(newctx.vsContinue);
        if (inc != null) {
            vset = inc.check(env, newctx, vset, exp);
        }
        // Make sure the back-branch fits the entry of the loop.
        // Must include variables declared in the for-init part in the
        // set of variables visible upon loop entry that must be checked.
        initctx.checkBackBranch(env, this, vsEntry, vset);
        // exit by testing false or executing a break;
        vset = newctx.vsBreak.join(cvars.vsFalse);
        return ctx.removeAdditionalVars(vset);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        ctx = new Context(ctx, this);
        if (init != null) {
            Statement body[] = {init, this};
            init = null;
            return new CompoundStatement(where, body).inline(env, ctx);
        }
        if (cond != null) {
            cond = cond.inlineValue(env, ctx);
        }
        if (body != null) {
            body = body.inline(env, ctx);
        }
        if (inc != null) {
            inc = inc.inline(env, ctx);
        }
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        ForStatement s = (ForStatement)clone();
        if (init != null) {
            s.init = init.copyInline(ctx, valNeeded);
        }
        if (cond != null) {
            s.cond = cond.copyInline(ctx);
        }
        if (body != null) {
            s.body = body.copyInline(ctx, valNeeded);
        }
        if (inc != null) {
            s.inc = inc.copyInline(ctx);
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        int cost = 2;
        if (init != null) {
            cost += init.costInline(thresh, env, ctx);
        }
        if (cond != null) {
            cost += cond.costInline(thresh, env, ctx);
        }
        if (body != null) {
            cost += body.costInline(thresh, env, ctx);
        }
        if (inc != null) {
            cost += inc.costInline(thresh, env, ctx);
        }
        return cost;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);
        if (init != null) {
            init.code(env, newctx, asm);
        }

        Label l1 = new Label();
        Label l2 = new Label();

        asm.add(where, opc_goto, l2);

        asm.add(l1);
        if (body != null) {
            body.code(env, newctx, asm);
        }

        asm.add(newctx.contLabel);
        if (inc != null) {
            inc.code(env, newctx, asm);
        }

        asm.add(l2);
        if (cond != null) {
            cond.codeBranch(env, newctx, asm, l1, true);
        } else {
            asm.add(where, opc_goto, l1);
        }
        asm.add(newctx.breakLabel);
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("for (");
        if (init != null) {
            init.print(out, indent);
            out.print(" ");
        } else {
            out.print("; ");
        }
        if (cond != null) {
            cond.print(out);
            out.print(" ");
        }
        out.print("; ");
        if (inc != null) {
            inc.print(out);
        }
        out.print(") ");
        if (body != null) {
            body.print(out, indent);
        } else {
            out.print(";");
        }
    }
}
