/*
 * Copyright (c) 1994, 2014, Oracle and/or its affiliates. All rights reserved.
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
import sun.tools.asm.TryData;
import sun.tools.asm.CatchData;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class FinallyStatement extends Statement {
    Statement body;
    Statement finalbody;
    boolean finallyCanFinish; // does finalBody never return?
    boolean needReturnSlot;   // set by inner return statement
    Statement init;           // try object expression  or declaration from parser
    LocalMember tryTemp;      // temp holding the try object, if any

    /**
     * Constructor
     */
    public FinallyStatement(long where, Statement body, Statement finalbody) {
        super(FINALLY, where);
        this.body = body;
        this.finalbody = finalbody;
    }

//    /**
//     * Constructor for  try (init) {body}
//     */
//    public FinallyStatement(long where, Statement init, Statement body, int junk) {
//      this(where, body, null);
//      this.init = init;
//    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        vset = reach(env, vset);
        Hashtable<Object, Object> newexp = new Hashtable<>();

        // Handle the proposed 'try (init) { stmts } finally { stmts }' syntax.
        // This feature has not been adopted, and support is presently disabled.
        /*-----------------------------------------------------------*
        if (init != null) {
            ClassDefinition sourceClass = ctx.field.getClassDefinition();
            Expression tryExpr = null;
            DeclarationStatement tryDecl = null;
            long where = init.getWhere();
            // find out whether init is a simple expression or a declaration
            if (init.getOp() == EXPRESSION) {
                tryExpr = ((ExpressionStatement)init).expr;
                init = null;    // restore it below
                vset = tryExpr.checkValue(env, ctx, vset, exp);
            } else if (init.getOp() == DECLARATION) {
                tryDecl = (DeclarationStatement) init;
                init = null;    // restore it below
                vset = tryDecl.checkBlockStatement(env, ctx, vset, exp);
                if (tryDecl.args.length != 1) {
                    env.error(where, "invalid.decl");
                } else {
                    LocalMember field =
                        ((VarDeclarationStatement) tryDecl.args[0]).field;
                    tryExpr = new IdentifierExpression(where, field);
                    tryExpr.type = field.getType();
                }
            } else {
                env.error(where, "invalid.expr");
                vset = init.check(env, ctx, vset, exp);
            }
            Type type = (tryExpr == null) ? Type.tError : tryExpr.getType();

            MemberDefinition tryEnter = null;
            MemberDefinition tryExit = null;
            if (!type.isType(TC_CLASS)) {
                if (!type.isType(TC_ERROR)) {
                    env.error(where, "invalid.method.invoke", type);
                }
            } else {
                Identifier idTryEnter = Identifier.lookup("tryEnter");
                Identifier idTryExit = Identifier.lookup("tryExit");
                Type tTryMethod = Type.tMethod(Type.tVoid);
                try {
                    ClassDefinition tryClass = env.getClassDefinition(type);
                    tryEnter = tryClass.matchMethod(env, sourceClass, idTryEnter);
                    tryExit = tryClass.matchMethod(env, sourceClass, idTryExit);
                    if (tryEnter != null && !tryEnter.getType().equals(tTryMethod)) {
                        tryEnter = null;
                    }
                    if (tryExit != null && !tryExit.getType().equals(tTryMethod)) {
                        tryExit = null;
                    }
                } catch (ClassNotFound ee) {
                    env.error(where, "class.not.found", ee.name, ctx.field);
                } catch (AmbiguousMember ee) {
                    Identifier id = ee.field1.getName();
                    env.error(where, "ambig.field", id, ee.field1, ee.field2);
                }
            }
            if (tryEnter == null || tryExit == null) {
                // Make a better (more didactic) error here!
                env.error(where, "invalid.method.invoke", type);
            } else {
                tryTemp = new LocalMember(where, sourceClass, 0,
                                          type, Identifier.lookup("<try_object>"));
                ctx = new Context(ctx, this);
                ctx.declare(env, tryTemp);

                Expression e;
                e = new IdentifierExpression(where, tryTemp);
                e = new AssignExpression(where, e, tryExpr);
                e = new MethodExpression(where, e, tryEnter, new Expression[0]);
                e.type = Type.tVoid;
                Statement enterCall = new ExpressionStatement(where, e);
                // store it on the init, for code generation
                if (tryDecl != null) {
                    Statement args2[] = { tryDecl.args[0], enterCall };
                    tryDecl.args = args2;
                    init = tryDecl;
                } else {
                    init = enterCall;
                }
                e = new IdentifierExpression(where, tryTemp);
                e = new MethodExpression(where, e, tryExit, new Expression[0]);
                e.type = Type.tVoid;
                Statement exitCall = new ExpressionStatement(where, e);
                finalbody = exitCall;
            }
        }
        *-----------------------------------------------------------*/

        // Check the try part. We reach the end of the try part either by
        // finishing normally, or doing a break to the label of the try/finally.
        // NOTE: I don't think newctx1.vsBreak is ever used -- see TryStatement.
        CheckContext newctx1 = new CheckContext(ctx, this);
        Vset vset1 = body.check(env, newctx1, vset.copy(), newexp)
            .join(newctx1.vsBreak);
        // Check the finally part.
        CheckContext newctx2 = new CheckContext(ctx, this);
        // Should never access this field.  The null indicates the finally part.
        newctx2.vsContinue = null;
        Vset vset2 = finalbody.check(env, newctx2, vset, exp);
        finallyCanFinish = !vset2.isDeadEnd();
        vset2 = vset2.join(newctx2.vsBreak);
        // If !finallyCanFinish, then the only possible exceptions that can
        // occur at this point are the ones preceding the try/finally, or
        // the ones generated by the finally.  Anything in the try is
        // irrelevant. Otherwise, we have to merge in all the exceptions
        // generated by the body into exp.
        if (finallyCanFinish) {
            // Add newexp's back into exp; cf. ThrowStatement.check().
            for (Enumeration<?> e = newexp.keys() ; e.hasMoreElements() ; ) {
                Object def = e.nextElement();
                exp.put(def, newexp.get(def));
            }
        }
        return ctx.removeAdditionalVars(vset1.addDAandJoinDU(vset2));
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        if (tryTemp != null) {
            ctx = new Context(ctx, this);
            ctx.declare(env, tryTemp);
        }
        if (init != null) {
            init = init.inline(env, ctx);
        }
        if (body != null) {
            body = body.inline(env, ctx);
        }
        if (finalbody != null) {
            finalbody = finalbody.inline(env, ctx);
        }
        if (body == null) {
            return eliminate(env, finalbody);
        }
        if (finalbody == null) {
            return eliminate(env, body);
        }
        return this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        FinallyStatement s = (FinallyStatement)clone();
        if (tryTemp != null) {
            s.tryTemp = tryTemp.copyInline(ctx);
        }
        if (init != null) {
            s.init = init.copyInline(ctx, valNeeded);
        }
        if (body != null) {
            s.body = body.copyInline(ctx, valNeeded);
        }
        if (finalbody != null) {
            s.finalbody = finalbody.copyInline(ctx, valNeeded);
        }
        return s;
     }

    /**
     * Compute cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx){
        int cost = 4;
        if (init != null) {
            cost += init.costInline(thresh, env,ctx);
            if (cost >= thresh) return cost;
        }
        if (body != null) {
            cost += body.costInline(thresh, env,ctx);
            if (cost >= thresh) return cost;
        }
        if (finalbody != null) {
            cost += finalbody.costInline(thresh, env,ctx);
        }
        return cost;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        ctx = new Context(ctx);
        Integer num1 = null, num2 = null;
        Label endLabel = new Label();

        if (tryTemp != null) {
            ctx.declare(env, tryTemp);
        }
        if (init != null) {
            CodeContext exprctx = new CodeContext(ctx, this);
            init.code(env, exprctx, asm);
        }

        if (finallyCanFinish) {
            LocalMember f1, f2;
            ClassDefinition thisClass = ctx.field.getClassDefinition();

            if (needReturnSlot) {
                Type returnType = ctx.field.getType().getReturnType();
                LocalMember localfield = new LocalMember(0, thisClass, 0,
                                                       returnType,
                                                       idFinallyReturnValue);
                ctx.declare(env, localfield);
                Environment.debugOutput("Assigning return slot to " + localfield.number);
            }

            // allocate space for the exception and return address
            f1 = new LocalMember(where, thisClass, 0, Type.tObject, null);
            f2 = new LocalMember(where, thisClass, 0, Type.tInt, null);
            num1 = ctx.declare(env, f1);
            num2 = ctx.declare(env, f2);
        }

        TryData td = new TryData();
        td.add(null);

        // Main body
        CodeContext bodyctx = new CodeContext(ctx, this);
        asm.add(where, opc_try, td); // start of protected code
        body.code(env, bodyctx, asm);
        asm.add(bodyctx.breakLabel);
        asm.add(td.getEndLabel());   // end of protected code

        // Cleanup afer body
        if (finallyCanFinish) {
            asm.add(where, opc_jsr, bodyctx.contLabel);
            asm.add(where, opc_goto, endLabel);
        } else {
            // just goto the cleanup code.  It will never return.
            asm.add(where, opc_goto, bodyctx.contLabel);
        }

        // Catch code
        CatchData cd = td.getCatch(0);
        asm.add(cd.getLabel());
        if (finallyCanFinish) {
            asm.add(where, opc_astore, num1); // store exception
            asm.add(where, opc_jsr, bodyctx.contLabel);
            asm.add(where, opc_aload, num1); // rethrow exception
            asm.add(where, opc_athrow);
        } else {
            // pop exception off stack.  Fall through to finally code
            asm.add(where, opc_pop);
        }

        // The finally part, which is marked by the contLabel.  Update
        //    breakLabel: since break's in the finally are different
        //    contLabel:  to null to indicate no longer in the protected code.
        asm.add(bodyctx.contLabel);
        bodyctx.contLabel = null;
        bodyctx.breakLabel = endLabel;
        if (finallyCanFinish) {
            asm.add(where, opc_astore, num2);  // save the return address
            finalbody.code(env, bodyctx, asm); // execute the cleanup code
            asm.add(where, opc_ret, num2);     // return
        } else {
            finalbody.code(env, bodyctx, asm); // execute the cleanup code
        }
        asm.add(endLabel);                     // breaks come here
    }

    /**
     * Print
     */
    public void print(PrintStream out, int indent) {
        super.print(out, indent);
        out.print("try ");
        if (body != null) {
            body.print(out, indent);
        } else {
            out.print("<empty>");
        }
        out.print(" finally ");
        if (finalbody != null) {
            finalbody.print(out, indent);
        } else {
            out.print("<empty>");
        }
    }
}
