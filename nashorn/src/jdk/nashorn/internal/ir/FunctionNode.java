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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.UserAccessorProperty;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;

/**
 * IR representation for function (or script.)
 */
@Immutable
public final class FunctionNode extends LexicalContextExpression implements Flags<FunctionNode> {

    /** Type used for all FunctionNodes */
    public static final Type FUNCTION_TYPE = Type.typeFor(ScriptFunction.class);

    /** Function kinds */
    public enum Kind {
        /** a normal function - nothing special */
        NORMAL,
        /** a script function */
        SCRIPT,
        /** a getter, @see {@link UserAccessorProperty} */
        GETTER,
        /** a setter, @see {@link UserAccessorProperty} */
        SETTER
    }

    /** Compilation states available */
    public enum CompilationState {
        /** compiler is ready */
        INITIALIZED,
        /** method has been parsed */
        PARSED,
        /** method has been parsed */
        PARSE_ERROR,
        /** constant folding pass */
        CONSTANT_FOLDED,
        /** method has been lowered */
        LOWERED,
        /** method hass been attributed */
        ATTR,
        /** method has been split */
        SPLIT,
        /** method has had its types finalized */
        FINALIZED,
        /** method has been emitted to bytecode */
        EMITTED
    }
    /** Source of entity. */
    private final Source source;

    /** External function identifier. */
    @Ignore
    private final IdentNode ident;

    /** Parsed version of functionNode */
    @Ignore
    private final FunctionNode snapshot;

    /** The body of the function node */
    private final Block body;

    /** Internal function name. */
    private final String name;

    /** Compilation unit. */
    private final CompileUnit compileUnit;

    /** Function kind. */
    private final Kind kind;

    /** List of parameters. */
    private final List<IdentNode> parameters;

    /** First token of function. **/
    private final long firstToken;

    /** Last token of function. **/
    private final long lastToken;

    /** Declared symbols in this function node */
    @Ignore
    private final Set<Symbol> declaredSymbols;

    /** Method's namespace. */
    private final Namespace namespace;

    /** Current compilation state */
    @Ignore
    private final EnumSet<CompilationState> compilationState;

    @Ignore
    private final Compiler.Hints hints;

    /** Properties of this object assigned in this function */
    @Ignore
    private HashSet<String> thisProperties;

    /** Function flags. */
    private final int flags;

    private final int lineNumber;

    /** Is anonymous function flag. */
    public static final int IS_ANONYMOUS                = 1 << 0;

    /** Is the function created in a function declaration (as opposed to a function expression) */
    public static final int IS_DECLARED                 = 1 << 1;

    /** is this a strict mode function? */
    public static final int IS_STRICT                   = 1 << 2;

    /** Does the function use the "arguments" identifier ? */
    public static final int USES_ARGUMENTS              = 1 << 3;

    /** Has this node been split because it was too large? */
    public static final int IS_SPLIT                    = 1 << 4;

    /** Does the function call eval? If it does, then all variables in this function might be get/set by it and it can
     * introduce new variables into this function's scope too.*/
    public static final int HAS_EVAL                    = 1 << 5;

    /** Does a nested function contain eval? If it does, then all variables in this function might be get/set by it. */
    public static final int HAS_NESTED_EVAL = 1 << 6;

    /** Does this function have any blocks that create a scope? This is used to determine if the function needs to
     * have a local variable slot for the scope symbol. */
    public static final int HAS_SCOPE_BLOCK = 1 << 7;

    /**
     * Flag this function as one that defines the identifier "arguments" as a function parameter or nested function
     * name. This precludes it from needing to have an Arguments object defined as "arguments" local variable. Note that
     * defining a local variable named "arguments" still requires construction of the Arguments object (see
     * ECMAScript 5.1 Chapter 10.5).
     * @see #needsArguments()
     */
    public static final int DEFINES_ARGUMENTS           = 1 << 8;

    /** Does this function or any of its descendants use variables from an ancestor function's scope (incl. globals)? */
    public static final int USES_ANCESTOR_SCOPE         = 1 << 9;

    /** Is this function lazily compiled? */
    public static final int IS_LAZY                     = 1 << 10;

    /** Does this function have lazy, yet uncompiled children */
    public static final int HAS_LAZY_CHILDREN           = 1 << 11;

    /** Does this function have lazy, yet uncompiled children */
    public static final int IS_PROGRAM                  = 1 << 12;

    /** Does this function have nested declarations? */
    public static final int HAS_FUNCTION_DECLARATIONS   = 1 << 13;

