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
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a function call.
 *
 */
public class CallNode extends Node implements TypeOverride {

    private Type type;

    /** Function identifier or function body. */
    private Node function;

    /** Call arguments. */
    private List<Node> args;

    /** flag - is new expression */
    private boolean isNew;

    /** flag - is in with block */
    private boolean inWithBlock;

    /**
     * Arguments to be passed to builtin {@code eval} function
     */
    public static class EvalArgs {
        /** evaluated code */
        public Node    code;
        /** 'this' passed to evaluated code */
        public Node    evalThis;
        /** location string for the eval call */
        public String  location;
        /** is this call from a strict context? */
        public boolean strictMode;
    }

    /** arguments for 'eval' call. Non-null only if this call node is 'eval' */
    private EvalArgs evalArgs;

    /**
     * Constructors
     *
     * @param source   the source
     * @param token    token
     * @param finish   finish
     * @param function the function to call
     * @param args     args to the call
     */
    public CallNode(final Source source, final long token, final int finish, final Node function, final List<Node> args) {
        super(source, token, finish);

        setStart(function.getStart());

        this.function     = function;
        this.args         = args;
    }

    private CallNode(final CallNode callNode, final CopyState cs) {
        super(callNode);

        final List<Node> newArgs = new ArrayList<>();

        for (final Node arg : callNode.args) {
            newArgs.add(cs.existingOrCopy(arg));
        }

        function     = cs.existingOrCopy(callNode.function);     //TODO existing or same?
        args         = newArgs;
        isNew        = callNode.isNew;
        inWithBlock  = callNode.inWithBlock;
    }


    @Override
    public Type getType() {
        if (hasCallSiteType()) {
            return type;
        }
        assert !function.getType().isUnknown();
        return function.getType();
    }

    @Override
    public void setType(final Type type) {
        this.type = type;
    }

    private boolean hasCallSiteType() {
        return this.type != null;
    }

    @Override
    public boolean canHaveCallSiteType() {
        return true;
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new CallNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     *
     * @return node or replacement
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            function = function.accept(visitor);

            for (int i = 0, count = args.size(); i < count; i++) {
                args.set(i, args.get(i).accept(visitor));
            }

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : getType().getDescriptor());
            sb.append('}');
        }

        function.toString(sb);

        sb.append('(');

        boolean first = true;

        for (final Node arg : args) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            arg.toString(sb);
        }

        sb.append(')');
    }

    /**
     * Get the arguments for the call
     * @return a list of arguments
     */
    public List<Node> getArgs() {
        return Collections.unmodifiableList(args);
    }

    /**
     * Reset the arguments for the call
     * @param args new arguments list
     */
    public void setArgs(final List<Node> args) {
        this.args = args;
    }

    /**
     * If this call is an {@code eval} call, get its EvalArgs structure
     * @return EvalArgs for call
     */
    public EvalArgs getEvalArgs() {
        return evalArgs;
    }

    /**
     * Set the EvalArgs structure for this call, if it has been determined it is an
     * {@code eval}
     *
     * @param evalArgs eval args
     */
    public void setEvalArgs(final EvalArgs evalArgs) {
        this.evalArgs = evalArgs;
    }

    /**
     * Check if this call is a call to {@code eval}
     * @return true if this is a call to {@code eval}
     */
    public boolean isEval() {
        return evalArgs != null;
    }

    /**
     * Return the function expression that this call invokes
     * @return the function
     */
    public Node getFunction() {
        return function;
    }

    /**
     * Reset the function expression that this call invokes
     * @param node the function
     */
    public void setFunction(final Node node) {
        function = node;
    }

    /**
     * Check if this call is a new operation
     * @return true if this a new operation
     */
    public boolean isNew() {
        return isNew;
    }

    /**
     * Flag this call as a new operation
     */
    public void setIsNew() {
        this.isNew = true;
    }

    /**
     * Check if this call is inside a {@code with} block
     * @return true if the call is inside a {@code with} block
     */
    public boolean inWithBlock() {
        return inWithBlock;
    }

    /**
     * Flag this call to be inside a {@code with} block
     */
    public void setInWithBlock() {
        this.inWithBlock = true;
    }
}
