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
public
class ArrayAccessExpression extends UnaryExpression {

    /**
     * The index expression for the array access.  Note that
     * ArrayAccessExpression also `moonlights' as a structure for
     * storing array types (like Object[]) which are used as part
     * of cast expressions.  For properly formed array types, the
     * value of index is null.  We need to be on the lookout for
     * null indices in true array accesses, and non-null indices
     * in array types.  We also need to make sure general purpose
     * methods (like copyInline(), which is called for both) are
     * prepared to handle either null or non-null indices.
     */
    Expression index;

    /**
     * constructor
     */
    public ArrayAccessExpression(long where, Expression right, Expression index) {
        super(ARRAYACCESS, where, Type.tError, right);
        this.index = index;
    }

    /**
     * Check expression type
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        vset = right.checkValue(env, ctx, vset, exp);
        if (index == null) {
            env.error(where, "array.index.required");
            return vset;
        }
        vset = index.checkValue(env, ctx, vset, exp);
        index = convert(env, ctx, Type.tInt, index);

        if (!right.type.isType(TC_ARRAY)) {
            if (!right.type.isType(TC_ERROR)) {
                env.error(where, "not.array", right.type);
            }
            return vset;
        }

        type = right.type.getElementType();
        return vset;
    }

    public Vset checkAmbigName(Environment env, Context ctx,
                               Vset vset, Hashtable<Object, Object> exp,
                               UnaryExpression loc) {
        if (index == null) {
            vset = right.checkAmbigName(env, ctx, vset, exp, this);
            if (right.type == Type.tPackage) {
                FieldExpression.reportFailedPackagePrefix(env, right);
                return vset;
            }

            // Nope.  Is this field expression a type?
            if (right instanceof TypeExpression) {
                Type atype = Type.tArray(right.type);
                loc.right = new TypeExpression(where, atype);
                return vset;
            }

            env.error(where, "array.index.required");
            return vset;
        }
        return super.checkAmbigName(env, ctx, vset, exp, loc);
    }

    /*
     * Check the array if it appears on the LHS of an assignment
     */
    public Vset checkLHS(Environment env, Context ctx,
                         Vset vset, Hashtable<Object, Object> exp) {
        return checkValue(env, ctx, vset, exp);
    }

    /*
     * Check the array if it appears on the LHS of an op= expression
     */
    public Vset checkAssignOp(Environment env, Context ctx,
                              Vset vset, Hashtable<Object, Object> exp, Expression outside) {
        return checkValue(env, ctx, vset, exp);
    }

    /**
     * An array access expression never requires the use of an access method to perform
     * an assignment to an array element, though an access method may be required to
     * fetch the array object itself.
     */
    public FieldUpdater getAssigner(Environment env, Context ctx) {
        return null;
    }

    /**
     * An array access expression never requires a field updater.
     */
    public FieldUpdater getUpdater(Environment env, Context ctx) {
        return null;
    }

    /**
     * Convert to a type
     */
    Type toType(Environment env, Context ctx) {
        return toType(env, right.toType(env, ctx));
    }
    Type toType(Environment env, Type t) {
        if (index != null) {
            env.error(index.where, "array.dim.in.type");
        }
        return Type.tArray(t);
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        // It isn't possible to simply replace an array access
        // with a CommaExpression as happens with many binary
        // operators, because array accesses may have side effects
        // such as NullPointerException or IndexOutOfBoundsException.
        right = right.inlineValue(env, ctx);
        index = index.inlineValue(env, ctx);
        return this;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        // inlineValue() should not end up being called when the index is
        // null.  If it is null, we let this method fail with a
        // NullPointerException.

        right = right.inlineValue(env, ctx);
        index = index.inlineValue(env, ctx);
        return this;
    }
    public Expression inlineLHS(Environment env, Context ctx) {
        return inlineValue(env, ctx);
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        ArrayAccessExpression e = (ArrayAccessExpression)clone();
        e.right = right.copyInline(ctx);
        if (index == null) {
            // The index can be null when this node is being used to
            // represent a type (e.g. Object[]) used in a cast expression.
            // We need to copy such structures without complaint.
            e.index = null;
        } else {
            e.index = index.copyInline(ctx);
        }
        return e;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        // costInline() should not end up being called when the index is
        // null.  If it is null, we let this method fail with a
        // NullPointerException.

        return 1 + right.costInline(thresh, env, ctx)
            + index.costInline(thresh, env, ctx);
    }

    /**
     * Code
     */
    int codeLValue(Environment env, Context ctx, Assembler asm) {
        // codeLValue() should not end up being called when the index is
        // null.  If it is null, we let this method fail with a
        // NullPointerException.

        right.codeValue(env, ctx, asm);
        index.codeValue(env, ctx, asm);
        return 2;
    }
    void codeLoad(Environment env, Context ctx, Assembler asm) {
        switch (type.getTypeCode()) {
          case TC_BOOLEAN:
          case TC_BYTE:
            asm.add(where, opc_baload);
            break;
          case TC_CHAR:
            asm.add(where, opc_caload);
            break;
          case TC_SHORT:
            asm.add(where, opc_saload);
            break;
          default:
            asm.add(where, opc_iaload + type.getTypeCodeOffset());
        }
    }
    void codeStore(Environment env, Context ctx, Assembler asm) {
        switch (type.getTypeCode()) {
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
            asm.add(where, opc_iastore + type.getTypeCodeOffset());
        }
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        codeLValue(env, ctx, asm);
        codeLoad(env, ctx, asm);
    }


    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        right.print(out);
        out.print(" ");
        if (index != null) {
            index.print(out);
        } else {
        out.print("<empty>");
        }
        out.print(")");
    }
}