    /** Can this function be specialized? */
    public static final int CAN_SPECIALIZE              = 1 << 14;

    /** Does this function or any nested functions contain an eval? */
    private static final int HAS_DEEP_EVAL = HAS_EVAL | HAS_NESTED_EVAL;

    /** Does this function need to store all its variables in scope? */
    private static final int HAS_ALL_VARS_IN_SCOPE = HAS_DEEP_EVAL | IS_SPLIT | HAS_LAZY_CHILDREN;

    /** Does this function potentially need "arguments"? Note that this is not a full test, as further negative check of REDEFINES_ARGS is needed. */
    private static final int MAYBE_NEEDS_ARGUMENTS = USES_ARGUMENTS | HAS_EVAL;

    /** Does this function need the parent scope? It needs it if either it or its descendants use variables from it, or have a deep eval.
     *  We also pessimistically need a parent scope if we have lazy children that have not yet been compiled */
    private static final int NEEDS_PARENT_SCOPE = USES_ANCESTOR_SCOPE | HAS_DEEP_EVAL | HAS_LAZY_CHILDREN;

    /** What is the return type of this function? */
    private Type returnType = Type.UNKNOWN;

    /**
     * Constructor
     *
     * @param source     the source
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param firstToken first token of the funtion node (including the function declaration)
     * @param namespace  the namespace
     * @param ident      the identifier
     * @param name       the name of the function
     * @param parameters parameter list
     * @param kind       kind of function as in {@link FunctionNode.Kind}
     * @param flags      initial flags
     */
    public FunctionNode(
        final Source source,
        final int lineNumber,
        final long token,
        final int finish,
        final long firstToken,
        final Namespace namespace,
        final IdentNode ident,
        final String name,
        final List<IdentNode> parameters,
        final FunctionNode.Kind kind,
        final int flags) {
        super(token, finish);

        this.source           = source;
        this.lineNumber       = lineNumber;
        this.ident            = ident;
        this.name             = name;
        this.kind             = kind;
        this.parameters       = parameters;
        this.firstToken       = firstToken;
        this.lastToken        = token;
        this.namespace        = namespace;
        this.compilationState = EnumSet.of(CompilationState.INITIALIZED);
        this.declaredSymbols  = new HashSet<>();
        this.flags            = flags;
        this.compileUnit      = null;
        this.body             = null;
        this.snapshot         = null;
        this.hints            = null;
    }

