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
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation for a function call.
 */
@Immutable
public final class CallNode extends LexicalContextNode implements TypeOverride<CallNode> {

    private final Type type;

    /** Function identifier or function body. */
    private final Node function;

    /** Call arguments. */
    private final List<Node> args;

    /** Is this a "new" operation */
    public static final int IS_NEW        = 0x1;

    private final int flags;

    /**
     * Arguments to be passed to builtin {@code eval} function
     */
    public static class EvalArgs {
        /** evaluated code */
        private final Node code;

        /** 'this' passed to evaluated code */
        private final IdentNode evalThis;

        /** location string for the eval call */
        private final String location;

        /** is this call from a strict context? */
        private final boolean strictMode;

        /**
         * Constructor
         *
         * @param code       code to evaluate
         * @param evalThis   this node
         * @param location   location for the eval call
         * @param strictMode is this a call from a strict context?
         */
        public EvalArgs(final Node code, final IdentNode evalThis, final String location, final boolean strictMode) {
            this.code = code;
            this.evalThis = evalThis;
            this.location = location;
            this.strictMode = strictMode;
        }

        /**
         * Return the code that is to be eval:ed by this eval function
         * @return code as an AST node
         */
        public Node getCode() {
            return code;
        }

        private EvalArgs setCode(final Node code) {
            if (this.code == code) {
                return this;
            }
            return new EvalArgs(code, evalThis, location, strictMode);
        }

        /**
         * Get the {@code this} symbol used to invoke this eval call
         * @return the {@code this} symbol
         */
        public IdentNode getThis() {
            return this.evalThis;
        }

        private EvalArgs setThis(final IdentNode evalThis) {
            if (this.evalThis == evalThis) {
                return this;
            }
            return new EvalArgs(code, evalThis, location, strictMode);
        }

        /**
         * Get the human readable location for this eval call
         * @return the location
         */
        public String getLocation() {
            return this.location;
        }

        /**
         * Check whether this eval call is executed in strict mode
         * @return true if executed in strict mode, false otherwise
         */
        public boolean getStrictMode() {
            return this.strictMode;
        }
    }

    /** arguments for 'eval' call. Non-null only if this call node is 'eval' */
    @Ignore
    private final EvalArgs evalArgs;

    /**
     * Constructors
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param function   the function to call
     * @param args       args to the call
     */
    public CallNode(final int lineNumber, final long token, final int finish, final Node function, final List<Node> args) {
        super(lineNumber, token, finish);

        this.function = function;
        this.args     = args;
        this.flags    = 0;
        this.type     = null;
        this.evalArgs = null;
    }

    private CallNode(final CallNode callNode, final Node function, final List<Node> args, final int flags, final Type type, final EvalArgs evalArgs) {
        super(callNode);
        this.function = function;
        this.args = args;
        this.flags = flags;
        this.type = type;
        this.evalArgs = evalArgs;
    }

    @Override
    public Type getType() {
        if (hasCallSiteType()) {
            return type;
        }
        return function instanceof FunctionNode ? ((FunctionNode)function).getReturnType() : Type.OBJECT;
    }

    @Override
    public CallNode setType(final TemporarySymbols ts, final LexicalContext lc, final Type type) {
        if (this.type == type) {
            return this;
        }
        return new CallNode(this, function, args, flags, type, evalArgs);
    }

    private boolean hasCallSiteType() {
        return this.type != null;
    }

    @Override
    public boolean canHaveCallSiteType() {
        return true;
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     *
     * @return node or replacement
     */
    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterCallNode(this)) {
            final CallNode newCallNode = (CallNode)visitor.leaveCallNode(
                    setFunction(function.accept(visitor)).
                    setArgs(Node.accept(visitor, Node.class, args)).
                    setFlags(flags).
                    setType(null, lc, type).
                    setEvalArgs(evalArgs == null ?
                            null :
                            evalArgs.setCode(evalArgs.getCode().accept(visitor)).
                                setThis((IdentNode)evalArgs.getThis().accept(visitor))));
            // Theoretically, we'd need to instead pass lc to every setter and do a replacement on each. In practice,
            // setType from TypeOverride can't accept a lc, and we don't necessarily want to go there now.
            if(this != newCallNode) {
                return Node.replaceInLexicalContext(lc, this, newCallNode);
            }
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? 'O' : getType().getDescriptor());
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
    private CallNode setArgs(final List<Node> args) {
        if (this.args == args) {
            return this;
        }
        return new CallNode(this, function, args, flags, type, evalArgs);
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
     * @return same node or new one on state change
     */
    public CallNode setEvalArgs(final EvalArgs evalArgs) {
        if (this.evalArgs == evalArgs) {
            return this;
        }
        return new CallNode(this, function, args, flags, type, evalArgs);
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
     * @param function the function
     * @return same node or new one on state change
     */
    public CallNode setFunction(final Node function) {
        if (this.function == function) {
            return this;
        }
        return new CallNode(this, function, args, flags, type, evalArgs);
    }

    /**
     * Check if this call is a new operation
     * @return true if this a new operation
     */
    public boolean isNew() {
        return (flags & IS_NEW) == IS_NEW;
    }

    /**
     * Flag this call as a new operation
     * @return same node or new one on state change
     */
    public CallNode setIsNew() {
        return setFlags(IS_NEW);
    }

    private CallNode setFlags(final int flags) {
        if (this.flags == flags) {
            return this;
        }
        return new CallNode(this, function, args, flags, type, evalArgs);
    }
}
