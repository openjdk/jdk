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

import static jdk.nashorn.internal.codegen.CompilerConstants.LITERAL_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.TEMP_PREFIX;
import static jdk.nashorn.internal.ir.Symbol.IS_CONSTANT;
import static jdk.nashorn.internal.ir.Symbol.IS_TEMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.Frame;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.UserAccessorProperty;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;

/**
 * IR representation for function (or script.)
 *
 */
public class FunctionNode extends Block {

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

    /** External function identifier. */
    @Ignore
    private IdentNode ident;

    /** Internal function name. */
    private String name;

    /** Compilation unit. */
    private CompileUnit compileUnit;

    /** Method emitter for current method. */
    private MethodEmitter method;

    /** Function kind. */
    private Kind kind;

    /** List of parameters. */
    private List<IdentNode> parameters;

    /** List of nested functions. */
    private List<FunctionNode> functions;

    /** First token of function. **/
    private long firstToken;

    /** Last token of function. **/
    private long lastToken;

    /** Variable frames. */
    private Frame frames;

    /** Method's namespace. */
    private final Namespace namespace;

    /** Node representing current this. */
    @Ignore
    private IdentNode thisNode;

    /** Node representing current scope. */
    @Ignore
    private IdentNode scopeNode;

    /** Node representing return value. */
    @Ignore
    private IdentNode resultNode;

    /** Node representing current arguments. */
    @Ignore
    private IdentNode argumentsNode;

    /** Node representing callee */
    @Ignore
    private IdentNode calleeNode;

    /** Node representing varargs */
    @Ignore
    private IdentNode varArgsNode;

    /** Pending label list. */
    private final Stack<LabelNode> labelStack;

    /** Pending control list. */
    private final Stack<Node> controlStack;

    /** Variable declarations in the function's scope */
    @Ignore
    private final List<VarNode> declarations;

    /** VarNode for this function statement */
    @Ignore //this is explicit code anyway and should not be traversed after lower
    private VarNode funcVarNode;

    /** Line number for function declaration */
    @Ignore
    private LineNumberNode funcVarLineNumberNode;

    /** Initializer var func = __callee__, where applicable */
    @Ignore
    private Node selfSymbolInit;

    /** Current compilation state */
    @Ignore
    private final EnumSet<CompilationState> compilationState;

    /** Function flags. */
    private int flags;

    /** Is anonymous function flag. */
    private static final int IS_ANONYMOUS                = 0b0000_0000_0000_0001;
    /** Is statement flag */
    private static final int IS_STATEMENT                = 0b0000_0000_0000_0010;
    /** is this a strict mode function? */
    private static final int IS_STRICT_MODE              = 0b0000_0000_0000_0100;
    /** Does the function use the "arguments" identifier ? */
    private static final int USES_ARGUMENTS              = 0b0000_0000_0000_1000;
    /** Are we lowered ? */
    private static final int IS_LOWERED                  = 0b0000_0000_0001_0000;
    /** Has this node been split because it was too large? */
    private static final int IS_SPLIT                    = 0b0000_0000_0010_0000;
    /** Is this function lazily compiled? */
    private static final int IS_LAZY                     = 0b0000_0000_0100_0000;
    /** Does the function call eval? */
    private static final int HAS_EVAL                    = 0b0000_0000_1000_0000;
    /** Does the function contain a with block ? */
    private static final int HAS_WITH                    = 0b0000_0001_0000_0000;
    /** Does a descendant function contain a with or eval? */
    private static final int HAS_DESCENDANT_WITH_OR_EVAL = 0b0000_0010_0000_0000;
    /** Does the function define "arguments" identifier as a parameter of nested function name? */
    private static final int DEFINES_ARGUMENTS           = 0b0000_0100_0000_0000;
    /** Does the function need a self symbol? */
    private static final int NEEDS_SELF_SYMBOL           = 0b0000_1000_0000_0000;
    /** Does this function or any of its descendants use variables from an ancestor function's scope (incl. globals)? */
    private static final int USES_ANCESTOR_SCOPE         = 0b0001_0000_0000_0000;