    private FunctionNode(
        final FunctionNode functionNode,
        final long lastToken,
        final int flags,
        final String name,
        final Type returnType,
        final CompileUnit compileUnit,
        final EnumSet<CompilationState> compilationState,
        final Block body,
        final List<IdentNode> parameters,
        final FunctionNode snapshot,
        final Compiler.Hints hints) {
        super(functionNode);
        this.lineNumber       = functionNode.lineNumber;
        this.flags            = flags;
        this.name             = name;
        this.returnType       = returnType;
        this.compileUnit      = compileUnit;
        this.lastToken        = lastToken;
        this.compilationState = compilationState;
        this.body             = body;
        this.parameters       = parameters;
        this.snapshot         = snapshot;
        this.hints            = hints;

        // the fields below never change - they are final and assigned in constructor
        this.source          = functionNode.source;
        this.ident           = functionNode.ident;
        this.namespace       = functionNode.namespace;
        this.declaredSymbols = functionNode.declaredSymbols;
        this.kind            = functionNode.kind;
        this.firstToken      = functionNode.firstToken;
        this.thisProperties  = functionNode.thisProperties;
    }

    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterFunctionNode(this)) {
            return visitor.leaveFunctionNode(setBody(lc, (Block)body.accept(visitor)));
        }
        return this;
    }

    /**
     * Get the source for this function
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns the line number.
     * @return the line number.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the version of this function node's code as it looked upon construction
     * i.e typically parsed and nothing else
     * @return initial version of function node
     */
    public FunctionNode getSnapshot() {
        return snapshot;
    }

    /**
     * Throw away the snapshot, if any, to save memory. Used when heuristic
     * determines that a method is not worth specializing
     *
     * @param lc lexical context
     * @return new function node if a snapshot was present, now with snapsnot null
     */
    public FunctionNode clearSnapshot(final LexicalContext lc) {
        if (this.snapshot == null) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, null, hints));
    }

    /**
     * Take a snapshot of this function node at a given point in time
     * and store it in the function node
     * @param lc lexical context
     * @return function node
     */
    public FunctionNode snapshot(final LexicalContext lc) {
        if (this.snapshot == this) {
            return this;
        }
        if (isProgram() || parameters.isEmpty()) {
            return this; //never specialize anything that won't be recompiled
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, this, hints));
    }

    /**
     * Can this function node be regenerated with more specific type args?
     * @return true if specialization is possible
     */
    public boolean canSpecialize() {
        return snapshot != null && getFlag(CAN_SPECIALIZE);
    }

    /**
     * Get the compilation state of this function
     * @return the compilation state
     */
    public EnumSet<CompilationState> getState() {
        return compilationState;
    }

    /**
     * Check whether this FunctionNode has reached a give CompilationState.
     *
     * @param state the state to check for
     * @return true of the node is in the given state
     */
    public boolean hasState(final EnumSet<CompilationState> state) {
        return compilationState.equals(state);
    }

    /**
     * Check whether the state of this FunctionNode contains a given compilation
     * state.
     *
     * A node can be in many states at once, e.g. both lowered and initialized.
     * To check for an exact state, use {FunctionNode{@link #hasState(EnumSet)}
     *
     * @param state state to check for
     * @return true if state is present in the total compilation state of this FunctionNode
     */
    public boolean hasState(final CompilationState state) {
        return compilationState.contains(state);
    }

    /**
     * Add a state to the total CompilationState of this node, e.g. if
     * FunctionNode has been lowered, the compiler will add
     * {@code CompilationState#LOWERED} to the state vector
     *
     * @param lc lexical context
     * @param state {@link CompilationState} to add
     * @return function node or a new one if state was changed
     */
    public FunctionNode setState(final LexicalContext lc, final CompilationState state) {
        if (this.compilationState.contains(state)) {
            return this;
        }
        final EnumSet<CompilationState> newState = EnumSet.copyOf(this.compilationState);
        newState.add(state);
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, newState, body, parameters, snapshot, hints));
    }

    /**
     * Get any compiler hints that may associated with the function
     * @return compiler hints
     */
    public Compiler.Hints getHints() {
        return this.hints == null ? Compiler.Hints.EMPTY : hints;
    }

    /**
     * Set compiler hints for this function
     * @param lc    lexical context
     * @param hints compiler hints
     * @return new function if hints changed
     */
    public FunctionNode setHints(final LexicalContext lc, final Compiler.Hints hints) {
        if (this.hints == hints) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Create a unique name in the namespace of this FunctionNode
     * @param base prefix for name
     * @return base if no collision exists, otherwise a name prefix with base
     */
    public String uniqueName(final String base) {
        return namespace.uniqueName(base);
    }


    @Override
    public void toString(final StringBuilder sb) {
        sb.append('[');
        sb.append(returnType);
        sb.append(']');
        sb.append(' ');

        sb.append("function");

        if (ident != null) {
            sb.append(' ');
            ident.toString(sb);
        }

        sb.append('(');
        boolean first = true;

        for (final IdentNode parameter : parameters) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            parameter.toString(sb);
        }

        sb.append(')');
    }

    @Override
    public boolean getFlag(final int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public FunctionNode setFlags(final LexicalContext lc, int flags) {
        if (this.flags == flags) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    @Override
    public FunctionNode clearFlag(final LexicalContext lc, final int flag) {
        return setFlags(lc, flags & ~flag);
    }

    @Override
    public FunctionNode setFlag(final LexicalContext lc, final int flag) {
        return setFlags(lc, flags | flag);
    }

    /**
     * Returns true if the function is the top-level program.
     * @return True if this function node represents the top-level program.
     */
    public boolean isProgram() {
        return getFlag(IS_PROGRAM);
    }

    /**
     * Should this function node be lazily code generated, i.e. first at link time
     * @return true if lazy
     */
    public boolean isLazy() {
        return getFlag(IS_LAZY);
    }

    /**
     * Check if the {@code eval} keyword is used in this function
     *
     * @return true if {@code eval} is used
     */
    public boolean hasEval() {
        return getFlag(HAS_EVAL);
    }

    /**
     * Get the first token for this function
     * @return the first token
     */
    public long getFirstToken() {
        return firstToken;
    }

    /**
     * Check whether this function has nested function declarations
     * @return true if nested function declarations exist
     */
    public boolean hasDeclaredFunctions() {
        return getFlag(HAS_FUNCTION_DECLARATIONS);
    }

    /**
     * Check if this function's generated Java method needs a {@code callee} parameter. Functions that need access to
     * their parent scope, functions that reference themselves, and non-strict functions that need an Arguments object
     * (since it exposes {@code arguments.callee} property) will need to have a callee parameter.
     *
     * @return true if the function's generated Java method needs a {@code callee} parameter.
     */
    public boolean needsCallee() {
        return needsParentScope() || needsSelfSymbol() || (needsArguments() && !isStrict());
    }

    /**
     * Get the identifier for this function, this is its symbol.
     * @return the identifier as an IdentityNode
     */
    public IdentNode getIdent() {
        return ident;
    }

    /**
     * Return a set of symbols declared in this function node. This
     * is only relevant after Attr, otherwise it will be an empty
     * set as no symbols have been introduced
     * @return set of declared symbols in function
     */
    public Set<Symbol> getDeclaredSymbols() {
        return Collections.unmodifiableSet(declaredSymbols);
    }

    /**
     * Add a declared symbol to this function node
     * @param symbol symbol that is declared
     */
    public void addDeclaredSymbol(final Symbol symbol) {
        declaredSymbols.add(symbol);
    }

    /**
     * Get the function body
     * @return the function body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the function body
     * @param lc lexical context
     * @param body new body
     * @return new function node if body changed, same if not
     */
    public FunctionNode setBody(final LexicalContext lc, final Block body) {
        if(this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags | (body.needsScope() ? FunctionNode.HAS_SCOPE_BLOCK : 0), name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Does this function's method needs to be variable arity (gather all script-declared parameters in a final
     * {@code Object[]} parameter. Functions that need to have the "arguments" object as well as functions that simply
     * declare too many arguments for JVM to handle with fixed arity will need to be variable arity.
     * @return true if the Java method in the generated code that implements this function needs to be variable arity.
     * @see #needsArguments()
     * @see LinkerCallSite#ARGLIMIT
     */
    public boolean isVarArg() {
        return needsArguments() || parameters.size() > LinkerCallSite.ARGLIMIT;
    }

    /**
     * Returns true if this function needs to have an Arguments object defined as a local variable named "arguments".
     * Functions that use "arguments" as identifier and don't define it as a name of a parameter or a nested function
     * (see ECMAScript 5.1 Chapter 10.5), as well as any function that uses eval or with, or has a nested function that
     * does the same, will have an "arguments" object. Also, if this function is a script, it will not have an
     * "arguments" object, because it does not have local variables; rather the Global object will have an explicit
     * "arguments" property that provides command-line arguments for the script.
     * @return true if this function needs an arguments object.
     */
    public boolean needsArguments() {
        // uses "arguments" or calls eval, but it does not redefine "arguments", and finally, it's not a script, since
        // for top-level script, "arguments" is picked up from Context by Global.init() instead.
        return getFlag(MAYBE_NEEDS_ARGUMENTS) && !getFlag(DEFINES_ARGUMENTS) && !isProgram();
    }

    /**
     * Returns true if this function needs access to its parent scope. Functions referencing variables outside their
     * scope (including global variables), as well as functions that call eval or have a with block, or have nested
     * functions that call eval or have a with block, will need a parent scope. Top-level script functions also need a
     * parent scope since they might be used from within eval, and eval will need an externally passed scope.
     * @return true if the function needs parent scope.
     */
    public boolean needsParentScope() {
        return getFlag(NEEDS_PARENT_SCOPE) || isProgram();
    }

    /**
     * Register a property assigned to the this object in this function.
     * @param key the property name
     */
    public void addThisProperty(final String key) {
        if (thisProperties == null) {
            thisProperties = new HashSet<>();
        }
        thisProperties.add(key);
    }

    /**
     * Get the number of properties assigned to the this object in this function.
     * @return number of properties
     */
    public int countThisProperties() {
        return thisProperties == null ? 0 : thisProperties.size();
    }

    /**
     * Returns true if any of the blocks in this function create their own scope.
     * @return true if any of the blocks in this function create their own scope.
     */
    public boolean hasScopeBlock() {
        return getFlag(HAS_SCOPE_BLOCK);
    }

    /**
     * Return the kind of this function
     * @see FunctionNode.Kind
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Return the last token for this function's code
     * @return last token
     */
    public long getLastToken() {
        return lastToken;
    }

    /**
     * Set the last token for this function's code
     * @param lc lexical context
     * @param lastToken the last token
     * @return function node or a new one if state was changed
     */
    public FunctionNode setLastToken(final LexicalContext lc, final long lastToken) {
        if (this.lastToken == lastToken) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Get the name of this function
     * @return the name
     */
    public String getName() {
        return name;
    }


    /**
     * Set the internal name for this function
     * @param lc    lexical context
     * @param name new name
     * @return new function node if changed, otherwise the same
     */
    public FunctionNode setName(final LexicalContext lc, final String name) {
        if (this.name.equals(name)) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Check if this function should have all its variables in its own scope. Scripts, split sub-functions, and
     * functions having with and/or eval blocks are such.
     *
     * @return true if all variables should be in scope
     */
    public boolean allVarsInScope() {
        return isProgram() || getFlag(HAS_ALL_VARS_IN_SCOPE);
    }

    /**
     * Checks if this function is a sub-function generated by splitting a larger one
     *
     * @return true if this function is split from a larger one
     */
    public boolean isSplit() {
        return getFlag(IS_SPLIT);
    }

    /**
     * Checks if this function has yet-to-be-generated child functions
     *
     * @return true if there are lazy child functions
     */
    public boolean hasLazyChildren() {
        return getFlag(HAS_LAZY_CHILDREN);
    }

    /**
     * Get the parameters to this function
     * @return a list of IdentNodes which represent the function parameters, in order
     */
    public List<IdentNode> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * Reset the compile unit used to compile this function
     * @see Compiler
     * @param  lc lexical context
     * @param  parameters the compile unit
     * @return function node or a new one if state was changed
     */
    public FunctionNode setParameters(final LexicalContext lc, final List<IdentNode> parameters) {
        if (this.parameters == parameters) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Check if this function is created as a function declaration (as opposed to function expression)
     * @return true if function is declared.
     */
    public boolean isDeclared() {
        return getFlag(IS_DECLARED);
    }

    /**
     * Check if this function is anonymous
     * @return true if function is anonymous
     */
    public boolean isAnonymous() {
        return getFlag(IS_ANONYMOUS);
    }

    /**
     * Does this function need a self symbol - this is needed only for self
     * referring functions
     * @return true if function needs a symbol for self
     */
    public boolean needsSelfSymbol() {
        return body.getFlag(Block.NEEDS_SELF_SYMBOL);
    }

    @Override
    public Type getType() {
        return FUNCTION_TYPE;
    }

    /**
     * Get the return type for this function. Return types can be specialized
     * if the compiler knows them, but parameters cannot, as they need to go through
     * appropriate object conversion
     *
     * @return the return type
     */
    public Type getReturnType() {
        return returnType;
    }

    /**
     * Set the function return type
     * @param lc lexical context
     * @param returnType new return type
     * @return function node or a new one if state was changed
     */
    public FunctionNode setReturnType(final LexicalContext lc, final Type returnType) {
        //we never bother with object types narrower than objects, that will lead to byte code verification errors
        //as for instance even if we know we are returning a string from a method, the code generator will always
        //treat it as an object, at least for now
        if (this.returnType == returnType) {
            return this;
        }
        return Node.replaceInLexicalContext(
            lc,
            this,
            new FunctionNode(
                this,
                lastToken,
                flags,
                name,
                Type.widest(this.returnType, returnType.isObject() ?
                    Type.OBJECT :
                    returnType),
                compileUnit,
                compilationState,
                body,
                parameters,
                snapshot,
                hints));
    }

    /**
     * Check if the function is generated in strict mode
     * @return true if strict mode enabled for function
     */
    public boolean isStrict() {
        return getFlag(IS_STRICT);
    }

    /**
     * Get the compile unit used to compile this function
     * @see Compiler
     * @return the compile unit
     */
    public CompileUnit getCompileUnit() {
        return compileUnit;
    }

    /**
     * Reset the compile unit used to compile this function
     * @see Compiler
     * @param lc lexical context
     * @param compileUnit the compile unit
     * @return function node or a new one if state was changed
     */
    public FunctionNode setCompileUnit(final LexicalContext lc, final CompileUnit compileUnit) {
        if (this.compileUnit == compileUnit) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new FunctionNode(this, lastToken, flags, name, returnType, compileUnit, compilationState, body, parameters, snapshot, hints));
    }

    /**
     * Create a temporary variable to the current frame.
     *
     * @param block that needs the temporary
     * @param type  Strong type of symbol.
     * @param node  Primary node to use symbol.
     *
     * @return Symbol used.
     */

    /**
     * Get the symbol for a compiler constant, or null if not available (yet)
     * @param cc compiler constant
     * @return symbol for compiler constant, or null if not defined yet (for example in Lower)
     */
    public Symbol compilerConstant(final CompilerConstants cc) {
        return body.getExistingSymbol(cc.symbolName());
    }
}
