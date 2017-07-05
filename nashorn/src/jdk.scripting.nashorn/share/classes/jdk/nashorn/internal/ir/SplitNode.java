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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Node indicating code is split across classes.
 */
@Immutable
public class SplitNode extends LexicalContextStatement implements CompileUnitHolder {
    private static final long serialVersionUID = 1L;

    /** Split node method name. */
    private final String name;

    /** Compilation unit. */
    private final CompileUnit compileUnit;

    /** Body of split code. */
    private final Block body;

    /**
     * Constructor
     *
     * @param name        name of split node
     * @param body        body of split code
     * @param compileUnit compile unit to use for the body
     */
    public SplitNode(final String name, final Block body, final CompileUnit compileUnit) {
        super(body.getFirstStatementLineNumber(), body.getToken(), body.getFinish());
        this.name        = name;
        this.body        = body;
        this.compileUnit = compileUnit;
    }

    private SplitNode(final SplitNode splitNode, final Block body, final CompileUnit compileUnit) {
        super(splitNode);
        this.name        = splitNode.name;
        this.body        = body;
        this.compileUnit = compileUnit;
    }

    /**
     * Get the body for this split node - i.e. the actual code it encloses
     * @return body for split node
     */
    public Block getBody() {
        return body;
    }

    private SplitNode setBody(final LexicalContext lc, final Block body) {
        if (this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SplitNode(this, body, compileUnit));
    }

    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterSplitNode(this)) {
            return visitor.leaveSplitNode(setBody(lc, (Block)body.accept(visitor)));
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("<split>(");
        sb.append(compileUnit.getClass().getSimpleName());
        sb.append(") ");
        body.toString(sb, printType);
    }

    /**
     * Get the name for this split node
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the compile unit for this split node
     * @return compile unit
     */
    @Override
    public CompileUnit getCompileUnit() {
        return compileUnit;
    }

    /**
     * Set the compile unit for this split node
     * @param lc lexical context
     * @param compileUnit compile unit
     * @return new node if changed, otherwise same node
     */
    public SplitNode setCompileUnit(final LexicalContext lc, final CompileUnit compileUnit) {
        if (this.compileUnit == compileUnit) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new SplitNode(this, body, compileUnit));
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        // We are only serializing the AST after we run SplitIntoFunctions; no SplitNodes can remain for the
        // serialization.
        throw new NotSerializableException(getClass().getName());
    }
}
