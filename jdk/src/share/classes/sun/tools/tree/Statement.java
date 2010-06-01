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
class Statement extends Node {
    public static final Vset DEAD_END = Vset.DEAD_END;
    Identifier labels[] = null;

    /**
     * Constructor
     */
    Statement(int op, long where) {
        super(op, where);
    }

    /**
     * An empty statement.  Its costInline is infinite.
     */
    public static final Statement empty = new Statement(STAT, 0);

    /**
     * The largest possible interesting inline cost value.
     */
    public static final int MAXINLINECOST =
                      Integer.getInteger("javac.maxinlinecost",
                                         30).intValue();

    /**
     * Insert a bit of code at the front of a statement.
     * Side-effect s2, if it is a CompoundStatement.
     */
    public static Statement insertStatement(Statement s1, Statement s2) {
        if (s2 == null) {
            s2 = s1;
        } else if (s2 instanceof CompoundStatement) {
            // Do not add another level of block nesting.
            ((CompoundStatement)s2).insertStatement(s1);
        } else {
            Statement body[] = { s1, s2 };
            s2 = new CompoundStatement(s1.getWhere(), body);
        }
        return s2;
    }

    /**
     * Set the label of a statement
     */
    public void setLabel(Environment env, Expression e) {
        if (e.op == IDENT) {
            if (labels == null) {
                labels = new Identifier[1];
            } else {
                // this should almost never happen.  Multiple labels on
                // the same statement.  But handle it gracefully.
                Identifier newLabels[] = new Identifier[labels.length + 1];
                System.arraycopy(labels, 0, newLabels, 1, labels.length);
                labels = newLabels;
            }
            labels[0] = ((IdentifierExpression)e).id;
        } else {
            env.error(e.where, "invalid.label");
        }
    }

    /**
     * Check a statement
     */
    public Vset checkMethod(Environment env, Context ctx, Vset vset, Hashtable exp) {
        // Set up ctx.getReturnContext() for the sake of ReturnStatement.check().
        CheckContext mctx = new CheckContext(ctx, new Statement(METHOD, 0));
        ctx = mctx;

        vset = check(env, ctx, vset, exp);

        // Check for return
        if (!ctx.field.getType().getReturnType().isType(TC_VOID)) {
            // In general, we suppress further error messages due to
            // unreachable statements after reporting the first error
            // along a flow path (using 'clearDeadEnd').   Here, we
            // report an error anyway, because the end of the method
            // should be unreachable despite the earlier error.  The
            // difference in treatment is due to the fact that, in this
            // case, the error is reachability, not unreachability.
            // NOTE: In addition to this subtle difference in the quality
            // of the error diagnostics, this treatment is essential to
            // preserve the correctness of using 'clearDeadEnd' to implement
            // the special-case reachability rules for if-then and if-then-else.
            if (!vset.isDeadEnd()) {
                env.error(ctx.field.getWhere(), "return.required.at.end", ctx.field);
            }
        }

        // Simulate a return at the end.
        vset = vset.join(mctx.vsBreak);

        return vset;
    }
    Vset checkDeclaration(Environment env, Context ctx, Vset vset, int mod, Type t, Hashtable exp) {
        throw new CompilerError("checkDeclaration");
    }

