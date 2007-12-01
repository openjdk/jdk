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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ExpressionStatement extends Statement {
    Expression expr;

    /**
     * Constructor
     */
    public ExpressionStatement(long where, Expression expr) {
        super(EXPRESSION, where);
        this.expr = expr;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        return expr.check(env, ctx, reach(env, vset), exp);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        if (expr != null) {
            expr = expr.inline(env, ctx);
            return (expr == null) ? null : this;
        }
        return null;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        ExpressionStatement s = (ExpressionStatement)clone();
        s.expr = expr.copyInline(ctx);
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return expr.costInline(thresh, env, ctx);
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        expr.code(env, ctx, asm);
    }

    /**
     * Check if the first thing is a constructor invocation
     */
    public Expression firstConstructor() {
        return expr.firstConstructor();
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        if (expr != null) {
            expr.print(out);
        } else {
            out.print("<empty>");
        }
        out.print(";");
    }
}
