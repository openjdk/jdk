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

package jdk.nashorn.internal.ir;

import java.util.Objects;
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation for synthetic jump into an inlined finally statement.
 */
@Immutable
public final class JumpToInlinedFinally extends JumpStatement {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     *
     * @param labelName  label name for inlined finally block
     */
    public JumpToInlinedFinally(final String labelName) {
        super(NO_LINE_NUMBER, NO_TOKEN, NO_FINISH, Objects.requireNonNull(labelName));
    }

    private JumpToInlinedFinally(final JumpToInlinedFinally breakNode, final LocalVariableConversion conversion) {
        super(breakNode, conversion);
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterJumpToInlinedFinally(this)) {
            return visitor.leaveJumpToInlinedFinally(this);
        }

        return this;
    }

    @Override
    JumpStatement createNewJumpStatement(final LocalVariableConversion conversion) {
        return new JumpToInlinedFinally(this, conversion);
    }

    @Override
    String getStatementName() {
        return ":jumpToInlinedFinally";
    }

    @Override
    public Block getTarget(final LexicalContext lc) {
        return lc.getInlinedFinally(getLabelName());
    }

    @Override
    public TryNode getPopScopeLimit(final LexicalContext lc) {
        // Returns the try node to which this jump's target belongs. This will make scope popping also pop the scope
        // for the body of the try block, if it needs scope.
        return lc.getTryNodeForInlinedFinally(getLabelName());
    }

    @Override
    Label getTargetLabel(final BreakableNode target) {
        assert target != null;
        // We're jumping to the entry of the inlined finally block
        return ((Block)target).getEntryLabel();
    }
}