    /** Does this function or any nested functions contain a with or an eval? */
    private static final int HAS_DEEP_WITH_OR_EVAL = HAS_EVAL | HAS_WITH | HAS_DESCENDANT_WITH_OR_EVAL;
    /** Does this function need to store all its variables in scope? */
    private static final int HAS_ALL_VARS_IN_SCOPE = HAS_DEEP_WITH_OR_EVAL | IS_SPLIT;
    /** Does this function potentially need "arguments"? Note that this is not a full test, as further negative check of REDEFINES_ARGS is needed. */
    private static final int MAYBE_NEEDS_ARGUMENTS = USES_ARGUMENTS | HAS_EVAL;
    /** Does this function need the parent scope? It needs it if either it or its descendants use variables from it, or have a deep with or eval. */
    private static final int NEEDS_PARENT_SCOPE = USES_ANCESTOR_SCOPE | HAS_DEEP_WITH_OR_EVAL;

    /** What is the return type of this function? */
    private Type returnType = Type.UNKNOWN;

    /**
     * Used to keep track of a function's parent blocks.
     * This is needed when a (finally body) block is cloned than contains inner functions.
     * Does not include function.getParent().
     */
    @Ignore
    private List<Block> referencingParentBlocks;

    /**
     * Constructor
     *
     * @param source    the source
     * @param token     token
     * @param finish    finish
     * @param namespace the namespace
     * @param parent    the parent block
     * @param ident     the identifier
     * @param name      the name of the function
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public FunctionNode(final Source source, final long token, final int finish, final Namespace namespace, final Block parent, final IdentNode ident, final String name) {
        super(source, token, finish, parent, null);

        this.ident             = ident;
        this.name              = name;
        this.kind              = Kind.NORMAL;
        this.parameters        = new ArrayList<>();
        this.functions         = new ArrayList<>();
        this.firstToken        = token;
        this.lastToken         = token;
        this.namespace         = namespace;
        this.labelStack        = new Stack<>();
        this.controlStack      = new Stack<>();
        this.declarations      = new ArrayList<>();
        // my block -> function is this. We added @SuppressWarnings("LeakingThisInConstructor") as NetBeans identifies
        // it as such a leak - this is a false positive as we're setting this into a field of the object being
        // constructed, so it can't be seen from other threads.
        this.function          = this;
        this.compilationState  = EnumSet.of(CompilationState.INITIALIZED);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private FunctionNode(final FunctionNode functionNode, final CopyState cs) {
        super(functionNode, cs);

        this.ident = (IdentNode)cs.existingOrCopy(functionNode.ident);
        this.name  = functionNode.name;
        this.kind  = functionNode.kind;

        this.parameters = new ArrayList<>();
        for (final IdentNode param : functionNode.getParameters()) {
            this.parameters.add((IdentNode) cs.existingOrCopy(param));
        }

        this.functions         = new ArrayList<>();
        this.firstToken        = functionNode.firstToken;
        this.lastToken         = functionNode.lastToken;
        this.namespace         = functionNode.getNamespace();
        this.thisNode          = (IdentNode)cs.existingOrCopy(functionNode.thisNode);
        this.scopeNode         = (IdentNode)cs.existingOrCopy(functionNode.scopeNode);
        this.resultNode        = (IdentNode)cs.existingOrCopy(functionNode.resultNode);
        this.argumentsNode     = (IdentNode)cs.existingOrCopy(functionNode.argumentsNode);
        this.varArgsNode       = (IdentNode)cs.existingOrCopy(functionNode.varArgsNode);
        this.calleeNode        = (IdentNode)cs.existingOrCopy(functionNode.calleeNode);
        this.labelStack        = new Stack<>();
        this.controlStack      = new Stack<>();
        this.declarations      = new ArrayList<>();

        for (final VarNode decl : functionNode.getDeclarations()) {
            declarations.add((VarNode) cs.existingOrCopy(decl)); //TODO same?
        }

        this.flags = functionNode.flags;

        this.funcVarNode = (VarNode)cs.existingOrCopy(functionNode.funcVarNode);
        /** VarNode for this function statement */

        // my block -> function is this. We added @SuppressWarnings("LeakingThisInConstructor") as NetBeans identifies
        // it as such a leak - this is a false positive as we're setting this into a field of the object being
        // constructed, so it can't be seen from other threads.
        this.function = this;

