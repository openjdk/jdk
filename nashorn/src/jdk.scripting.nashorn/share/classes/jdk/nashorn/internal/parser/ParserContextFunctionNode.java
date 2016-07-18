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
package jdk.nashorn.internal.parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.Module;

/**
 * ParserContextNode that represents a function that is currently being parsed
 */
class ParserContextFunctionNode extends ParserContextBaseNode {

    /** Function name */
    private final String name;

    /** Function identifier node */
    private final IdentNode ident;

    /** Name space for function */
    private final Namespace namespace;

    /** Line number for function declaration */
    private final int line;

    /** Function node kind, see {@link FunctionNode.Kind} */
    private final FunctionNode.Kind kind;

    /** List of parameter identifiers for function */
    private List<IdentNode> parameters;

    /** Token for function start */
    private final long token;

    /** Last function token */
    private long lastToken;

    /** Opaque node for parser end state, see {@link Parser} */
    private Object endParserState;

    private HashSet<String> parameterBoundNames;
    private IdentNode duplicateParameterBinding;
    private boolean simpleParameterList = true;

    private Module module;

    private int debugFlags;
    private Map<IdentNode, Expression> parameterExpressions;

    /**
     * @param token The token for the function
     * @param ident External function name
     * @param name  Internal name of the function
     * @param namespace Function's namespace
     * @param line  The source line of the function
     * @param kind  Function kind
     * @param parameters The parameters of the function
     */
    public ParserContextFunctionNode(final long token, final IdentNode ident, final String name, final Namespace namespace, final int line, final FunctionNode.Kind kind, final List<IdentNode> parameters) {
        this.ident      = ident;
        this.namespace  = namespace;
        this.line       = line;
        this.kind       = kind;
        this.name       = name;
        this.parameters = parameters;
        this.token      = token;
    }

    /**
     * @return Internal name of the function
     */
    public String getName() {
        return name;
    }

    /**
     * @return The external identifier for the function
     */
    public IdentNode getIdent() {
        return ident;
    }

    /**
     *
     * @return true if function is the program function
     */
    public boolean isProgram() {
        return getFlag(FunctionNode.IS_PROGRAM) != 0;
    }

    /**
     * @return if function in strict mode
     */
    public boolean isStrict() {
        return getFlag(FunctionNode.IS_STRICT) != 0;
    }

    /**
     * @return true if the function has nested evals
     */
    public boolean hasNestedEval() {
        return getFlag(FunctionNode.HAS_NESTED_EVAL) != 0;
    }

    /**
     * Returns true if any of the blocks in this function create their own scope.
     * @return true if any of the blocks in this function create their own scope.
     */
    public boolean hasScopeBlock() {
        return getFlag(FunctionNode.HAS_SCOPE_BLOCK) != 0;
    }

    /**
     * Create a unique name in the namespace of this FunctionNode
     * @param base prefix for name
     * @return base if no collision exists, otherwise a name prefix with base
     */
    public String uniqueName(final String base) {
        return namespace.uniqueName(base);
    }

    /**
     * @return line number of the function
     */
    public int getLineNumber() {
        return line;
    }

    /**
     * @return The kind if function
     */
    public FunctionNode.Kind getKind() {
        return kind;
    }

    /**
     * Get parameters
     * @return The parameters of the function
     */
    public List<IdentNode> getParameters() {
        return parameters;
    }

    void setParameters(List<IdentNode> parameters) {
        this.parameters = parameters;
    }

    /**
     * Return ES6 function parameter expressions
     *
     * @return ES6 function parameter expressions
     */
    public Map<IdentNode, Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    void addParameterExpression(IdentNode ident, Expression node) {
        if (parameterExpressions == null) {
            parameterExpressions = new HashMap<>();
        }
        parameterExpressions.put(ident, node);
    }

    /**
     * Set last token
     * @param token New last token
     */
    public void setLastToken(final long token) {
        this.lastToken = token;

    }

    /**
     * @return lastToken Function's last token
     */
    public long getLastToken() {
        return lastToken;
    }

    /**
     * Returns the ParserState of when the parsing of this function was ended
     * @return endParserState The end parser state
     */
    public Object getEndParserState() {
        return endParserState;
    }

    /**
     * Sets the ParserState of when the parsing of this function was ended
     * @param endParserState The end parser state
     */
    public void setEndParserState(final Object endParserState) {
        this.endParserState = endParserState;
    }

    /**
     * Returns the if of this function
     * @return The function id
     */
    public int getId() {
        return isProgram() ? -1 : Token.descPosition(token);
    }

    /**
     * Returns the debug flags for this function.
     *
     * @return the debug flags
     */
    int getDebugFlags() {
        return debugFlags;
    }

    /**
     * Sets a debug flag for this function.
     *
     * @param debugFlag the debug flag
     */
    void setDebugFlag(final int debugFlag) {
        debugFlags |= debugFlag;
    }

    public boolean isMethod() {
        return getFlag(FunctionNode.ES6_IS_METHOD) != 0;
    }

    public boolean isClassConstructor() {
        return getFlag(FunctionNode.ES6_IS_CLASS_CONSTRUCTOR) != 0;
    }

    public boolean isSubclassConstructor() {
        return getFlag(FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR) != 0;
    }

    boolean addParameterBinding(final IdentNode bindingIdentifier) {
        if (Parser.isArguments(bindingIdentifier)) {
            setFlag(FunctionNode.DEFINES_ARGUMENTS);
        }

        if (parameterBoundNames == null) {
            parameterBoundNames = new HashSet<>();
        }
        if (parameterBoundNames.add(bindingIdentifier.getName())) {
            return true;
        } else {
            duplicateParameterBinding = bindingIdentifier;
            return false;
        }
    }

    public IdentNode getDuplicateParameterBinding() {
        return duplicateParameterBinding;
    }

    public boolean isSimpleParameterList() {
        return simpleParameterList;
    }

    public void setSimpleParameterList(final boolean simpleParameterList) {
        this.simpleParameterList = simpleParameterList;
    }

    public Module getModule() {
        return module;
    }

    public void setModule(final Module module) {
        this.module = module;
    }
}
