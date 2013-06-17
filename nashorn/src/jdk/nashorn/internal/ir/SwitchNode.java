/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of a SWITCH statement.
 */
@Immutable
public final class SwitchNode extends BreakableNode {
    /** Switch expression. */
    private final Node expression;

    /** Switch cases. */
    private final List<CaseNode> cases;

    /** Switch default index. */
    private final int defaultCaseIndex;

    /** Tag symbol. */
    private Symbol tag;

    /**
     * Constructor
     *
     * @param lineNumber  lineNumber
     * @param token       token
     * @param finish      finish
     * @param expression  switch expression
     * @param cases       cases
     * @param defaultCase the default case node - null if none, otherwise has to be present in cases list
     */
    public SwitchNode(final int lineNumber, final long token, final int finish, final Node expression, final List<CaseNode> cases, final CaseNode defaultCase) {
        super(lineNumber, token, finish, new Label("switch_break"));
        this.expression       = expression;
        this.cases            = cases;
        this.defaultCaseIndex = defaultCase == null ? -1 : cases.indexOf(defaultCase);
    }

    private SwitchNode(final SwitchNode switchNode, final Node expression, final List<CaseNode> cases, final int defaultCase) {
        super(switchNode);
        this.expression       = expression;
        this.cases            = cases;
        this.defaultCaseIndex = defaultCase;
        this.tag              = switchNode.getTag(); //TODO are symbols inhereted as references?
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        final List<CaseNode> newCases = new ArrayList<>();
        for (final CaseNode caseNode : cases) {
            newCases.add(new CaseNode(caseNode, caseNode.getTest(), caseNode.getBody()));
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, newCases, defaultCaseIndex));
    }

    @Override
    public boolean isTerminal() {
        //there must be a default case, and that including all other cases must terminate
        if (!cases.isEmpty() && defaultCaseIndex != -1) {
            for (final CaseNode caseNode : cases) {
                if (!caseNode.isTerminal()) {
                    return false;
                }
            }
            return true;
        }
        return false;

    }

    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterSwitchNode(this)) {
            return visitor.leaveSwitchNode(
                setExpression(lc, expression.accept(visitor)).
                setCases(lc, Node.accept(visitor, CaseNode.class, cases), defaultCaseIndex));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("switch (");
        expression.toString(sb);
        sb.append(')');
    }

    /**
     * Return the case node that is default case
     * @return default case or null if none
     */
    public CaseNode getDefaultCase() {
        return defaultCaseIndex == -1 ? null : cases.get(defaultCaseIndex);
    }

    /**
     * Get the cases in this switch
     * @return a list of case nodes
     */
    public List<CaseNode> getCases() {
        return Collections.unmodifiableList(cases);
    }

    /**
     * Replace case nodes with new list. the cases have to be the same
     * and the default case index the same. This is typically used
     * by NodeVisitors who perform operations on every case node
     * @param lc    lexical context
     * @param cases list of cases
     * @return new switcy node or same if no state was changed
     */
    public SwitchNode setCases(final LexicalContext lc, final List<CaseNode> cases) {
        return setCases(lc, cases, defaultCaseIndex);
    }

    private SwitchNode setCases(final LexicalContext lc, final List<CaseNode> cases, final int defaultCaseIndex) {
        if (this.cases == cases) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex));
    }

    /**
     * Set or reset the list of cases in this switch
     * @param lc lexical context
     * @param cases a list of cases, case nodes
     * @param defaultCase a case in the list that is the default - must be in the list or class will assert
     * @return new switch node or same if no state was changed
     */
    public SwitchNode setCases(final LexicalContext lc, final List<CaseNode> cases, final CaseNode defaultCase) {
        return setCases(lc, cases, defaultCase == null ? -1 : cases.indexOf(defaultCase));
    }

    /**
     * Return the expression to switch on
     * @return switch expression
     */
    public Node getExpression() {
        return expression;
    }

    /**
     * Set or reset the expression to switch on
     * @param lc lexical context
     * @param expression switch expression
     * @return new switch node or same if no state was changed
     */
    public SwitchNode setExpression(final LexicalContext lc, final Node expression) {
        if (this.expression == expression) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex));
    }

    /**
     * Get the tag symbol for this switch. The tag symbol is where
     * the switch expression result is stored
     * @return tag symbol
     */
    public Symbol getTag() {
        return tag;
    }

    /**
     * Set the tag symbol for this switch. The tag symbol is where
     * the switch expression result is stored
     * @param tag a symbol
     */
    public void setTag(final Symbol tag) {
        this.tag = tag;
    }
}

