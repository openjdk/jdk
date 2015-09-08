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
public final class SwitchNode extends BreakableStatement {
    private static final long serialVersionUID = 1L;

    /** Switch expression. */
    private final Expression expression;

    /** Switch cases. */
    private final List<CaseNode> cases;

    /** Switch default index. */
    private final int defaultCaseIndex;

    /** True if all cases are 32-bit signed integer constants, without repetitions. It's a prerequisite for
     * using a tableswitch/lookupswitch when generating code. */
    private final boolean uniqueInteger;

    /** Tag symbol. */
    private final Symbol tag;

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
    public SwitchNode(final int lineNumber, final long token, final int finish, final Expression expression, final List<CaseNode> cases, final CaseNode defaultCase) {
        super(lineNumber, token, finish, new Label("switch_break"));
        this.expression       = expression;
        this.cases            = cases;
        this.defaultCaseIndex = defaultCase == null ? -1 : cases.indexOf(defaultCase);
        this.uniqueInteger    = false;
        this.tag = null;
    }

    private SwitchNode(final SwitchNode switchNode, final Expression expression, final List<CaseNode> cases,
            final int defaultCaseIndex, final LocalVariableConversion conversion, final boolean uniqueInteger, final Symbol tag) {
        super(switchNode, conversion);
        this.expression       = expression;
        this.cases            = cases;
        this.defaultCaseIndex = defaultCaseIndex;
        this.tag              = tag;
        this.uniqueInteger    = uniqueInteger;
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        final List<CaseNode> newCases = new ArrayList<>();
        for (final CaseNode caseNode : cases) {
            newCases.add(new CaseNode(caseNode, caseNode.getTest(), caseNode.getBody(), caseNode.getLocalVariableConversion()));
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, newCases, defaultCaseIndex, conversion, uniqueInteger, tag));
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
                setExpression(lc, (Expression)expression.accept(visitor)).
                setCases(lc, Node.accept(visitor, cases), defaultCaseIndex));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("switch (");
        expression.toString(sb, printType);
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
     * @return new switch node or same if no state was changed
     */
    public SwitchNode setCases(final LexicalContext lc, final List<CaseNode> cases) {
        return setCases(lc, cases, defaultCaseIndex);
    }

    private SwitchNode setCases(final LexicalContext lc, final List<CaseNode> cases, final int defaultCaseIndex) {
        if (this.cases == cases) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex, conversion, uniqueInteger, tag));
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
    public Expression getExpression() {
        return expression;
    }

    /**
     * Set or reset the expression to switch on
     * @param lc lexical context
     * @param expression switch expression
     * @return new switch node or same if no state was changed
     */
    public SwitchNode setExpression(final LexicalContext lc, final Expression expression) {
        if (this.expression == expression) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex, conversion, uniqueInteger, tag));
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
     * @param lc lexical context
     * @param tag a symbol
     * @return a switch node with the symbol set
     */
    public SwitchNode setTag(final LexicalContext lc, final Symbol tag) {
        if (this.tag == tag) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex, conversion, uniqueInteger, tag));
    }

    /**
     * Returns true if all cases of this switch statement are 32-bit signed integer constants, without repetitions.
     * @return true if all cases of this switch statement are 32-bit signed integer constants, without repetitions.
     */
    public boolean isUniqueInteger() {
        return uniqueInteger;
    }

    /**
     * Sets whether all cases of this switch statement are 32-bit signed integer constants, without repetitions.
     * @param lc lexical context
     * @param uniqueInteger if true, all cases of this switch statement have been determined to be 32-bit signed
     * integer constants, without repetitions.
     * @return this switch node, if the value didn't change, or a new switch node with the changed value
     */
    public SwitchNode setUniqueInteger(final LexicalContext lc, final boolean uniqueInteger) {
        if(this.uniqueInteger == uniqueInteger) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex, conversion, uniqueInteger, tag));
    }

    @Override
    JoinPredecessor setLocalVariableConversionChanged(final LexicalContext lc, final LocalVariableConversion conversion) {
        return Node.replaceInLexicalContext(lc, this, new SwitchNode(this, expression, cases, defaultCaseIndex, conversion, uniqueInteger, tag));
    }

}
