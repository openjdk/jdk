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
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Reference;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;


/**
 * Node indicating code is split across classes.
 */
public class SplitNode extends Node {
    /** Split node method name. */
    private final String name;

    /** Compilation unit. */
    private CompileUnit compileUnit;

    /** Method emitter for current method. */
    private MethodEmitter method;

    /** Method emitter for caller method. */
    private MethodEmitter caller;

    /** Containing function. */
    @Reference
    private final FunctionNode functionNode;

    /** A list of target labels in parent methods this split node may encounter. */
    @Ignore
    private final List<Label> externalTargets;

    /** True if this split node or any of its children contain a return statement. */
    private boolean hasReturn;

    /** Body of split code. */
    @Ignore
    private Node body;

    /**
     * Constructor
     *
     * @param name          name of split node
     * @param functionNode  function node to split in
     * @param body          body of split code
     */
    public SplitNode(final String name, final FunctionNode functionNode, final Node body) {
        super(body.getSource(), body.getToken(), body.getFinish());

        this.name         = name;
        this.functionNode = functionNode;
        this.body         = body;
        this.externalTargets = new ArrayList<>();
    }

    private SplitNode(final SplitNode splitNode, final CopyState cs) {
        super(splitNode);

        this.name         = splitNode.name;
        this.functionNode = (FunctionNode)cs.existingOrSame(splitNode.functionNode);
        this.body         = cs.existingOrCopy(splitNode.body);
        this.externalTargets = new ArrayList<>();
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new SplitNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        final CompileUnit   saveCompileUnit = visitor.getCurrentCompileUnit();
        final MethodEmitter saveMethod      = visitor.getCurrentMethodEmitter();

        setCaller(saveMethod);

        visitor.setCurrentCompileUnit(getCompileUnit());
        visitor.setCurrentMethodEmitter(getMethodEmitter());

        try {
            if (visitor.enterSplitNode(this) != null) {
                body = body.accept(visitor);

                return visitor.leaveSplitNode(this);
            }
        } finally {
            visitor.setCurrentCompileUnit(saveCompileUnit);
            visitor.setCurrentMethodEmitter(saveMethod);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("<split>(");
        sb.append(compileUnit.getClass().getSimpleName());
        sb.append(") ");
        body.toString(sb);
    }

    /**
     * Get the method emitter of the caller for this split node
     * @return caller method emitter
     */
    public MethodEmitter getCaller() {
        return caller;
    }

    /**
     * Set the caller method emitter for this split node
     * @param caller method emitter
     */
    public void setCaller(final MethodEmitter caller) {
        this.caller = caller;
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
    public CompileUnit getCompileUnit() {
        return compileUnit;
    }

    /**
     * Set the compile unit for this split node
     * @param compileUnit compile unit
     */
    public void setCompileUnit(final CompileUnit compileUnit) {
        this.compileUnit = compileUnit;
    }

    /**
     * Get the method emitter for this split node
     * @return method emitter
     */
    public MethodEmitter getMethodEmitter() {
        return method;
    }

    /**
     * Set the method emitter for this split node
     * @param method method emitter
     */
    public void setMethodEmitter(final MethodEmitter method) {
        this.method = method;
    }

    /**
     * Get the function node this SplitNode splits
     * @return function node reference
     */
    public FunctionNode getFunctionNode() {
        return functionNode;
    }

    /**
     * Get the external targets for this SplitNode
     * @return list of external targets
     */
    public List<Label> getExternalTargets() {
        return Collections.unmodifiableList(externalTargets);
    }

    /**
     * Add an external target for this SplitNode
     * @param targetLabel target label
     */
    public void addExternalTarget(final Label targetLabel) {
        externalTargets.add(targetLabel);
    }

    /**
     * Check whether this SplitNode returns a value
     * @return true if return
     */
    public boolean hasReturn() {
        return hasReturn;
    }

    /**
     * Set whether this SplitNode returns a value or not
     * @param hasReturn true if return exists, false otherwise
     */
    public void setHasReturn(final boolean hasReturn) {
        this.hasReturn = hasReturn;
    }
}
