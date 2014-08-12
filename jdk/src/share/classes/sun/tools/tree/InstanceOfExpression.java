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
class InstanceOfExpression extends BinaryExpression {
    /**
     * constructor
     */
    public InstanceOfExpression(long where, Expression left, Expression right) {
        super(INSTANCEOF, where, Type.tBoolean, left, right);
    }

    /**
     * Check the expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        vset = left.checkValue(env, ctx, vset, exp);
        right = new TypeExpression(right.where, right.toType(env, ctx));

        if (right.type.isType(TC_ERROR) || left.type.isType(TC_ERROR)) {
            // An error was already reported
            return vset;
        }

        if (!right.type.inMask(TM_CLASS|TM_ARRAY)) {
            env.error(right.where, "invalid.arg.type", right.type, opNames[op]);
            return vset;
        }
        try {
            if (!env.explicitCast(left.type, right.type)) {
                env.error(where, "invalid.instanceof", left.type, right.type);
            }
        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, opNames[op]);
        }
        return vset;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return left.inline(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        left = left.inlineValue(env, ctx);
        return this;
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        if (ctx == null) {
            return 1 + left.costInline(thresh, env, ctx);
        }
        // sourceClass is the current class trying to inline this method
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        try {
            // We only allow the inlining if the current class can access
            // the "instance of" class
            if (right.type.isType(TC_ARRAY) ||
                 sourceClass.permitInlinedAccess(env, env.getClassDeclaration(right.type)))
                return 1 + left.costInline(thresh, env, ctx);
        } catch (ClassNotFound e) {
        }
        return thresh;
    }




    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        left.codeValue(env, ctx, asm);
        if (right.type.isType(TC_CLASS)) {
            asm.add(where, opc_instanceof, env.getClassDeclaration(right.type));
        } else {
            asm.add(where, opc_instanceof, right.type);
        }
    }
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        codeValue(env, ctx, asm);
        asm.add(where, whenTrue ? opc_ifne : opc_ifeq, lbl, whenTrue);
    }
    public void code(Environment env, Context ctx, Assembler asm) {
        left.code(env, ctx, asm);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        left.print(out);
        out.print(" ");
        if (right.op == TYPE) {
            out.print(right.type.toString());
        } else {
            right.print(out);
        }
        out.print(")");
    }
}