        this.compilationState = EnumSet.copyOf(functionNode.compilationState);
    }

    @Override
    protected Node copy(final CopyState cs) {
        // deep clone all parent blocks
        return fixBlockChain(new FunctionNode(this, cs));
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        final FunctionNode saveFunctionNode = visitor.getCurrentFunctionNode();
        final Block        saveBlock        = visitor.getCurrentBlock();

        visitor.setCurrentFunctionNode(this);
        visitor.setCurrentCompileUnit(getCompileUnit());
        visitor.setCurrentMethodEmitter(getMethodEmitter());
        visitor.setCurrentBlock(this);

        try {
            if (visitor.enter(this) != null) {
                if (ident != null) {
                    ident = (IdentNode)ident.accept(visitor);
                }

                for (int i = 0, count = parameters.size(); i < count; i++) {
                    parameters.set(i, (IdentNode)parameters.get(i).accept(visitor));
                }

                for (int i = 0, count = functions.size(); i < count; i++) {
                    functions.set(i, (FunctionNode)functions.get(i).accept(visitor));
                }

                for (int i = 0, count = statements.size(); i < count; i++) {
                    statements.set(i, statements.get(i).accept(visitor));
                }

                return visitor.leave(this);
            }
        } finally {
            visitor.setCurrentBlock(saveBlock);
            visitor.setCurrentFunctionNode(saveFunctionNode);
            visitor.setCurrentCompileUnit(saveFunctionNode != null ? saveFunctionNode.getCompileUnit() : null);
            visitor.setCurrentMethodEmitter(saveFunctionNode != null ? saveFunctionNode.getMethodEmitter() : null);
        }

        return this;
    }

    /**
     * Locate the parent function.
     *
     * @return Parent function.
     */
    public FunctionNode findParentFunction() {
        return getParent() != null ? getParent().getFunction() : null;
    }

    /**
     * Add parent name to the builder.
     *
     * @param sb String builder.
     */
    @Override
    public void addParentName(final StringBuilder sb) {
        if (!isScript()) {
            sb.append(getName());
            sb.append("$");
        }
    }

    @Override
    public boolean needsScope() {
        return super.needsScope() || isScript();
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
     * @param state {@link CompilationState} to add
     */
    public void setState(final CompilationState state) {
        compilationState.add(state);
    }

    /*
     * Frame management.
     */

    /**
     * Push a new block frame.
     *
     * @return the new frame
     */
    public final Frame pushFrame() {
        frames = new Frame(frames);
        return frames;
    }

    /**
     * Pop a block frame.
     */
    public final void popFrame() {
        frames = frames.getPrevious();
    }

    /**
     * Create a temporary variable to the current frame.
     *
     * @param currentFrame Frame to add to - defaults to current function frame
     * @param type  Strong type of symbol.
     * @param node  Primary node to use symbol.
     *
     * @return Symbol used.
     */
    public Symbol newTemporary(final Frame currentFrame, final Type type, final Node node) {
        assert currentFrame != null;
        Symbol symbol = node.getSymbol();

        // If no symbol already present.
        if (symbol == null) {
            final String uname = uniqueName(TEMP_PREFIX.tag());
            symbol = new Symbol(uname, IS_TEMP, type);
            symbol.setNode(node);
        }

        // Assign a slot if it doesn't have one.
        if (!symbol.hasSlot()) {
            currentFrame.addSymbol(symbol);
        }

        // Set symbol to node.
        node.setSymbol(symbol);

        return symbol;
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
     * Add a new temporary variable to the current frame
     *
     * @param type Strong type of symbol
     * @param node Primary node to use symbol
     *
     * @return symbol used
     */
    public Symbol newTemporary(final Type type, final Node node) {
        return newTemporary(frames, type, node);
    }

    /**
     * Create a virtual symbol for a literal.
     *
     * @param literalNode Primary node to use symbol.
     *
     * @return Symbol used.
     */
    public Symbol newLiteral(final LiteralNode<?> literalNode) {
        final String uname = uniqueName(LITERAL_PREFIX.tag());
        final Symbol symbol = new Symbol(uname, IS_CONSTANT, literalNode.getType());
        symbol.setNode(literalNode);
        literalNode.setSymbol(symbol);

        return symbol;
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

    /**
     * Determine if script function.
     *
     * @return True if script function.
     */
    public boolean isScript() {
        return getParent() == null;
    }

    /**
     * Get the control stack. Used when parsing to establish nesting depths of
     * different control structures
     *
     * @return the control stack
     */
    public Stack<Node> getControlStack() {
        return controlStack;
    }

    /**
     * Should this function node be lazily code generated, i.e. first at link time
     * @return true if lazy
     */
    public boolean isLazy() {
        return (flags & IS_LAZY) != 0;
    }

    /**
     * Set if this function should be lazily generated
     * @param isLazy is lazy
     */
    public void setIsLazy(final boolean isLazy) {
        this.flags = isLazy ? flags | IS_LAZY : flags & ~IS_LAZY;
    }

    /**
     * Check if the {@code with} keyword is used in this function
     *
     * @return true if {@code with} is used
     */
    public boolean hasWith() {
        return (flags & HAS_WITH) != 0;
    }

    /**
     * Flag this function as using the {@code with} keyword
     */
    public void setHasWith() {
        if(!hasWith()) {
            this.flags |= HAS_WITH;
            // with requires scope in parents.
            // TODO: refine this. with should not force all variables in parents to be in scope, only those that are
            // actually referenced as identifiers by name
            markParentForWithOrEval();
        }
    }

    private void markParentForWithOrEval() {
        // If this is invoked, then either us or a descendant uses with or eval, meaning we must have our own scope.
        setNeedsScope();

        final FunctionNode parentFunction = findParentFunction();
        if(parentFunction != null) {
            parentFunction.setDescendantHasWithOrEval();
        }
    }

    private void setDescendantHasWithOrEval() {
        if((flags & HAS_DESCENDANT_WITH_OR_EVAL) == 0) {
            flags |= HAS_DESCENDANT_WITH_OR_EVAL;
            markParentForWithOrEval();
        }
    }

    /**
     * Check if the {@code eval} keyword is used in this function
     *
     * @return true if {@code eval} is used
     */
    public boolean hasEval() {
        return (flags & HAS_EVAL) != 0;
    }

    /**
     * Flag this function as calling the {@code eval} function
     */
    public void setHasEval() {
        if(!hasEval()) {
            this.flags |= HAS_EVAL;
            markParentForWithOrEval();
        }
    }

    /**
     * Test whether this function or any of its nested functions contains a <tt>with</tt> statement
     * or an <tt>eval</tt> call.
     *
     * @see #hasWith()
     * @see #hasEval()
     * @return true if this or a nested function contains with or eval
     */
    public boolean hasDeepWithOrEval() {
        return (flags & HAS_DEEP_WITH_OR_EVAL) != 0;
    }

    /**
     * Get the first token for this function
     * @return the first token
     */
    public long getFirstToken() {
        return firstToken;
    }

    /**
     * Set the first token for this function
     * @param firstToken the first token
     */
    public void setFirstToken(final long firstToken) {
        this.firstToken = firstToken;
    }

    /**
     * Get all nested functions
     * @return list of nested functions in this function
     */
    public List<FunctionNode> getFunctions() {
        return Collections.unmodifiableList(functions);
    }

    /**
     * Get the label stack. This is used by the parser to establish
     * label nesting depth
     *
     * @return the label stack
     */
    public Stack<LabelNode> getLabelStack() {
        return labelStack;
    }

    /**
     * If this function needs to use var args, return the identifier to the node used
     * for the var args structure
     *
     * @return IdentNode representing the var args structure
     */
    public IdentNode getVarArgsNode() {
        return varArgsNode;
    }

    /**
     * Set the identifier to the node used for the var args structure
     *
     * @param varArgsNode IdentNode representing the var args
     */
    public void setVarArgsNode(final IdentNode varArgsNode) {
        this.varArgsNode = varArgsNode;
    }

    /**
     * If this function uses the {@code callee} variable, return the node used
     * as this variable
     *
     * @return an IdentNode representing the {@code callee} variable
     */
    public IdentNode getCalleeNode() {
        return calleeNode;
    }

    /**
     * If this function uses the {@code callee} variable, set the node representing the
     * callee
     * @param calleeNode an IdentNode representing the callee
     */
    public void setCalleeNode(final IdentNode calleeNode) {
        this.calleeNode = calleeNode;
    }

    /**
     * Check if this function's generated Java method needs a {@code callee} parameter. Functions that need access to
     * their parent scope, functions that reference themselves, and non-strict functions that need an Arguments object
     * (since it exposes {@code arguments.callee} property) will need to have a callee parameter.
     * @return true if the function's generated Java method needs a {@code callee} parameter.
     */
    public boolean needsCallee() {
        return needsParentScope() || needsSelfSymbol() || (needsArguments() && !isStrictMode());
    }

    /**
     * If this is a function where {@code arguments} is used, return the node used as the {@code arguments}
     * variable
     * @return an IdentNode representing {@code arguments}
     */
    public IdentNode getArgumentsNode() {
        return argumentsNode;
    }

    /**
     * If this is a Function where {@code arguments} is used, an identifier to the node representing
     * the {@code arguments} value has to be supplied by the compiler
     *
     * @param argumentsNode IdentNode that represents {@code arguments}
     */
    public void setArgumentsNode(final IdentNode argumentsNode) {
        this.argumentsNode = argumentsNode;
    }

    /**
     * Get the identifier for this function
     * @return the identifier as an IdentityNode
     */
    public IdentNode getIdent() {
        return ident;
    }

    /**
     * Reset the identifier for this function
     * @param ident IdentNode for new identifier
     */
    public void setIdent(final IdentNode ident) {
        this.ident = ident;
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
     * Flag this function as one that defines the identifier "arguments" as a function parameter or nested function
     * name. This precludes it from needing to have an Arguments object defined as "arguments" local variable. Note that
     * defining a local variable named "arguments" still requires construction of the Arguments object (see
     * ECMAScript 5.1 Chapter 10.5).
     * @see #needsArguments()
     */
    public void setDefinesArguments() {
        this.flags |= DEFINES_ARGUMENTS;
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
        return (flags & MAYBE_NEEDS_ARGUMENTS) != 0 && (flags & DEFINES_ARGUMENTS) == 0 && !isScript();
    }

    /**
     * Flags this function as one that uses the "arguments" identifier.
     * @see #needsArguments()
     */
    public void setUsesArguments() {
        flags |= USES_ARGUMENTS;
    }

    /**
     * Returns true if this function needs access to its parent scope. Functions referencing variables outside their
     * scope (including global variables), as well as functions that call eval or have a with block, or have nested
     * functions that call eval or have a with block, will need a parent scope. Top-level script functions also need a
     * parent scope since they might be used from within eval, and eval will need an externally passed scope.
     * @return true if the function needs parent scope.
     */
    public boolean needsParentScope() {
        return (flags & NEEDS_PARENT_SCOPE) != 0 || isScript();
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
     * Set the kind of this function
     * @see FunctionNode.Kind
     * @param kind the kind
     */
    public void setKind(final Kind kind) {
        this.kind = kind;
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
     * @param lastToken the last token
     */
    public void setLastToken(final long lastToken) {
        this.lastToken = lastToken;
    }

    /**
     * Get the name of this function
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of this function
     * @param name the name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Check if this function should have all its variables in its own scope. Scripts, split sub-functions, and
     * functions having with and/or eval blocks are such.
     *
     * @return true if all variables should be in scope
     */
    public boolean varsInScope() {
        return isScript() || (flags & HAS_ALL_VARS_IN_SCOPE) != 0;
    }

    /**
     * Checks if this function is a sub-function generated by splitting a larger one
     *
     * @return true if this function is split from a larger one
     */
    public boolean isSplit() {
        return (flags & IS_SPLIT) != 0;
    }

    /**
     * Flag this function node as being a sub-function generated by the splitter
     */
    public void setIsSplit() {
        this.flags |= IS_SPLIT;
        setNeedsScope();
    }

    /**
     * Get the parameters to this function
     * @return a list of IdentNodes which represent the function parameters, in order
     */
    public List<IdentNode> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * Set the paremeters to this function
     * @param parameters a list of IdentNodes representing parameters in left to right order
     */
    public void setParameters(final List<IdentNode> parameters) {
        this.parameters = parameters;
    }

    /**
     * Get the identifier for the variable in which the function return value
     * should be stored
     * @return an IdentNode representing the return value
     */
    public IdentNode getResultNode() {
        return resultNode;
    }

    /**
     * Set the identifier representing the variable in which the function return
     * value should be stored
     * @param resultNode an IdentNode representing the return value
     */
    public void setResultNode(final IdentNode resultNode) {
        this.resultNode = resultNode;
    }

    /**
     * Get the identifier representing this function's scope
     * @return an IdentNode representing this function's scope
     */
    public IdentNode getScopeNode() {
        return scopeNode;
    }

    /**
     * Set the identifier representing this function's scope
     * @param scopeNode an IdentNode representing this function's scope
     */
    public void setScopeNode(final IdentNode scopeNode) {
        this.scopeNode = scopeNode;
    }

    /**
     * Check if this function is a statement
     * @return true if function is a statement
     */
    public boolean isStatement() {
        return (flags & IS_STATEMENT) != 0;
    }

    /**
     * Flag this function as a statement
     * @see Parser
     */
    public void setIsStatement() {
        this.flags |= IS_STATEMENT;
    }

    /**
     * Check if this function is anonymous
     * @return true if function is anonymous
     */
    public boolean isAnonymous() {
        return (flags & IS_ANONYMOUS) != 0;
    }

    /**
     * Flag this function as an anonymous function.
     * @see Parser
     */
    public void setIsAnonymous() {
        this.flags |= IS_ANONYMOUS;
    }

    /**
     * Does this function need a self symbol - this is needed only for self
     * referring functions
     * @return true if function needs a symbol for self
     */
    public boolean needsSelfSymbol() {
        return (flags & NEEDS_SELF_SYMBOL) != 0;
    }

    /**
     * Get the initializer statement for the __callee__ variable, where applicable
     * for self references
     * @return initialization
     */
    public Node getSelfSymbolInit() {
        return this.selfSymbolInit;
    }

    /**
     * Flag the function as needing a self symbol. This is needed only for
     * self referring functions
     * @param selfSymbolInit initialization expression for self symbol
     */
    public void setNeedsSelfSymbol(final Node selfSymbolInit) {
        this.flags |= NEEDS_SELF_SYMBOL;
        this.selfSymbolInit = selfSymbolInit;
    }

    /**
     * Marks this function as one using any global symbol. The function and all its parent functions will all be marked
     * as needing parent scope.
     * @see #needsParentScope()
     */
    public void setUsesGlobalSymbol() {
        this.flags |= USES_ANCESTOR_SCOPE;
        final FunctionNode parentFn = findParentFunction();
        if(parentFn != null) {
            parentFn.setUsesGlobalSymbol();
        }
    }

    /**
     * Marks this function as using a specified scoped symbol. The function and its parent functions up to but not
     * including the function defining the symbol will be marked as needing parent scope. The function defining the
     * symbol will be marked as one that needs to have its own scope.
     * @param symbol the symbol being used.
     * @see #needsParentScope()
     */
    public void setUsesScopeSymbol(final Symbol symbol) {
        if(symbol.getBlock() == this) {
            setNeedsScope();
        } else {
            this.flags |= USES_ANCESTOR_SCOPE;
            final FunctionNode parentFn = findParentFunction();
            if(parentFn != null) {
                parentFn.setUsesScopeSymbol(symbol);
            }
        }
    }

    /**
     * Return the node representing {@code this} in this function
     * @return IdentNode representing {@code this}
     */
    public IdentNode getThisNode() {
        return thisNode;
    }

    /**
     * Set the node representing {@code this} in this function
     * @param thisNode identifier representing {@code this}
     */
    public void setThisNode(final IdentNode thisNode) {
        this.thisNode = thisNode;
    }

    /**
     * Every function declared as {@code function x()} is internally hoisted
     * and represented as {@code var x = function()  ... }. This getter returns
     * the VarNode representing this virtual assignment
     *
     * @return the var node emitted for setting this function symbol
     */
    public VarNode getFunctionVarNode() {
        return funcVarNode;
    }

    /**
     * Set the virtual VarNode assignment for this function.
     * @see FunctionNode#getFunctionVarNode()
     *
     * @param varNode the virtual var node assignment
     */
    public void setFunctionVarNode(final VarNode varNode) {
        funcVarNode = varNode;
    }

    /**
     * The line number information where the function was declared must be propagated
     * to the virtual {@code var x = function() ... } assignment described in
     * {@link FunctionNode#getFunctionVarNode()}
     * This maintains the line number of the declaration
     *
     * @return a line number node representing the line this function was declared
     */
    public LineNumberNode getFunctionVarLineNumberNode() {
        return funcVarLineNumberNode;
    }

    /**
     * Set the virtual VarNode assignment for this function, along with
     * a line number node for tracking the original start line of the function
     * declaration
     *
     * @param varNode    the virtual var node assignment
     * @param lineNumber the line number node for the function declaration
     */
    public void setFunctionVarNode(final VarNode varNode, final LineNumberNode lineNumber) {
        funcVarNode           = varNode;
        funcVarLineNumberNode = lineNumber;
    }

    /**
     * Get the namespace this function uses for its symbols
     * @return the namespace
     */
    public Namespace getNamespace() {
        return namespace;
    }

    @Override
    public Type getType() {
        return getReturnType();
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
     *
     * @param returnType new return type
     */
    public void setReturnType(final Type returnType) {
        //we never bother with object types narrower than objects, that will lead to byte code verification errors
        //as for instance even if we know we are returning a string from a method, the code generator will always
        //treat it as an object, at least for now
        this.returnType = Type.widest(this.returnType,  returnType.isObject() ? Type.OBJECT : returnType);
    }

    /**
     * Set strict mode on or off for this function
     *
     * @param isStrictMode true if strict mode should be enabled
     */
    public void setStrictMode(final boolean isStrictMode) {
        flags = isStrictMode ? flags | IS_STRICT_MODE : flags & ~IS_STRICT_MODE;
    }

    /**
     * Check if the function is generated in strict mode
     * @return true if strict mode enabled for function
     */
    public boolean isStrictMode() {
        return (flags & IS_STRICT_MODE) != 0;
    }

    /**
     * Set the lowered state
     */
    public void setIsLowered() {
        flags |= IS_LOWERED;
    }

    /**
     * Get the lowered state
     *
     * @return true if function is lowered
     */
    public boolean isLowered() {
        return (flags & IS_LOWERED) != 0;
    }

    /**
     * Add a new function to the function list.
     *
     * @param functionNode Function node to add.
     */
    @Override
    public void addFunction(final FunctionNode functionNode) {
        assert functionNode != null;
        functions.add(functionNode);
    }

    /**
     * Add a list of functions to the function list.
     *
     * @param functionNodes  Function nodes to add.
     */
    @Override
    public void addFunctions(final List<FunctionNode> functionNodes) {
        functions.addAll(functionNodes);
    }

    /**
     * Set a function list
     *
     * @param functionNodes to set
     */
    @Override
    public void setFunctions(final List<FunctionNode> functionNodes) {
        this.functions = functionNodes;
    }

    /**
     * Add a variable declaration that should be visible to the entire function
     * scope. Parser does this.
     *
     * @param varNode a var node
     */
    public void addDeclaration(final VarNode varNode) {
        declarations.add(varNode);
    }

    /**
     * Return all variable declarations from this function scope
     *
     * @return all VarNodes in scope
     */
    public List<VarNode> getDeclarations() {
        return Collections.unmodifiableList(declarations);
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
     * @param compileUnit the compile unit
     */
    public void setCompileUnit(final CompileUnit compileUnit) {
        this.compileUnit = compileUnit;
    }

    /**
     * Return the method emitter used to write bytecode for this function
     * @return the method emitter
     */
    public MethodEmitter getMethodEmitter() {
        return method;
    }

    /**
     * Set the method emitter that is to be used to write bytecode for this function
     * @param method a method emitter
     */
    public void setMethodEmitter(final MethodEmitter method) {
        this.method = method;
    }

    /**
     * Each FunctionNode maintains a list of reference to its parent blocks.
     * Add a parent block to this function.
     *
     * @param parentBlock  a block to remember as parent
     */
    public void addReferencingParentBlock(final Block parentBlock) {
        assert parentBlock.getFunction() == function.findParentFunction(); // all parent blocks must be in the same function
        if (parentBlock != function.getParent()) {
            if (referencingParentBlocks == null) {
                referencingParentBlocks = new LinkedList<>();
            }
            referencingParentBlocks.add(parentBlock);
        }
    }

    /**
     * Get the known parent blocks to this function
     *
     * @return list of parent blocks
     */
    public List<Block> getReferencingParentBlocks() {
        if (referencingParentBlocks == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(referencingParentBlocks);
    }
}
