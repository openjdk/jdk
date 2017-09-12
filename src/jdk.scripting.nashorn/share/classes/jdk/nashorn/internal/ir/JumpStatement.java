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

import jdk.nashorn.internal.codegen.Label;

/**
 * Common base class for jump statements (e.g. {@code break} and {@code continue}).
 */
public abstract class JumpStatement extends Statement implements JoinPredecessor {
    private static final long serialVersionUID = 1L;

    private final String labelName;
    private final LocalVariableConversion conversion;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param labelName  label name for break or null if none
     */
    protected JumpStatement(final int lineNumber, final long token, final int finish, final String labelName) {
        super(lineNumber, token, finish);
        this.labelName = labelName;
        this.conversion = null;
    }

    /**
     * Copy constructor.
     * @param jumpStatement the original jump statement.
     * @param conversion a new local variable conversion.
     */
    protected JumpStatement(final JumpStatement jumpStatement, final LocalVariableConversion conversion) {
        super(jumpStatement);
        this.labelName = jumpStatement.labelName;
        this.conversion = conversion;
    }

    @Override
    public boolean hasGoto() {
        return true;
    }

    /**
     * Get the label name for this break node
     * @return label name, or null if none
     */
    public String getLabelName() {
        return labelName;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append(getStatementName());

        if (labelName != null) {
            sb.append(' ').append(labelName);
        }
    }

    abstract String getStatementName();

    /**
     * Finds the target for this jump statement in a lexical context.
     * @param lc the lexical context
     * @return the target, or null if not found
     */
    public abstract BreakableNode getTarget(final LexicalContext lc);

    /**
     * Returns the label corresponding to this kind of jump statement (either a break or continue label) in the target.
     * @param target the target. Note that it need not be the target of this jump statement, as the method can retrieve
     * a label on any passed target as long as the target has a label of the requisite kind. Of course, it is advisable
     * to invoke the method on a jump statement that targets the breakable.
     * @return the label of the target corresponding to the kind of jump statement.
     * @throws ClassCastException if invoked on the kind of breakable node that this jump statement is not prepared to
     * handle.
     */
    abstract Label getTargetLabel(final BreakableNode target);

    /**
     * Returns the label this jump statement targets.
     * @param lc the lexical context
     * @return the label this jump statement targets.
     */
    public Label getTargetLabel(final LexicalContext lc) {
        return getTargetLabel(getTarget(lc));
    }

    /**
     * Returns the limit node for popping scopes when this jump statement is effected.
     * @param lc the current lexical context
     * @return the limit node for popping scopes when this jump statement is effected.
     */
    public LexicalContextNode getPopScopeLimit(final LexicalContext lc) {
        // In most cases (break and continue) this is equal to the target.
        return getTarget(lc);
    }

    @Override
    public JumpStatement setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(this.conversion == conversion) {
            return this;
        }
        return createNewJumpStatement(conversion);
    }

    abstract JumpStatement createNewJumpStatement(LocalVariableConversion newConversion);

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }
}
