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
class CastExpression extends BinaryExpression {
    /**
     * constructor
     */
    public CastExpression(long where, Expression left, Expression right) {
        super(CAST, where, left.type, left, right);
    }

    /**
     * Check the expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        type = left.toType(env, ctx);
        vset = right.checkValue(env, ctx, vset, exp);

        if (type.isType(TC_ERROR) || right.type.isType(TC_ERROR)) {
            // An error was already reported
            return vset;
        }

        if (type.equals(right.type)) {
            // The types are already the same
            return vset;
        }

        try {
            if (env.explicitCast(right.type, type)) {
                right = new ConvertExpression(where, type, right);
                return vset;
            }
        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, opNames[op]);
        }

        // The cast is not allowed
        env.error(where, "invalid.cast", right.type, type);
        return vset;
    }

    /**
     * Check if constant
     */
    public boolean isConstant() {
        if (type.inMask(TM_REFERENCE) && !type.equals(Type.tString)) {
            // must be a primitive type, or String
            return false;
        }
        return right.isConstant();
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return right.inline(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        return right.inlineValue(env, ctx);
    }


    public int costInline(int thresh, Environment env, Context ctx) {
        if (ctx == null) {
            return 1 + right.costInline(thresh, env, ctx);
        }
        // sourceClass is the current class trying to inline this method
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        try {
            // We only allow the inlining if the current class can access
            // the casting class
            if (left.type.isType(TC_ARRAY) ||
                 sourceClass.permitInlinedAccess(env,
                                  env.getClassDeclaration(left.type)))
                return 1 + right.costInline(thresh, env, ctx);
        } catch (ClassNotFound e) {
        }
        return thresh;
    }



    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        if (type.isType(TC_ERROR)) {
            left.print(out);
        } else {
            out.print(type);
        }
        out.print(" ");
        right.print(out);
        out.print(")");
    }
}
