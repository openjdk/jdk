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
import sun.tools.asm.ArrayData;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class NewArrayExpression extends NaryExpression {
    Expression init;

    /**
     * Constructor
     */
    public NewArrayExpression(long where, Expression right, Expression args[]) {
        super(NEWARRAY, where, Type.tError, right, args);
    }

    public NewArrayExpression(long where, Expression right, Expression args[], Expression init) {
        this(where, right, args);
        this.init = init;
    }

    /**
     * Check
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        type = right.toType(env, ctx);

        boolean flag = (init != null);  // flag says that dims are forbidden
        for (int i = 0 ; i < args.length ; i++) {
            Expression dim = args[i];
            if (dim == null) {
                if (i == 0 && !flag) {
                    env.error(where, "array.dim.missing");
                }
                flag = true;
            } else {
                if (flag) {
                    env.error(dim.where, "invalid.array.dim");
                }
                vset = dim.checkValue(env, ctx, vset, exp);
                args[i] = convert(env, ctx, Type.tInt, dim);
            }
            type = Type.tArray(type);
        }
        if (init != null) {
            vset = init.checkInitializer(env, ctx, vset, type, exp);
            init = convert(env, ctx, type, init);
        }
        return vset;
    }

    public Expression copyInline(Context ctx) {
        NewArrayExpression e = (NewArrayExpression)super.copyInline(ctx);
        if (init != null) {
            e.init = init.copyInline(ctx);
        }
        return e;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        Expression e = null;
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                e = (e != null) ? new CommaExpression(where, e, args[i]) : args[i];
            }
        }
        if (init != null)
            e = (e != null) ? new CommaExpression(where, e, init) : init;
        return (e != null) ? e.inline(env, ctx) : null;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        if (init != null)
            return init.inlineValue(env, ctx); // args are all null
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                args[i] = args[i].inlineValue(env, ctx);
            }
        }
        return this;
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        int t = 0;
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                args[i].codeValue(env, ctx, asm);
                t++;
            }
        }
        if (args.length > 1) {
            asm.add(where, opc_multianewarray, new ArrayData(type, t));
            return;
        }

        switch (type.getElementType().getTypeCode()) {
            case TC_BOOLEAN:
                asm.add(where, opc_newarray, T_BOOLEAN);   break;
            case TC_BYTE:
                asm.add(where, opc_newarray, T_BYTE);      break;
            case TC_SHORT:
                asm.add(where, opc_newarray, T_SHORT);     break;
            case TC_CHAR:
                asm.add(where, opc_newarray, T_CHAR);      break;
            case TC_INT:
                asm.add(where, opc_newarray, T_INT);       break;
            case TC_LONG:
                asm.add(where, opc_newarray, T_LONG);      break;
            case TC_FLOAT:
                asm.add(where, opc_newarray, T_FLOAT);     break;
            case TC_DOUBLE:
                asm.add(where, opc_newarray, T_DOUBLE);    break;
            case TC_ARRAY:
                asm.add(where, opc_anewarray, type.getElementType());   break;
            case TC_CLASS:
                asm.add(where, opc_anewarray,
                        env.getClassDeclaration(type.getElementType()));
                break;
            default:
                throw new CompilerError("codeValue");
        }
    }
}
