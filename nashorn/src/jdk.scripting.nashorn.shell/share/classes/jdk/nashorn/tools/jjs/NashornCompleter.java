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

import java.util.List;
import jdk.internal.jline.console.completer.Completer;
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
import jdk.nashorn.internal.runtime.ScriptRuntime;

// A simple source completer for nashorn
final class NashornCompleter implements Completer {
    private final Context context;
    private final Global global;
    private final Parser parser;

    NashornCompleter(final Context context, final Global global) {
        this.context = context;
        this.global = global;
        this.parser = Parser.create();
    }

    @Override
    public int complete(final String test, final int cursor, final List<CharSequence> result) {
        // check that cursor is at the end of test string. Do not complete in the middle!
        if (cursor != test.length()) {
            return cursor;
        }

        // if it has a ".", then assume it is a member selection expression
        final int idx = test.lastIndexOf('.');
        if (idx == -1) {
            if (isIdentifier(test)) {
                // identifier - return matching global variable names, if any
                result.addAll(PropertiesHelper.getProperties(global, test));
                return idx + 1;
            }

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

    // return if the given String is a valid identifier name or not
    private boolean isIdentifier(final String test) {
        if (test.isEmpty()) {
            return false;
        }

        final char[] buf = test.toCharArray();
        if (! Character.isJavaIdentifierStart(buf[0])) {
            return false;
        }

        for (int idx = 1; idx < buf.length; idx++) {
            if (! Character.isJavaIdentifierPart(buf[idx])) {
                return false;
            }
        }

        return true;
    }
}
