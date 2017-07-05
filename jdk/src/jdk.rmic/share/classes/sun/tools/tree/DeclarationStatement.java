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
import java.io.PrintStream;
import sun.tools.asm.Assembler;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class DeclarationStatement extends Statement {
    int mod;
    Expression type;
    Statement args[];

    /**
     * Constructor
     */
    public DeclarationStatement(long where, int mod, Expression type, Statement args[]) {
        super(DECLARATION, where);
        this.mod = mod;
        this.type = type;
        this.args = args;
    }

    /**
     * Check statement
     * Report an error unless the call is checkBlockStatement.
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        env.error(where, "invalid.decl");
        return checkBlockStatement(env, ctx, vset, exp);
    }
    Vset checkBlockStatement(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        if (labels != null) {
            env.error(where, "declaration.with.label", labels[0]);
        }
        vset = reach(env, vset);
        Type t = type.toType(env, ctx);

        for (int i = 0 ; i < args.length ; i++) {
            vset = args[i].checkDeclaration(env, ctx, vset, mod, t, exp);
        }

        return vset;
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        int n = 0;
        for (int i = 0 ; i < args.length ; i++) {
            if ((args[i] = args[i].inline(env, ctx)) != null) {
                n++;
            }
        }
        return (n == 0) ? null : this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        DeclarationStatement s = (DeclarationStatement)clone();
        if (type != null) {
            s.type = type.copyInline(ctx);
        }
        s.args = new Statement[args.length];
        for (int i = 0; i < args.length; i++){
            if (args[i] != null){
                s.args[i] = args[i].copyInline(ctx, valNeeded);
            }
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        int cost = 1;
        for (int i = 0; i < args.length; i++){
            if (args[i] != null){
                cost += args[i].costInline(thresh, env, ctx);
            }
        }
        return cost;
    }


    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                args[i].code(env, ctx, asm);
            }
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        out.print("declare ");
        super.print(out, indent);
        type.print(out);
        out.print(" ");
        for (int i = 0 ; i < args.length ; i++) {
            if (i > 0) {
                out.print(", ");
            }
            if (args[i] != null)  {
                args[i].print(out);
            } else {
                out.print("<empty>");
            }
        }
        out.print(";");
    }
}
