/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Synthetic AST node that represents return from a split fragment of a split function for control flow reasons (break
 * or continue into a target outside the current fragment). It has no JavaScript source representation and only occurs
 * in synthetic functions created by the split-into-functions transformation. It is different from a return node in
 * that the return value is irrelevant, and doesn't affect the function's return type calculation.
 */
public final class SplitReturn extends Statement {
    private static final long serialVersionUID = 1L;

    /** The sole instance of this AST node. */
    @Ignore
    public static final SplitReturn INSTANCE = new SplitReturn();

    private SplitReturn() {
        super(NO_LINE_NUMBER, NO_TOKEN, NO_FINISH);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        return visitor.enterSplitReturn(this) ? visitor.leaveSplitReturn(this) : this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append(":splitreturn;");
    }

    private Object readResolve() {
        return INSTANCE;
    }
}
