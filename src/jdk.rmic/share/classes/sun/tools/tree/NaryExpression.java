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

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class NaryExpression extends UnaryExpression {
    Expression args[];

    /**
     * Constructor
     */
    NaryExpression(int op, long where, Type type, Expression right, Expression args[]) {
        super(op, where, type, right);
        this.args = args;
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        NaryExpression e = (NaryExpression)clone();
        if (right != null) {
            e.right = right.copyInline(ctx);
        }
        e.args = new Expression[args.length];
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                e.args[i] = args[i].copyInline(ctx);
            }
        }
        return e;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        int cost = 3;
        if (right != null)
            cost += right.costInline(thresh, env, ctx);
        for (int i = 0 ; (i < args.length) && (cost < thresh) ; i++) {
            if (args[i] != null) {
                cost += args[i].costInline(thresh, env, ctx);
            }
        }
        return cost;
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + "#" + hashCode());
        if (right != null) {
            out.print(" ");
            right.print(out);
        }
        for (int i = 0 ; i < args.length ; i++) {
            out.print(" ");
            if (args[i] != null) {
                args[i].print(out);
            } else {
                out.print("<null>");
            }
        }
        out.print(")");
    }
}
