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
import sun.tools.asm.TryData;
import sun.tools.asm.CatchData;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class TryStatement extends Statement {
    Statement body;
    Statement args[];
    long arrayCloneWhere;       // private note posted from MethodExpression

    /**
     * Constructor
     */
    public TryStatement(long where, Statement body, Statement args[]) {
        super(TRY, where);
        this.body = body;
        this.args = args;
    }

    /**
     * Check statement
     */
    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        checkLabel(env, ctx);
        try {
            vset = reach(env, vset);
            Hashtable newexp = new Hashtable();
            CheckContext newctx =  new CheckContext(ctx, this);

            // Check 'try' block.  A variable is DA (DU) before the try
            // block if it is DA (DU) before the try statement.
            Vset vs = body.check(env, newctx, vset.copy(), newexp);

            // A variable is DA before a catch block if it is DA before the
            // try statement.  A variable is DU before a catch block if it
            // is DU after the try block and before any 'break', 'continue',
            // 'throw', or 'return' contained therein. That is, the variable
            // is DU upon entry to the try-statement and is not assigned to
            // anywhere within the try block.
            Vset cvs = Vset.firstDAandSecondDU(vset, vs.copy().join(newctx.vsTryExit));

            for (int i = 0 ; i < args.length ; i++) {
                // A variable is DA (DU) after a try statement if
                // it is DA (DU) after every catch block.
                vs = vs.join(args[i].check(env, newctx, cvs.copy(), exp));
            }

            // Check that catch statements are actually reached
            for (int i = 1 ; i < args.length ; i++) {
                CatchStatement cs = (CatchStatement)args[i];
                if (cs.field == null) {
                    continue;
                }
                Type type = cs.field.getType();
                ClassDefinition def = env.getClassDefinition(type);

                for (int j = 0 ; j < i ; j++) {
                    CatchStatement cs2 = (CatchStatement)args[j];
                    if (cs2.field == null) {
                        continue;
                    }
                    Type t = cs2.field.getType();
                    ClassDeclaration c = env.getClassDeclaration(t);
                    if (def.subClassOf(env, c)) {
                        env.error(args[i].where, "catch.not.reached");
                        break;
                    }
                }
            }

            ClassDeclaration ignore1 = env.getClassDeclaration(idJavaLangError);
            ClassDeclaration ignore2 = env.getClassDeclaration(idJavaLangRuntimeException);

            // Make sure the exception is actually throw in that part of the code
            for (int i = 0 ; i < args.length ; i++) {
                CatchStatement cs = (CatchStatement)args[i];
                if (cs.field == null) {
                    continue;
                }
                Type type = cs.field.getType();
                if (!type.isType(TC_CLASS)) {
                    // CatchStatement.checkValue() will have already printed
                    // an error message
                    continue;
                }

                ClassDefinition def = env.getClassDefinition(type);

                // Anyone can throw these!
                if (def.subClassOf(env, ignore1) || def.superClassOf(env, ignore1) ||
                    def.subClassOf(env, ignore2) || def.superClassOf(env, ignore2)) {
                    continue;
                }

                // Make sure the exception is actually throw in that part of the code
                boolean ok = false;
                for (Enumeration e = newexp.keys() ; e.hasMoreElements() ; ) {
                    ClassDeclaration c = (ClassDeclaration)e.nextElement();
                    if (def.superClassOf(env, c) || def.subClassOf(env, c)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok && arrayCloneWhere != 0
                    && def.getName().toString().equals("java.lang.CloneNotSupportedException")) {
                    env.error(arrayCloneWhere, "warn.array.clone.supported", def.getName());
                }

                if (!ok) {
                    env.error(cs.where, "catch.not.thrown", def.getName());
                }
            }

            // Only carry over exceptions that are not caught
            for (Enumeration e = newexp.keys() ; e.hasMoreElements() ; ) {
                ClassDeclaration c = (ClassDeclaration)e.nextElement();
                ClassDefinition def = c.getClassDefinition(env);
                boolean add = true;
                for (int i = 0 ; i < args.length ; i++) {
                    CatchStatement cs = (CatchStatement)args[i];
                    if (cs.field == null) {
                        continue;
                    }
                    Type type = cs.field.getType();
                    if (type.isType(TC_ERROR))
                        continue;
                    if (def.subClassOf(env, env.getClassDeclaration(type))) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    exp.put(c, newexp.get(c));
                }
            }
            // A variable is DA (DU) after a try statement if it is DA (DU)
            // after the try block and after every catch block. These variables
            // are represented by 'vs'.  If the try statement is labelled, we
            // may also exit from it (including from within a catch block) via
            // a break statement.
            // If there is a finally block, the Vset returned here is further
            // adjusted. Note that this 'TryStatement' node will be a child of
            // a 'FinallyStatement' node in that case.
            return ctx.removeAdditionalVars(vs.join(newctx.vsBreak));
        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, opNames[op]);
            return vset;
        }
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        if (body != null) {
            body = body.inline(env, new Context(ctx, this));
        }
        if (body == null) {
            return null;
        }
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                args[i] = args[i].inline(env, new Context(ctx, this));
            }
        }
        return (args.length == 0) ? eliminate(env, body) : this;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        TryStatement s = (TryStatement)clone();
        if (body != null) {
            s.body = body.copyInline(ctx, valNeeded);
        }
        s.args = new Statement[args.length];
        for (int i = 0 ; i < args.length ; i++) {
            if (args[i] != null) {
                s.args[i] = args[i].copyInline(ctx, valNeeded);
            }
        }
        return s;
    }

    /**
     * Compute cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx){

        // Don't inline methods containing try statements.
        // If the try statement is being inlined in order to
        // inline a method that returns a value which is
        // a subexpression of an expression involving the
        // operand stack, then the early operands may get lost.
        // This shows up as a verifier error.  For example,
        // in the following:
        //
        //    public static int test() {
        //       try { return 2; } catch (Exception e)  { return 0; }
        //    }
        //
        //    System.out.println(test());
        //
        // an inlined call to test() might look like this:
        //
        //     0 getstatic <Field java.io.PrintStream out>
        //     3 iconst_2
        //     4 goto 9
        //     7 pop
        //     8 iconst_0
        //     9 invokevirtual <Method void println(int)>
        //    12 return
        //  Exception table:
        //     from   to  target type
        //       3     7     7   <Class java.lang.Exception>
        //
        // This fails to verify because the operand stored
        // for System.out gets axed at an exception, leading to
        // an inconsistent stack depth at pc=7.
        //
        // Note that although all code must be able to be inlined
        // to implement initializers, this problem doesn't come up,
        // as try statements themselves can never be expressions.
        // It suffices here to make sure they are never inlined as part
        // of optimization.

        return thresh;
    }

    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        CodeContext newctx = new CodeContext(ctx, this);

        TryData td = new TryData();
        for (int i = 0 ; i < args.length ; i++) {
            Type t = ((CatchStatement)args[i]).field.getType();
            if (t.isType(TC_CLASS)) {
                td.add(env.getClassDeclaration(t));
            } else {
                td.add(t);
            }
        }
        asm.add(where, opc_try, td);
        if (body != null) {
            body.code(env, newctx, asm);
        }

        asm.add(td.getEndLabel());
        asm.add(where, opc_goto, newctx.breakLabel);

        for (int i = 0 ; i < args.length ; i++) {
            CatchData cd = td.getCatch(i);
            asm.add(cd.getLabel());
            args[i].code(env, newctx, asm);
            asm.add(where, opc_goto, newctx.breakLabel);
        }

        asm.add(newctx.breakLabel);
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
        for (int i = 0 ; i < args.length ; i++) {
            out.print(" ");
            args[i].print(out, indent);
        }
    }
}