    /**
     * Make sure the labels on this statement do not duplicate the
     * labels on any enclosing statement.  Provided as a convenience
     * for subclasses.
     */
    protected void checkLabel(Environment env, Context ctx) {
        if (labels != null) {
            loop: for (int i = 0; i < labels.length; i++) {
                // Make sure there is not a double label on this statement.
                for (int j = i+1; j < labels.length; j++) {
                    if (labels[i] == labels[j]) {
                        env.error(where, "nested.duplicate.label", labels[i]);
                        continue loop;
                    }
                }

                // Make sure no enclosing statement has the same label.
                CheckContext destCtx =
                    (CheckContext) ctx.getLabelContext(labels[i]);

                if (destCtx != null) {
                    // Check to make sure the label is in not uplevel.
                    if (destCtx.frameNumber == ctx.frameNumber) {
                        env.error(where, "nested.duplicate.label", labels[i]);
                    }
                }
            } // end loop
        }
    }

    Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        throw new CompilerError("check");
    }

    /** This is called in contexts where declarations are valid. */
    Vset checkBlockStatement(Environment env, Context ctx, Vset vset, Hashtable exp) {
        return check(env, ctx, vset, exp);
    }

    Vset reach(Environment env, Vset vset) {
        if (vset.isDeadEnd()) {
            env.error(where, "stat.not.reached");
            vset = vset.clearDeadEnd();
        }
        return vset;
    }

    /**
     * Inline
     */
    public Statement inline(Environment env, Context ctx) {
        return this;
    }

    /**
     * Eliminate this statement, which is only possible if it has no label.
     */
    public Statement eliminate(Environment env, Statement s) {
        if ((s != null) && (labels != null)) {
            Statement args[] = {s};
            s = new CompoundStatement(where, args);
            s.labels = labels;
        }
        return s;
    }


    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        throw new CompilerError("code");
    }

    /**
     * Generate the code to call all finally's for a break, continue, or
     * return statement.  We must call "jsr" on all the cleanup code between
     * the current context "ctx", and the destination context "stopctx".
     * If 'save' isn't null, there is also a value on the top of the stack
     */
    void codeFinally(Environment env, Context ctx, Assembler asm,
                        Context stopctx, Type save) {
        Integer num = null;
        boolean haveCleanup = false; // there is a finally or synchronize;
        boolean haveNonLocalFinally = false; // some finally doesn't return;

        for (Context c = ctx; (c != null) && (c != stopctx); c = c.prev) {
            if (c.node == null)
                continue;
            if (c.node.op == SYNCHRONIZED) {
                haveCleanup = true;
            } else if (c.node.op == FINALLY
                          && ((CodeContext)c).contLabel != null) {
                // c.contLabel == null indicates we're in the "finally" part
                haveCleanup = true;
                FinallyStatement st = ((FinallyStatement)(c.node));
                if (!st.finallyCanFinish) {
                    haveNonLocalFinally = true;
                    // after hitting a non-local finally, no need generating
                    // further code, because it won't get executed.
                    break;
                }
            }
        }
        if (!haveCleanup) {
            // there is no cleanup that needs to be done.  Just quit.
            return;
        }
        if (save != null) {
            // This statement has a return value on the stack.
            ClassDefinition def = ctx.field.getClassDefinition();
            if (!haveNonLocalFinally) {
                // Save the return value in the register which should have
                // been reserved.
                LocalMember lf = ctx.getLocalField(idFinallyReturnValue);
                num = new Integer(lf.number);
                asm.add(where, opc_istore + save.getTypeCodeOffset(), num);
            } else {
                // Pop the return value.
                switch(ctx.field.getType().getReturnType().getTypeCode()) {
                    case TC_VOID:
                        break;
                    case TC_DOUBLE: case TC_LONG:
                        asm.add(where, opc_pop2); break;
                    default:
                        asm.add(where, opc_pop); break;
                }
            }
        }
        // Call each of the cleanup functions, as necessary.
        for (Context c = ctx ; (c != null)  && (c != stopctx) ; c = c.prev) {
            if (c.node == null)
                continue;
            if (c.node.op == SYNCHRONIZED) {
                asm.add(where, opc_jsr, ((CodeContext)c).contLabel);
            } else if (c.node.op == FINALLY
                          && ((CodeContext)c).contLabel != null) {
                FinallyStatement st = ((FinallyStatement)(c.node));
                Label label = ((CodeContext)c).contLabel;
                if (st.finallyCanFinish) {
                    asm.add(where, opc_jsr, label);
                } else {
                    // the code never returns, so we're done.
                    asm.add(where, opc_goto, label);
                    break;
                }
            }
        }
        // Move the return value from the register back to the stack.
        if (num != null) {
            asm.add(where, opc_iload + save.getTypeCodeOffset(), num);
        }
    }

    /*
     * Return true if the statement has the given label
     */
    public boolean hasLabel (Identifier lbl) {
        Identifier labels[] = this.labels;
        if (labels != null) {
            for (int i = labels.length; --i >= 0; ) {
                if (labels[i].equals(lbl)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the first thing is a constructor invocation
     */
    public Expression firstConstructor() {
        return null;
    }

    /**
     * Create a copy of the statement for method inlining
     */
    public Statement copyInline(Context ctx, boolean valNeeded) {
        return (Statement)clone();
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        return thresh;
    }


    /**
     * Print
     */
    void printIndent(PrintStream out, int indent) {
        for (int i = 0 ; i < indent ; i++) {
            out.print("    ");
        }
    }
    public void print(PrintStream out, int indent) {
        if (labels != null) {
            for (int i = labels.length; --i >= 0; )
                out.print(labels[i] + ": ");
        }
    }
    public void print(PrintStream out) {
        print(out, 0);
    }
}
