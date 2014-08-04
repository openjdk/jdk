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
import sun.tools.asm.LocalVariable;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class VarDeclarationStatement extends Statement {
    LocalMember field;
    Expression expr;

    /**
     * Constructor
     */
    public VarDeclarationStatement(long where, Expression expr) {
        super(VARDECLARATION, where);
        this.expr = expr;
    }
    public VarDeclarationStatement(long where, LocalMember field, Expression expr) {
        super(VARDECLARATION, where);
        this.field = field;
        this.expr = expr;
    }

    /**
     * Check statement
     */
    Vset checkDeclaration(Environment env, Context ctx, Vset vset, int mod, Type t, Hashtable<Object, Object> exp) {
        if (labels != null) {
            env.error(where, "declaration.with.label", labels[0]);
        }
        if (field != null) {
            if (ctx.getLocalClass(field.getName()) != null
                && field.isInnerClass()) {
                env.error(where, "local.class.redefined", field.getName());
            }

            ctx.declare(env, field);
            if (field.isInnerClass()) {
                ClassDefinition body = field.getInnerClass();
                try {
                    vset = body.checkLocalClass(env, ctx, vset,
                                                null, null, null);
                } catch (ClassNotFound ee) {
                    env.error(where, "class.not.found", ee.name, opNames[op]);
                }
                return vset;
            }
            vset.addVar(field.number);
            return (expr != null) ? expr.checkValue(env, ctx, vset, exp) : vset;
        }

        // Argument 'expr' is either an IdentifierExpression for a declaration of
        // the form 'type x' or an AssignmentExpression for a declaration of the
        // form 'type x = initvalue'.  Note that these expressions are treated
        // specially in this context, and don't have much connection to their ordinary
        // meaning.

        Expression e = expr;

        if (e.op == ASSIGN) {
            expr = ((AssignExpression)e).right;
            e = ((AssignExpression)e).left;
        } else {
            expr = null;
        }

        boolean declError = t.isType(TC_ERROR);
        while (e.op == ARRAYACCESS) {
            ArrayAccessExpression array = (ArrayAccessExpression)e;
            if (array.index != null) {
                env.error(array.index.where, "array.dim.in.type");
                declError = true;
            }
            e = array.right;
            t = Type.tArray(t);
        }
        if (e.op == IDENT) {
            Identifier id = ((IdentifierExpression)e).id;
            if (ctx.getLocalField(id) != null) {
                env.error(where, "local.redefined", id);
            }

            field = new LocalMember(e.where, ctx.field.getClassDefinition(), mod, t, id);
            ctx.declare(env, field);

            if (expr != null) {
                vset = expr.checkInitializer(env, ctx, vset, t, exp);
                expr = convert(env, ctx, t, expr);
                field.setValue(expr); // for the sake of non-blank finals
                if (field.isConstant()) {
                    // Keep in mind that isConstant() only means expressions
                    // that are constant according to the JLS.  They might
                    // not be either constants or evaluable (eg. 1/0).
                    field.addModifiers(M_INLINEABLE);
                }
                vset.addVar(field.number);
            } else if (declError) {
                vset.addVar(field.number);
            } else {
                vset.addVarUnassigned(field.number);
            }
            return vset;
        }
        env.error(e.where, "invalid.decl");
        return vset;
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        if (field.isInnerClass()) {
            ClassDefinition body = field.getInnerClass();
            body.inlineLocalClass(env);
            return null;
        }

        // Don't generate code for variable if unused and
        // optimization is on, whether or not debugging is on
        if (env.opt() && !field.isUsed()) {
            return new ExpressionStatement(where, expr).inline(env, ctx);
        }

        ctx.declare(env, field);

        if (expr != null) {
            expr = expr.inlineValue(env, ctx);
            field.setValue(expr); // for the sake of non-blank finals
            if (env.opt() && (field.writecount == 0)) {
                if (expr.op == IDENT) {

                    // This code looks like it tests whether a final variable
                    // is being initialized by an identifier expression.
                    // Then if the identifier is a local of the same method
                    // it makes the final variable eligible to be inlined.
                    // BUT: why isn't the local also checked to make sure
                    // it is itself final?  Unknown.

                    IdentifierExpression e = (IdentifierExpression)expr;
                    if (e.field.isLocal() && ((ctx = ctx.getInlineContext()) != null) &&
                        (((LocalMember)e.field).number < ctx.varNumber)) {
                        //System.out.println("FINAL IDENT = " + field + " in " + ctx.field);
                        field.setValue(expr);
                        field.addModifiers(M_INLINEABLE);

                        // The two lines below used to elide the declaration
                        // of inlineable variables, on the theory that there
                        // wouldn't be any references.  But this breaks the
                        // translation of nested classes, which might refer to
                        // the variable.

                        //expr = null;
                        //return null;
                    }
                }
                if (expr.isConstant() || (expr.op == THIS) || (expr.op == SUPER)) {
                    //System.out.println("FINAL = " + field + " in " + ctx.field);
                    field.setValue(expr);
                    field.addModifiers(M_INLINEABLE);

                    // The two lines below used to elide the declaration
                    // of inlineable variables, on the theory that there
                    // wouldn't be any references.  But this breaks the
                    // translation of nested classes, which might refer to
                    // the variable.  Fix for 4073244.

                    //expr = null;
                    //return null;
                }
            }
        }
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        VarDeclarationStatement s = (VarDeclarationStatement)clone();
        if (expr != null) {
            s.expr = expr.copyInline(ctx);
        }
        return s;
    }

    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        if (field != null && field.isInnerClass()) {
            return thresh;      // don't copy classes...
        }
        return (expr != null) ? expr.costInline(thresh, env, ctx) : 0;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        if (expr != null && !expr.type.isType(TC_VOID)) {
            // The two lines of code directly following this comment used
            // to be in the opposite order.  They were switched so that
            // lines like the following:
            //
            //     int j = (j = 4);
            //
            // will compile correctly.  (Constructions like the above are
            // legal.  JLS 14.3.2 says that the scope of a local variable
            // includes its own initializer.)  It is important that we
            // declare `field' before we code `expr', because otherwise
            // situations can arise where `field' thinks it is assigned
            // a local variable slot that is, in actuality, assigned to
            // an entirely different variable.  (Bug id 4076729)
            ctx.declare(env, field);
            expr.codeValue(env, ctx, asm);

            asm.add(where, opc_istore + field.getType().getTypeCodeOffset(),
                    new LocalVariable(field, field.number));
        } else {
            ctx.declare(env, field);
            if (expr != null) {
                // an initial side effect, rather than an initial value
                expr.code(env, ctx, asm);
            }
        }
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        out.print("local ");
        if (field != null) {
            out.print(field + "#" + field.hashCode());
            if (expr != null) {
                out.print(" = ");
                expr.print(out);
            }
        } else {
            expr.print(out);
            out.print(";");
        }
    }
}
