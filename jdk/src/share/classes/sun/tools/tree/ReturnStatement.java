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
class ReturnStatement extends Statement {
    Expression expr;

    /**
     * Constructor
     */
    public ReturnStatement(long where, Expression expr) {
        super(RETURN, where);
        this.expr = expr;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        vset = reach(env, vset);
        if (expr != null) {
            vset = expr.checkValue(env, ctx, vset, exp);
        }

        // Make sure the return isn't inside a static initializer
        if (ctx.field.isInitializer()) {
            env.error(where, "return.inside.static.initializer");
            return DEAD_END;
        }
        // Check return type
        if (ctx.field.getType().getReturnType().isType(TC_VOID)) {
            if (expr != null) {
                if (ctx.field.isConstructor()) {
                    env.error(where, "return.with.value.constr", ctx.field);
                } else {
                    env.error(where, "return.with.value", ctx.field);
                }
                expr = null;
            }
        } else {
            if (expr == null) {
                env.error(where, "return.without.value", ctx.field);
            } else {
                expr = convert(env, ctx, ctx.field.getType().getReturnType(), expr);
            }
        }
        CheckContext mctx = ctx.getReturnContext();
        if (mctx != null) {
            mctx.vsBreak = mctx.vsBreak.join(vset);
        }
        CheckContext exitctx = ctx.getTryExitContext();
        if (exitctx != null) {
            exitctx.vsTryExit = exitctx.vsTryExit.join(vset);
        }
        if (expr != null) {
            // see if we are returning a value out of a try or synchronized
            // statement.  If so, find the outermost one. . . .
            Node outerFinallyNode = null;
            for (Context c = ctx; c != null; c = c.prev) {
                if (c.node == null) {
                    continue;
                }
                if (c.node.op == METHOD) {
                    // Don't search outside current method. Fixes 4084230.
                    break;
                }
                if (c.node.op == SYNCHRONIZED) {
                    outerFinallyNode = c.node;
                    break;
                } else if (c.node.op == FINALLY
                           && ((CheckContext)c).vsContinue != null) {
                    outerFinallyNode = c.node;
                }
            }
            if (outerFinallyNode != null) {
                if (outerFinallyNode.op == FINALLY) {
                    ((FinallyStatement)outerFinallyNode).needReturnSlot = true;
                } else {
                    ((SynchronizedStatement)outerFinallyNode).needReturnSlot = true;
                }
            }
        }
        return DEAD_END;
    }


    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        if (expr != null) {
            expr = expr.inlineValue(env, ctx);
        }
        return this;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1 + ((expr != null) ? expr.costInline(thresh, env, ctx) : 0);
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        Expression e = (expr != null) ? expr.copyInline(ctx) : null;
        if ((!valNeeded) && (e != null)) {
            Statement body[] = {
                new ExpressionStatement(where, e),
                new InlineReturnStatement(where, null)
            };
            return new CompoundStatement(where, body);
        }
        return new InlineReturnStatement(where, e);
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        if (expr == null) {
            codeFinally(env, ctx, asm, null, null);
            asm.add(where, opc_return);
        } else {
            expr.codeValue(env, ctx, asm);
            codeFinally(env, ctx, asm, null, expr.type);
            asm.add(where, opc_ireturn + expr.type.getTypeCodeOffset());
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("return");
        if (expr != null) {
            out.print(" ");
            expr.print(out);
        }
        out.print(";");
    }
}
