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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public abstract
class AssignOpExpression extends BinaryAssignExpression {
    protected Type itype;       // Type of intermediate result, before assigning
    final int NOINC = Integer.MAX_VALUE;

    protected FieldUpdater updater = null;   // Used also in 'AssignAddExpression'.

    /**
     * Constructor
     */
    public AssignOpExpression(int op, long where, Expression left, Expression right) {
        super(op, where, left, right);
    }

    /**
     * Select the type
     *
     */
    @SuppressWarnings("fallthrough")
    final void selectType(Environment env, Context ctx, int tm) {
        Type rtype = null;      // special conversion type for RHS
        switch(op) {
            case ASGADD:
                if (left.type == Type.tString) {
                    if (right.type == Type.tVoid) {
                        // The type of the right hand side can be
                        // anything except void.  Fix for 4119864.
                        env.error(where, "incompatible.type",
                                  opNames[op], Type.tVoid, Type.tString);
                        type = Type.tError;
                    } else {
                        type = itype = Type.tString;
                    }
                    return;
                }
                /* Fall through */
            case ASGDIV: case ASGMUL: case ASGSUB: case ASGREM:
                if ((tm & TM_DOUBLE) != 0) {
                    itype = Type.tDouble;
                } else if ((tm & TM_FLOAT) != 0) {
                    itype = Type.tFloat;
                } else if ((tm & TM_LONG) != 0) {
                    itype = Type.tLong;
                } else {
                    itype = Type.tInt;
                }
                break;

            case ASGBITAND: case ASGBITOR: case ASGBITXOR:
                if ((tm & TM_BOOLEAN) != 0) {
                    itype = Type.tBoolean;
                } else if ((tm & TM_LONG) != 0) {
                    itype = Type.tLong;
                } else {
                    itype = Type.tInt;
                }
                break;

            case ASGLSHIFT: case ASGRSHIFT: case ASGURSHIFT:
                rtype = Type.tInt;

                // Fix for bug 4134459.
                // We allow any integral type (even long) to
                // be the right hand side of a shift operation.
                if (right.type.inMask(TM_INTEGER)) {
                    right = new ConvertExpression(where, Type.tInt, right);
                }
                // The intermediate type of the expression is the
                // type of the left hand side after undergoing
                // unary (not binary) type promotion.  We ignore
                // tm -- it contains information about both left
                // and right hand sides -- and we compute the
                // type only from the type of the lhs.
                if (left.type == Type.tLong) {
                    itype = Type.tLong;
                } else {
                    itype = Type.tInt;
                }

                break;

            default:
                throw new CompilerError("Bad assignOp type: " + op);
        }
        if (rtype == null) {
            rtype = itype;
        }
        right = convert(env, ctx, rtype, right);
        // The result is always the type of the left operand.

        type = left.type;
    }


    /**
     * Get the increment, return NOINC if an increment is not possible
     */
    int getIncrement() {
        if ((left.op == IDENT) && type.isType(TC_INT) && (right.op == INTVAL))
            if ((op == ASGADD) || (op == ASGSUB))
                if (((IdentifierExpression)left).field.isLocal()) {
                    int val = ((IntExpression)right).value;
                    if (op == ASGSUB)
                        val = -val;
                    if (val == (short)val)
                        return val;
                }
        return NOINC;
    }


    /**
     * Check an assignment expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        vset = left.checkAssignOp(env, ctx, vset, exp, this);
        vset = right.checkValue(env, ctx, vset, exp);
        int tm = left.type.getTypeMask() | right.type.getTypeMask();
        if ((tm & TM_ERROR) != 0) {
            return vset;
        }
        selectType(env, ctx, tm);
        if (!type.isType(TC_ERROR)) {
            convert(env, ctx, itype, left);
        }
        updater = left.getUpdater(env, ctx);  // Must be called after 'checkAssignOp'.
        return vset;
    }

    /**
     * Inline
     */
    public Expression inlineValue(Environment env, Context ctx) {
        // Why not inlineLHS?  But that does not work.
        left = left.inlineValue(env, ctx);
        right = right.inlineValue(env, ctx);
        if (updater != null) {
            updater = updater.inline(env, ctx);
        }
        return this;
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        AssignOpExpression e = (AssignOpExpression)clone();
        e.left = left.copyInline(ctx);
        e.right = right.copyInline(ctx);
        if (updater != null) {
            e.updater = updater.copyInline(ctx);
        }
        return e;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        /*----------*
        return (getIncrement() != NOINC)
            ? 2
            : (3 + super.costInline(thresh, env, ctx));
        *----------*/
        if (updater == null) {
            return (getIncrement() != NOINC)
                // Increment variable in place.  Count 3 bytes for 'iinc'.
                ? 3
                // Cost of rhs expression + cost of lhs expression + cost
                // of load/op/store instructions.  E.g.: iload = 1 or 2,
                // istore = 1 or 2, iadd = 1.  Cost could be higher if
                // getfield/putfield or conversions needed, lower if rhs is
                // a small constant.  Costs are highly approximate.
                : right.costInline(thresh, env, ctx) +
                      left.costInline(thresh, env, ctx) + 4;
        } else {
            // Cost of rhs expression + (2 * cost of access method call) +
            // cost of operator.  Does not account for cost of conversions,
            // or duplications in value-needed context.
            return right.costInline(thresh, env, ctx) +
                updater.costInline(thresh, env, ctx, true) + 1;
        }
    }

    /**
     * Code
     */
    void code(Environment env, Context ctx, Assembler asm, boolean valNeeded) {

        // Handle cases in which a '+=' or '-=' operator can be optimized using
        // the 'iinc' instruction.  See also 'IncDecExpression.codeIncDec'.
        // The 'iinc' instruction cannot be used if an access method call is required.
        int val = getIncrement();
        if (val != NOINC && updater == null) {
            int v = ((LocalMember)((IdentifierExpression)left).field).number;
            int[] operands = { v, val };
            asm.add(where, opc_iinc, operands);
            if (valNeeded) {
                left.codeValue(env, ctx, asm);
            }
            return;
        }

        if (updater == null) {
            // Field is directly accessible.
            int depth = left.codeLValue(env, ctx, asm);
            codeDup(env, ctx, asm, depth, 0);
            left.codeLoad(env, ctx, asm);
            codeConversion(env, ctx, asm, left.type, itype);
            right.codeValue(env, ctx, asm);
            codeOperation(env, ctx, asm);
            codeConversion(env, ctx, asm, itype, type);
            if (valNeeded) {
                codeDup(env, ctx, asm, type.stackSize(), depth);
            }
            left.codeStore(env, ctx, asm);
        } else {
            // Must use access methods.
            updater.startUpdate(env, ctx, asm, false);
            codeConversion(env, ctx, asm, left.type, itype);
            right.codeValue(env, ctx, asm);
            codeOperation(env, ctx, asm);
            codeConversion(env, ctx, asm, itype, type);
            updater.finishUpdate(env, ctx, asm, valNeeded);
        }
    }

    public void codeValue(Environment env, Context ctx, Assembler asm) {
        code(env, ctx, asm, true);
    }
    public void code(Environment env, Context ctx, Assembler asm) {
        code(env, ctx, asm, false);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        left.print(out);
        out.print(" ");
        right.print(out);
        out.print(")");
    }
}
