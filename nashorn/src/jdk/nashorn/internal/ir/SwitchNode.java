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
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of a SWITCH statement.
 */
public class SwitchNode extends BreakableNode {
    /** Switch expression. */
    private Node expression;

    /** Tag symbol. */
    private Symbol tag;

    /** Switch cases. */
    private List<CaseNode> cases;

    /** Switch default. */
    @Ignore //points to one of the members in the list above, don't traverse twice
    private CaseNode defaultCase;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     */
    public SwitchNode(final Source source, final long token, final int finish) {
        super(source, token, finish);
        this.breakLabel  = new Label("switch_break");
    }

    private SwitchNode(final SwitchNode switchNode, final CopyState cs) {
        super(switchNode);

        final List<CaseNode> newCases = new ArrayList<>();

        for (final CaseNode caseNode : switchNode.getCases()) {
           newCases.add((CaseNode)cs.existingOrCopy(caseNode));
        }

        this.expression  = cs.existingOrCopy(switchNode.getExpression());
        this.tag         = switchNode.getTag();
        this.cases       = newCases;
        this.defaultCase = (CaseNode)cs.existingOrCopy(switchNode.getDefaultCase());
        this.breakLabel  = new Label(switchNode.getBreakLabel());
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new SwitchNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            expression = expression.accept(visitor);

            for (int i = 0, count = cases.size(); i < count; i++) {
                cases.set(i, (CaseNode)cases.get(i).accept(visitor));
            }

            //the default case is in the cases list and should not be explicitly traversed!

            return visitor.leave(this);
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
     * Get the cases in this switch
     * @return a list of case nodes
     */
    public List<CaseNode> getCases() {
        return Collections.unmodifiableList(cases);
    }

    /**
     * Set or reset the list of cases in this switch
     * @param cases a list of cases, case nodes
     */
    public void setCases(final List<CaseNode> cases) {
        this.cases = cases;
    }

    /**
     * Get the default case for this switch
     * @return default case node
     */
    public CaseNode getDefaultCase() {
        return defaultCase;
    }

    /**
     * Set the default case for this switch
     * @param defaultCase default case node
     */
    public void setDefaultCase(final CaseNode defaultCase) {
        this.defaultCase = defaultCase;
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
     * @param expression switch expression
     */
    public void setExpression(final Node expression) {
        this.expression = expression;
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

