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
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class IncDecExpression extends UnaryExpression {

    private FieldUpdater updater = null;

    /**
     * Constructor
     */
    public IncDecExpression(int op, long where, Expression right) {
        super(op, where, right.type, right);
    }

    /**
     * Check an increment or decrement expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        vset = right.checkAssignOp(env, ctx, vset, exp, this);
        if (right.type.inMask(TM_NUMBER)) {
            type = right.type;
        } else {
            if (!right.type.isType(TC_ERROR)) {
                env.error(where, "invalid.arg.type", right.type, opNames[op]);
            }
            type = Type.tError;
        }
        updater = right.getUpdater(env, ctx);  // Must be called after 'checkAssignOp'.
        return vset;
    }

    /**
     * Check void expression
     */
    public Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        return checkValue(env, ctx, vset, exp);
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return inlineValue(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        // Why not inlineLHS?  But that does not work.
        right = right.inlineValue(env, ctx);
        if (updater != null) {
            updater = updater.inline(env, ctx);
        }
        return this;
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        if (updater == null) {
            if ((right.op == IDENT) && type.isType(TC_INT) &&
                (((IdentifierExpression)right).field.isLocal())) {
                // Increment variable in place.  Count 3 bytes for 'iinc'.
                return 3;
            }
            // Cost to load lhs reference, fetch local, increment, and store.
            // Load/store cost will be higher if variable is a field.  Note that
            // costs are highly approximate. See 'AssignOpExpression.costInline'
            // Does not account for cost of conversions,or duplications in
            // value-needed context..
            return right.costInline(thresh, env, ctx) + 4;
        } else {
            // Cost of two access method calls (get/set) + cost of increment.
            return updater.costInline(thresh, env, ctx, true) + 1;
        }
    }


    /**
     * Code
     */

    private void codeIncDecOp(Assembler asm, boolean inc) {
        switch (type.getTypeCode()) {
          case TC_BYTE:
            asm.add(where, opc_ldc, new Integer(1));
            asm.add(where, inc ? opc_iadd : opc_isub);
            asm.add(where, opc_i2b);
            break;
          case TC_SHORT:
            asm.add(where, opc_ldc, new Integer(1));
            asm.add(where, inc ? opc_iadd : opc_isub);
            asm.add(where, opc_i2s);
            break;
          case TC_CHAR:
            asm.add(where, opc_ldc, new Integer(1));
            asm.add(where, inc ? opc_iadd : opc_isub);
            asm.add(where, opc_i2c);
            break;
          case TC_INT:
            asm.add(where, opc_ldc, new Integer(1));
            asm.add(where, inc ? opc_iadd : opc_isub);
            break;
          case TC_LONG:
            asm.add(where, opc_ldc2_w, new Long(1));
            asm.add(where, inc ? opc_ladd : opc_lsub);
            break;
          case TC_FLOAT:
            asm.add(where, opc_ldc, new Float(1));
            asm.add(where, inc ? opc_fadd : opc_fsub);
            break;
          case TC_DOUBLE:
            asm.add(where, opc_ldc2_w, new Double(1));
            asm.add(where, inc ? opc_dadd : opc_dsub);
            break;
          default:
            throw new CompilerError("invalid type");
        }
    }

    void codeIncDec(Environment env, Context ctx, Assembler asm, boolean inc, boolean prefix, boolean valNeeded) {

        // The 'iinc' instruction cannot be used if an access method call is required.
        if ((right.op == IDENT) && type.isType(TC_INT) &&
            (((IdentifierExpression)right).field.isLocal()) && updater == null) {
            if (valNeeded && !prefix) {
                right.codeLoad(env, ctx, asm);
            }
            int v = ((LocalMember)((IdentifierExpression)right).field).number;
            int[] operands = { v, inc ? 1 : -1 };
            asm.add(where, opc_iinc, operands);
            if (valNeeded && prefix) {
                right.codeLoad(env, ctx, asm);
            }
            return;

        }

        if (updater == null) {
            // Field is directly accessible.
            int depth = right.codeLValue(env, ctx, asm);
            codeDup(env, ctx, asm, depth, 0);
            right.codeLoad(env, ctx, asm);
            if (valNeeded && !prefix) {
                codeDup(env, ctx, asm, type.stackSize(), depth);
            }
            codeIncDecOp(asm, inc);
            if (valNeeded && prefix) {
                codeDup(env, ctx, asm, type.stackSize(), depth);
            }
            right.codeStore(env, ctx, asm);
        } else {
            // Must use access methods.
            updater.startUpdate(env, ctx, asm, (valNeeded && !prefix));
            codeIncDecOp(asm, inc);
            updater.finishUpdate(env, ctx, asm, (valNeeded && prefix));
        }
    }

}
