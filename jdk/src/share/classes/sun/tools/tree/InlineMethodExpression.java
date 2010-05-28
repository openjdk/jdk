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
import sun.tools.asm.Label;
import sun.tools.asm.Assembler;
import java.io.PrintStream;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class InlineMethodExpression extends Expression {
    MemberDefinition field;
    Statement body;

    /**
     * Constructor
     */
    InlineMethodExpression(long where, Type type, MemberDefinition field, Statement body) {
        super(INLINEMETHOD, where, type);
        this.field = field;
        this.body = body;
    }
    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        body = body.inline(env, new Context(ctx, this));
        if (body == null) {
            return null;
        } else if (body.op == INLINERETURN) {
            Expression expr = ((InlineReturnStatement)body).expr;
            if (expr != null && type.isType(TC_VOID)) {
                throw new CompilerError("value on inline-void return");
            }
            return expr;
        } else {
            return this;
        }
    }
    public Expression inlineValue(Environment env, Context ctx) {
        // When this node was constructed, "copyInline" walked the body
        // with a "valNeeded" flag which made all returns either void
        // or value-bearing.  The type of this node reflects that
        // earlier choice.  The present inline/inlineValue distinction
        // is ignored.
        return inline(env, ctx);
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        InlineMethodExpression e = (InlineMethodExpression)clone();
        if (body != null) {
            e.body = body.copyInline(ctx, true);
        }
        return e;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        // pop the result if there is any (usually, type is already void)
        super.code(env, ctx, asm);
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);
        body.code(env, newctx, asm);
        asm.add(newctx.breakLabel);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + "\n");
        body.print(out, 1);
        out.print(")");
    }
}
