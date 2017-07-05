/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * IR representation for class definitions.
 */
public class ClassNode extends Expression {
    private static final long serialVersionUID = 1L;

    private final IdentNode ident;
    private final Expression classHeritage;
    private final PropertyNode constructor;
    private final List<PropertyNode> classElements;
    private final int line;
    private final boolean isStatement;

    /**
     * Constructor.
     *
     * @param line line number
     * @param token token
     * @param finish finish
     * @param ident ident
     * @param classHeritage class heritage
     * @param constructor constructor
     * @param classElements class elements
     * @param isStatement is this a statement or an expression?
     */
    public ClassNode(final int line, final long token, final int finish, final IdentNode ident, final Expression classHeritage, final PropertyNode constructor,
                     final List<PropertyNode> classElements, final boolean isStatement) {
        super(token, finish);
        this.line = line;
        this.ident = ident;
        this.classHeritage = classHeritage;
        this.constructor = constructor;
        this.classElements = classElements;
        this.isStatement = isStatement;
    }

    /**
     * Class identifier. Optional.
     *
     * @return the class identifier
     */
    public IdentNode getIdent() {
        return ident;
    }

    /**
     * The expression of the {@code extends} clause. Optional.
     *
     * @return the class heritage
     */
    public Expression getClassHeritage() {
        return classHeritage;
    }

    /**
     * Get the constructor method definition.
     *
     * @return the constructor
     */
    public PropertyNode getConstructor() {
        return constructor;
    }

    /**
     * Get method definitions except the constructor.
     *
     * @return the class elements
     */
    public List<PropertyNode> getClassElements() {
        return Collections.unmodifiableList(classElements);
    }

    /**
     * Returns if this class was a statement or an expression
     *
     * @return true if this class was a statement
     */
    public boolean isStatement() {
        return isStatement;
    }

    /**
     * Returns the line number.
     *
     * @return the line number
     */
    public int getLineNumber() {
        return line;
    }

    @Override
    public Type getType() {
        return Type.OBJECT;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterClassNode(this)) {
            return visitor.leaveClassNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("class");
        if (ident != null) {
            sb.append(' ');
            ident.toString(sb, printType);
        }
        if (classHeritage != null) {
            sb.append(" extends");
            classHeritage.toString(sb, printType);
        }
        sb.append(" {");
        if (constructor != null) {
            constructor.toString(sb, printType);
        }
        for (final PropertyNode classElement : classElements) {
            sb.append(" ");
            classElement.toString(sb, printType);
        }
        sb.append("}");
    }
}
