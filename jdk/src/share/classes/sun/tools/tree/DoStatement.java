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
class DoStatement extends Statement {
    Statement body;
    Expression cond;

    /**
     * Constructor
     */
    public DoStatement(long where, Statement body, Expression cond) {
        super(DO, where);
        this.body = body;
        this.cond = cond;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env,ctx);
        CheckContext newctx = new CheckContext(ctx, this);
        // remember what was unassigned on entry
        Vset vsEntry = vset.copy();
        vset = body.check(env, newctx, reach(env, vset), exp);
        vset = vset.join(newctx.vsContinue);
        // get to the test either by falling through the body, or through
        // a "continue" statement.
        ConditionVars cvars =
            cond.checkCondition(env, newctx, vset, exp);
        cond = convert(env, newctx, Type.tBoolean, cond);
        // make sure the back-branch fits the entry of the loop
        ctx.checkBackBranch(env, this, vsEntry, cvars.vsTrue);
        // exit the loop through the test returning false, or a "break"
        vset = newctx.vsBreak.join(cvars.vsFalse);
        return ctx.removeAdditionalVars(vset);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        ctx = new Context(ctx, this);
        if (body != null) {
            body = body.inline(env, ctx);
        }
        cond = cond.inlineValue(env, ctx);
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        DoStatement s = (DoStatement)clone();
        s.cond = cond.copyInline(ctx);
        if (body != null) {
            s.body = body.copyInline(ctx, valNeeded);
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1 + cond.costInline(thresh, env, ctx)
                + ((body != null) ? body.costInline(thresh, env, ctx) : 0);
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        Label l1 = new Label();
        asm.add(l1);

        CodeContext newctx = new CodeContext(ctx, this);

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
        out.print("do ");
        body.print(out, indent);
        out.print(" while ");
        cond.print(out);
        out.print(";");
    }
}
