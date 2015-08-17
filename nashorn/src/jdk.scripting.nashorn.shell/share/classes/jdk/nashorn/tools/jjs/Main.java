/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;
import jdk.nashorn.api.tree.AssignmentTree;
import jdk.nashorn.api.tree.BinaryTree;
import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.CompoundAssignmentTree;
import jdk.nashorn.api.tree.ConditionalExpressionTree;
import jdk.nashorn.api.tree.ExpressionTree;
import jdk.nashorn.api.tree.ExpressionStatementTree;
import jdk.nashorn.api.tree.InstanceOfTree;
import jdk.nashorn.api.tree.MemberSelectTree;
import jdk.nashorn.api.tree.SimpleTreeVisitorES5_1;
import jdk.nashorn.api.tree.Tree;
import jdk.nashorn.api.tree.UnaryTree;
import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.tools.Shell;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.UserInterruptException;

/**
 * Interactive command line Shell for Nashorn.
 */
public final class Main extends Shell {
    private Main() {}

    static final Preferences PREFS = Preferences.userRoot().node("tool/jjs");

    /**
     * Main entry point with the default input, output and error streams.
     *
     * @param args The command line arguments
     */
    public static void main(final String[] args) {
        try {
            final int exitCode = main(System.in, System.out, System.err, args);
            if (exitCode != SUCCESS) {
                System.exit(exitCode);
            }
        } catch (final IOException e) {
            System.err.println(e); //bootstrapping, Context.err may not exist
            System.exit(IO_ERROR);
        }
    }

    /**
     * Starting point for executing a {@code Shell}. Starts a shell with the
     * given arguments and streams and lets it run until exit.
     *
     * @param in input stream for Shell
     * @param out output stream for Shell
     * @param err error stream for Shell
     * @param args arguments to Shell
     *
     * @return exit code
     *
     * @throws IOException if there's a problem setting up the streams
     */
    public static int main(final InputStream in, final OutputStream out, final OutputStream err, final String[] args) throws IOException {
        return new Main().run(in, out, err, args);
    }

    /**
     * read-eval-print loop for Nashorn shell.
     *
     * @param context the nashorn context
     * @param global  global scope object to use
     * @return return code
     */
    protected int readEvalPrint(final Context context, final Global global) {
        final ScriptEnvironment env = context.getEnv();
        final String prompt = bundle.getString("shell.prompt");
        final PrintWriter err = context.getErr();
        final Global oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        final Parser parser = Parser.create();

        // simple source "tab completer" for nashorn
        final Completer completer = new Completer() {
            @Override
            public int complete(final String test, final int cursor, final List<CharSequence> result) {
                // check that cursor is at the end of test string. Do not complete in the middle!
                if (cursor != test.length()) {
                    return cursor;
                }

                // if it has a ".", then assume it is a member selection expression
                final int idx = test.lastIndexOf('.');
                if (idx == -1) {
                    return cursor;
                }

                // stuff before the last "."
                final String exprBeforeDot = test.substring(0, idx);

                // Make sure that completed code will have a member expression! Adding ".x" as a
                // random property/field name selected to make it possible to be a proper member select
                final ExpressionTree topExpr = getTopLevelExpression(parser, exprBeforeDot + ".x");
                if (topExpr == null) {
                    // did not parse to be a top level expression, no suggestions!
                    return cursor;
                }


                // Find 'right most' member select expression's start position
                final int startPosition = (int) getStartOfMemberSelect(topExpr);
                if (startPosition == -1) {
                    // not a member expression that we can handle for completion
                    return cursor;
                }

                // The part of the right most member select expression before the "."
                final String objExpr = test.substring(startPosition, idx);

                // try to evaluate the object expression part as a script
                Object obj = null;
                try {
                    obj = context.eval(global, objExpr, global, "<suggestions>");
                } catch (Exception ignored) {
                    // throw the exception - this is during tab-completion
                }

                if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                    // where is the last dot? Is there a partial property name specified?
                    final String prefix = test.substring(idx + 1);
                    if (prefix.isEmpty()) {
                        // no user specified "prefix". List all properties of the object
                        result.addAll(PropertiesHelper.getProperties(obj));
                        return cursor;
                    } else {
                        // list of properties matching the user specified prefix
                        result.addAll(PropertiesHelper.getProperties(obj, prefix));
                        return idx + 1;
                    }
                }

                return cursor;
            }
        };

        try (final Console in = new Console(System.in, System.out, PREFS, completer)) {
            if (globalChanged) {
                Context.setGlobal(global);
            }

            global.addShellBuiltins();

            while (true) {
                String source = "";
                try {
                    source = in.readLine(prompt);
                } catch (final IOException ioe) {
                    err.println(ioe.toString());
                    if (env._dump_on_error) {
                        ioe.printStackTrace(err);
                    }
                    return IO_ERROR;
                } catch (final UserInterruptException ex) {
                    break;
                }

                if (source.isEmpty()) {
                    continue;
                }

                try {
                    final Object res = context.eval(global, source, global, "<shell>");
                    if (res != ScriptRuntime.UNDEFINED) {
                        err.println(JSType.toString(res));
                    }
                } catch (final Exception e) {
                    err.println(e);
                    if (env._dump_on_error) {
                        e.printStackTrace(err);
                    }
                }
            }
        } catch (final Exception e) {
            err.println(e);
            if (env._dump_on_error) {
                e.printStackTrace(err);
            }
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }

        return SUCCESS;
    }

    // returns ExpressionTree if the given code parses to a top level expression.
    // Or else returns null.
    private ExpressionTree getTopLevelExpression(final Parser parser, final String code) {
        try {
            final CompilationUnitTree cut = parser.parse("<code>", code, null);
            final List<? extends Tree> stats = cut.getSourceElements();
            if (stats.size() == 1) {
                final Tree stat = stats.get(0);
                if (stat instanceof ExpressionStatementTree) {
                    return ((ExpressionStatementTree)stat).getExpression();
                }
            }
        } catch (final NashornException ignored) {
            // ignore any parser error. This is for completion anyway!
            // And user will get that error later when the expression is evaluated.
        }

        return null;
    }


    private long getStartOfMemberSelect(final ExpressionTree expr) {
        if (expr instanceof MemberSelectTree) {
            return ((MemberSelectTree)expr).getStartPosition();
        }

        final Tree rightMostExpr = expr.accept(new SimpleTreeVisitorES5_1<Tree, Void>() {
            @Override
            public Tree visitAssignment(final AssignmentTree at, final Void v) {
                return at.getExpression();
            }

            @Override
            public Tree visitCompoundAssignment(final CompoundAssignmentTree cat, final Void v) {
                return cat.getExpression();
            }

            @Override
            public Tree visitConditionalExpression(final ConditionalExpressionTree cet, final Void v) {
                return cet.getFalseExpression();
            }

            @Override
            public Tree visitBinary(final BinaryTree bt, final Void v) {
                return bt.getRightOperand();
            }

            @Override
            public Tree visitInstanceOf(final InstanceOfTree it, final Void v) {
                return it.getType();
            }

            @Override
            public Tree visitUnary(final UnaryTree ut, final Void v) {
                return ut.getExpression();
            }
        }, null);

        return (rightMostExpr instanceof MemberSelectTree)?
            rightMostExpr.getStartPosition() : -1L;
    }
}
