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

import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Represents ES6 template string expression. Note that this Node class is used
 * only in "parse only" mode. In evaluation mode, Parser directly folds template
 * literal as string concatenation. Parser API uses this node to represent ES6
 * template literals "as is" rather than as a String concatenation.
 */
public final class TemplateLiteral extends Expression {
    private static final long serialVersionUID = 1L;
    private final List<Expression> exprs;

    public TemplateLiteral(final List<Expression> exprs) {
        super(exprs.get(0).getToken(), exprs.get(exprs.size() - 1).finish);
        this.exprs = exprs;
    }

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    public Node accept(NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterTemplateLiteral(this)) {
            return visitor.leaveTemplateLiteral(this);
        }

        return this;
    }

    @Override
    public void toString(StringBuilder sb, boolean printType) {
        for (Expression expr : exprs) {
            sb.append(expr);
        }
    }

    /**
     * The list of expressions that are part of this template literal.
     *
     * @return the list of expressions that are part of this template literal.
     */
    public List<Expression> getExpressions() {
        return Collections.unmodifiableList(exprs);
    }
}