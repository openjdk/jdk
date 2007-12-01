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
import sun.tools.asm.Label;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class WhileStatement extends Statement {
    Expression cond;
    Statement body;

    /**
     * Constructor
     */
    public WhileStatement(long where, Expression cond, Statement body) {
        super(WHILE, where);
        this.cond = cond;
        this.body = body;
    }

    /**
     * Check a while statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        CheckContext newctx = new CheckContext(ctx, this);
        // remember what was unassigned on entry
        Vset vsEntry = vset.copy();
        // check the condition.  Determine which variables have values if
        // it returns true or false.
        ConditionVars cvars =
              cond.checkCondition(env, newctx, reach(env, vset), exp);
        cond = convert(env, newctx, Type.tBoolean, cond);
        // check the body, given that the condition returned true.
        vset = body.check(env, newctx, cvars.vsTrue, exp);
        vset = vset.join(newctx.vsContinue);
        // make sure the back-branch fits the entry of the loop
        ctx.checkBackBranch(env, this, vsEntry, vset);
        // Exit the while loop by testing false or getting a break statement
        vset = newctx.vsBreak.join(cvars.vsFalse);
        return ctx.removeAdditionalVars(vset);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        ctx = new Context(ctx, this);
        cond = cond.inlineValue(env, ctx);
        if (body != null) {
            body = body.inline(env, ctx);
        }
        return this;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1 + cond.costInline(thresh, env, ctx)
                 + ((body != null) ? body.costInline(thresh, env, ctx) : 0);
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        WhileStatement s = (WhileStatement)clone();
        s.cond = cond.copyInline(ctx);
        if (body != null) {
            s.body = body.copyInline(ctx, valNeeded);
        }
        return s;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);

        asm.add(where, opc_goto, newctx.contLabel);

        Label l1 = new Label();
        asm.add(l1);

        if (body != null) {
            body.code(env, newctx, asm);
        }

        asm.add(newctx.contLabel);
        cond.codeBranch(env, newctx, asm, l1, true);
        asm.add(newctx.breakLabel);
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("while ");
        cond.print(out);
        if (body != null) {
            out.print(" ");
            body.print(out, indent);
        } else {
            out.print(";");
        }
    }
}
