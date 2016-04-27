/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.codegen.CompilerConstants.ANON_FUNCTION_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.EVAL;
import static jdk.nashorn.internal.codegen.CompilerConstants.PROGRAM;
import static jdk.nashorn.internal.parser.TokenType.ARROW;
import static jdk.nashorn.internal.parser.TokenType.ASSIGN;
import static jdk.nashorn.internal.parser.TokenType.CASE;
import static jdk.nashorn.internal.parser.TokenType.CATCH;
import static jdk.nashorn.internal.parser.TokenType.CLASS;
import static jdk.nashorn.internal.parser.TokenType.COLON;
import static jdk.nashorn.internal.parser.TokenType.COMMARIGHT;
import static jdk.nashorn.internal.parser.TokenType.COMMENT;
import static jdk.nashorn.internal.parser.TokenType.CONST;
import static jdk.nashorn.internal.parser.TokenType.DECPOSTFIX;
import static jdk.nashorn.internal.parser.TokenType.DECPREFIX;
import static jdk.nashorn.internal.parser.TokenType.ELLIPSIS;
import static jdk.nashorn.internal.parser.TokenType.ELSE;
import static jdk.nashorn.internal.parser.TokenType.EOF;
import static jdk.nashorn.internal.parser.TokenType.EOL;
import static jdk.nashorn.internal.parser.TokenType.EQ_STRICT;
import static jdk.nashorn.internal.parser.TokenType.ESCSTRING;
import static jdk.nashorn.internal.parser.TokenType.EXPORT;
import static jdk.nashorn.internal.parser.TokenType.EXTENDS;
import static jdk.nashorn.internal.parser.TokenType.FINALLY;
import static jdk.nashorn.internal.parser.TokenType.FUNCTION;
import static jdk.nashorn.internal.parser.TokenType.IDENT;
import static jdk.nashorn.internal.parser.TokenType.IF;
import static jdk.nashorn.internal.parser.TokenType.IMPORT;
import static jdk.nashorn.internal.parser.TokenType.INCPOSTFIX;
import static jdk.nashorn.internal.parser.TokenType.LBRACE;
import static jdk.nashorn.internal.parser.TokenType.LBRACKET;
import static jdk.nashorn.internal.parser.TokenType.LET;
import static jdk.nashorn.internal.parser.TokenType.LPAREN;
import static jdk.nashorn.internal.parser.TokenType.MUL;
import static jdk.nashorn.internal.parser.TokenType.PERIOD;
import static jdk.nashorn.internal.parser.TokenType.RBRACE;
import static jdk.nashorn.internal.parser.TokenType.RBRACKET;
import static jdk.nashorn.internal.parser.TokenType.RPAREN;
import static jdk.nashorn.internal.parser.TokenType.SEMICOLON;
import static jdk.nashorn.internal.parser.TokenType.SPREAD_ARRAY;
import static jdk.nashorn.internal.parser.TokenType.STATIC;
import static jdk.nashorn.internal.parser.TokenType.STRING;
import static jdk.nashorn.internal.parser.TokenType.SUPER;
import static jdk.nashorn.internal.parser.TokenType.TEMPLATE;
import static jdk.nashorn.internal.parser.TokenType.TEMPLATE_HEAD;
import static jdk.nashorn.internal.parser.TokenType.TEMPLATE_MIDDLE;
import static jdk.nashorn.internal.parser.TokenType.TEMPLATE_TAIL;
import static jdk.nashorn.internal.parser.TokenType.TERNARY;
import static jdk.nashorn.internal.parser.TokenType.VAR;
import static jdk.nashorn.internal.parser.TokenType.VOID;
import static jdk.nashorn.internal.parser.TokenType.WHILE;
import static jdk.nashorn.internal.parser.TokenType.YIELD;
import static jdk.nashorn.internal.parser.TokenType.YIELD_STAR;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ClassNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DebuggerNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ErrorNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionList;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Module;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyKey;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSErrorType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptingFunctions;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Timing;
import jdk.nashorn.internal.runtime.linker.NameCodec;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Builds the IR.
 */
@Logger(name="parser")
public class Parser extends AbstractParser implements Loggable {
    private static final String ARGUMENTS_NAME = CompilerConstants.ARGUMENTS_VAR.symbolName();

    /** Current env. */
    private final ScriptEnvironment env;

    /** Is scripting mode. */
    private final boolean scripting;

    private List<Statement> functionDeclarations;

    private final ParserContext lc;
    private final Deque<Object> defaultNames;

    /** Namespace for function names where not explicitly given */
    private final Namespace namespace;

    private final DebugLogger log;

    /** to receive line information from Lexer when scanning multine literals. */
    protected final Lexer.LineInfoReceiver lineInfoReceiver;

    private RecompilableScriptFunctionData reparsedFunction;

    /**
     * Constructor
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors) {
        this(env, source, errors, env._strict, null);
    }

    /**
     * Constructor
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     * @param strict  strict
     * @param log debug logger if one is needed
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors, final boolean strict, final DebugLogger log) {
        this(env, source, errors, strict, 0, log);
    }

    /**
     * Construct a parser.
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     * @param strict  parser created with strict mode enabled.
     * @param lineOffset line offset to start counting lines from
     * @param log debug logger if one is needed
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors, final boolean strict, final int lineOffset, final DebugLogger log) {
        super(source, errors, strict, lineOffset);
        this.lc = new ParserContext();
        this.defaultNames = new ArrayDeque<>();
        this.env = env;
        this.namespace = new Namespace(env.getNamespace());
        this.scripting = env._scripting;
        if (this.scripting) {
            this.lineInfoReceiver = new Lexer.LineInfoReceiver() {
                @Override
                public void lineInfo(final int receiverLine, final int receiverLinePosition) {
                    // update the parser maintained line information
                    Parser.this.line = receiverLine;
                    Parser.this.linePosition = receiverLinePosition;
                }
            };
        } else {
            // non-scripting mode script can't have multi-line literals
            this.lineInfoReceiver = null;
        }

        this.log = log == null ? DebugLogger.DISABLED_LOGGER : log;
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    /**
     * Sets the name for the first function. This is only used when reparsing anonymous functions to ensure they can
     * preserve their already assigned name, as that name doesn't appear in their source text.
     * @param name the name for the first parsed function.
     */
    public void setFunctionName(final String name) {
        defaultNames.push(createIdentNode(0, 0, name));
    }

    /**
     * Sets the {@link RecompilableScriptFunctionData} representing the function being reparsed (when this
     * parser instance is used to reparse a previously parsed function, as part of its on-demand compilation).
     * This will trigger various special behaviors, such as skipping nested function bodies.
     * @param reparsedFunction the function being reparsed.
     */
    public void setReparsedFunction(final RecompilableScriptFunctionData reparsedFunction) {
        this.reparsedFunction = reparsedFunction;
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * This is the default parse call, which will name the function node
     * {code :program} {@link CompilerConstants#PROGRAM}
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse() {
        return parse(PROGRAM.symbolName(), 0, source.getLength(), false);
    }

    /**
     * Set up first token. Skips opening EOL.
     */
    private void scanFirstToken() {
        k = -1;
        next();
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * This should be used to create one and only one function node
     *
     * @param scriptName name for the script, given to the parsed FunctionNode
     * @param startPos start position in source
     * @param len length of parse
     * @param allowPropertyFunction if true, "get" and "set" are allowed as first tokens of the program, followed by
     * a property getter or setter function. This is used when reparsing a function that can potentially be defined as a
     * property getter or setter in an object literal.
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse(final String scriptName, final int startPos, final int len, final boolean allowPropertyFunction) {
        final boolean isTimingEnabled = env.isTimingEnabled();
        final long t0 = isTimingEnabled ? System.nanoTime() : 0L;
        log.info(this, " begin for '", scriptName, "'");

        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, startPos, len, stream, scripting && !env._no_syntax_extensions, env._es6, reparsedFunction != null);
            lexer.line = lexer.pendingLine = lineOffset + 1;
            line = lineOffset;

            scanFirstToken();
            // Begin parse.
            return program(scriptName, allowPropertyFunction);
        } catch (final Exception e) {
            handleParseException(e);

            return null;
        } finally {
            final String end = this + " end '" + scriptName + "'";
            if (isTimingEnabled) {
                env._timing.accumulateTime(toString(), System.nanoTime() - t0);
                log.info(end, "' in ", Timing.toMillisPrint(System.nanoTime() - t0), " ms");
            } else {
                log.info(end);
            }
        }
    }

    /**
     * Parse and return the resulting module.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * @param moduleName name for the module, given to the parsed FunctionNode
     * @param startPos start position in source
     * @param len length of parse
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parseModule(final String moduleName, final int startPos, final int len) {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, startPos, len, stream, scripting && !env._no_syntax_extensions, env._es6, reparsedFunction != null);
            lexer.line = lexer.pendingLine = lineOffset + 1;
            line = lineOffset;

            scanFirstToken();
            // Begin parse.
            return module(moduleName);
        } catch (final Exception e) {
            handleParseException(e);

            return null;
        }
    }

    /**
     * Entry point for parsing a module.
     *
     * @param moduleName the module name
     * @return the parsed module
     */
    public FunctionNode parseModule(final String moduleName) {
        return parseModule(moduleName, 0, source.getLength());
    }

    /**
     * Parse and return the list of function parameter list. A comma
     * separated list of function parameter identifiers is expected to be parsed.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail. This method is used to check if parameter Strings
     * passed to "Function" constructor is a valid or not.
     *
     * @return the list of IdentNodes representing the formal parameter list
     */
    public List<IdentNode> parseFormalParameterList() {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, stream, scripting && !env._no_syntax_extensions, env._es6);

            scanFirstToken();

            return formalParameterList(TokenType.EOF, false);
        } catch (final Exception e) {
            handleParseException(e);
            return null;
        }
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail. This method is used to check if code String
     * passed to "Function" constructor is a valid function body or not.
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parseFunctionBody() {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, stream, scripting && !env._no_syntax_extensions, env._es6);
            final int functionLine = line;

            scanFirstToken();

            // Make a fake token for the function.
            final long functionToken = Token.toDesc(FUNCTION, 0, source.getLength());
            // Set up the function to append elements.

