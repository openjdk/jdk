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
class CompoundStatement extends Statement {
    Statement args[];

    /**
     * Constructor
     */
    public CompoundStatement(long where, Statement args[]) {
        super(STAT, where);
        this.args = args;
        // To avoid the need for subsequent null checks:
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] == null) {
                args[i] = new CompoundStatement(where, new Statement[0]);
            }
        }
    }

    /**
     * Insert a new statement at the front.
     * This is used to introduce an implicit super-class constructor call.
     */
    public void insertStatement(Statement s) {
        Statement newargs[] = new Statement[1+args.length];
        newargs[0] = s;
        for (int i = 0 ; i < args.length ; i++) {
            newargs[i+1] = args[i];
        }
        this.args = newargs;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        if (args.length > 0) {
            vset = reach(env, vset);
            CheckContext newctx = new CheckContext(ctx, this);
            // In this environment, 'resolveName' will look for local classes.
            Environment newenv = Context.newEnvironment(env, newctx);
            for (int i = 0 ; i < args.length ; i++) {
                vset = args[i].checkBlockStatement(newenv, newctx, vset, exp);
            }
            vset = vset.join(newctx.vsBreak);
        }
        return ctx.removeAdditionalVars(vset);
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        ctx = new Context(ctx, this);
        boolean expand = false;
        int count = 0;
        for (int i = 0 ; i < args.length ; i++) {
            Statement s = args[i];
            if (s != null) {
                if ((s = s.inline(env, ctx)) != null) {
                    if ((s.op == STAT) && (s.labels == null)) {
                        count += ((CompoundStatement)s).args.length;
                    } else {
                        count++;
                    }
                    expand = true;
                }
                args[i] = s;
            }
        }
        switch (count) {
          case 0:
            return null;

          case 1:
            for (int i = args.length ; i-- > 0 ;) {
                if (args[i] != null) {
                    return eliminate(env, args[i]);
                }
            }
            break;
        }
        if (expand || (count != args.length)) {
            Statement newArgs[] = new Statement[count];
            for (int i = args.length ; i-- > 0 ;) {
                Statement s = args[i];
                if (s != null) {
                    if ((s.op == STAT) && (s.labels == null)) {
                        Statement a[] = ((CompoundStatement)s).args;
                        for (int j = a.length ; j-- > 0 ; ) {
                            newArgs[--count] = a[j];
                        }
                    } else {
                        newArgs[--count] = s;
                    }
                }
            }
            args = newArgs;
        }
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        CompoundStatement s = (CompoundStatement)clone();
        s.args = new Statement[args.length];
        for (int i = 0 ; i < args.length ; i++) {
            s.args[i] = args[i].copyInline(ctx, valNeeded);
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        int cost = 0;
        for (int i = 0 ; (i < args.length) && (cost < thresh) ; i++) {
            cost += args[i].costInline(thresh, env, ctx);
        }
        return cost;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);
        for (int i = 0 ; i < args.length ; i++) {
            args[i].code(env, newctx, asm);
        }
        asm.add(newctx.breakLabel);
    }

    /**
     * Check if the first thing is a constructor invocation
     */
    public Expression firstConstructor() {
        return (args.length > 0) ? args[0].firstConstructor() : null;
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("{\n");
        for (int i = 0 ; i < args.length ; i++) {
            printIndent(out, indent+1);
            if (args[i] != null) {
                args[i].print(out, indent + 1);
            } else {
                out.print("<empty>");
            }
            out.print("\n");
        }
        printIndent(out, indent);
        out.print("}");
    }
}
