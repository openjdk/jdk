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
import java.util.Vector;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class InlineNewInstanceExpression extends Expression {
    MemberDefinition field;
    Statement body;

    /**
     * Constructor
     */
    InlineNewInstanceExpression(long where, Type type, MemberDefinition field, Statement body) {
        super(INLINENEWINSTANCE, where, type);
        this.field = field;
        this.body = body;
    }
    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return inlineValue(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        if (body != null) {
            LocalMember v = (LocalMember)field.getArguments().elementAt(0);
            Context newctx = new Context(ctx, this);
            newctx.declare(env, v);
            body = body.inline(env, newctx);
        }
        if ((body != null) && (body.op == INLINERETURN)) {
            body = null;
        }
        return this;
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        InlineNewInstanceExpression e = (InlineNewInstanceExpression)clone();
        e.body = body.copyInline(ctx, true);
        return e;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        codeCommon(env, ctx, asm, false);
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        codeCommon(env, ctx, asm, true);
    }
    private void codeCommon(Environment env, Context ctx, Assembler asm,
                            boolean forValue) {
        asm.add(where, opc_new, field.getClassDeclaration());
        if (body != null) {
            LocalMember v = (LocalMember)field.getArguments().elementAt(0);
            CodeContext newctx = new CodeContext(ctx, this);
            newctx.declare(env, v);
            asm.add(where, opc_astore, new Integer(v.number));
            body.code(env, newctx, asm);
            asm.add(newctx.breakLabel);
            if (forValue) {
                asm.add(where, opc_aload, new Integer(v.number));
            }
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        LocalMember v = (LocalMember)field.getArguments().elementAt(0);
        out.println("(" + opNames[op] + "#" + v.hashCode() + "=" + field.hashCode());
        if (body != null) {
            body.print(out, 1);
        } else {
            out.print("<empty>");
        }
        out.print(")");
    }
}