            final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), PROGRAM.symbolName());
            final ParserContextFunctionNode function = createParserContextFunctionNode(ident, functionToken, FunctionNode.Kind.NORMAL, functionLine, Collections.<IdentNode>emptyList());
            lc.push(function);

            final ParserContextBlockNode body = newBlock();

            functionDeclarations = new ArrayList<>();
            sourceElements(false);
            addFunctionDeclarations(function);
            functionDeclarations = null;

            restoreBlock(body);
            body.setFlag(Block.NEEDS_SCOPE);

            final Block functionBody = new Block(functionToken, source.getLength() - 1,
                body.getFlags() | Block.IS_SYNTHETIC, body.getStatements());
            lc.pop(function);

            expect(EOF);

            final FunctionNode functionNode = createFunctionNode(
                    function,
                    functionToken,
                    ident,
                    Collections.<IdentNode>emptyList(),
                    FunctionNode.Kind.NORMAL,
                    functionLine,
                    functionBody);
            printAST(functionNode);
            return functionNode;
        } catch (final Exception e) {
            handleParseException(e);
            return null;
        }
    }

    private void handleParseException(final Exception e) {
        // Extract message from exception.  The message will be in error
        // message format.
        String message = e.getMessage();

        // If empty message.
        if (message == null) {
            message = e.toString();
        }

        // Issue message.
        if (e instanceof ParserException) {
            errors.error((ParserException)e);
        } else {
            errors.error(message);
        }

        if (env._dump_on_error) {
            e.printStackTrace(env.getErr());
        }
    }

    /**
     * Skip to a good parsing recovery point.
     */
    private void recover(final Exception e) {
        if (e != null) {
            // Extract message from exception.  The message will be in error
            // message format.
            String message = e.getMessage();

            // If empty message.
            if (message == null) {
                message = e.toString();
            }

            // Issue message.
            if (e instanceof ParserException) {
                errors.error((ParserException)e);
            } else {
                errors.error(message);
            }

            if (env._dump_on_error) {
                e.printStackTrace(env.getErr());
            }
        }

        // Skip to a recovery point.
        loop:
        while (true) {
            switch (type) {
            case EOF:
                // Can not go any further.
                break loop;
            case EOL:
            case SEMICOLON:
            case RBRACE:
                // Good recovery points.
                next();
                break loop;
            default:
                // So we can recover after EOL.
                nextOrEOL();
                break;
            }
        }
    }

    /**
     * Set up a new block.
     *
     * @return New block.
     */
    private ParserContextBlockNode newBlock() {
        return lc.push(new ParserContextBlockNode(token));
    }

    private ParserContextFunctionNode createParserContextFunctionNode(final IdentNode ident, final long functionToken, final FunctionNode.Kind kind, final int functionLine, final List<IdentNode> parameters) {
        // Build function name.
        final StringBuilder sb = new StringBuilder();

        final ParserContextFunctionNode parentFunction = lc.getCurrentFunction();
        if (parentFunction != null && !parentFunction.isProgram()) {
            sb.append(parentFunction.getName()).append('$');
        }

        assert ident.getName() != null;
        sb.append(ident.getName());

        final String name = namespace.uniqueName(sb.toString());
        assert parentFunction != null || name.equals(PROGRAM.symbolName()) : "name = " + name;

        int flags = 0;
        if (isStrictMode) {
            flags |= FunctionNode.IS_STRICT;
        }
        if (parentFunction == null) {
            flags |= FunctionNode.IS_PROGRAM;
        }

        final ParserContextFunctionNode functionNode = new ParserContextFunctionNode(functionToken, ident, name, namespace, functionLine, kind, parameters);
        functionNode.setFlag(flags);
        return functionNode;
    }

    private FunctionNode createFunctionNode(final ParserContextFunctionNode function, final long startToken, final IdentNode ident, final List<IdentNode> parameters, final FunctionNode.Kind kind, final int functionLine, final Block body) {
        // assert body.isFunctionBody() || body.getFlag(Block.IS_PARAMETER_BLOCK) && ((BlockStatement) body.getLastStatement()).getBlock().isFunctionBody();
        // Start new block.
        final FunctionNode functionNode =
            new FunctionNode(
                source,
                functionLine,
                body.getToken(),
                Token.descPosition(body.getToken()),
                startToken,
                function.getLastToken(),
                namespace,
                ident,
                function.getName(),
                parameters,
                kind,
                function.getFlags(),
                body,
                function.getEndParserState(),
                function.getModule(),
                function.getDebugFlags());

        printAST(functionNode);

        return functionNode;
    }

    /**
     * Restore the current block.
     */
    private ParserContextBlockNode restoreBlock(final ParserContextBlockNode block) {
        return lc.pop(block);
    }

    /**
     * Get the statements in a block.
     * @return Block statements.
     */
    private Block getBlock(final boolean needsBraces) {
        final long blockToken = token;
        final ParserContextBlockNode newBlock = newBlock();
        try {
            // Block opening brace.
            if (needsBraces) {
                expect(LBRACE);
            }
            // Accumulate block statements.
            statementList();

        } finally {
            restoreBlock(newBlock);
        }

        // Block closing brace.
        if (needsBraces) {
            expect(RBRACE);
        }

        final int flags = newBlock.getFlags() | (needsBraces ? 0 : Block.IS_SYNTHETIC);
        return new Block(blockToken, finish, flags, newBlock.getStatements());
    }

    /**
     * Get the statements in a case clause.
     */
    private List<Statement> caseStatementList() {
        final ParserContextBlockNode newBlock = newBlock();
        try {
            statementList();
        } finally {
            restoreBlock(newBlock);
        }
        return newBlock.getStatements();
    }

    /**
     * Get all the statements generated by a single statement.
     * @return Statements.
     */
    private Block getStatement() {
        return getStatement(false);
    }

    private Block getStatement(boolean labelledStatement) {
        if (type == LBRACE) {
            return getBlock(true);
        }
        // Set up new block. Captures first token.
        final ParserContextBlockNode newBlock = newBlock();
        try {
            statement(false, false, true, labelledStatement);
        } finally {
            restoreBlock(newBlock);
        }
        return new Block(newBlock.getToken(), finish, newBlock.getFlags() | Block.IS_SYNTHETIC, newBlock.getStatements());
    }

    /**
     * Detect calls to special functions.
     * @param ident Called function.
     */
    private void detectSpecialFunction(final IdentNode ident) {
        final String name = ident.getName();

        if (EVAL.symbolName().equals(name)) {
            markEval(lc);
        } else if (SUPER.getName().equals(name)) {
            assert ident.isDirectSuper();
            markSuperCall(lc);
        }
    }

    /**
     * Detect use of special properties.
     * @param ident Referenced property.
     */
    private void detectSpecialProperty(final IdentNode ident) {
        if (isArguments(ident)) {
            // skip over arrow functions, e.g. function f() { return (() => arguments.length)(); }
            getCurrentNonArrowFunction().setFlag(FunctionNode.USES_ARGUMENTS);
        }
    }

    private boolean useBlockScope() {
        return env._es6;
    }

    private boolean isES6() {
        return env._es6;
    }

    private static boolean isArguments(final String name) {
        return ARGUMENTS_NAME.equals(name);
    }

    static boolean isArguments(final IdentNode ident) {
        return isArguments(ident.getName());
    }

    /**
     * Tells whether a IdentNode can be used as L-value of an assignment
     *
     * @param ident IdentNode to be checked
     * @return whether the ident can be used as L-value
     */
    private static boolean checkIdentLValue(final IdentNode ident) {
        return ident.tokenType().getKind() != TokenKind.KEYWORD;
    }

    /**
     * Verify an assignment expression.
     * @param op  Operation token.
     * @param lhs Left hand side expression.
     * @param rhs Right hand side expression.
     * @return Verified expression.
     */
    private Expression verifyAssignment(final long op, final Expression lhs, final Expression rhs) {
        final TokenType opType = Token.descType(op);

        switch (opType) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
            if (lhs instanceof IdentNode) {
                if (!checkIdentLValue((IdentNode)lhs)) {
                    return referenceError(lhs, rhs, false);
                }
                verifyIdent((IdentNode)lhs, "assignment");
                break;
            } else if (lhs instanceof AccessNode || lhs instanceof IndexNode) {
                break;
            } else if (opType == ASSIGN && isDestructuringLhs(lhs)) {
                verifyDestructuringAssignmentPattern(lhs, "assignment");
                break;
            } else {
                return referenceError(lhs, rhs, env._early_lvalue_error);
            }
        default:
            break;
        }

        // Build up node.
        if(BinaryNode.isLogical(opType)) {
            return new BinaryNode(op, new JoinPredecessorExpression(lhs), new JoinPredecessorExpression(rhs));
        }
        return new BinaryNode(op, lhs, rhs);
    }

    private boolean isDestructuringLhs(Expression lhs) {
        if (lhs instanceof ObjectNode || lhs instanceof LiteralNode.ArrayLiteralNode) {
            return isES6();
        }
        return false;
    }

    private void verifyDestructuringAssignmentPattern(Expression pattern, String contextString) {
        assert pattern instanceof ObjectNode || pattern instanceof LiteralNode.ArrayLiteralNode;
        pattern.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterLiteralNode(LiteralNode<?> literalNode) {
                if (literalNode.isArray()) {
                    boolean restElement = false;
                    for (Expression element : literalNode.getElementExpressions()) {
                        if (element != null) {
                            if (restElement) {
                                throw error(String.format("Unexpected element after rest element"), element.getToken());
                            }
                            if (element.isTokenType(SPREAD_ARRAY)) {
                                restElement = true;
                                Expression lvalue = ((UnaryNode) element).getExpression();
                                if (!checkValidLValue(lvalue, contextString)) {
                                    throw error(AbstractParser.message("invalid.lvalue"), lvalue.getToken());
                                }
                            }
                            element.accept(this);
                        }
                    }
                    return false;
                } else {
                    return enterDefault(literalNode);
                }
            }

            @Override
            public boolean enterObjectNode(ObjectNode objectNode) {
                return true;
            }

            @Override
            public boolean enterPropertyNode(PropertyNode propertyNode) {
                if (propertyNode.getValue() != null) {
                    propertyNode.getValue().accept(this);
                    return false;
                } else {
                    return enterDefault(propertyNode);
                }
            }

            @Override
            public boolean enterIdentNode(IdentNode identNode) {
                verifyIdent(identNode, contextString);
                if (!checkIdentLValue(identNode)) {
                    referenceError(identNode, null, true);
                    return false;
                }
                return false;
            }

            @Override
            public boolean enterAccessNode(AccessNode accessNode) {
                return false;
            }

            @Override
            public boolean enterIndexNode(IndexNode indexNode) {
                return false;
            }

            @Override
            public boolean enterBinaryNode(BinaryNode binaryNode) {
                if (binaryNode.isTokenType(ASSIGN)) {
                    binaryNode.lhs().accept(this);
                    // Initializer(rhs) can be any AssignmentExpression
                    return false;
                } else {
                    return enterDefault(binaryNode);
                }
            }

            @Override
            public boolean enterUnaryNode(UnaryNode unaryNode) {
                if (unaryNode.isTokenType(SPREAD_ARRAY)) {
                    // rest element
                    return true;
                } else {
                    return enterDefault(unaryNode);
                }
            }

            @Override
            protected boolean enterDefault(Node node) {
                throw error(String.format("unexpected node in AssignmentPattern: %s", node));
            }
        });
    }

    private static Expression newBinaryExpression(final long op, final Expression lhs, final Expression rhs) {
        final TokenType opType = Token.descType(op);

        // Build up node.
        if (BinaryNode.isLogical(opType)) {
            return new BinaryNode(op, new JoinPredecessorExpression(lhs), new JoinPredecessorExpression(rhs));
        }
        return new BinaryNode(op, lhs, rhs);
    }


    /**
     * Reduce increment/decrement to simpler operations.
     * @param firstToken First token.
     * @param tokenType  Operation token (INCPREFIX/DEC.)
     * @param expression Left hand side expression.
     * @param isPostfix  Prefix or postfix.
     * @return           Reduced expression.
     */
    private static UnaryNode incDecExpression(final long firstToken, final TokenType tokenType, final Expression expression, final boolean isPostfix) {
        if (isPostfix) {
            return new UnaryNode(Token.recast(firstToken, tokenType == DECPREFIX ? DECPOSTFIX : INCPOSTFIX), expression.getStart(), Token.descPosition(firstToken) + Token.descLength(firstToken), expression);
        }

        return new UnaryNode(firstToken, expression);
    }

    /**
     * -----------------------------------------------------------------------
     *
     * Grammar based on
     *
     *      ECMAScript Language Specification
     *      ECMA-262 5th Edition / December 2009
     *
     * -----------------------------------------------------------------------
     */

    /**
     * Program :
     *      SourceElements?
     *
     * See 14
     *
     * Parse the top level script.
     */
    private FunctionNode program(final String scriptName, final boolean allowPropertyFunction) {
        // Make a pseudo-token for the script holding its start and length.
        final long functionToken = Token.toDesc(FUNCTION, Token.descPosition(Token.withDelimiter(token)), source.getLength());
        final int  functionLine  = line;

        final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), scriptName);
        final ParserContextFunctionNode script = createParserContextFunctionNode(
                ident,
                functionToken,
                FunctionNode.Kind.SCRIPT,
                functionLine,
                Collections.<IdentNode>emptyList());
        lc.push(script);
        final ParserContextBlockNode body = newBlock();

        functionDeclarations = new ArrayList<>();
        sourceElements(allowPropertyFunction);
        addFunctionDeclarations(script);
        functionDeclarations = null;

        restoreBlock(body);
        body.setFlag(Block.NEEDS_SCOPE);
        final Block programBody = new Block(functionToken, finish, body.getFlags() | Block.IS_SYNTHETIC | Block.IS_BODY, body.getStatements());
        lc.pop(script);
        script.setLastToken(token);

        expect(EOF);

        return createFunctionNode(script, functionToken, ident, Collections.<IdentNode>emptyList(), FunctionNode.Kind.SCRIPT, functionLine, programBody);
    }

    /**
     * Directive value or null if statement is not a directive.
     *
     * @param stmt Statement to be checked
     * @return Directive value if the given statement is a directive
     */
    private String getDirective(final Node stmt) {
        if (stmt instanceof ExpressionStatement) {
            final Node expr = ((ExpressionStatement)stmt).getExpression();
            if (expr instanceof LiteralNode) {
                final LiteralNode<?> lit = (LiteralNode<?>)expr;
                final long litToken = lit.getToken();
                final TokenType tt = Token.descType(litToken);
                // A directive is either a string or an escape string
                if (tt == TokenType.STRING || tt == TokenType.ESCSTRING) {
                    // Make sure that we don't unescape anything. Return as seen in source!
                    return source.getString(lit.getStart(), Token.descLength(litToken));
                }
            }
        }

        return null;
    }

    /**
     * SourceElements :
     *      SourceElement
     *      SourceElements SourceElement
     *
     * See 14
     *
     * Parse the elements of the script or function.
     */
    private void sourceElements(final boolean shouldAllowPropertyFunction) {
        List<Node>    directiveStmts        = null;
        boolean       checkDirective        = true;
        boolean       allowPropertyFunction = shouldAllowPropertyFunction;
        final boolean oldStrictMode         = isStrictMode;


        try {
            // If is a script, then process until the end of the script.
            while (type != EOF) {
                // Break if the end of a code block.
                if (type == RBRACE) {
                    break;
                }

                try {
                    // Get the next element.
                    statement(true, allowPropertyFunction, false, false);
                    allowPropertyFunction = false;

                    // check for directive prologues
                    if (checkDirective) {
                        // skip any debug statement like line number to get actual first line
                        final Statement lastStatement = lc.getLastStatement();

                        // get directive prologue, if any
                        final String directive = getDirective(lastStatement);

                        // If we have seen first non-directive statement,
                        // no more directive statements!!
                        checkDirective = directive != null;

                        if (checkDirective) {
                            if (!oldStrictMode) {
                                if (directiveStmts == null) {
                                    directiveStmts = new ArrayList<>();
                                }
                                directiveStmts.add(lastStatement);
                            }

                            // handle use strict directive
                            if ("use strict".equals(directive)) {
                                isStrictMode = true;
                                final ParserContextFunctionNode function = lc.getCurrentFunction();
                                function.setFlag(FunctionNode.IS_STRICT);

                                // We don't need to check these, if lexical environment is already strict
                                if (!oldStrictMode && directiveStmts != null) {
                                    // check that directives preceding this one do not violate strictness
                                    for (final Node statement : directiveStmts) {
                                        // the get value will force unescape of preceding
                                        // escaped string directives
                                        getValue(statement.getToken());
                                    }

                                    // verify that function name as well as parameter names
                                    // satisfy strict mode restrictions.
                                    verifyIdent(function.getIdent(), "function name");
                                    for (final IdentNode param : function.getParameters()) {
                                        verifyIdent(param, "function parameter");
                                    }
                                }
                            } else if (Context.DEBUG) {
                                final int debugFlag = FunctionNode.getDirectiveFlag(directive);
                                if (debugFlag != 0) {
                                    final ParserContextFunctionNode function = lc.getCurrentFunction();
                                    function.setDebugFlag(debugFlag);
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    final int errorLine = line;
                    final long errorToken = token;
                    //recover parsing
                    recover(e);
                    final ErrorNode errorExpr = new ErrorNode(errorToken, finish);
                    final ExpressionStatement expressionStatement = new ExpressionStatement(errorLine, errorToken, finish, errorExpr);
                    appendStatement(expressionStatement);
                }

                // No backtracking from here on.
                stream.commit(k);
            }
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    /**
     * Parse any of the basic statement types.
     *
     * Statement :
     *      BlockStatement
     *      VariableStatement
     *      EmptyStatement
     *      ExpressionStatement
     *      IfStatement
     *      BreakableStatement
     *      ContinueStatement
     *      BreakStatement
     *      ReturnStatement
     *      WithStatement
     *      LabelledStatement
     *      ThrowStatement
     *      TryStatement
     *      DebuggerStatement
     *
     * BreakableStatement :
     *      IterationStatement
     *      SwitchStatement
     *
     * BlockStatement :
     *      Block
     *
     * Block :
     *      { StatementList opt }
     *
     * StatementList :
     *      StatementListItem
     *      StatementList StatementListItem
     *
     * StatementItem :
     *      Statement
     *      Declaration
     *
     * Declaration :
     *     HoistableDeclaration
     *     ClassDeclaration
     *     LexicalDeclaration
     *
     * HoistableDeclaration :
     *     FunctionDeclaration
     *     GeneratorDeclaration
     */
    private void statement() {
        statement(false, false, false, false);
    }

    /**
     * @param topLevel does this statement occur at the "top level" of a script or a function?
     * @param allowPropertyFunction allow property "get" and "set" functions?
     * @param singleStatement are we in a single statement context?
     */
    private void statement(final boolean topLevel, final boolean allowPropertyFunction, final boolean singleStatement, final boolean labelledStatement) {
        switch (type) {
        case LBRACE:
            block();
            break;
        case VAR:
            variableStatement(type);
            break;
        case SEMICOLON:
            emptyStatement();
            break;
        case IF:
            ifStatement();
            break;
        case FOR:
            forStatement();
            break;
        case WHILE:
            whileStatement();
            break;
        case DO:
            doStatement();
            break;
        case CONTINUE:
            continueStatement();
            break;
        case BREAK:
            breakStatement();
            break;
        case RETURN:
            returnStatement();
            break;
        case WITH:
            withStatement();
            break;
        case SWITCH:
            switchStatement();
            break;
        case THROW:
            throwStatement();
            break;
        case TRY:
            tryStatement();
            break;
        case DEBUGGER:
            debuggerStatement();
            break;
        case RPAREN:
        case RBRACKET:
        case EOF:
            expect(SEMICOLON);
            break;
        case FUNCTION:
            // As per spec (ECMA section 12), function declarations as arbitrary statement
            // is not "portable". Implementation can issue a warning or disallow the same.
            if (singleStatement) {
                // ES6 B.3.2 Labelled Function Declarations
                // It is a Syntax Error if any strict mode source code matches this rule:
                // LabelledItem : FunctionDeclaration.
                if (!labelledStatement || isStrictMode) {
                    throw error(AbstractParser.message("expected.stmt", "function declaration"), token);
                }
            }
            functionExpression(true, topLevel || labelledStatement);
            return;
        default:
            if (useBlockScope() && (type == LET && lookaheadIsLetDeclaration(false) || type == CONST)) {
                if (singleStatement) {
                    throw error(AbstractParser.message("expected.stmt", type.getName() + " declaration"), token);
                }
                variableStatement(type);
                break;
            } else if (type == CLASS && isES6()) {
                if (singleStatement) {
                    throw error(AbstractParser.message("expected.stmt", "class declaration"), token);
                }
                classDeclaration(false);
                break;
            }
            if (env._const_as_var && type == CONST) {
                variableStatement(TokenType.VAR);
                break;
            }

            if (type == IDENT || isNonStrictModeIdent()) {
                if (T(k + 1) == COLON) {
                    labelStatement();
                    return;
                }
                if(allowPropertyFunction) {
                    final String ident = (String)getValue();
                    final long propertyToken = token;
                    final int propertyLine = line;
                    if ("get".equals(ident)) {
                        next();
                        addPropertyFunctionStatement(propertyGetterFunction(propertyToken, propertyLine));
                        return;
                    } else if ("set".equals(ident)) {
                        next();
                        addPropertyFunctionStatement(propertySetterFunction(propertyToken, propertyLine));
                        return;
                    }
                }
            }

            expressionStatement();
            break;
        }
    }

    private void addPropertyFunctionStatement(final PropertyFunction propertyFunction) {
        final FunctionNode fn = propertyFunction.functionNode;
        functionDeclarations.add(new ExpressionStatement(fn.getLineNumber(), fn.getToken(), finish, fn));
    }

    /**
     * ClassDeclaration[Yield, Default] :
     *   class BindingIdentifier[?Yield] ClassTail[?Yield]
     *   [+Default] class ClassTail[?Yield]
     */
    private ClassNode classDeclaration(boolean isDefault) {
        int classLineNumber = line;

        ClassNode classExpression = classExpression(!isDefault);

        if (!isDefault) {
            VarNode classVar = new VarNode(classLineNumber, classExpression.getToken(), classExpression.getIdent().getFinish(), classExpression.getIdent(), classExpression, VarNode.IS_CONST);
            appendStatement(classVar);
        }
        return classExpression;
    }

    /**
     * ClassExpression[Yield] :
     *   class BindingIdentifier[?Yield]opt ClassTail[?Yield]
     */
    private ClassNode classExpression(boolean isStatement) {
        assert type == CLASS;
        int classLineNumber = line;
        long classToken = token;
        next();

        IdentNode className = null;
        if (isStatement || type == IDENT) {
            className = getIdent();
        }

        return classTail(classLineNumber, classToken, className);
    }

    private static final class ClassElementKey {
        private final boolean isStatic;
        private final String propertyName;

        private ClassElementKey(boolean isStatic, String propertyName) {
            this.isStatic = isStatic;
            this.propertyName = propertyName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (isStatic ? 1231 : 1237);
            result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ClassElementKey) {
                ClassElementKey other = (ClassElementKey) obj;
                return this.isStatic == other.isStatic && Objects.equals(this.propertyName, other.propertyName);
            }
            return false;
        }
    }

    /**
     * Parse ClassTail and ClassBody.
     *
     * ClassTail[Yield] :
     *   ClassHeritage[?Yield]opt { ClassBody[?Yield]opt }
     * ClassHeritage[Yield] :
     *   extends LeftHandSideExpression[?Yield]
     *
     * ClassBody[Yield] :
     *   ClassElementList[?Yield]
     * ClassElementList[Yield] :
     *   ClassElement[?Yield]
     *   ClassElementList[?Yield] ClassElement[?Yield]
     * ClassElement[Yield] :
     *   MethodDefinition[?Yield]
     *   static MethodDefinition[?Yield]
     *   ;
     */
    private ClassNode classTail(final int classLineNumber, final long classToken, final IdentNode className) {
        final boolean oldStrictMode = isStrictMode;
        isStrictMode = true;
        try {
            Expression classHeritage = null;
            if (type == EXTENDS) {
                next();
                classHeritage = leftHandSideExpression();
            }

            expect(LBRACE);

            PropertyNode constructor = null;
            final ArrayList<PropertyNode> classElements = new ArrayList<>();
            final Map<ClassElementKey, Integer> keyToIndexMap = new HashMap<>();
            for (;;) {
                if (type == SEMICOLON) {
                    next();
                    continue;
                }
                if (type == RBRACE) {
                    break;
                }
                final long classElementToken = token;
                boolean isStatic = false;
                if (type == STATIC) {
                    isStatic = true;
                    next();
                }
                boolean generator = false;
                if (isES6() && type == MUL) {
                    generator = true;
                    next();
                }
                final PropertyNode classElement = methodDefinition(isStatic, classHeritage != null, generator);
                if (classElement.isComputed()) {
                    classElements.add(classElement);
                } else if (!classElement.isStatic() && classElement.getKeyName().equals("constructor")) {
                    if (constructor == null) {
                        constructor = classElement;
                    } else {
                        throw error(AbstractParser.message("multiple.constructors"), classElementToken);
                    }
                } else {
                    // Check for duplicate method definitions and combine accessor methods.
                    // In ES6, a duplicate is never an error regardless of strict mode (in consequence of computed property names).

                    final ClassElementKey key = new ClassElementKey(classElement.isStatic(), classElement.getKeyName());
                    final Integer existing = keyToIndexMap.get(key);

                    if (existing == null) {
                        keyToIndexMap.put(key, classElements.size());
                        classElements.add(classElement);
                    } else {
                        final PropertyNode existingProperty = classElements.get(existing);

                        final Expression   value  = classElement.getValue();
                        final FunctionNode getter = classElement.getGetter();
                        final FunctionNode setter = classElement.getSetter();

                        if (value != null || existingProperty.getValue() != null) {
                            keyToIndexMap.put(key, classElements.size());
                            classElements.add(classElement);
                        } else if (getter != null) {
                            assert existingProperty.getGetter() != null || existingProperty.getSetter() != null;
                            classElements.set(existing, existingProperty.setGetter(getter));
                        } else if (setter != null) {
                            assert existingProperty.getGetter() != null || existingProperty.getSetter() != null;
                            classElements.set(existing, existingProperty.setSetter(setter));
                        }
                    }
                }
            }

            final long lastToken = token;
            expect(RBRACE);

            if (constructor == null) {
                constructor = createDefaultClassConstructor(classLineNumber, classToken, lastToken, className, classHeritage != null);
            }

            classElements.trimToSize();
            return new ClassNode(classLineNumber, classToken, finish, className, classHeritage, constructor, classElements);
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    private PropertyNode createDefaultClassConstructor(int classLineNumber, long classToken, long lastToken, IdentNode className, boolean subclass) {
        final int ctorFinish = finish;
        final List<Statement> statements;
        final List<IdentNode> parameters;
        final long identToken = Token.recast(classToken, TokenType.IDENT);
        if (subclass) {
            final IdentNode superIdent = createIdentNode(identToken, ctorFinish, SUPER.getName()).setIsDirectSuper();
            final IdentNode argsIdent = createIdentNode(identToken, ctorFinish, "args").setIsRestParameter();
            final Expression spreadArgs = new UnaryNode(Token.recast(classToken, TokenType.SPREAD_ARGUMENT), argsIdent);
            final CallNode superCall = new CallNode(classLineNumber, classToken, ctorFinish, superIdent, Collections.singletonList(spreadArgs), false);
            statements = Collections.singletonList(new ExpressionStatement(classLineNumber, classToken, ctorFinish, superCall));
            parameters = Collections.singletonList(argsIdent);
        } else {
            statements = Collections.emptyList();
            parameters = Collections.emptyList();
        }

        final Block body = new Block(classToken, ctorFinish, Block.IS_BODY, statements);
        final IdentNode ctorName = className != null ? className : createIdentNode(identToken, ctorFinish, "constructor");
        final ParserContextFunctionNode function = createParserContextFunctionNode(ctorName, classToken, FunctionNode.Kind.NORMAL, classLineNumber, parameters);
        function.setLastToken(lastToken);

        function.setFlag(FunctionNode.ES6_IS_METHOD);
        function.setFlag(FunctionNode.ES6_IS_CLASS_CONSTRUCTOR);
        if (subclass) {
            function.setFlag(FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR);
            function.setFlag(FunctionNode.ES6_HAS_DIRECT_SUPER);
        }
        if (className == null) {
            function.setFlag(FunctionNode.IS_ANONYMOUS);
        }

        final PropertyNode constructor = new PropertyNode(classToken, ctorFinish, ctorName, createFunctionNode(
                        function,
                        classToken,
                        ctorName,
                        parameters,
                        FunctionNode.Kind.NORMAL,
                        classLineNumber,
                        body
                        ), null, null, false, false);
        return constructor;
    }

    private PropertyNode methodDefinition(final boolean isStatic, final boolean subclass, final boolean generator) {
        final long methodToken = token;
        final int methodLine = line;
        final boolean computed = type == LBRACKET;
        final boolean isIdent = type == IDENT;
        final Expression propertyName = propertyName();
        int flags = FunctionNode.ES6_IS_METHOD;
        if (!computed) {
            final String name = ((PropertyKey)propertyName).getPropertyName();
            if (!generator && isIdent && type != LPAREN && name.equals("get")) {
                final PropertyFunction methodDefinition = propertyGetterFunction(methodToken, methodLine, flags);
                verifyAllowedMethodName(methodDefinition.key, isStatic, methodDefinition.computed, generator, true);
                return new PropertyNode(methodToken, finish, methodDefinition.key, null, methodDefinition.functionNode, null, isStatic, methodDefinition.computed);
            } else if (!generator && isIdent && type != LPAREN && name.equals("set")) {
                final PropertyFunction methodDefinition = propertySetterFunction(methodToken, methodLine, flags);
                verifyAllowedMethodName(methodDefinition.key, isStatic, methodDefinition.computed, generator, true);
                return new PropertyNode(methodToken, finish, methodDefinition.key, null, null, methodDefinition.functionNode, isStatic, methodDefinition.computed);
            } else {
                if (!isStatic && !generator && name.equals("constructor")) {
                    flags |= FunctionNode.ES6_IS_CLASS_CONSTRUCTOR;
                    if (subclass) {
                        flags |= FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR;
                    }
                }
                verifyAllowedMethodName(propertyName, isStatic, computed, generator, false);
            }
        }
        final PropertyFunction methodDefinition = propertyMethodFunction(propertyName, methodToken, methodLine, generator, flags, computed);
        return new PropertyNode(methodToken, finish, methodDefinition.key, methodDefinition.functionNode, null, null, isStatic, computed);
    }

    /**
     * ES6 14.5.1 Static Semantics: Early Errors.
     */
    private void verifyAllowedMethodName(final Expression key, final boolean isStatic, final boolean computed, final boolean generator, final boolean accessor) {
        if (!computed) {
            if (!isStatic && generator && ((PropertyKey) key).getPropertyName().equals("constructor")) {
                throw error(AbstractParser.message("generator.constructor"), key.getToken());
            }
            if (!isStatic && accessor && ((PropertyKey) key).getPropertyName().equals("constructor")) {
                throw error(AbstractParser.message("accessor.constructor"), key.getToken());
            }
            if (isStatic && ((PropertyKey) key).getPropertyName().equals("prototype")) {
                throw error(AbstractParser.message("static.prototype.method"), key.getToken());
            }
        }
    }

    /**
     * block :
     *      { StatementList? }
     *
     * see 12.1
     *
     * Parse a statement block.
     */
    private void block() {
        appendStatement(new BlockStatement(line, getBlock(true)));
    }

    /**
     * StatementList :
     *      Statement
     *      StatementList Statement
     *
     * See 12.1
     *
     * Parse a list of statements.
     */
    private void statementList() {
        // Accumulate statements until end of list. */
        loop:
        while (type != EOF) {
            switch (type) {
            case EOF:
            case CASE:
            case DEFAULT:
            case RBRACE:
                break loop;
            default:
                break;
            }

            // Get next statement.
            statement();
        }
    }

    /**
     * Make sure that the identifier name used is allowed.
     *
     * @param ident         Identifier that is verified
     * @param contextString String used in error message to give context to the user
     */
    private void verifyIdent(final IdentNode ident, final String contextString) {
        verifyStrictIdent(ident, contextString);
        if (isES6()) {
            final TokenType tokenType = TokenLookup.lookupKeyword(ident.getName().toCharArray(), 0, ident.getName().length());
            if (tokenType != IDENT && tokenType.getKind() != TokenKind.FUTURESTRICT) {
                throw error(expectMessage(IDENT));
            }
        }
    }

    /**
     * Make sure that in strict mode, the identifier name used is allowed.
     *
     * @param ident         Identifier that is verified
     * @param contextString String used in error message to give context to the user
     */
    private void verifyStrictIdent(final IdentNode ident, final String contextString) {
        if (isStrictMode) {
            switch (ident.getName()) {
            case "eval":
            case "arguments":
                throw error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
            default:
                break;
            }

            if (ident.isFutureStrictName()) {
                throw error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
            }
        }
    }

    /*
     * VariableStatement :
     *      var VariableDeclarationList ;
     *
     * VariableDeclarationList :
     *      VariableDeclaration
     *      VariableDeclarationList , VariableDeclaration
     *
     * VariableDeclaration :
     *      Identifier Initializer?
     *
     * Initializer :
     *      = AssignmentExpression
     *
     * See 12.2
     *
     * Parse a VAR statement.
     * @param isStatement True if a statement (not used in a FOR.)
     */
    private void variableStatement(final TokenType varType) {
        variableDeclarationList(varType, true, -1);
    }

    private List<Expression> variableDeclarationList(final TokenType varType, final boolean isStatement, final int sourceOrder) {
        // VAR tested in caller.
        assert varType == VAR || varType == LET || varType == CONST;
        next();

        final List<Expression> bindings = new ArrayList<>();
        int varFlags = 0;
        if (varType == LET) {
            varFlags |= VarNode.IS_LET;
        } else if (varType == CONST) {
            varFlags |= VarNode.IS_CONST;
        }

        Expression missingAssignment = null;
        while (true) {
            // Get starting token.
            final int  varLine  = line;
            final long varToken = token;
            // Get name of var.
            if (type == YIELD && inGeneratorFunction()) {
                expect(IDENT);
            }

            final String contextString = "variable name";
            Expression binding = bindingIdentifierOrPattern(contextString);
            final boolean isDestructuring = !(binding instanceof IdentNode);
            if (isDestructuring) {
                final int finalVarFlags = varFlags;
                verifyDestructuringBindingPattern(binding, new Consumer<IdentNode>() {
                    public void accept(final IdentNode identNode) {
                        verifyIdent(identNode, contextString);
                        final VarNode var = new VarNode(varLine, varToken, sourceOrder, identNode.getFinish(), identNode.setIsDeclaredHere(), null, finalVarFlags);
                        appendStatement(var);
                    }
                });
            }

            // Assume no init.
            Expression init = null;

            // Look for initializer assignment.
            if (type == ASSIGN) {
                next();

                // Get initializer expression. Suppress IN if not statement.
                if (!isDestructuring) {
                    defaultNames.push(binding);
                }
                try {
                    init = assignmentExpression(!isStatement);
                } finally {
                    if (!isDestructuring) {
                        defaultNames.pop();
                    }
                }
            } else if (isStatement) {
                if (isDestructuring) {
                    throw error(AbstractParser.message("missing.destructuring.assignment"), token);
                } else if (varType == CONST) {
                    throw error(AbstractParser.message("missing.const.assignment", ((IdentNode)binding).getName()));
                }
                // else, if we are in a for loop, delay checking until we know the kind of loop
            }

            if (!isDestructuring) {
                assert init != null || varType != CONST || !isStatement;
                final IdentNode ident = (IdentNode)binding;
                if (!isStatement && ident.getName().equals("let")) {
                    throw error(AbstractParser.message("let.binding.for")); //ES6 13.7.5.1
                }
                // Only set declaration flag on lexically scoped let/const as it adds runtime overhead.
                final IdentNode name = varType == LET || varType == CONST ? ident.setIsDeclaredHere() : ident;
                binding = name;
                final VarNode var = new VarNode(varLine, varToken, sourceOrder, finish, name, init, varFlags);
                appendStatement(var);
                if (init == null && varType == CONST) {
                    if (missingAssignment == null) {
                        missingAssignment = binding;
                    }
                }
            } else {
                assert init != null || !isStatement;
                binding = init == null ? binding : verifyAssignment(Token.recast(varToken, ASSIGN), binding, init);
                if (isStatement) {
                    appendStatement(new ExpressionStatement(varLine, binding.getToken(), finish, binding));
                } else if (init == null) {
                    if (missingAssignment == null) {
                        missingAssignment = binding;
                    }
                }
            }
            bindings.add(binding);

            if (type != COMMARIGHT) {
                break;
            }
            next();
        }

        // If is a statement then handle end of line.
        if (isStatement) {
            endOfLine();
        } else {
            if (type == SEMICOLON) {
                // late check for missing assignment, now we know it's a for (init; test; modify) loop
                if (missingAssignment != null) {
                    if (missingAssignment instanceof IdentNode) {
                        throw error(AbstractParser.message("missing.const.assignment", ((IdentNode)missingAssignment).getName()));
                    } else {
                        throw error(AbstractParser.message("missing.destructuring.assignment"), missingAssignment.getToken());
                    }
                }
            }
        }

        return bindings;
    }

    private boolean isBindingIdentifier() {
        return type == IDENT || isNonStrictModeIdent();
    }

    private IdentNode bindingIdentifier(final String contextString) {
        final IdentNode name = getIdent();
        verifyIdent(name, contextString);
        return name;
    }

    private Expression bindingPattern() {
        if (type == LBRACKET) {
            return arrayLiteral();
        } else if (type == LBRACE) {
            return objectLiteral();
        } else {
            throw error(AbstractParser.message("expected.binding"));
        }
    }

    private Expression bindingIdentifierOrPattern(final String contextString) {
        if (isBindingIdentifier() || !isES6()) {
            return bindingIdentifier(contextString);
        } else {
            return bindingPattern();
        }
    }

    /**
     * Verify destructuring variable declaration binding pattern and extract bound variable declarations.
     */
    private void verifyDestructuringBindingPattern(final Expression pattern, final Consumer<IdentNode> identifierCallback) {
        assert pattern instanceof ObjectNode || pattern instanceof LiteralNode.ArrayLiteralNode;
        pattern.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
                if (literalNode.isArray()) {
                    boolean restElement = false;
                    for (final Expression element : literalNode.getElementExpressions()) {
                        if (restElement) {
                            throw error(String.format("Unexpected element after rest element"), element.getToken());
                        }
                        if (element != null) {
                            if (element.isTokenType(SPREAD_ARRAY)) {
                                restElement = true;
                                if (!(((UnaryNode) element).getExpression() instanceof IdentNode)) {
                                    throw error(String.format("Expected a valid binding identifier"), element.getToken());

                                }
                            }
                            element.accept(this);
                        }
                    }
                    return false;
                } else {
                    return enterDefault(literalNode);
                }
            }

            @Override
            public boolean enterObjectNode(final ObjectNode objectNode) {
                return true;
            }

            @Override
            public boolean enterPropertyNode(final PropertyNode propertyNode) {
                if (propertyNode.getValue() != null) {
                    propertyNode.getValue().accept(this);
                    return false;
                } else {
                    return enterDefault(propertyNode);
                }
            }

            @Override
            public boolean enterIdentNode(final IdentNode identNode) {
                identifierCallback.accept(identNode);
                return false;
            }

            @Override
            public boolean enterBinaryNode(final BinaryNode binaryNode) {
                if (binaryNode.isTokenType(ASSIGN)) {
                    binaryNode.lhs().accept(this);
                    // Initializer(rhs) can be any AssignmentExpression
                    return false;
                } else {
                    return enterDefault(binaryNode);
                }
            }

            @Override
            public boolean enterUnaryNode(final UnaryNode unaryNode) {
                if (unaryNode.isTokenType(SPREAD_ARRAY)) {
                    // rest element
                    return true;
                } else {
                    return enterDefault(unaryNode);
                }
            }

            @Override
            protected boolean enterDefault(final Node node) {
                throw error(String.format("unexpected node in BindingPattern: %s", node));
            }
        });
    }

    /**
     * EmptyStatement :
     *      ;
     *
     * See 12.3
     *
     * Parse an empty statement.
     */
    private void emptyStatement() {
        if (env._empty_statements) {
            appendStatement(new EmptyNode(line, token, Token.descPosition(token) + Token.descLength(token)));
        }

        // SEMICOLON checked in caller.
        next();
    }

    /**
     * ExpressionStatement :
     *      Expression ; // [lookahead ~( or  function )]
     *
     * See 12.4
     *
     * Parse an expression used in a statement block.
     */
    private void expressionStatement() {
        // Lookahead checked in caller.
        final int  expressionLine  = line;
        final long expressionToken = token;

        // Get expression and add as statement.
        final Expression expression = expression();

        ExpressionStatement expressionStatement = null;
        if (expression != null) {
            expressionStatement = new ExpressionStatement(expressionLine, expressionToken, finish, expression);
            appendStatement(expressionStatement);
        } else {
            expect(null);
        }

        endOfLine();
    }

    /**
     * IfStatement :
     *      if ( Expression ) Statement else Statement
     *      if ( Expression ) Statement
     *
     * See 12.5
     *
     * Parse an IF statement.
     */
    private void ifStatement() {
        // Capture IF token.
        final int  ifLine  = line;
        final long ifToken = token;
         // IF tested in caller.
        next();

        expect(LPAREN);
        final Expression test = expression();
        expect(RPAREN);
        final Block pass = getStatement();

        Block fail = null;
        if (type == ELSE) {
            next();
            fail = getStatement();
        }

        appendStatement(new IfNode(ifLine, ifToken, fail != null ? fail.getFinish() : pass.getFinish(), test, pass, fail));
    }

    /**
     * ... IterationStatement:
     *           ...
     *           for ( Expression[NoIn]?; Expression? ; Expression? ) Statement
     *           for ( var VariableDeclarationList[NoIn]; Expression? ; Expression? ) Statement
     *           for ( LeftHandSideExpression in Expression ) Statement
     *           for ( var VariableDeclaration[NoIn] in Expression ) Statement
     *
     * See 12.6
     *
     * Parse a FOR statement.
     */
    @SuppressWarnings("fallthrough")
    private void forStatement() {
        final long forToken = token;
        final int forLine = line;
        // start position of this for statement. This is used
        // for sort order for variables declared in the initializer
        // part of this 'for' statement (if any).
        final int forStart = Token.descPosition(forToken);
        // When ES6 for-let is enabled we create a container block to capture the LET.
        final ParserContextBlockNode outer = useBlockScope() ? newBlock() : null;

        // Create FOR node, capturing FOR token.
        final ParserContextLoopNode forNode = new ParserContextLoopNode();
        lc.push(forNode);
        Block body = null;
        List<Expression> vars = null;
        Expression init = null;
        JoinPredecessorExpression test = null;
        JoinPredecessorExpression modify = null;

        int flags = 0;
        boolean isForOf = false;

        try {
            // FOR tested in caller.
            next();

            // Nashorn extension: for each expression.
            // iterate property values rather than property names.
            if (!env._no_syntax_extensions && type == IDENT && "each".equals(getValue())) {
                flags |= ForNode.IS_FOR_EACH;
                next();
            }

            expect(LPAREN);

            switch (type) {
            case VAR:
                // Var declaration captured in for outer block.
                vars = variableDeclarationList(type, false, forStart);
                break;
            case SEMICOLON:
                break;
            default:
                if (useBlockScope() && (type == LET && lookaheadIsLetDeclaration(true) || type == CONST)) {
                    flags |= ForNode.PER_ITERATION_SCOPE;
                    // LET/CONST declaration captured in container block created above.
                    vars = variableDeclarationList(type, false, forStart);
                    break;
                }
                if (env._const_as_var && type == CONST) {
                    // Var declaration captured in for outer block.
                    vars = variableDeclarationList(TokenType.VAR, false, forStart);
                    break;
                }

                init = expression(unaryExpression(), COMMARIGHT.getPrecedence(), true);
                break;
            }

            switch (type) {
            case SEMICOLON:
                // for (init; test; modify)

                // for each (init; test; modify) is invalid
                if ((flags & ForNode.IS_FOR_EACH) != 0) {
                    throw error(AbstractParser.message("for.each.without.in"), token);
                }

                expect(SEMICOLON);
                if (type != SEMICOLON) {
                    test = joinPredecessorExpression();
                }
                expect(SEMICOLON);
                if (type != RPAREN) {
                    modify = joinPredecessorExpression();
                }
                break;

            case IDENT:
                if (env._es6 && "of".equals(getValue())) {
                    isForOf = true;
                    // fall through
                } else {
                    expect(SEMICOLON); // fail with expected message
                    break;
                }
            case IN:

                flags |= isForOf ? ForNode.IS_FOR_OF : ForNode.IS_FOR_IN;
                test = new JoinPredecessorExpression();
                if (vars != null) {
                    // for (var i in obj)
                    if (vars.size() == 1) {
                        init = new IdentNode((IdentNode)vars.get(0));
                        if (init.isTokenType(ASSIGN)) {
                            throw error(AbstractParser.message("for.in.loop.initializer"), init.getToken());
                        }
                        assert init instanceof IdentNode || isDestructuringLhs(init);
                    } else {
                        // for (var i, j in obj) is invalid
                        throw error(AbstractParser.message("many.vars.in.for.in.loop", isForOf ? "of" : "in"), vars.get(1).getToken());
                    }
                } else {
                    // for (expr in obj)
                    assert init != null : "for..in/of init expression can not be null here";

                    // check if initial expression is a valid L-value
                    if (!(init instanceof AccessNode ||
                          init instanceof IndexNode ||
                          init instanceof IdentNode)) {
                        throw error(AbstractParser.message("not.lvalue.for.in.loop", isForOf ? "of" : "in"), init.getToken());
                    }

                    if (init instanceof IdentNode) {
                        if (!checkIdentLValue((IdentNode)init)) {
                            throw error(AbstractParser.message("not.lvalue.for.in.loop", isForOf ? "of" : "in"), init.getToken());
                        }
                        verifyStrictIdent((IdentNode)init, isForOf ? "for-of iterator" : "for-in iterator");
                    }
                }

                next();

                // For-of only allows AssignmentExpression.
                modify = isForOf ? new JoinPredecessorExpression(assignmentExpression(false)) : joinPredecessorExpression();
                break;

            default:
                expect(SEMICOLON);
                break;
            }

            expect(RPAREN);

            // Set the for body.
            body = getStatement();
        } finally {
            lc.pop(forNode);

            for (final Statement var : forNode.getStatements()) {
                assert var instanceof VarNode;
                appendStatement(var);
            }
            if (body != null) {
                appendStatement(new ForNode(forLine, forToken, body.getFinish(), body, (forNode.getFlags() | flags), init, test, modify));
            }
            if (outer != null) {
                restoreBlock(outer);
                if (body != null) {
                    appendStatement(new BlockStatement(forLine, new Block(
                                    outer.getToken(),
                                    body.getFinish(),
                                    outer.getStatements())));
                }
            }
        }
    }

    private boolean checkValidLValue(final Expression init, final String contextString) {
        if (init instanceof IdentNode) {
            if (!checkIdentLValue((IdentNode)init)) {
                return false;
            }
            verifyIdent((IdentNode)init, contextString);
            return true;
        } else if (init instanceof AccessNode || init instanceof IndexNode) {
            return true;
        } else if (isDestructuringLhs(init)) {
            verifyDestructuringAssignmentPattern(init, contextString);
            return true;
        } else {
            return false;
        }
    }

    private boolean lookaheadIsLetDeclaration(final boolean ofContextualKeyword) {
        assert type == LET;
        for (int i = 1;; i++) {
            TokenType t = T(k + i);
            switch (t) {
            case EOL:
            case COMMENT:
                continue;
            case IDENT:
                if (ofContextualKeyword && isES6() && "of".equals(getValue(getToken(k + i)))) {
                    return false;
                }
                // fall through
            case LBRACKET:
            case LBRACE:
                return true;
            default:
                // accept future strict tokens in non-strict mode (including LET)
                if (!isStrictMode && t.getKind() == TokenKind.FUTURESTRICT) {
                    return true;
                }
                return false;
            }
        }
    }

    /**
     * ...IterationStatement :
     *           ...
     *           while ( Expression ) Statement
     *           ...
     *
     * See 12.6
     *
     * Parse while statement.
     */
    private void whileStatement() {
        // Capture WHILE token.
        final long whileToken = token;
        final int whileLine = line;
        // WHILE tested in caller.
        next();

        final ParserContextLoopNode whileNode = new ParserContextLoopNode();
        lc.push(whileNode);

        JoinPredecessorExpression test = null;
        Block body = null;

        try {
            expect(LPAREN);
            test = joinPredecessorExpression();
            expect(RPAREN);
            body = getStatement();
        } finally {
            lc.pop(whileNode);
        }

        if (body != null) {
            appendStatement(new WhileNode(whileLine, whileToken, body.getFinish(), false, test, body));
        }
    }

    /**
     * ...IterationStatement :
     *           ...
     *           do Statement while( Expression ) ;
     *           ...
     *
     * See 12.6
     *
     * Parse DO WHILE statement.
     */
    private void doStatement() {
        // Capture DO token.
        final long doToken = token;
        int doLine = 0;
        // DO tested in the caller.
        next();

        final ParserContextLoopNode doWhileNode = new ParserContextLoopNode();
        lc.push(doWhileNode);

        Block body = null;
        JoinPredecessorExpression test = null;

        try {
           // Get DO body.
            body = getStatement();

            expect(WHILE);
            expect(LPAREN);
            doLine = line;
            test = joinPredecessorExpression();
            expect(RPAREN);

            if (type == SEMICOLON) {
                endOfLine();
            }
        } finally {
            lc.pop(doWhileNode);
        }

        appendStatement(new WhileNode(doLine, doToken, finish, true, test, body));
    }

    /**
     * ContinueStatement :
     *      continue Identifier? ; // [no LineTerminator here]
     *
     * See 12.7
     *
     * Parse CONTINUE statement.
     */
    private void continueStatement() {
        // Capture CONTINUE token.
        final int  continueLine  = line;
        final long continueToken = token;
        // CONTINUE tested in caller.
        nextOrEOL();

        ParserContextLabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = lc.findLabel(ident.getName());

            if (labelNode == null) {
                throw error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        final String labelName = labelNode == null ? null : labelNode.getLabelName();
        final ParserContextLoopNode targetNode = lc.getContinueTo(labelName);

        if (targetNode == null) {
            throw error(AbstractParser.message("illegal.continue.stmt"), continueToken);
        }

        endOfLine();

        // Construct and add CONTINUE node.
        appendStatement(new ContinueNode(continueLine, continueToken, finish, labelName));
    }

    /**
     * BreakStatement :
     *      break Identifier? ; // [no LineTerminator here]
     *
     * See 12.8
     *
     */
    private void breakStatement() {
        // Capture BREAK token.
        final int  breakLine  = line;
        final long breakToken = token;
        // BREAK tested in caller.
        nextOrEOL();

        ParserContextLabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = lc.findLabel(ident.getName());

            if (labelNode == null) {
                throw error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        //either an explicit label - then get its node or just a "break" - get first breakable
        //targetNode is what we are breaking out from.
        final String labelName = labelNode == null ? null : labelNode.getLabelName();
        final ParserContextBreakableNode targetNode = lc.getBreakable(labelName);
        if (targetNode == null) {
            throw error(AbstractParser.message("illegal.break.stmt"), breakToken);
        }

        endOfLine();

        // Construct and add BREAK node.
        appendStatement(new BreakNode(breakLine, breakToken, finish, labelName));
    }

    /**
     * ReturnStatement :
     *      return Expression? ; // [no LineTerminator here]
     *
     * See 12.9
     *
     * Parse RETURN statement.
     */
    private void returnStatement() {
        // check for return outside function
        if (lc.getCurrentFunction().getKind() == FunctionNode.Kind.SCRIPT || lc.getCurrentFunction().getKind() == FunctionNode.Kind.MODULE) {
            throw error(AbstractParser.message("invalid.return"));
        }

        // Capture RETURN token.
        final int  returnLine  = line;
        final long returnToken = token;
        // RETURN tested in caller.
        nextOrEOL();

        Expression expression = null;

        // SEMICOLON or expression.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            expression = expression();
            break;
        }

        endOfLine();

        // Construct and add RETURN node.
        appendStatement(new ReturnNode(returnLine, returnToken, finish, expression));
    }

    /**
     * Parse YieldExpression.
     *
     * YieldExpression[In] :
     *   yield
     *   yield [no LineTerminator here] AssignmentExpression[?In, Yield]
     *   yield [no LineTerminator here] * AssignmentExpression[?In, Yield]
     */
    private Expression yieldExpression(final boolean noIn) {
        assert inGeneratorFunction();
        // Capture YIELD token.
        long yieldToken = token;
        // YIELD tested in caller.
        assert type == YIELD;
        nextOrEOL();

        Expression expression = null;

        boolean yieldAsterisk = false;
        if (type == MUL) {
            yieldAsterisk = true;
            yieldToken = Token.recast(yieldToken, YIELD_STAR);
            next();
        }

        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
        case COMMARIGHT:
        case RPAREN:
        case RBRACKET:
        case COLON:
            if (!yieldAsterisk) {
                // treat (yield) as (yield void 0)
                expression = newUndefinedLiteral(yieldToken, finish);
                if (type == EOL) {
                    next();
                }
                break;
            } else {
                // AssignmentExpression required, fall through
            }

        default:
            expression = assignmentExpression(noIn);
            break;
        }

        // Construct and add YIELD node.
        return new UnaryNode(yieldToken, expression);
    }

    private static UnaryNode newUndefinedLiteral(final long token, final int finish) {
        return new UnaryNode(Token.recast(token, VOID), LiteralNode.newInstance(token, finish, 0));
    }

    /**
     * WithStatement :
     *      with ( Expression ) Statement
     *
     * See 12.10
     *
     * Parse WITH statement.
     */
    private void withStatement() {
        // Capture WITH token.
        final int  withLine  = line;
        final long withToken = token;
        // WITH tested in caller.
        next();

        // ECMA 12.10.1 strict mode restrictions
        if (isStrictMode) {
            throw error(AbstractParser.message("strict.no.with"), withToken);
        }

        expect(LPAREN);
        final Expression expression = expression();
        expect(RPAREN);
        final Block body = getStatement();

        appendStatement(new WithNode(withLine, withToken, finish, expression, body));
    }

    /**
     * SwitchStatement :
     *      switch ( Expression ) CaseBlock
     *
     * CaseBlock :
     *      { CaseClauses? }
     *      { CaseClauses? DefaultClause CaseClauses }
     *
     * CaseClauses :
     *      CaseClause
     *      CaseClauses CaseClause
     *
     * CaseClause :
     *      case Expression : StatementList?
     *
     * DefaultClause :
     *      default : StatementList?
     *
     * See 12.11
     *
     * Parse SWITCH statement.
     */
    private void switchStatement() {
        final int  switchLine  = line;
        final long switchToken = token;

        // Block to capture variables declared inside the switch statement.
        final ParserContextBlockNode switchBlock = newBlock();

        // SWITCH tested in caller.
        next();

        // Create and add switch statement.
        final ParserContextSwitchNode switchNode = new ParserContextSwitchNode();
        lc.push(switchNode);

        CaseNode defaultCase = null;
        // Prepare to accumulate cases.
        final List<CaseNode> cases = new ArrayList<>();

        Expression expression = null;

        try {
            expect(LPAREN);
            expression = expression();
            expect(RPAREN);

            expect(LBRACE);


            while (type != RBRACE) {
                // Prepare for next case.
                Expression caseExpression = null;
                final long caseToken = token;

                switch (type) {
                case CASE:
                    next();
                    caseExpression = expression();
                    break;

                case DEFAULT:
                    if (defaultCase != null) {
                        throw error(AbstractParser.message("duplicate.default.in.switch"));
                    }
                    next();
                    break;

                default:
                    // Force an error.
                    expect(CASE);
                    break;
                }

                expect(COLON);

                // Get CASE body.
                final Block statements = getBlock(false); // TODO: List<Statement> statements = caseStatementList();
                final CaseNode caseNode = new CaseNode(caseToken, finish, caseExpression, statements);

                if (caseExpression == null) {
                    defaultCase = caseNode;
                }

                cases.add(caseNode);
            }

            next();
        } finally {
            lc.pop(switchNode);
            restoreBlock(switchBlock);
        }

        final SwitchNode switchStatement = new SwitchNode(switchLine, switchToken, finish, expression, cases, defaultCase);
        appendStatement(new BlockStatement(switchLine, new Block(switchToken, finish, switchBlock.getFlags() | Block.IS_SYNTHETIC | Block.IS_SWITCH_BLOCK, switchStatement)));
    }

    /**
     * LabelledStatement :
     *      Identifier : Statement
     *
     * See 12.12
     *
     * Parse label statement.
     */
    private void labelStatement() {
        // Capture label token.
        final long labelToken = token;
        // Get label ident.
        final IdentNode ident = getIdent();

        expect(COLON);

        if (lc.findLabel(ident.getName()) != null) {
            throw error(AbstractParser.message("duplicate.label", ident.getName()), labelToken);
        }

        final ParserContextLabelNode labelNode = new ParserContextLabelNode(ident.getName());
        Block body = null;
        try {
            lc.push(labelNode);
            body = getStatement(true);
        } finally {
            assert lc.peek() instanceof ParserContextLabelNode;
            lc.pop(labelNode);
        }

        appendStatement(new LabelNode(line, labelToken, finish, ident.getName(), body));
    }

    /**
     * ThrowStatement :
     *      throw Expression ; // [no LineTerminator here]
     *
     * See 12.13
     *
     * Parse throw statement.
     */
    private void throwStatement() {
        // Capture THROW token.
        final int  throwLine  = line;
        final long throwToken = token;
        // THROW tested in caller.
        nextOrEOL();

        Expression expression = null;

        // SEMICOLON or expression.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
            break;

        default:
            expression = expression();
            break;
        }

        if (expression == null) {
            throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        endOfLine();

        appendStatement(new ThrowNode(throwLine, throwToken, finish, expression, false));
    }

    /**
     * TryStatement :
     *      try Block Catch
     *      try Block Finally
     *      try Block Catch Finally
     *
     * Catch :
     *      catch( Identifier if Expression ) Block
     *      catch( Identifier ) Block
     *
     * Finally :
     *      finally Block
     *
     * See 12.14
     *
     * Parse TRY statement.
     */
    private void tryStatement() {
        // Capture TRY token.
        final int  tryLine  = line;
        final long tryToken = token;
        // TRY tested in caller.
        next();

        // Container block needed to act as target for labeled break statements
        final int startLine = line;
        final ParserContextBlockNode outer = newBlock();
        // Create try.

        try {
            final Block       tryBody     = getBlock(true);
            final List<Block> catchBlocks = new ArrayList<>();

            while (type == CATCH) {
                final int  catchLine  = line;
                final long catchToken = token;
                next();
                expect(LPAREN);
                final IdentNode exception = getIdent();

                // ECMA 12.4.1 strict mode restrictions
                verifyStrictIdent(exception, "catch argument");

                // Nashorn extension: catch clause can have optional
                // condition. So, a single try can have more than one
                // catch clause each with it's own condition.
                final Expression ifExpression;
                if (!env._no_syntax_extensions && type == IF) {
                    next();
                    // Get the exception condition.
                    ifExpression = expression();
                } else {
                    ifExpression = null;
                }

                expect(RPAREN);

                final ParserContextBlockNode catchBlock = newBlock();
                try {
                    // Get CATCH body.
                    final Block catchBody = getBlock(true);
                    final CatchNode catchNode = new CatchNode(catchLine, catchToken, finish, exception, ifExpression, catchBody, false);
                    appendStatement(catchNode);
                } finally {
                    restoreBlock(catchBlock);
                    catchBlocks.add(new Block(catchBlock.getToken(), finish, catchBlock.getFlags() | Block.IS_SYNTHETIC, catchBlock.getStatements()));
                }

                // If unconditional catch then should to be the end.
                if (ifExpression == null) {
                    break;
                }
            }

            // Prepare to capture finally statement.
            Block finallyStatements = null;

            if (type == FINALLY) {
                next();
                finallyStatements = getBlock(true);
            }

            // Need at least one catch or a finally.
            if (catchBlocks.isEmpty() && finallyStatements == null) {
                throw error(AbstractParser.message("missing.catch.or.finally"), tryToken);
            }

            final TryNode tryNode = new TryNode(tryLine, tryToken, finish, tryBody, catchBlocks, finallyStatements);
            // Add try.
            assert lc.peek() == outer;
            appendStatement(tryNode);
        } finally {
            restoreBlock(outer);
        }

        appendStatement(new BlockStatement(startLine, new Block(tryToken, finish, outer.getFlags() | Block.IS_SYNTHETIC, outer.getStatements())));
    }

    /**
     * DebuggerStatement :
     *      debugger ;
     *
     * See 12.15
     *
     * Parse debugger statement.
     */
    private void  debuggerStatement() {
        // Capture DEBUGGER token.
        final int  debuggerLine  = line;
        final long debuggerToken = token;
        // DEBUGGER tested in caller.
        next();
        endOfLine();
        appendStatement(new DebuggerNode(debuggerLine, debuggerToken, finish));
    }

    /**
     * PrimaryExpression :
     *      this
     *      IdentifierReference
     *      Literal
     *      ArrayLiteral
     *      ObjectLiteral
     *      RegularExpressionLiteral
     *      TemplateLiteral
     *      CoverParenthesizedExpressionAndArrowParameterList
     *
     * CoverParenthesizedExpressionAndArrowParameterList :
     *      ( Expression )
     *      ( )
     *      ( ... BindingIdentifier )
     *      ( Expression , ... BindingIdentifier )
     *
     * Parse primary expression.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private Expression primaryExpression() {
        // Capture first token.
        final int  primaryLine  = line;
        final long primaryToken = token;

        switch (type) {
        case THIS:
            final String name = type.getName();
            next();
            markThis(lc);
            return new IdentNode(primaryToken, finish, name);
        case IDENT:
            final IdentNode ident = getIdent();
            if (ident == null) {
                break;
            }
            detectSpecialProperty(ident);
            return ident;
        case OCTAL_LEGACY:
            if (isStrictMode) {
               throw error(AbstractParser.message("strict.no.octal"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case OCTAL:
        case BINARY_NUMBER:
        case FLOATING:
        case REGEX:
        case XML:
            return getLiteral();
        case EXECSTRING:
            return execString(primaryLine, primaryToken);
        case FALSE:
            next();
            return LiteralNode.newInstance(primaryToken, finish, false);
        case TRUE:
            next();
            return LiteralNode.newInstance(primaryToken, finish, true);
        case NULL:
            next();
            return LiteralNode.newInstance(primaryToken, finish);
        case LBRACKET:
            return arrayLiteral();
        case LBRACE:
            return objectLiteral();
        case LPAREN:
            next();

            if (isES6()) {
                if (type == RPAREN) {
                    // ()
                    nextOrEOL();
                    expectDontAdvance(ARROW);
                    return new ExpressionList(primaryToken, finish, Collections.emptyList());
                } else if (type == ELLIPSIS) {
                    // (...rest)
                    final IdentNode restParam = formalParameterList(false).get(0);
                    expectDontAdvance(RPAREN);
                    nextOrEOL();
                    expectDontAdvance(ARROW);
                    return new ExpressionList(primaryToken, finish, Collections.singletonList(restParam));
                }
            }

            final Expression expression = expression();

            expect(RPAREN);

            return expression;
        case TEMPLATE:
        case TEMPLATE_HEAD:
            return templateLiteral();

        default:
            // In this context some operator tokens mark the start of a literal.
            if (lexer.scanLiteral(primaryToken, type, lineInfoReceiver)) {
                next();
                return getLiteral();
            }
            if (isNonStrictModeIdent()) {
                return getIdent();
            }
            break;
        }

        return null;
    }

    /**
     * Convert execString to a call to $EXEC.
     *
     * @param primaryToken Original string token.
     * @return callNode to $EXEC.
     */
    CallNode execString(final int primaryLine, final long primaryToken) {
        // Synthesize an ident to call $EXEC.
        final IdentNode execIdent = new IdentNode(primaryToken, finish, ScriptingFunctions.EXEC_NAME);
        // Skip over EXECSTRING.
        next();
        // Set up argument list for call.
        // Skip beginning of edit string expression.
        expect(LBRACE);
        // Add the following expression to arguments.
        final List<Expression> arguments = Collections.singletonList(expression());
        // Skip ending of edit string expression.
        expect(RBRACE);

        return new CallNode(primaryLine, primaryToken, finish, execIdent, arguments, false);
    }

    /**
     * ArrayLiteral :
     *      [ Elision? ]
     *      [ ElementList ]
     *      [ ElementList , Elision? ]
     *      [ expression for (LeftHandExpression in expression) ( (if ( Expression ) )? ]
     *
     * ElementList : Elision? AssignmentExpression
     *      ElementList , Elision? AssignmentExpression
     *
     * Elision :
     *      ,
     *      Elision ,
     *
     * See 12.1.4
     * JavaScript 1.8
     *
     * Parse array literal.
     * @return Expression node.
     */
    private LiteralNode<Expression[]> arrayLiteral() {
        // Capture LBRACKET token.
        final long arrayToken = token;
        // LBRACKET tested in caller.
        next();

        // Prepare to accumulate elements.
        final List<Expression> elements = new ArrayList<>();
        // Track elisions.
        boolean elision = true;
        loop:
        while (true) {
            long spreadToken = 0;
            switch (type) {
            case RBRACKET:
                next();

                break loop;

            case COMMARIGHT:
                next();

                // If no prior expression
                if (elision) {
                    elements.add(null);
                }

                elision = true;

                break;

            case ELLIPSIS:
                if (isES6()) {
                    spreadToken = token;
                    next();
                }
                // fall through

            default:
                if (!elision) {
                    throw error(AbstractParser.message("expected.comma", type.getNameOrType()));
                }

                // Add expression element.
                Expression expression = assignmentExpression(false);
                if (expression != null) {
                    if (spreadToken != 0) {
                        expression = new UnaryNode(Token.recast(spreadToken, SPREAD_ARRAY), expression);
                    }
                    elements.add(expression);
                } else {
                    expect(RBRACKET);
                }

                elision = false;
                break;
            }
        }

        return LiteralNode.newInstance(arrayToken, finish, elements);
    }

    /**
     * ObjectLiteral :
     *      { }
     *      { PropertyNameAndValueList } { PropertyNameAndValueList , }
     *
     * PropertyNameAndValueList :
     *      PropertyAssignment
     *      PropertyNameAndValueList , PropertyAssignment
     *
     * See 11.1.5
     *
     * Parse an object literal.
     * @return Expression node.
     */
    private ObjectNode objectLiteral() {
        // Capture LBRACE token.
        final long objectToken = token;
        // LBRACE tested in caller.
        next();

        // Object context.
        // Prepare to accumulate elements.
        final List<PropertyNode> elements = new ArrayList<>();
        final Map<String, Integer> map = new HashMap<>();

        // Create a block for the object literal.
        boolean commaSeen = true;
        loop:
        while (true) {
            switch (type) {
                case RBRACE:
                    next();
                    break loop;

                case COMMARIGHT:
                    if (commaSeen) {
                        throw error(AbstractParser.message("expected.property.id", type.getNameOrType()));
                    }
                    next();
                    commaSeen = true;
                    break;

                default:
                    if (!commaSeen) {
                        throw error(AbstractParser.message("expected.comma", type.getNameOrType()));
                    }

                    commaSeen = false;
                    // Get and add the next property.
                    final PropertyNode property = propertyAssignment();

                    if (property.isComputed()) {
                        elements.add(property);
                        break;
                    }

                    final String key = property.getKeyName();
                    final Integer existing = map.get(key);

                    if (existing == null) {
                        map.put(key, elements.size());
                        elements.add(property);
                        break;
                    }

                    final PropertyNode existingProperty = elements.get(existing);

                    // ECMA section 11.1.5 Object Initialiser
                    // point # 4 on property assignment production
                    final Expression   value  = property.getValue();
                    final FunctionNode getter = property.getGetter();
                    final FunctionNode setter = property.getSetter();

                    final Expression   prevValue  = existingProperty.getValue();
                    final FunctionNode prevGetter = existingProperty.getGetter();
                    final FunctionNode prevSetter = existingProperty.getSetter();

                    if (!isES6()) {
                        checkPropertyRedefinition(property, value, getter, setter, prevValue, prevGetter, prevSetter);
                    } else {
                        if (property.getKey() instanceof IdentNode && ((IdentNode)property.getKey()).isProtoPropertyName() &&
                                        existingProperty.getKey() instanceof IdentNode && ((IdentNode)existingProperty.getKey()).isProtoPropertyName()) {
                            throw error(AbstractParser.message("multiple.proto.key"), property.getToken());
                        }
                    }

                    if (value != null || prevValue != null) {
                        map.put(key, elements.size());
                        elements.add(property);
                    } else if (getter != null) {
                        assert prevGetter != null || prevSetter != null;
                        elements.set(existing, existingProperty.setGetter(getter));
                    } else if (setter != null) {
                        assert prevGetter != null || prevSetter != null;
                        elements.set(existing, existingProperty.setSetter(setter));
                    }
                    break;
            }
        }

        return new ObjectNode(objectToken, finish, elements);
    }

    private void checkPropertyRedefinition(final PropertyNode property, final Expression value, final FunctionNode getter, final FunctionNode setter, final Expression prevValue, final FunctionNode prevGetter, final FunctionNode prevSetter) {
        // ECMA 11.1.5 strict mode restrictions
        if (isStrictMode && value != null && prevValue != null) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        final boolean isPrevAccessor = prevGetter != null || prevSetter != null;
        final boolean isAccessor     = getter != null     || setter != null;

        // data property redefined as accessor property
        if (prevValue != null && isAccessor) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        // accessor property redefined as data
        if (isPrevAccessor && value != null) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        if (isAccessor && isPrevAccessor) {
            if (getter != null && prevGetter != null ||
                    setter != null && prevSetter != null) {
                throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
            }
        }
    }

    /**
     * LiteralPropertyName :
     *      IdentifierName
     *      StringLiteral
     *      NumericLiteral
     *
     * @return PropertyName node
     */
    @SuppressWarnings("fallthrough")
    private PropertyKey literalPropertyName() {
        switch (type) {
        case IDENT:
            return getIdent().setIsPropertyName();
        case OCTAL_LEGACY:
            if (isStrictMode) {
                throw error(AbstractParser.message("strict.no.octal"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case OCTAL:
        case BINARY_NUMBER:
        case FLOATING:
            return getLiteral();
        default:
            return getIdentifierName().setIsPropertyName();
        }
    }

    /**
     * ComputedPropertyName :
     *      AssignmentExpression
     *
     * @return PropertyName node
     */
    private Expression computedPropertyName() {
        expect(LBRACKET);
        Expression expression = assignmentExpression(false);
        expect(RBRACKET);
        return expression;
    }

    /**
     * PropertyName :
     *      LiteralPropertyName
     *      ComputedPropertyName
     *
     * @return PropertyName node
     */
    private Expression propertyName() {
        if (type == LBRACKET && isES6()) {
            return computedPropertyName();
        } else {
            return (Expression)literalPropertyName();
        }
    }

    /**
     * PropertyAssignment :
     *      PropertyName : AssignmentExpression
     *      get PropertyName ( ) { FunctionBody }
     *      set PropertyName ( PropertySetParameterList ) { FunctionBody }
     *
     * PropertySetParameterList :
     *      Identifier
     *
     * PropertyName :
     *      IdentifierName
     *      StringLiteral
     *      NumericLiteral
     *
     * See 11.1.5
     *
     * Parse an object literal property.
     * @return Property or reference node.
     */
    private PropertyNode propertyAssignment() {
        // Capture firstToken.
        final long propertyToken = token;
        final int  functionLine  = line;

        final Expression propertyName;
        final boolean isIdentifier;

        boolean generator = false;
        if (type == MUL && isES6()) {
            generator = true;
            next();
        }

        final boolean computed = type == LBRACKET;
        if (type == IDENT) {
            // Get IDENT.
            final String ident = (String)expectValue(IDENT);

            if (type != COLON && (type != LPAREN || !isES6())) {
                final long getSetToken = propertyToken;

                switch (ident) {
                case "get":
                    final PropertyFunction getter = propertyGetterFunction(getSetToken, functionLine);
                    return new PropertyNode(propertyToken, finish, getter.key, null, getter.functionNode, null, false, getter.computed);

                case "set":
                    final PropertyFunction setter = propertySetterFunction(getSetToken, functionLine);
                    return new PropertyNode(propertyToken, finish, setter.key, null, null, setter.functionNode, false, setter.computed);
                default:
                    break;
                }
            }

            isIdentifier = true;
            IdentNode identNode = createIdentNode(propertyToken, finish, ident).setIsPropertyName();
            if (type == COLON && ident.equals("__proto__")) {
                identNode = identNode.setIsProtoPropertyName();
            }
            propertyName = identNode;
        } else {
            isIdentifier = isNonStrictModeIdent();
            propertyName = propertyName();
        }

        Expression propertyValue;

        if (generator) {
            expectDontAdvance(LPAREN);
        }

        if (type == LPAREN && isES6()) {
            propertyValue = propertyMethodFunction(propertyName, propertyToken, functionLine, generator, FunctionNode.ES6_IS_METHOD, computed).functionNode;
        } else if (isIdentifier && (type == COMMARIGHT || type == RBRACE || type == ASSIGN) && isES6()) {
            propertyValue = createIdentNode(propertyToken, finish, ((IdentNode) propertyName).getPropertyName());
            if (type == ASSIGN && isES6()) {
                // TODO if not destructuring, this is a SyntaxError
                final long assignToken = token;
                next();
                final Expression rhs = assignmentExpression(false);
                propertyValue = verifyAssignment(assignToken, propertyValue, rhs);
            }
        } else {
            expect(COLON);

            defaultNames.push(propertyName);
            try {
                propertyValue = assignmentExpression(false);
            } finally {
                defaultNames.pop();
            }
        }

        return new PropertyNode(propertyToken, finish, propertyName, propertyValue, null, null, false, computed);
    }

    private PropertyFunction propertyGetterFunction(final long getSetToken, final int functionLine) {
        return propertyGetterFunction(getSetToken, functionLine, FunctionNode.ES6_IS_METHOD);
    }

    private PropertyFunction propertyGetterFunction(final long getSetToken, final int functionLine, final int flags) {
        final boolean computed = type == LBRACKET;
        final Expression propertyName = propertyName();
        final String getterName = propertyName instanceof PropertyKey ? ((PropertyKey) propertyName).getPropertyName() : getDefaultValidFunctionName(functionLine, false);
        final IdentNode getNameNode = createIdentNode((propertyName).getToken(), finish, NameCodec.encode("get " + getterName));
        expect(LPAREN);
        expect(RPAREN);

        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(getNameNode, getSetToken, FunctionNode.Kind.GETTER, functionLine, Collections.<IdentNode>emptyList());
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        Block functionBody;


        try {
            functionBody = functionBody(functionNode);
        } finally {
            lc.pop(functionNode);
        }

        final FunctionNode  function = createFunctionNode(
                functionNode,
                getSetToken,
                getNameNode,
                Collections.<IdentNode>emptyList(),
                FunctionNode.Kind.GETTER,
                functionLine,
                functionBody);

        return new PropertyFunction(propertyName, function, computed);
    }

    private PropertyFunction propertySetterFunction(final long getSetToken, final int functionLine) {
        return propertySetterFunction(getSetToken, functionLine, FunctionNode.ES6_IS_METHOD);
    }

    private PropertyFunction propertySetterFunction(final long getSetToken, final int functionLine, final int flags) {
        final boolean computed = type == LBRACKET;
        final Expression propertyName = propertyName();
        final String setterName = propertyName instanceof PropertyKey ? ((PropertyKey) propertyName).getPropertyName() : getDefaultValidFunctionName(functionLine, false);
        final IdentNode setNameNode = createIdentNode((propertyName).getToken(), finish, NameCodec.encode("set " + setterName));
        expect(LPAREN);
        // be sloppy and allow missing setter parameter even though
        // spec does not permit it!
        final IdentNode argIdent;
        if (isBindingIdentifier()) {
            argIdent = getIdent();
            verifyIdent(argIdent, "setter argument");
        } else {
            argIdent = null;
        }
        expect(RPAREN);
        final List<IdentNode> parameters = new ArrayList<>();
        if (argIdent != null) {
            parameters.add(argIdent);
        }


        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(setNameNode, getSetToken, FunctionNode.Kind.SETTER, functionLine, parameters);
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        Block functionBody;
        try {
            functionBody = functionBody(functionNode);
        } finally {
            lc.pop(functionNode);
        }


        final FunctionNode  function = createFunctionNode(
                functionNode,
                getSetToken,
                setNameNode,
                parameters,
                FunctionNode.Kind.SETTER,
                functionLine,
                functionBody);

        return new PropertyFunction(propertyName, function, computed);
    }

    private PropertyFunction propertyMethodFunction(Expression key, final long methodToken, final int methodLine, final boolean generator, final int flags, boolean computed) {
        final String methodName = key instanceof PropertyKey ? ((PropertyKey) key).getPropertyName() : getDefaultValidFunctionName(methodLine, false);
        final IdentNode methodNameNode = createIdentNode(((Node)key).getToken(), finish, methodName);

        FunctionNode.Kind functionKind = generator ? FunctionNode.Kind.GENERATOR : FunctionNode.Kind.NORMAL;
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(methodNameNode, methodToken, functionKind, methodLine, null);
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        try {
            final ParserContextBlockNode parameterBlock = newBlock();
            final List<IdentNode> parameters;
            try {
                expect(LPAREN);
                parameters = formalParameterList(generator);
                functionNode.setParameters(parameters);
                expect(RPAREN);
            } finally {
                restoreBlock(parameterBlock);
            }

            Block functionBody = functionBody(functionNode);

            functionBody = maybeWrapBodyInParameterBlock(functionBody, parameterBlock);

            final FunctionNode  function = createFunctionNode(
                            functionNode,
                            methodToken,
                            methodNameNode,
                            parameters,
                            functionKind,
                            methodLine,
                            functionBody);
            return new PropertyFunction(key, function, computed);
        } finally {
            lc.pop(functionNode);
        }
    }

    private static class PropertyFunction {
        final Expression key;
        final FunctionNode functionNode;
        final boolean computed;

        PropertyFunction(final Expression key, final FunctionNode function, final boolean computed) {
            this.key = key;
            this.functionNode = function;
            this.computed = computed;
        }
    }

    /**
     * LeftHandSideExpression :
     *      NewExpression
     *      CallExpression
     *
     * CallExpression :
     *      MemberExpression Arguments
     *      SuperCall
     *      CallExpression Arguments
     *      CallExpression [ Expression ]
     *      CallExpression . IdentifierName
     *
     * SuperCall :
     *      super Arguments
     *
     * See 11.2
     *
     * Parse left hand side expression.
     * @return Expression node.
     */
    private Expression leftHandSideExpression() {
        int  callLine  = line;
        long callToken = token;

        Expression lhs = memberExpression();

        if (type == LPAREN) {
            final List<Expression> arguments = optimizeList(argumentList());

            // Catch special functions.
            if (lhs instanceof IdentNode) {
                detectSpecialFunction((IdentNode)lhs);
            }

            lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);
        }

        loop:
        while (true) {
            // Capture token.
            callLine  = line;
            callToken = token;

            switch (type) {
            case LPAREN: {
                // Get NEW or FUNCTION arguments.
                final List<Expression> arguments = optimizeList(argumentList());

                // Create call node.
                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            case LBRACKET: {
                next();

                // Get array index.
                final Expression rhs = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(callToken, finish, lhs, rhs);

                break;
            }
            case PERIOD: {
                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(callToken, finish, lhs, property.getName());

                break;
            }
            case TEMPLATE:
            case TEMPLATE_HEAD: {
                // tagged template literal
                final List<Expression> arguments = templateLiteralArgumentList();

                // Create call node.
                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            default:
                break loop;
            }
        }

        return lhs;
    }

    /**
     * NewExpression :
     *      MemberExpression
     *      new NewExpression
     *
     * See 11.2
     *
     * Parse new expression.
     * @return Expression node.
     */
    private Expression newExpression() {
        final long newToken = token;
        // NEW is tested in caller.
        next();

        if (type == PERIOD && isES6()) {
            next();
            if (type == IDENT && "target".equals(getValue())) {
                if (lc.getCurrentFunction().isProgram()) {
                    throw error(AbstractParser.message("new.target.in.function"), token);
                }
                next();
                markNewTarget(lc);
                return new IdentNode(newToken, finish, "new.target");
            } else {
                throw error(AbstractParser.message("expected.target"), token);
            }
        }

        // Get function base.
        final int  callLine    = line;
        final Expression constructor = memberExpression();
        if (constructor == null) {
            return null;
        }
        // Get arguments.
        ArrayList<Expression> arguments;

        // Allow for missing arguments.
        if (type == LPAREN) {
            arguments = argumentList();
        } else {
            arguments = new ArrayList<>();
        }

        // Nashorn extension: This is to support the following interface implementation
        // syntax:
        //
        //     var r = new java.lang.Runnable() {
        //         run: function() { println("run"); }
        //     };
        //
        // The object literal following the "new Constructor()" expression
        // is passed as an additional (last) argument to the constructor.
        if (!env._no_syntax_extensions && type == LBRACE) {
            arguments.add(objectLiteral());
        }

        final CallNode callNode = new CallNode(callLine, constructor.getToken(), finish, constructor, optimizeList(arguments), true);

        return new UnaryNode(newToken, callNode);
    }

    /**
     * MemberExpression :
     *      PrimaryExpression
     *        FunctionExpression
     *        ClassExpression
     *        GeneratorExpression
     *      MemberExpression [ Expression ]
     *      MemberExpression . IdentifierName
     *      MemberExpression TemplateLiteral
     *      SuperProperty
     *      MetaProperty
     *      new MemberExpression Arguments
     *
     * SuperProperty :
     *      super [ Expression ]
     *      super . IdentifierName
     *
     * MetaProperty :
     *      NewTarget
     *
     * Parse member expression.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private Expression memberExpression() {
        // Prepare to build operation.
        Expression lhs;
        boolean isSuper = false;

        switch (type) {
        case NEW:
            // Get new expression.
            lhs = newExpression();
            break;

        case FUNCTION:
            // Get function expression.
            lhs = functionExpression(false, false);
            break;

        case CLASS:
            if (isES6()) {
                lhs = classExpression(false);
                break;
            } else {
                // fall through
            }

        case SUPER:
            if (isES6()) {
                final ParserContextFunctionNode currentFunction = getCurrentNonArrowFunction();
                if (currentFunction.isMethod()) {
                    long identToken = Token.recast(token, IDENT);
                    next();
                    lhs = createIdentNode(identToken, finish, SUPER.getName());

                    switch (type) {
                        case LBRACKET:
                        case PERIOD:
                            getCurrentNonArrowFunction().setFlag(FunctionNode.ES6_USES_SUPER);
                            isSuper = true;
                            break;
                        case LPAREN:
                            if (currentFunction.isSubclassConstructor()) {
                                lhs = ((IdentNode)lhs).setIsDirectSuper();
                                break;
                            } else {
                                // fall through to throw error
                            }
                        default:
                            throw error(AbstractParser.message("invalid.super"), identToken);
                    }
                    break;
                } else {
                    // fall through
                }
            } else {
                // fall through
            }

        default:
            // Get primary expression.
            lhs = primaryExpression();
            break;
        }

        loop:
        while (true) {
            // Capture token.
            final long callToken = token;

            switch (type) {
            case LBRACKET: {
                next();

                // Get array index.
                final Expression index = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(callToken, finish, lhs, index);

                if (isSuper) {
                    isSuper = false;
                    lhs = ((BaseNode) lhs).setIsSuper();
                }

                break;
            }
            case PERIOD: {
                if (lhs == null) {
                    throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
                }

                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(callToken, finish, lhs, property.getName());

                if (isSuper) {
                    isSuper = false;
                    lhs = ((BaseNode) lhs).setIsSuper();
                }

                break;
            }
            case TEMPLATE:
            case TEMPLATE_HEAD: {
                // tagged template literal
                final int callLine = line;
                final List<Expression> arguments = templateLiteralArgumentList();

                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            default:
                break loop;
            }
        }

        return lhs;
    }

    /**
     * Arguments :
     *      ( )
     *      ( ArgumentList )
     *
     * ArgumentList :
     *      AssignmentExpression
     *      ... AssignmentExpression
     *      ArgumentList , AssignmentExpression
     *      ArgumentList , ... AssignmentExpression
     *
     * See 11.2
     *
     * Parse function call arguments.
     * @return Argument list.
     */
    private ArrayList<Expression> argumentList() {
        // Prepare to accumulate list of arguments.
        final ArrayList<Expression> nodeList = new ArrayList<>();
        // LPAREN tested in caller.
        next();

        // Track commas.
        boolean first = true;

        while (type != RPAREN) {
            // Comma prior to every argument except the first.
            if (!first) {
                expect(COMMARIGHT);
            } else {
                first = false;
            }

            long spreadToken = 0;
            if (type == ELLIPSIS && isES6()) {
                spreadToken = token;
                next();
            }

            // Get argument expression.
            Expression expression = assignmentExpression(false);
            if (spreadToken != 0) {
                expression = new UnaryNode(Token.recast(spreadToken, TokenType.SPREAD_ARGUMENT), expression);
            }
            nodeList.add(expression);
        }

        expect(RPAREN);
        return nodeList;
    }

    private static <T> List<T> optimizeList(final ArrayList<T> list) {
        switch(list.size()) {
            case 0: {
                return Collections.emptyList();
            }
            case 1: {
                return Collections.singletonList(list.get(0));
            }
            default: {
                list.trimToSize();
                return list;
            }
        }
    }

    /**
     * FunctionDeclaration :
     *      function Identifier ( FormalParameterList? ) { FunctionBody }
     *
     * FunctionExpression :
     *      function Identifier? ( FormalParameterList? ) { FunctionBody }
     *
     * See 13
     *
     * Parse function declaration.
     * @param isStatement True if for is a statement.
     *
     * @return Expression node.
     */
    private Expression functionExpression(final boolean isStatement, final boolean topLevel) {
        final long functionToken = token;
        final int  functionLine  = line;
        // FUNCTION is tested in caller.
        assert type == FUNCTION;
        next();

        boolean generator = false;
        if (type == MUL && isES6()) {
            generator = true;
            next();
        }

        IdentNode name = null;

        if (isBindingIdentifier()) {
            if (type == YIELD && ((!isStatement && generator) || (isStatement && inGeneratorFunction()))) {
                // 12.1.1 Early SyntaxError if:
                // GeneratorExpression with BindingIdentifier yield
                // HoistableDeclaration with BindingIdentifier yield in generator function body
                expect(IDENT);
            }
            name = getIdent();
            verifyStrictIdent(name, "function name");
        } else if (isStatement) {
            // Nashorn extension: anonymous function statements.
            // Do not allow anonymous function statement if extensions
            // are now allowed. But if we are reparsing then anon function
            // statement is possible - because it was used as function
            // expression in surrounding code.
            if (env._no_syntax_extensions && reparsedFunction == null) {
                expect(IDENT);
            }
        }

        // name is null, generate anonymous name
        boolean isAnonymous = false;
        if (name == null) {
            final String tmpName = getDefaultValidFunctionName(functionLine, isStatement);
            name = new IdentNode(functionToken, Token.descPosition(functionToken), tmpName);
            isAnonymous = true;
        }

        FunctionNode.Kind functionKind = generator ? FunctionNode.Kind.GENERATOR : FunctionNode.Kind.NORMAL;
        List<IdentNode> parameters = Collections.emptyList();
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(name, functionToken, functionKind, functionLine, parameters);
        lc.push(functionNode);

        Block functionBody = null;
        // Hide the current default name across function boundaries. E.g. "x3 = function x1() { function() {}}"
        // If we didn't hide the current default name, then the innermost anonymous function would receive "x3".
        hideDefaultName();
        try {
            final ParserContextBlockNode parameterBlock = newBlock();
            try {
                expect(LPAREN);
                parameters = formalParameterList(generator);
                functionNode.setParameters(parameters);
                expect(RPAREN);
            } finally {
                restoreBlock(parameterBlock);
            }

            functionBody = functionBody(functionNode);

            functionBody = maybeWrapBodyInParameterBlock(functionBody, parameterBlock);
        } finally {
            defaultNames.pop();
            lc.pop(functionNode);
        }

        if (isStatement) {
            if (topLevel || useBlockScope() || (!isStrictMode && env._function_statement == ScriptEnvironment.FunctionStatementBehavior.ACCEPT)) {
                functionNode.setFlag(FunctionNode.IS_DECLARED);
            } else if (isStrictMode) {
                throw error(JSErrorType.SYNTAX_ERROR, AbstractParser.message("strict.no.func.decl.here"), functionToken);
            } else if (env._function_statement == ScriptEnvironment.FunctionStatementBehavior.ERROR) {
                throw error(JSErrorType.SYNTAX_ERROR, AbstractParser.message("no.func.decl.here"), functionToken);
            } else if (env._function_statement == ScriptEnvironment.FunctionStatementBehavior.WARNING) {
                warning(JSErrorType.SYNTAX_ERROR, AbstractParser.message("no.func.decl.here.warn"), functionToken);
            }
            if (isArguments(name)) {
               lc.getCurrentFunction().setFlag(FunctionNode.DEFINES_ARGUMENTS);
            }
        }

        if (isAnonymous) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }

        verifyParameterList(parameters, functionNode);

        final FunctionNode function = createFunctionNode(
                functionNode,
                functionToken,
                name,
                parameters,
                functionKind,
                functionLine,
                functionBody);

        if (isStatement) {
            if (isAnonymous) {
                appendStatement(new ExpressionStatement(functionLine, functionToken, finish, function));
                return function;
            }

            // mark ES6 block functions as lexically scoped
            final int     varFlags = (topLevel || !useBlockScope()) ? 0 : VarNode.IS_LET;
            final VarNode varNode  = new VarNode(functionLine, functionToken, finish, name, function, varFlags);
            if (topLevel) {
                functionDeclarations.add(varNode);
            } else if (useBlockScope()) {
                prependStatement(varNode); // Hoist to beginning of current block
            } else {
                appendStatement(varNode);
            }
        }

        return function;
    }

    private void verifyParameterList(final List<IdentNode> parameters, final ParserContextFunctionNode functionNode) {
        final IdentNode duplicateParameter = functionNode.getDuplicateParameterBinding();
        if (duplicateParameter != null) {
            if (functionNode.isStrict() || functionNode.getKind() == FunctionNode.Kind.ARROW || !functionNode.isSimpleParameterList()) {
                throw error(AbstractParser.message("strict.param.redefinition", duplicateParameter.getName()), duplicateParameter.getToken());
            }

            final int arity = parameters.size();
            final HashSet<String> parametersSet = new HashSet<>(arity);

            for (int i = arity - 1; i >= 0; i--) {
                final IdentNode parameter = parameters.get(i);
                String parameterName = parameter.getName();

                if (parametersSet.contains(parameterName)) {
                    // redefinition of parameter name, rename in non-strict mode
                    parameterName = functionNode.uniqueName(parameterName);
                    final long parameterToken = parameter.getToken();
                    parameters.set(i, new IdentNode(parameterToken, Token.descPosition(parameterToken), functionNode.uniqueName(parameterName)));
                }
                parametersSet.add(parameterName);
            }
        }
    }

    private static Block maybeWrapBodyInParameterBlock(Block functionBody, ParserContextBlockNode parameterBlock) {
        assert functionBody.isFunctionBody();
        if (!parameterBlock.getStatements().isEmpty()) {
            parameterBlock.appendStatement(new BlockStatement(functionBody));
            return new Block(parameterBlock.getToken(), functionBody.getFinish(), (functionBody.getFlags() | Block.IS_PARAMETER_BLOCK) & ~Block.IS_BODY, parameterBlock.getStatements());
        }
        return functionBody;
    }

    private String getDefaultValidFunctionName(final int functionLine, final boolean isStatement) {
        final String defaultFunctionName = getDefaultFunctionName();
        if (isValidIdentifier(defaultFunctionName)) {
            if (isStatement) {
                // The name will be used as the LHS of a symbol assignment. We add the anonymous function
                // prefix to ensure that it can't clash with another variable.
                return ANON_FUNCTION_PREFIX.symbolName() + defaultFunctionName;
            }
            return defaultFunctionName;
        }
        return ANON_FUNCTION_PREFIX.symbolName() + functionLine;
    }

    private static boolean isValidIdentifier(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); ++i) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String getDefaultFunctionName() {
        if (!defaultNames.isEmpty()) {
            final Object nameExpr = defaultNames.peek();
            if (nameExpr instanceof PropertyKey) {
                markDefaultNameUsed();
                return ((PropertyKey)nameExpr).getPropertyName();
            } else if (nameExpr instanceof AccessNode) {
                markDefaultNameUsed();
                return ((AccessNode)nameExpr).getProperty();
            }
        }
        return null;
    }

    private void markDefaultNameUsed() {
        defaultNames.pop();
        hideDefaultName();
    }

    private void hideDefaultName() {
        // Can be any value as long as getDefaultFunctionName doesn't recognize it as something it can extract a value
        // from. Can't be null
        defaultNames.push("");
    }

    /**
     * FormalParameterList :
     *      Identifier
     *      FormalParameterList , Identifier
     *
     * See 13
     *
     * Parse function parameter list.
     * @return List of parameter nodes.
     */
    private List<IdentNode> formalParameterList(final boolean yield) {
        return formalParameterList(RPAREN, yield);
    }

    /**
     * Same as the other method of the same name - except that the end
     * token type expected is passed as argument to this method.
     *
     * FormalParameterList :
     *      Identifier
     *      FormalParameterList , Identifier
     *
     * See 13
     *
     * Parse function parameter list.
     * @return List of parameter nodes.
     */
    private List<IdentNode> formalParameterList(final TokenType endType, final boolean yield) {
        // Prepare to gather parameters.
        final ArrayList<IdentNode> parameters = new ArrayList<>();
        // Track commas.
        boolean first = true;

        while (type != endType) {
            // Comma prior to every argument except the first.
            if (!first) {
                expect(COMMARIGHT);
            } else {
                first = false;
            }

            boolean restParameter = false;
            if (type == ELLIPSIS && isES6()) {
                next();
                restParameter = true;
            }

            if (type == YIELD && yield) {
                expect(IDENT);
            }

            final long paramToken = token;
            final int paramLine = line;
            final String contextString = "function parameter";
            IdentNode ident;
            if (isBindingIdentifier() || restParameter || !isES6()) {
                ident = bindingIdentifier(contextString);

                if (restParameter) {
                    ident = ident.setIsRestParameter();
                    // rest parameter must be last
                    expectDontAdvance(endType);
                    parameters.add(ident);
                    break;
                } else if (type == ASSIGN && isES6()) {
                    next();
                    ident = ident.setIsDefaultParameter();

                    if (type == YIELD && yield) {
                        // error: yield in default expression
                        expect(IDENT);
                    }

                    // default parameter
                    Expression initializer = assignmentExpression(false);

                    ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                    if (currentFunction != null) {
                        // desugar to: param = (param === undefined) ? initializer : param;
                        // possible alternative: if (param === undefined) param = initializer;
                        BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                        TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                        BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), ident, value);
                        lc.getFunctionBody(currentFunction).appendStatement(new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                    }
                }

                ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    currentFunction.addParameterBinding(ident);
                    if (ident.isRestParameter() || ident.isDefaultParameter()) {
                        currentFunction.setSimpleParameterList(false);
                    }
                }
            } else {
                final Expression pattern = bindingPattern();
                // Introduce synthetic temporary parameter to capture the object to be destructured.
                ident = createIdentNode(paramToken, pattern.getFinish(), String.format("arguments[%d]", parameters.size())).setIsDestructuredParameter();
                verifyDestructuringParameterBindingPattern(pattern, paramToken, paramLine, contextString);

                Expression value = ident;
                if (type == ASSIGN) {
                    next();
                    ident = ident.setIsDefaultParameter();

                    // binding pattern with initializer. desugar to: (param === undefined) ? initializer : param
                    Expression initializer = assignmentExpression(false);
                    // TODO initializer must not contain yield expression if yield=true (i.e. this is generator function's parameter list)
                    BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                    value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                }

                ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    // destructuring assignment
                    BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), pattern, value);
                    lc.getFunctionBody(currentFunction).appendStatement(new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                }
            }
            parameters.add(ident);
        }

        parameters.trimToSize();
        return parameters;
    }

    private void verifyDestructuringParameterBindingPattern(final Expression pattern, final long paramToken, final int paramLine, final String contextString) {
        verifyDestructuringBindingPattern(pattern, new Consumer<IdentNode>() {
            public void accept(IdentNode identNode) {
                verifyIdent(identNode, contextString);

                ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    // declare function-scope variables for destructuring bindings
                    lc.getFunctionBody(currentFunction).appendStatement(new VarNode(paramLine, Token.recast(paramToken, VAR), pattern.getFinish(), identNode, null));
                    // detect duplicate bounds names in parameter list
                    currentFunction.addParameterBinding(identNode);
                    currentFunction.setSimpleParameterList(false);
                }
            }
        });
    }

    /**
     * FunctionBody :
     *      SourceElements?
     *
     * See 13
     *
     * Parse function body.
     * @return function node (body.)
     */
    private Block functionBody(final ParserContextFunctionNode functionNode) {
        long lastToken = 0L;
        ParserContextBlockNode body = null;
        final long bodyToken = token;
        Block functionBody;
        int bodyFinish = 0;

        final boolean parseBody;
        Object endParserState = null;
        try {
            // Create a new function block.
            body = newBlock();
            if (env._debug_scopes) {
                // debug scope options forces everything to be in scope
                markEval(lc);
            }
            assert functionNode != null;
            final int functionId = functionNode.getId();
            parseBody = reparsedFunction == null || functionId <= reparsedFunction.getFunctionNodeId();
            // Nashorn extension: expression closures
            if ((!env._no_syntax_extensions || functionNode.getKind() == FunctionNode.Kind.ARROW) && type != LBRACE) {
                /*
                 * Example:
                 *
                 * function square(x) x * x;
                 * print(square(3));
                 */

                // just expression as function body
                final Expression expr = assignmentExpression(false);
                lastToken = previousToken;
                functionNode.setLastToken(previousToken);
                assert lc.getCurrentBlock() == lc.getFunctionBody(functionNode);
                // EOL uses length field to store the line number
                final int lastFinish = Token.descPosition(lastToken) + (Token.descType(lastToken) == EOL ? 0 : Token.descLength(lastToken));
                // Only create the return node if we aren't skipping nested functions. Note that we aren't
                // skipping parsing of these extended functions; they're considered to be small anyway. Also,
                // they don't end with a single well known token, so it'd be very hard to get correctly (see
                // the note below for reasoning on skipping happening before instead of after RBRACE for
                // details).
                if (parseBody) {
                    final ReturnNode returnNode = new ReturnNode(functionNode.getLineNumber(), expr.getToken(), lastFinish, expr);
                    appendStatement(returnNode);
                }
                // bodyFinish = finish;
            } else {
                expectDontAdvance(LBRACE);
                if (parseBody || !skipFunctionBody(functionNode)) {
                    next();
                    // Gather the function elements.
                    final List<Statement> prevFunctionDecls = functionDeclarations;
                    functionDeclarations = new ArrayList<>();
                    try {
                        sourceElements(false);
                        addFunctionDeclarations(functionNode);
                    } finally {
                        functionDeclarations = prevFunctionDecls;
                    }

                    lastToken = token;
                    if (parseBody) {
                        // Since the lexer can read ahead and lexify some number of tokens in advance and have
                        // them buffered in the TokenStream, we need to produce a lexer state as it was just
                        // before it lexified RBRACE, and not whatever is its current (quite possibly well read
                        // ahead) state.
                        endParserState = new ParserState(Token.descPosition(token), line, linePosition);

                        // NOTE: you might wonder why do we capture/restore parser state before RBRACE instead of
                        // after RBRACE; after all, we could skip the below "expect(RBRACE);" if we captured the
                        // state after it. The reason is that RBRACE is a well-known token that we can expect and
                        // will never involve us getting into a weird lexer state, and as such is a great reparse
                        // point. Typical example of a weird lexer state after RBRACE would be:
                        //     function this_is_skipped() { ... } "use strict";
                        // because lexer is doing weird off-by-one maneuvers around string literal quotes. Instead
                        // of compensating for the possibility of a string literal (or similar) after RBRACE,
                        // we'll rather just restart parsing from this well-known, friendly token instead.
                    }
                }
                bodyFinish = finish;
                functionNode.setLastToken(token);
                expect(RBRACE);
            }
        } finally {
            restoreBlock(body);
        }

        // NOTE: we can only do alterations to the function node after restoreFunctionNode.

        if (parseBody) {
            functionNode.setEndParserState(endParserState);
        } else if (!body.getStatements().isEmpty()){
            // This is to ensure the body is empty when !parseBody but we couldn't skip parsing it (see
            // skipFunctionBody() for possible reasons). While it is not strictly necessary for correctness to
            // enforce empty bodies in nested functions that were supposed to be skipped, we do assert it as
            // an invariant in few places in the compiler pipeline, so for consistency's sake we'll throw away
            // nested bodies early if we were supposed to skip 'em.
            body.setStatements(Collections.<Statement>emptyList());
        }

        if (reparsedFunction != null) {
            // We restore the flags stored in the function's ScriptFunctionData that we got when we first
            // eagerly parsed the code. We're doing it because some flags would be set based on the
            // content of the function, or even content of its nested functions, most of which are normally
            // skipped during an on-demand compilation.
            final RecompilableScriptFunctionData data = reparsedFunction.getScriptFunctionData(functionNode.getId());
            if (data != null) {
                // Data can be null if when we originally parsed the file, we removed the function declaration
                // as it was dead code.
                functionNode.setFlag(data.getFunctionFlags());
                // This compensates for missing markEval() in case the function contains an inner function
                // that contains eval(), that now we didn't discover since we skipped the inner function.
                if (functionNode.hasNestedEval()) {
                    assert functionNode.hasScopeBlock();
                    body.setFlag(Block.NEEDS_SCOPE);
                }
            }
        }
        functionBody = new Block(bodyToken, bodyFinish, body.getFlags() | Block.IS_BODY, body.getStatements());
        return functionBody;
    }

    private boolean skipFunctionBody(final ParserContextFunctionNode functionNode) {
        if (reparsedFunction == null) {
            // Not reparsing, so don't skip any function body.
            return false;
        }
        // Skip to the RBRACE of this function, and continue parsing from there.
        final RecompilableScriptFunctionData data = reparsedFunction.getScriptFunctionData(functionNode.getId());
        if (data == null) {
            // Nested function is not known to the reparsed function. This can happen if the FunctionNode was
            // in dead code that was removed. Both FoldConstants and Lower prune dead code. In that case, the
            // FunctionNode was dropped before a RecompilableScriptFunctionData could've been created for it.
            return false;
        }
        final ParserState parserState = (ParserState)data.getEndParserState();
        assert parserState != null;

        if (k < stream.last() && start < parserState.position && parserState.position <= Token.descPosition(stream.get(stream.last()))) {
            // RBRACE is already in the token stream, so fast forward to it
            for (; k < stream.last(); k++) {
                final long nextToken = stream.get(k + 1);
                if (Token.descPosition(nextToken) == parserState.position && Token.descType(nextToken) == RBRACE) {
                    token = stream.get(k);
                    type = Token.descType(token);
                    next();
                    assert type == RBRACE && start == parserState.position;
                    return true;
                }
            }
        }

        stream.reset();
        lexer = parserState.createLexer(source, lexer, stream, scripting && !env._no_syntax_extensions, env._es6);
        line = parserState.line;
        linePosition = parserState.linePosition;
        // Doesn't really matter, but it's safe to treat it as if there were a semicolon before
        // the RBRACE.
        type = SEMICOLON;
        scanFirstToken();

        return true;
    }

    /**
     * Encapsulates part of the state of the parser, enough to reconstruct the state of both parser and lexer
     * for resuming parsing after skipping a function body.
     */
    private static class ParserState implements Serializable {
        private final int position;
        private final int line;
        private final int linePosition;

        private static final long serialVersionUID = -2382565130754093694L;

        ParserState(final int position, final int line, final int linePosition) {
            this.position = position;
            this.line = line;
            this.linePosition = linePosition;
        }

        Lexer createLexer(final Source source, final Lexer lexer, final TokenStream stream, final boolean scripting, final boolean es6) {
            final Lexer newLexer = new Lexer(source, position, lexer.limit - position, stream, scripting, es6, true);
            newLexer.restoreState(new Lexer.State(position, Integer.MAX_VALUE, line, -1, linePosition, SEMICOLON));
            return newLexer;
        }
    }

    private void printAST(final FunctionNode functionNode) {
        if (functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_AST)) {
            env.getErr().println(new ASTWriter(functionNode));
        }

        if (functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_PARSE)) {
            env.getErr().println(new PrintVisitor(functionNode, true, false));
        }
    }

    private void addFunctionDeclarations(final ParserContextFunctionNode functionNode) {
        VarNode lastDecl = null;
        for (int i = functionDeclarations.size() - 1; i >= 0; i--) {
            Statement decl = functionDeclarations.get(i);
            if (lastDecl == null && decl instanceof VarNode) {
                decl = lastDecl = ((VarNode)decl).setFlag(VarNode.IS_LAST_FUNCTION_DECLARATION);
                functionNode.setFlag(FunctionNode.HAS_FUNCTION_DECLARATIONS);
            }
            prependStatement(decl);
        }
    }

    private RuntimeNode referenceError(final Expression lhs, final Expression rhs, final boolean earlyError) {
        if (earlyError) {
            throw error(JSErrorType.REFERENCE_ERROR, AbstractParser.message("invalid.lvalue"), lhs.getToken());
        }
        final ArrayList<Expression> args = new ArrayList<>();
        args.add(lhs);
        if (rhs == null) {
            args.add(LiteralNode.newInstance(lhs.getToken(), lhs.getFinish()));
        } else {
            args.add(rhs);
        }
        args.add(LiteralNode.newInstance(lhs.getToken(), lhs.getFinish(), lhs.toString()));
        return new RuntimeNode(lhs.getToken(), lhs.getFinish(), RuntimeNode.Request.REFERENCE_ERROR, args);
    }

    /**
     * PostfixExpression :
     *      LeftHandSideExpression
     *      LeftHandSideExpression ++ // [no LineTerminator here]
     *      LeftHandSideExpression -- // [no LineTerminator here]
     *
     * See 11.3
     *
     * UnaryExpression :
     *      PostfixExpression
     *      delete UnaryExpression
     *      void UnaryExpression
     *      typeof UnaryExpression
     *      ++ UnaryExpression
     *      -- UnaryExpression
     *      + UnaryExpression
     *      - UnaryExpression
     *      ~ UnaryExpression
     *      ! UnaryExpression
     *
     * See 11.4
     *
     * Parse unary expression.
     * @return Expression node.
     */
    private Expression unaryExpression() {
        final int  unaryLine  = line;
        final long unaryToken = token;

        switch (type) {
        case DELETE: {
            next();
            final Expression expr = unaryExpression();
            if (expr instanceof BaseNode || expr instanceof IdentNode) {
                return new UnaryNode(unaryToken, expr);
            }
            appendStatement(new ExpressionStatement(unaryLine, unaryToken, finish, expr));
            return LiteralNode.newInstance(unaryToken, finish, true);
        }
        case VOID:
        case TYPEOF:
        case ADD:
        case SUB:
        case BIT_NOT:
        case NOT:
            next();
            final Expression expr = unaryExpression();
            return new UnaryNode(unaryToken, expr);

        case INCPREFIX:
        case DECPREFIX:
            final TokenType opType = type;
            next();

            final Expression lhs = leftHandSideExpression();
            // ++, -- without operand..
            if (lhs == null) {
                throw error(AbstractParser.message("expected.lvalue", type.getNameOrType()));
            }

            return verifyIncDecExpression(unaryToken, opType, lhs, false);

        default:
            break;
        }

        Expression expression = leftHandSideExpression();

        if (last != EOL) {
            switch (type) {
            case INCPREFIX:
            case DECPREFIX:
                final long opToken = token;
                final TokenType opType = type;
                final Expression lhs = expression;
                // ++, -- without operand..
                if (lhs == null) {
                    throw error(AbstractParser.message("expected.lvalue", type.getNameOrType()));
                }
                next();

                return verifyIncDecExpression(opToken, opType, lhs, true);
            default:
                break;
            }
        }

        if (expression == null) {
            throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        return expression;
    }

    private Expression verifyIncDecExpression(final long unaryToken, final TokenType opType, final Expression lhs, final boolean isPostfix) {
        assert lhs != null;

        if (!(lhs instanceof AccessNode ||
              lhs instanceof IndexNode ||
              lhs instanceof IdentNode)) {
            return referenceError(lhs, null, env._early_lvalue_error);
        }

        if (lhs instanceof IdentNode) {
            if (!checkIdentLValue((IdentNode)lhs)) {
                return referenceError(lhs, null, false);
            }
            verifyIdent((IdentNode)lhs, "operand for " + opType.getName() + " operator");
        }

        return incDecExpression(unaryToken, opType, lhs, isPostfix);
    }

    /**
     * {@code
     * MultiplicativeExpression :
     *      UnaryExpression
     *      MultiplicativeExpression * UnaryExpression
     *      MultiplicativeExpression / UnaryExpression
     *      MultiplicativeExpression % UnaryExpression
     *
     * See 11.5
     *
     * AdditiveExpression :
     *      MultiplicativeExpression
     *      AdditiveExpression + MultiplicativeExpression
     *      AdditiveExpression - MultiplicativeExpression
     *
     * See 11.6
     *
     * ShiftExpression :
     *      AdditiveExpression
     *      ShiftExpression << AdditiveExpression
     *      ShiftExpression >> AdditiveExpression
     *      ShiftExpression >>> AdditiveExpression
     *
     * See 11.7
     *
     * RelationalExpression :
     *      ShiftExpression
     *      RelationalExpression < ShiftExpression
     *      RelationalExpression > ShiftExpression
     *      RelationalExpression <= ShiftExpression
     *      RelationalExpression >= ShiftExpression
     *      RelationalExpression instanceof ShiftExpression
     *      RelationalExpression in ShiftExpression // if !noIf
     *
     * See 11.8
     *
     *      RelationalExpression
     *      EqualityExpression == RelationalExpression
     *      EqualityExpression != RelationalExpression
     *      EqualityExpression === RelationalExpression
     *      EqualityExpression !== RelationalExpression
     *
     * See 11.9
     *
     * BitwiseANDExpression :
     *      EqualityExpression
     *      BitwiseANDExpression & EqualityExpression
     *
     * BitwiseXORExpression :
     *      BitwiseANDExpression
     *      BitwiseXORExpression ^ BitwiseANDExpression
     *
     * BitwiseORExpression :
     *      BitwiseXORExpression
     *      BitwiseORExpression | BitwiseXORExpression
     *
     * See 11.10
     *
     * LogicalANDExpression :
     *      BitwiseORExpression
     *      LogicalANDExpression && BitwiseORExpression
     *
     * LogicalORExpression :
     *      LogicalANDExpression
     *      LogicalORExpression || LogicalANDExpression
     *
     * See 11.11
     *
     * ConditionalExpression :
     *      LogicalORExpression
     *      LogicalORExpression ? AssignmentExpression : AssignmentExpression
     *
     * See 11.12
     *
     * AssignmentExpression :
     *      ConditionalExpression
     *      LeftHandSideExpression AssignmentOperator AssignmentExpression
     *
     * AssignmentOperator :
     *      = *= /= %= += -= <<= >>= >>>= &= ^= |=
     *
     * See 11.13
     *
     * Expression :
     *      AssignmentExpression
     *      Expression , AssignmentExpression
     *
     * See 11.14
     * }
     *
     * Parse expression.
     * @return Expression node.
     */
    protected Expression expression() {
        // This method is protected so that subclass can get details
        // at expression start point!

        // Include commas in expression parsing.
        return expression(false);
    }

    private Expression expression(final boolean noIn) {
        Expression assignmentExpression = assignmentExpression(noIn);
        while (type == COMMARIGHT) {
            long commaToken = token;
            next();

            boolean rhsRestParameter = false;
            if (type == ELLIPSIS && isES6()) {
                // (a, b, ...rest) is not a valid expression, unless we're parsing the parameter list of an arrow function (we need to throw the right error).
                // But since the rest parameter is always last, at least we know that the expression has to end here and be followed by RPAREN and ARROW, so peek ahead.
                if (isRestParameterEndOfArrowFunctionParameterList()) {
                    next();
                    rhsRestParameter = true;
                }
            }

            Expression rhs = assignmentExpression(noIn);

            if (rhsRestParameter) {
                rhs = ((IdentNode)rhs).setIsRestParameter();
                // Our only valid move is to end Expression here and continue with ArrowFunction.
                // We've already checked that this is the parameter list of an arrow function (see above).
                // RPAREN is next, so we'll finish the binary expression and drop out of the loop.
                assert type == RPAREN;
            }

            assignmentExpression = new BinaryNode(commaToken, assignmentExpression, rhs);
        }
        return assignmentExpression;
    }

    private Expression expression(final int minPrecedence, final boolean noIn) {
        return expression(unaryExpression(), minPrecedence, noIn);
    }

    private JoinPredecessorExpression joinPredecessorExpression() {
        return new JoinPredecessorExpression(expression());
    }

    private Expression expression(final Expression exprLhs, final int minPrecedence, final boolean noIn) {
        // Get the precedence of the next operator.
        int precedence = type.getPrecedence();
        Expression lhs = exprLhs;

        // While greater precedence.
        while (type.isOperator(noIn) && precedence >= minPrecedence) {
            // Capture the operator token.
            final long op = token;

            if (type == TERNARY) {
                // Skip operator.
                next();

                // Pass expression. Middle expression of a conditional expression can be a "in"
                // expression - even in the contexts where "in" is not permitted.
                final Expression trueExpr = expression(unaryExpression(), ASSIGN.getPrecedence(), false);

                expect(COLON);

                // Fail expression.
                final Expression falseExpr = expression(unaryExpression(), ASSIGN.getPrecedence(), noIn);

                // Build up node.
                lhs = new TernaryNode(op, lhs, new JoinPredecessorExpression(trueExpr), new JoinPredecessorExpression(falseExpr));
            } else {
                // Skip operator.
                next();

                 // Get the next primary expression.
                Expression rhs;
                final boolean isAssign = Token.descType(op) == ASSIGN;
                if(isAssign) {
                    defaultNames.push(lhs);
                }
                try {
                    rhs = unaryExpression();
                    // Get precedence of next operator.
                    int nextPrecedence = type.getPrecedence();

                    // Subtask greater precedence.
                    while (type.isOperator(noIn) &&
                           (nextPrecedence > precedence ||
                           nextPrecedence == precedence && !type.isLeftAssociative())) {
                        rhs = expression(rhs, nextPrecedence, noIn);
                        nextPrecedence = type.getPrecedence();
                    }
                } finally {
                    if(isAssign) {
                        defaultNames.pop();
                    }
                }
                lhs = verifyAssignment(op, lhs, rhs);
            }

            precedence = type.getPrecedence();
        }

        return lhs;
    }

    /**
     * AssignmentExpression.
     *
     * AssignmentExpression[In, Yield] :
     *   ConditionalExpression[?In, ?Yield]
     *   [+Yield] YieldExpression[?In]
     *   ArrowFunction[?In, ?Yield]
     *   LeftHandSideExpression[?Yield] = AssignmentExpression[?In, ?Yield]
     *   LeftHandSideExpression[?Yield] AssignmentOperator AssignmentExpression[?In, ?Yield]
     */
    protected Expression assignmentExpression(final boolean noIn) {
        // This method is protected so that subclass can get details
        // at assignment expression start point!

        if (type == YIELD && inGeneratorFunction() && isES6()) {
            return yieldExpression(noIn);
        }

        final long startToken = token;
        final int startLine = line;
        final Expression exprLhs = conditionalExpression(noIn);

        if (type == ARROW && isES6()) {
            if (checkNoLineTerminator()) {
                final Expression paramListExpr;
                if (exprLhs instanceof ExpressionList) {
                    paramListExpr = (((ExpressionList)exprLhs).getExpressions().isEmpty() ? null : ((ExpressionList)exprLhs).getExpressions().get(0));
                } else {
                    paramListExpr = exprLhs;
                }
                return arrowFunction(startToken, startLine, paramListExpr);
            }
        }
        assert !(exprLhs instanceof ExpressionList);

        if (isAssignmentOperator(type)) {
            final boolean isAssign = type == ASSIGN;
            if (isAssign) {
                defaultNames.push(exprLhs);
            }
            try {
                final long assignToken = token;
                next();
                final Expression exprRhs = assignmentExpression(noIn);
                return verifyAssignment(assignToken, exprLhs, exprRhs);
            } finally {
                if (isAssign) {
                    defaultNames.pop();
                }
            }
        } else {
            return exprLhs;
        }
    }

    /**
     * Is type one of {@code = *= /= %= += -= <<= >>= >>>= &= ^= |=}?
     */
    private static boolean isAssignmentOperator(TokenType type) {
        switch (type) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
            return true;
        }
        return false;
    }

    /**
     * ConditionalExpression.
     */
    private Expression conditionalExpression(boolean noIn) {
        return expression(TERNARY.getPrecedence(), noIn);
    }

    /**
     * ArrowFunction.
     *
     * @param startToken start token of the ArrowParameters expression
     * @param functionLine start line of the arrow function
     * @param paramListExpr ArrowParameters expression or {@code null} for {@code ()} (empty list)
     */
    private Expression arrowFunction(final long startToken, final int functionLine, final Expression paramListExpr) {
        // caller needs to check that there's no LineTerminator between parameter list and arrow
        assert type != ARROW || checkNoLineTerminator();
        expect(ARROW);

        final long functionToken = Token.recast(startToken, ARROW);
        final IdentNode name = new IdentNode(functionToken, Token.descPosition(functionToken), "=>:" + functionLine);
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(name, functionToken, FunctionNode.Kind.ARROW, functionLine, null);
        functionNode.setFlag(FunctionNode.IS_ANONYMOUS);

        lc.push(functionNode);
        try {
            ParserContextBlockNode parameterBlock = newBlock();
            final List<IdentNode> parameters;
            try {
                parameters = convertArrowFunctionParameterList(paramListExpr, functionLine);
                functionNode.setParameters(parameters);

                if (!functionNode.isSimpleParameterList()) {
                    markEvalInArrowParameterList(parameterBlock);
                }
            } finally {
                restoreBlock(parameterBlock);
            }
            Block functionBody = functionBody(functionNode);

            functionBody = maybeWrapBodyInParameterBlock(functionBody, parameterBlock);

            verifyParameterList(parameters, functionNode);

            final FunctionNode function = createFunctionNode(
                            functionNode,
                            functionToken,
                            name,
                            parameters,
                            FunctionNode.Kind.ARROW,
                            functionLine,
                            functionBody);
            return function;
        } finally {
            lc.pop(functionNode);
        }
    }

    private void markEvalInArrowParameterList(final ParserContextBlockNode parameterBlock) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        final ParserContextFunctionNode current = iter.next();
        final ParserContextFunctionNode parent = iter.next();

        if (parent.getFlag(FunctionNode.HAS_EVAL) != 0) {
            // we might have flagged has-eval in the parent function during parsing the parameter list,
            // if the parameter list contains eval; must tag arrow function as has-eval.
            for (final Statement st : parameterBlock.getStatements()) {
                st.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                    @Override
                    public boolean enterCallNode(final CallNode callNode) {
                        if (callNode.getFunction() instanceof IdentNode && ((IdentNode) callNode.getFunction()).getName().equals("eval")) {
                            current.setFlag(FunctionNode.HAS_EVAL);
                        }
                        return true;
                    }
                });
            }
            // TODO: function containing the arrow function should not be flagged has-eval
        }
    }

    private List<IdentNode> convertArrowFunctionParameterList(final Expression paramListExpr, final int functionLine) {
        final List<IdentNode> parameters;
        if (paramListExpr == null) {
            // empty parameter list, i.e. () =>
            parameters = Collections.emptyList();
        } else if (paramListExpr instanceof IdentNode || paramListExpr.isTokenType(ASSIGN) || isDestructuringLhs(paramListExpr)) {
            parameters = Collections.singletonList(verifyArrowParameter(paramListExpr, 0, functionLine));
        } else if (paramListExpr instanceof BinaryNode && Token.descType(paramListExpr.getToken()) == COMMARIGHT) {
            parameters = new ArrayList<>();
            Expression car = paramListExpr;
            do {
                final Expression cdr = ((BinaryNode) car).rhs();
                parameters.add(0, verifyArrowParameter(cdr, parameters.size(), functionLine));
                car = ((BinaryNode) car).lhs();
            } while (car instanceof BinaryNode && Token.descType(car.getToken()) == COMMARIGHT);
            parameters.add(0, verifyArrowParameter(car, parameters.size(), functionLine));
        } else {
            throw error(AbstractParser.message("expected.arrow.parameter"), paramListExpr.getToken());
        }
        return parameters;
    }

    private IdentNode verifyArrowParameter(Expression param, int index, int paramLine) {
        final String contextString = "function parameter";
        if (param instanceof IdentNode) {
            IdentNode ident = (IdentNode)param;
            verifyStrictIdent(ident, contextString);
            ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                currentFunction.addParameterBinding(ident);
            }
            return ident;
        }

        if (param.isTokenType(ASSIGN)) {
            Expression lhs = ((BinaryNode) param).lhs();
            long paramToken = lhs.getToken();
            Expression initializer = ((BinaryNode) param).rhs();
            if (lhs instanceof IdentNode) {
                // default parameter
                IdentNode ident = (IdentNode) lhs;

                ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                    TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                    BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), ident, value);
                    lc.getFunctionBody(currentFunction).appendStatement(new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));

                    currentFunction.addParameterBinding(ident);
                    currentFunction.setSimpleParameterList(false);
                }
                return ident;
            } else if (isDestructuringLhs(lhs)) {
                // binding pattern with initializer
                // Introduce synthetic temporary parameter to capture the object to be destructured.
                IdentNode ident = createIdentNode(paramToken, param.getFinish(), String.format("arguments[%d]", index)).setIsDestructuredParameter().setIsDefaultParameter();
                verifyDestructuringParameterBindingPattern(param, paramToken, paramLine, contextString);

                ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                    TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                    BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), param, value);
                    lc.getFunctionBody(currentFunction).appendStatement(new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                }
                return ident;
            }
        } else if (isDestructuringLhs(param)) {
            // binding pattern
            long paramToken = param.getToken();

            // Introduce synthetic temporary parameter to capture the object to be destructured.
            IdentNode ident = createIdentNode(paramToken, param.getFinish(), String.format("arguments[%d]", index)).setIsDestructuredParameter();
            verifyDestructuringParameterBindingPattern(param, paramToken, paramLine, contextString);

            ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), param, ident);
                lc.getFunctionBody(currentFunction).appendStatement(new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
            }
            return ident;
        }
        throw error(AbstractParser.message("invalid.arrow.parameter"), param.getToken());
    }

    private boolean checkNoLineTerminator() {
        assert type == ARROW;
        if (last == RPAREN) {
            return true;
        } else if (last == IDENT) {
            return true;
        }
        for (int i = k - 1; i >= 0; i--) {
            TokenType t = T(i);
            switch (t) {
            case RPAREN:
            case IDENT:
                return true;
            case EOL:
                return false;
            case COMMENT:
                continue;
            default:
                if (t.getKind() == TokenKind.FUTURESTRICT) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Peek ahead to see if what follows after the ellipsis is a rest parameter
     * at the end of an arrow function parameter list.
     */
    private boolean isRestParameterEndOfArrowFunctionParameterList() {
        assert type == ELLIPSIS;
        // find IDENT, RPAREN, ARROW, in that order, skipping over EOL (where allowed) and COMMENT
        int i = 1;
        for (;;) {
            TokenType t = T(k + i++);
            if (t == IDENT) {
                break;
            } else if (t == EOL || t == COMMENT) {
                continue;
            } else {
                return false;
            }
        }
        for (;;) {
            TokenType t = T(k + i++);
            if (t == RPAREN) {
                break;
            } else if (t == EOL || t == COMMENT) {
                continue;
            } else {
                return false;
            }
        }
        for (;;) {
            TokenType t = T(k + i++);
            if (t == ARROW) {
                break;
            } else if (t == COMMENT) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse an end of line.
     */
    private void endOfLine() {
        switch (type) {
        case SEMICOLON:
        case EOL:
            next();
            break;
        case RPAREN:
        case RBRACKET:
        case RBRACE:
        case EOF:
            break;
        default:
            if (last != EOL) {
                expect(SEMICOLON);
            }
            break;
        }
    }

    /**
     * Parse untagged template literal as string concatenation.
     */
    private Expression templateLiteral() {
        assert type == TEMPLATE || type == TEMPLATE_HEAD;
        final boolean noSubstitutionTemplate = type == TEMPLATE;
        long lastLiteralToken = token;
        LiteralNode<?> literal = getLiteral();
        if (noSubstitutionTemplate) {
            return literal;
        }

        Expression concat = literal;
        TokenType lastLiteralType;
        do {
            final Expression expression = expression();
            if (type != TEMPLATE_MIDDLE && type != TEMPLATE_TAIL) {
                throw error(AbstractParser.message("unterminated.template.expression"), token);
            }
            concat = new BinaryNode(Token.recast(lastLiteralToken, TokenType.ADD), concat, expression);
            lastLiteralType = type;
            lastLiteralToken = token;
            literal = getLiteral();
            concat = new BinaryNode(Token.recast(lastLiteralToken, TokenType.ADD), concat, literal);
        } while (lastLiteralType == TEMPLATE_MIDDLE);
        return concat;
    }

    /**
     * Parse tagged template literal as argument list.
     * @return argument list for a tag function call (template object, ...substitutions)
     */
    private List<Expression> templateLiteralArgumentList() {
        assert type == TEMPLATE || type == TEMPLATE_HEAD;
        final ArrayList<Expression> argumentList = new ArrayList<>();
        final ArrayList<Expression> rawStrings = new ArrayList<>();
        final ArrayList<Expression> cookedStrings = new ArrayList<>();
        argumentList.add(null); // filled at the end

        final long templateToken = token;
        final boolean hasSubstitutions = type == TEMPLATE_HEAD;
        addTemplateLiteralString(rawStrings, cookedStrings);

        if (hasSubstitutions) {
            TokenType lastLiteralType;
            do {
                final Expression expression = expression();
                if (type != TEMPLATE_MIDDLE && type != TEMPLATE_TAIL) {
                    throw error(AbstractParser.message("unterminated.template.expression"), token);
                }
                argumentList.add(expression);

                lastLiteralType = type;
                addTemplateLiteralString(rawStrings, cookedStrings);
            } while (lastLiteralType == TEMPLATE_MIDDLE);
        }

        final LiteralNode<Expression[]> rawStringArray = LiteralNode.newInstance(templateToken, finish, rawStrings);
        final LiteralNode<Expression[]> cookedStringArray = LiteralNode.newInstance(templateToken, finish, cookedStrings);
        final RuntimeNode templateObject = new RuntimeNode(templateToken, finish, RuntimeNode.Request.GET_TEMPLATE_OBJECT, rawStringArray, cookedStringArray);
        argumentList.set(0, templateObject);
        return optimizeList(argumentList);
    }

    private void addTemplateLiteralString(final ArrayList<Expression> rawStrings, final ArrayList<Expression> cookedStrings) {
        final long stringToken = token;
        final String rawString = lexer.valueOfRawString(stringToken);
        final String cookedString = (String) getValue();
        next();
        rawStrings.add(LiteralNode.newInstance(stringToken, finish, rawString));
        cookedStrings.add(LiteralNode.newInstance(stringToken, finish, cookedString));
    }


    /**
     * Parse a module.
     *
     * Module :
     *      ModuleBody?
     *
     * ModuleBody :
     *      ModuleItemList
     */
    private FunctionNode module(final String moduleName) {
        boolean oldStrictMode = isStrictMode;
        try {
            isStrictMode = true; // Module code is always strict mode code. (ES6 10.2.1)

            // Make a pseudo-token for the script holding its start and length.
            int functionStart = Math.min(Token.descPosition(Token.withDelimiter(token)), finish);
            final long functionToken = Token.toDesc(FUNCTION, functionStart, source.getLength() - functionStart);
            final int  functionLine  = line;

            final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), moduleName);
            final ParserContextFunctionNode script = createParserContextFunctionNode(
                            ident,
                            functionToken,
                            FunctionNode.Kind.MODULE,
                            functionLine,
                            Collections.<IdentNode>emptyList());
            lc.push(script);

            final ParserContextModuleNode module = new ParserContextModuleNode(moduleName);
            lc.push(module);

            final ParserContextBlockNode body = newBlock();

            functionDeclarations = new ArrayList<>();
            moduleBody();
            addFunctionDeclarations(script);
            functionDeclarations = null;

            restoreBlock(body);
            body.setFlag(Block.NEEDS_SCOPE);
            final Block programBody = new Block(functionToken, finish, body.getFlags() | Block.IS_SYNTHETIC | Block.IS_BODY, body.getStatements());
            lc.pop(module);
            lc.pop(script);
            script.setLastToken(token);

            expect(EOF);

            script.setModule(module.createModule());
            return createFunctionNode(script, functionToken, ident, Collections.<IdentNode>emptyList(), FunctionNode.Kind.MODULE, functionLine, programBody);
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    /**
     * Parse module body.
     *
     * ModuleBody :
     *      ModuleItemList
     *
     * ModuleItemList :
     *      ModuleItem
     *      ModuleItemList ModuleItem
     *
     * ModuleItem :
     *      ImportDeclaration
     *      ExportDeclaration
     *      StatementListItem
     */
    private void moduleBody() {
        loop:
        while (type != EOF) {
            switch (type) {
            case EOF:
                break loop;
            case IMPORT:
                importDeclaration();
                break;
            case EXPORT:
                exportDeclaration();
                break;
            default:
                // StatementListItem
                statement(true, false, false, false);
                break;
            }
        }
    }


    /**
     * Parse import declaration.
     *
     * ImportDeclaration :
     *     import ImportClause FromClause ;
     *     import ModuleSpecifier ;
     * ImportClause :
     *     ImportedDefaultBinding
     *     NameSpaceImport
     *     NamedImports
     *     ImportedDefaultBinding , NameSpaceImport
     *     ImportedDefaultBinding , NamedImports
     * ImportedDefaultBinding :
     *     ImportedBinding
     * ModuleSpecifier :
     *     StringLiteral
     * ImportedBinding :
     *     BindingIdentifier
     */
    private void importDeclaration() {
        expect(IMPORT);
        final ParserContextModuleNode module = lc.getCurrentModule();
        if (type == STRING || type == ESCSTRING) {
            // import ModuleSpecifier ;
            final String moduleSpecifier = (String) getValue();
            next();
            module.addModuleRequest(moduleSpecifier);
        } else {
            // import ImportClause FromClause ;
            List<Module.ImportEntry> importEntries;
            if (type == MUL) {
                importEntries = Collections.singletonList(nameSpaceImport());
            } else if (type == LBRACE) {
                importEntries = namedImports();
            } else if (isBindingIdentifier()) {
                // ImportedDefaultBinding
                final IdentNode importedDefaultBinding = bindingIdentifier("ImportedBinding");
                Module.ImportEntry defaultImport = Module.ImportEntry.importDefault(importedDefaultBinding.getName());

                if (type == COMMARIGHT) {
                    next();
                    importEntries = new ArrayList<>();
                    if (type == MUL) {
                        importEntries.add(nameSpaceImport());
                    } else if (type == LBRACE) {
                        importEntries.addAll(namedImports());
                    } else {
                        throw error(AbstractParser.message("expected.named.import"));
                    }
                } else {
                    importEntries = Collections.singletonList(defaultImport);
                }
            } else {
                throw error(AbstractParser.message("expected.import"));
            }

            final String moduleSpecifier = fromClause();
            module.addModuleRequest(moduleSpecifier);
            for (int i = 0; i < importEntries.size(); i++) {
                module.addImportEntry(importEntries.get(i).withFrom(moduleSpecifier));
            }
        }
        expect(SEMICOLON);
    }

    /**
     * NameSpaceImport :
     *     * as ImportedBinding
     *
     * @return imported binding identifier
     */
    private Module.ImportEntry nameSpaceImport() {
        assert type == MUL;
        next();
        final long asToken = token;
        final String as = (String) expectValue(IDENT);
        if (!"as".equals(as)) {
            throw error(AbstractParser.message("expected.as"), asToken);
        }
        final IdentNode localNameSpace = bindingIdentifier("ImportedBinding");
        return Module.ImportEntry.importStarAsNameSpaceFrom(localNameSpace.getName());
    }

    /**
     * NamedImports :
     *     { }
     *     { ImportsList }
     *     { ImportsList , }
     * ImportsList :
     *     ImportSpecifier
     *     ImportsList , ImportSpecifier
     * ImportSpecifier :
     *     ImportedBinding
     *     IdentifierName as ImportedBinding
     * ImportedBinding :
     *     BindingIdentifier
     */
    private List<Module.ImportEntry> namedImports() {
        assert type == LBRACE;
        next();
        List<Module.ImportEntry> importEntries = new ArrayList<>();
        while (type != RBRACE) {
            final boolean bindingIdentifier = isBindingIdentifier();
            final long nameToken = token;
            final IdentNode importName = getIdentifierName();
            if (type == IDENT && "as".equals(getValue())) {
                next();
                final IdentNode localName = bindingIdentifier("ImportedBinding");
                importEntries.add(Module.ImportEntry.importSpecifier(importName.getName(), localName.getName()));
            } else if (!bindingIdentifier) {
                throw error(AbstractParser.message("expected.binding.identifier"), nameToken);
            } else {
                importEntries.add(Module.ImportEntry.importSpecifier(importName.getName()));
            }
            if (type == COMMARIGHT) {
                next();
            } else {
                break;
            }
        }
        expect(RBRACE);
        return importEntries;
    }

    /**
     * FromClause :
     *     from ModuleSpecifier
     */
    private String fromClause() {
        final long fromToken = token;
        final String name = (String) expectValue(IDENT);
        if (!"from".equals(name)) {
            throw error(AbstractParser.message("expected.from"), fromToken);
        }
        if (type == STRING || type == ESCSTRING) {
            final String moduleSpecifier = (String) getValue();
            next();
            return moduleSpecifier;
        } else {
            throw error(expectMessage(STRING));
        }
    }

    /**
     * Parse export declaration.
     *
     * ExportDeclaration :
     *     export * FromClause ;
     *     export ExportClause FromClause ;
     *     export ExportClause ;
     *     export VariableStatement
     *     export Declaration
     *     export default HoistableDeclaration[Default]
     *     export default ClassDeclaration[Default]
     *     export default [lookahead !in {function, class}] AssignmentExpression[In] ;
     */
    private void exportDeclaration() {
        expect(EXPORT);
        final ParserContextModuleNode module = lc.getCurrentModule();
        switch (type) {
            case MUL: {
                next();
                final String moduleRequest = fromClause();
                expect(SEMICOLON);
                module.addModuleRequest(moduleRequest);
                module.addStarExportEntry(Module.ExportEntry.exportStarFrom(moduleRequest));
                break;
            }
            case LBRACE: {
                final List<Module.ExportEntry> exportEntries = exportClause();
                if (type == IDENT && "from".equals(getValue())) {
                    final String moduleRequest = fromClause();
                    module.addModuleRequest(moduleRequest);
                    for (Module.ExportEntry exportEntry : exportEntries) {
                        module.addIndirectExportEntry(exportEntry.withFrom(moduleRequest));
                    }
                } else {
                    for (Module.ExportEntry exportEntry : exportEntries) {
                        module.addLocalExportEntry(exportEntry);
                    }
                }
                expect(SEMICOLON);
                break;
            }
            case DEFAULT:
                next();
                final Expression assignmentExpression;
                IdentNode ident;
                final int lineNumber = line;
                final long rhsToken = token;
                final boolean declaration;
                switch (type) {
                    case FUNCTION:
                        assignmentExpression = functionExpression(false, true);
                        ident = ((FunctionNode) assignmentExpression).getIdent();
                        declaration = true;
                        break;
                    case CLASS:
                        assignmentExpression = classDeclaration(true);
                        ident = ((ClassNode) assignmentExpression).getIdent();
                        declaration = true;
                        break;
                    default:
                        assignmentExpression = assignmentExpression(false);
                        ident = null;
                        declaration = false;
                        break;
                }
                if (ident != null) {
                    module.addLocalExportEntry(Module.ExportEntry.exportDefault(ident.getName()));
                } else {
                    ident = createIdentNode(Token.recast(rhsToken, IDENT), finish, Module.DEFAULT_EXPORT_BINDING_NAME);
                    lc.appendStatementToCurrentNode(new VarNode(lineNumber, Token.recast(rhsToken, LET), finish, ident, assignmentExpression));
                    if (!declaration) {
                        expect(SEMICOLON);
                    }
                    module.addLocalExportEntry(Module.ExportEntry.exportDefault());
                }
                break;
            case VAR:
            case LET:
            case CONST:
                final List<Statement> statements = lc.getCurrentBlock().getStatements();
                final int previousEnd = statements.size();
                variableStatement(type);
                for (final Statement statement : statements.subList(previousEnd, statements.size())) {
                    if (statement instanceof VarNode) {
                        module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(((VarNode) statement).getName().getName()));
                    }
                }
                break;
            case CLASS: {
                final ClassNode classDeclaration = classDeclaration(false);
                module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(classDeclaration.getIdent().getName()));
                break;
            }
            case FUNCTION: {
                final FunctionNode functionDeclaration = (FunctionNode) functionExpression(true, true);
                module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(functionDeclaration.getIdent().getName()));
                break;
            }
            default:
                throw error(AbstractParser.message("invalid.export"), token);
        }
    }

    /**
     * ExportClause :
     *     { }
     *     { ExportsList }
     *     { ExportsList , }
     * ExportsList :
     *     ExportSpecifier
     *     ExportsList , ExportSpecifier
     * ExportSpecifier :
     *     IdentifierName
     *     IdentifierName as IdentifierName
     *
     * @return a list of ExportSpecifiers
     */
    private List<Module.ExportEntry> exportClause() {
        assert type == LBRACE;
        next();
        List<Module.ExportEntry> exports = new ArrayList<>();
        while (type != RBRACE) {
            final IdentNode localName = getIdentifierName();
            if (type == IDENT && "as".equals(getValue())) {
                next();
                final IdentNode exportName = getIdentifierName();
                exports.add(Module.ExportEntry.exportSpecifier(exportName.getName(), localName.getName()));
            } else {
                exports.add(Module.ExportEntry.exportSpecifier(localName.getName()));
            }
            if (type == COMMARIGHT) {
                next();
            } else {
                break;
            }
        }
        expect(RBRACE);
        return exports;
    }

    @Override
    public String toString() {
        return "'JavaScript Parsing'";
    }

    private static void markEval(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        boolean flaggedCurrentFn = false;
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!flaggedCurrentFn) {
                fn.setFlag(FunctionNode.HAS_EVAL);
                flaggedCurrentFn = true;
                if (fn.getKind() == FunctionNode.Kind.ARROW) {
                    // possible use of this in an eval that's nested in an arrow function, e.g.:
                    // function fun(){ return (() => eval("this"))(); };
                    markThis(lc);
                    markNewTarget(lc);
                }
            } else {
                fn.setFlag(FunctionNode.HAS_NESTED_EVAL);
            }
            final ParserContextBlockNode body = lc.getFunctionBody(fn);
            // NOTE: it is crucial to mark the body of the outer function as needing scope even when we skip
            // parsing a nested function. functionBody() contains code to compensate for the lack of invoking
            // this method when the parser skips a nested function.
            body.setFlag(Block.NEEDS_SCOPE);
            fn.setFlag(FunctionNode.HAS_SCOPE_BLOCK);
        }
    }

    private void prependStatement(final Statement statement) {
        lc.prependStatementToCurrentNode(statement);
    }

    private void appendStatement(final Statement statement) {
        lc.appendStatementToCurrentNode(statement);
    }

    private static void markSuperCall(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (fn.getKind() != FunctionNode.Kind.ARROW) {
                assert fn.isSubclassConstructor();
                fn.setFlag(FunctionNode.ES6_HAS_DIRECT_SUPER);
                break;
            }
        }
    }

    private ParserContextFunctionNode getCurrentNonArrowFunction() {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (fn.getKind() != FunctionNode.Kind.ARROW) {
                return fn;
            }
        }
        return null;
    }

    private static void markThis(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            fn.setFlag(FunctionNode.USES_THIS);
            if (fn.getKind() != FunctionNode.Kind.ARROW) {
                break;
            }
        }
    }

    private static void markNewTarget(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (fn.getKind() != FunctionNode.Kind.ARROW) {
                if (!fn.isProgram()) {
                    fn.setFlag(FunctionNode.ES6_USES_NEW_TARGET);
                }
                break;
            }
        }
    }

    private boolean inGeneratorFunction() {
        return lc.getCurrentFunction().getKind() == FunctionNode.Kind.GENERATOR;
    }
}
