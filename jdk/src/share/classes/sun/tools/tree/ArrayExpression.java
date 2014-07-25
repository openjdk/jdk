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
import sun.tools.asm.*;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ArrayExpression extends NaryExpression {
    /**
     * Constructor
     */
    public ArrayExpression(long where, Expression args[]) {
        super(ARRAY, where, Type.tError, null, args);
    }

    /**
     * Check expression type
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        env.error(where, "invalid.array.expr");
        return vset;
    }
    public Vset checkInitializer(Environment env, Context ctx, Vset vset, Type t, Hashtable exp) {
        if (!t.isType(TC_ARRAY)) {
            if (!t.isType(TC_ERROR)) {
                env.error(where, "invalid.array.init", t);
            }
            return vset;
        }
        type = t;
        t = t.getElementType();
        for (int i = 0 ; i < args.length ; i++) {
            vset = args[i].checkInitializer(env, ctx, vset, t, exp);
            args[i] = convert(env, ctx, t, args[i]);
        }
        return vset;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        Expression e = null;
        for (int i = 0 ; i < args.length ; i++) {
            args[i] = args[i].inline(env, ctx);
            if (args[i] != null) {
                e = (e == null) ? args[i] : new CommaExpression(where, e, args[i]);
            }
        }
        return e;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        for (int i = 0 ; i < args.length ; i++) {
            args[i] = args[i].inlineValue(env, ctx);
        }
        return this;
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        int t = 0;
        asm.add(where, opc_ldc, args.length);
        switch (type.getElementType().getTypeCode()) {
          case TC_BOOLEAN:      asm.add(where, opc_newarray, T_BOOLEAN);   break;
          case TC_BYTE:         asm.add(where, opc_newarray, T_BYTE);      break;
          case TC_SHORT:        asm.add(where, opc_newarray, T_SHORT);     break;
          case TC_CHAR:         asm.add(where, opc_newarray, T_CHAR);      break;
          case TC_INT:          asm.add(where, opc_newarray, T_INT);       break;
          case TC_LONG:         asm.add(where, opc_newarray, T_LONG);      break;
          case TC_FLOAT:        asm.add(where, opc_newarray, T_FLOAT);     break;
          case TC_DOUBLE:       asm.add(where, opc_newarray, T_DOUBLE);    break;

          case TC_ARRAY:
            asm.add(where, opc_anewarray, type.getElementType());
            break;

          case TC_CLASS:
            asm.add(where, opc_anewarray, env.getClassDeclaration(type.getElementType()));
            break;

          default:
            throw new CompilerError("codeValue");
        }

        for (int i = 0 ; i < args.length ; i++) {

            // If the array element is the default initial value,
            // then don't bother generating code for this element.
            if (args[i].equalsDefault()) continue;

            asm.add(where, opc_dup);
            asm.add(where, opc_ldc, i);
            args[i].codeValue(env, ctx, asm);
            switch (type.getElementType().getTypeCode()) {
              case TC_BOOLEAN:
              case TC_BYTE:
                asm.add(where, opc_bastore);
                break;
              case TC_CHAR:
                asm.add(where, opc_castore);
                break;
              case TC_SHORT:
                asm.add(where, opc_sastore);
                break;
              default:
                asm.add(where, opc_iastore + type.getElementType().getTypeCodeOffset());
            }
        }
    }
}
