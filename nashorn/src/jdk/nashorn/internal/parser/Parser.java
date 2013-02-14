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

package jdk.nashorn.internal.parser;

import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.EVAL;
import static jdk.nashorn.internal.codegen.CompilerConstants.FUNCTION_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.RUN_SCRIPT;
import static jdk.nashorn.internal.parser.TokenType.ASSIGN;
import static jdk.nashorn.internal.parser.TokenType.CASE;
import static jdk.nashorn.internal.parser.TokenType.CATCH;
import static jdk.nashorn.internal.parser.TokenType.COLON;
import static jdk.nashorn.internal.parser.TokenType.COMMARIGHT;
import static jdk.nashorn.internal.parser.TokenType.DECPOSTFIX;
import static jdk.nashorn.internal.parser.TokenType.DECPREFIX;
import static jdk.nashorn.internal.parser.TokenType.ELSE;
import static jdk.nashorn.internal.parser.TokenType.EOF;
import static jdk.nashorn.internal.parser.TokenType.EOL;
import static jdk.nashorn.internal.parser.TokenType.FINALLY;
import static jdk.nashorn.internal.parser.TokenType.FUNCTION;
import static jdk.nashorn.internal.parser.TokenType.IDENT;
import static jdk.nashorn.internal.parser.TokenType.IF;
import static jdk.nashorn.internal.parser.TokenType.INCPOSTFIX;
import static jdk.nashorn.internal.parser.TokenType.LBRACE;
import static jdk.nashorn.internal.parser.TokenType.LPAREN;
import static jdk.nashorn.internal.parser.TokenType.RBRACE;
import static jdk.nashorn.internal.parser.TokenType.RBRACKET;
import static jdk.nashorn.internal.parser.TokenType.RPAREN;
import static jdk.nashorn.internal.parser.TokenType.SEMICOLON;
import static jdk.nashorn.internal.parser.TokenType.TERNARY;
import static jdk.nashorn.internal.parser.TokenType.WHILE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.BreakableNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyKey;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSErrorType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.ScriptingFunctions;

/**
 * Builds the IR.
 *
 */
public class Parser extends AbstractParser {
    /** Current context. */
    private final Context context;

    /** Is scripting mode. */
    private final boolean scripting;

    /** Top level script being parsed. */
    private FunctionNode script;

    /** Current function being parsed. */
    private FunctionNode function;

    /** Current parsing block. */
    private Block block;

    /** Namespace for function names where not explicitly given */
    private final Namespace namespace;

    /**
     * Constructor
     *
     * @param context parser context
     * @param source  source to parse
     * @param errors  error manager
     */
    public Parser(final Context context, final Source source, final ErrorManager errors) {
        this(context, source, errors, context._strict);
    }

    /**
     * Construct a parser.
     *
     * @param context parser context
     * @param source  source to parse
     * @param errors  error manager
     * @param strict  parser created with strict mode enabled.
     */
    public Parser(final Context context, final Source source, final ErrorManager errors, final boolean strict) {
        super(source, errors, strict);
        this.context   = context;
        this.namespace = new Namespace(context.getNamespace());
        this.scripting = context._scripting;
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * This is the default parse call, which will name the function node
     * "runScript" {@link CompilerConstants#RUN_SCRIPT}
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse() {
        return parse(RUN_SCRIPT.tag());
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * @param scriptName name for the script, given to the parsed FunctionNode
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse(final String scriptName) {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, stream, scripting && !context._no_syntax_extensions);

            // Set up first token (skips opening EOL.)
            k = -1;
            next();

            // Begin parse.
            return program(scriptName);
        } catch (final Exception e) {
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

            if (context._dump_on_error) {
                e.printStackTrace(context.getErr());
            }

            return null;
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

            if (context._dump_on_error) {
                e.printStackTrace(context.getErr());
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
    private Block newBlock() {
        return block = new Block(source, token, Token.descPosition(token), block, function);
    }

    /**
     * Set up a new function block.
     *
     * @param ident Name of function.
     * @return New block.
     */
    private FunctionNode newFunctionBlock(final IdentNode ident) {
        // Build function name.
        final StringBuilder sb = new StringBuilder();

        if (block != null) {
            block.addParentName(sb);
        }

        sb.append(ident != null ? ident.getName() : FUNCTION_PREFIX.tag());
        final String name = namespace.uniqueName(sb.toString());
        assert function != null || name.equals(RUN_SCRIPT.tag())  : "name = " + name;// must not rename runScript().

        // Start new block.
        final FunctionNode functionBlock = new FunctionNode(source, token, Token.descPosition(token), namespace, block, ident, name);
        block = function = functionBlock;
        function.setStrictMode(isStrictMode);

        return functionBlock;
    }

    /**
     * Restore the current block.
     */
    private void restoreBlock() {
        block = block.getParent();
        function = block.getFunction();
    }

    /**
     * Get the statements in a block.
     * @return Block statements.
     */
    private Block getBlock(final boolean needsBraces) {
        // Set up new block. Captures LBRACE.
        final Block newBlock = newBlock();
        pushControlNode(newBlock);

        // Block opening brace.
        if (needsBraces) {
            expect(LBRACE);
        }

        try {
            // Accumulate block statements.
            statementList();
        } finally {
            restoreBlock();
            popControlNode();
        }

    final int possibleEnd = Token.descPosition(token) + Token.descLength(token);

        // Block closing brace.
        if (needsBraces) {
            expect(RBRACE);
        }

        newBlock.setFinish(possibleEnd);

        return newBlock;
    }

    /**
     * Get all the statements generated by a single statement.
     * @return Statements.
     */
    private Block getStatement() {
        if (type == LBRACE) {
            return getBlock(true);
        }
        // Set up new block. Captures first token.
        final Block newBlock = newBlock();

        try {
            // Accumulate statements.
            statement();
        } finally {
            restoreBlock();
        }

        return newBlock;
    }

    /**
     * Detect calls to special functions.
     * @param ident Called function.
     */
    private void detectSpecialFunction(final IdentNode ident) {
        final String name = ident.getName();

        if (EVAL.tag().equals(name)) {
            function.setHasEval();
        }
    }

    /**
     * Detect use of special properties.
     * @param ident Referenced property.
     */
    private void detectSpecialProperty(final IdentNode ident) {
        final String name = ident.getName();

        if (ARGUMENTS.tag().equals(name)) {
            function.setUsesArguments();
        }
    }

    /**
     * Tells whether a IdentNode can be used as L-value of an assignment
     *
     * @param ident IdentNode to be checked
     * @return whether the ident can be used as L-value
     */
    private static boolean checkIdentLValue(final IdentNode ident) {
        return Token.descType(ident.getToken()).getKind() != TokenKind.KEYWORD;
    }

    /**
     * Verify an assignment expression.
     * @param op  Operation token.
     * @param lhs Left hand side expression.
     * @param rhs Right hand side expression.
     * @return Verified expression.
     */
    private Node verifyAssignment(final long op, final Node lhs, final Node rhs) {
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
            if (!(lhs instanceof AccessNode ||
                  lhs instanceof IndexNode ||
                  lhs instanceof IdentNode)) {
                if (context._early_lvalue_error) {
                    error(JSErrorType.REFERENCE_ERROR, AbstractParser.message("invalid.lvalue"), lhs.getToken());
                }
                return referenceError(lhs, rhs);
            }

            if (lhs instanceof IdentNode) {
                if (! checkIdentLValue((IdentNode)lhs)) {
                    return referenceError(lhs, rhs);
                }
                verifyStrictIdent((IdentNode)lhs, "assignment");
            }
            break;

        default:
            break;
        }

        // Build up node.
        return new BinaryNode(source, op, lhs, rhs);
    }

    /**
     * Reduce increment/decrement to simpler operations.
     * @param firstToken First token.
     * @param tokenType  Operation token (INCPREFIX/DEC.)
     * @param expression Left hand side expression.
     * @param isPostfix  Prefix or postfix.
     * @return           Reduced expression.
     */
    private Node incDecExpression(final long firstToken, final TokenType tokenType, final Node expression, final boolean isPostfix) {
        long incDecToken = firstToken;
        if (isPostfix) {
            incDecToken = Token.recast(incDecToken, tokenType == DECPREFIX ? DECPOSTFIX : INCPOSTFIX);
        }

        final UnaryNode node = new UnaryNode(source, incDecToken, expression);
        if (isPostfix) {
            node.setStart(expression.getStart());
            node.setFinish(Token.descPosition(incDecToken) + Token.descLength(incDecToken));
        }

        return node;
    }

    /**
     * Find a label node in the label stack.
     * @param ident Ident to find.
     * @return null or the found label node.
     */
    private LabelNode findLabel(final IdentNode ident) {
        for (final LabelNode labelNode : function.getLabelStack()) {
            if (labelNode.getLabel().equals(ident)) {
                return labelNode;
            }
        }

        return null;
    }

    /**
     * Add a label to the label stack.
     * @param labelNode Label to add.
     */
    private void pushLabel(final LabelNode labelNode) {
        function.getLabelStack().push(labelNode);
    }

    /**
      * Remove a label from the label stack.
      */
    private void popLabel() {
        function.getLabelStack().pop();
    }

    /**
     * Track the current nesting of controls for break and continue.
     * @param node For, while, do or switch node.
     */
    private void pushControlNode(final Node node) {
        final boolean isLoop = node instanceof WhileNode;
        function.getControlStack().push(node);

        for (final LabelNode labelNode : function.getLabelStack()) {
            if (labelNode.getBreakNode() == null) {
                labelNode.setBreakNode(node);
            }

            if (isLoop && labelNode.getContinueNode() == null) {
                labelNode.setContinueNode(node);
            }
        }
    }

    /**
     * Finish with control.
     */
    private void popControlNode() {
        // Get control stack.
        final Stack<Node> controlStack = function.getControlStack();

        // Can be empty if missing brace.
        if (!controlStack.isEmpty()) {
            controlStack.pop();
        }
    }

    private void popControlNode(final Node node) {
        // Get control stack.
        final Stack<Node> controlStack = function.getControlStack();

        // Can be empty if missing brace.
        if (!controlStack.isEmpty() && controlStack.peek() == node) {
            controlStack.pop();
        }
    }

    private boolean isInWithBlock() {
        final Stack<Node> controlStack = function.getControlStack();
        for (int i = controlStack.size() - 1; i >= 0; i--) {
            final Node node = controlStack.get(i);

            if (node instanceof WithNode) {
                return true;
            }
        }

        return false;
    }

    private <T extends Node> T findControl(final Class<T> ctype) {
        final Stack<Node> controlStack = function.getControlStack();
        for (int i = controlStack.size() - 1; i >= 0; i--) {
            final Node node = controlStack.get(i);

            if (ctype.isAssignableFrom(node.getClass())) {
                return ctype.cast(node);
            }
        }

        return null;
    }

    private <T extends Node> List<T> findControls(final Class<T> ctype, final Node to) {
        final List<T> nodes = new ArrayList<>();
        final Stack<Node> controlStack = function.getControlStack();
        for (int i = controlStack.size() - 1; i >= 0; i--) {
            final Node node = controlStack.get(i);

            if (to == node) {
                break; //stop looking
            }

            if (ctype.isAssignableFrom(node.getClass())) {
                nodes.add(ctype.cast(node));
            }
        }

        return nodes;
    }

    private <T extends Node> int countControls(final Class<T> ctype, final Node to) {
        return findControls(ctype, to).size();
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
    private FunctionNode program(final String scriptName) {
        // Make a fake token for the script.
        final long functionToken = Token.toDesc(FUNCTION, 0, source.getLength());
        // Set up the script to append elements.
        script = newFunctionBlock(new IdentNode(source, functionToken, Token.descPosition(functionToken), scriptName));
        // set kind to be SCRIPT
        script.setKind(FunctionNode.Kind.SCRIPT);
        // Set the first token of the script.
        script.setFirstToken(functionToken);
        // Gather source elements.
        sourceElements();
        expect(EOF);
        // Set the last token of the script.
        script.setLastToken(token);
        script.setFinish(source.getLength() - 1);

        return script;
    }

    /**
     * Directive value or null if statement is not a directive.
     *
     * @param stmt Statement to be checked
     * @return Directive value if the given statement is a directive
     */
    private String getDirective(final Node stmt) {
        if (stmt instanceof ExecuteNode) {
            final Node expr = ((ExecuteNode)stmt).getExpression();
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
     * Return last node in a statement list.
     *
     * @param statements Statement list.
     *
     * @return Last (non-debug) statement or null if empty block.
     */
    private static Node lastStatement(final List<Node> statements) {
        for (int lastIndex = statements.size() - 1; lastIndex >= 0; lastIndex--) {
            final Node node = statements.get(lastIndex);
            if (!node.isDebug()) {
                return node;
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
    private void sourceElements() {
        List<Node>    directiveStmts = null;
        boolean       checkDirective = true;
        final boolean oldStrictMode = isStrictMode;

        try {
            // If is a script, then process until the end of the script.
            while (type != EOF) {
                // Break if the end of a code block.
                if (type == RBRACE) {
                    break;
                }

                try {
                    // Get the next element.
                    statement(true);

                    // check for directive prologues
                    if (checkDirective) {
                        // skip any debug statement like line number to get actual first line
                        final Node lastStatement = lastStatement(block.getStatements());

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
                                function.setStrictMode(true);

                                // We don't need to check these, if lexical environment is already strict
                                if (!oldStrictMode && directiveStmts != null) {
                                    // check that directives preceding this one do not violate strictness
                                    for (final Node statement : directiveStmts) {
                                        // the get value will force unescape of preceeding
                                        // escaped string directives
                                        getValue(statement.getToken());
                                    }

                                    // verify that function name as well as parameter names
                                    // satisfy strict mode restrictions.
                                    verifyStrictIdent(function.getIdent(), "function name");
                                    for (final IdentNode param : function.getParameters()) {
                                        verifyStrictIdent(param, "function parameter");
                                    }
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    // Recover parsing.
                    recover(e);
                }

                // No backtracking from here on.
                stream.commit(k);
            }
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    /**
     * Statement :
     *      Block
     *      VariableStatement
     *      EmptyStatement
     *      ExpressionStatement
     *      IfStatement
     *      IterationStatement
     *      ContinueStatement
     *      BreakStatement
     *      ReturnStatement
     *      WithStatement
     *      LabelledStatement
     *      SwitchStatement
     *      ThrowStatement
     *      TryStatement
     *      DebuggerStatement
     *
     * see 12
     *
     * Parse any of the basic statement types.
     */
    private void statement() {
        statement(false);
    }

    /**
     * @param topLevel does this statement occur at the "top level" of a script or a function?
     */
    private void statement(final boolean topLevel) {
        final LineNumberNode lineNumberNode = lineNumber();

        if (type == FUNCTION) {
            // As per spec (ECMA section 12), function declarations as arbitrary statement
            // is not "portable". Implementation can issue a warning or disallow the same.
            if (isStrictMode && !topLevel) {
                error(AbstractParser.message("strict.no.func.here"), token);
            }
            functionExpression(true);
            return;
        }

        block.addStatement(lineNumberNode);

        switch (type) {
        case LBRACE:
            block();
            break;
        case RBRACE:
            break;
        case VAR:
            variableStatement(true);
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
        case YIELD:
            yieldStatement();
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
        default:
            if (type == IDENT || isNonStrictModeIdent()) {
                if (T(k + 1) == COLON) {
                    labelStatement();
                    return;
                }
            }

            expressionStatement();
            break;
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
        // Get statements in block.
        final Block newBlock = getBlock(true);

        // Force block execution.
        final ExecuteNode executeNode = new ExecuteNode(source, newBlock.getToken(), finish, newBlock);

        block.addStatement(executeNode);
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
     * Make sure that in strict mode, the identifier name used is allowed.
     *
     * @param ident         Identifier that is verified
     * @param contextString String used in error message to give context to the user
     */
    @SuppressWarnings("fallthrough")
    private void verifyStrictIdent(final IdentNode ident, final String contextString) {
        if (isStrictMode) {
            switch (ident.getName()) {
            case "eval":
            case "arguments":
                error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
            default:
                break;
            }
        }
    }

    /**
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
    private List<VarNode> variableStatement(final boolean isStatement) {
        // VAR tested in caller.
        next();

        final List<VarNode> vars = new ArrayList<>();

        while (true) {
            // Get starting token.
            final long varToken = token;
            // Get name of var.
            final IdentNode name = getIdent();
            verifyStrictIdent(name, "variable name");

            // Assume no init.
            Node init = null;

            // Look for initializer assignment.
            if (type == ASSIGN) {
                next();

                // Get initializer expression. Suppress IN if not statement.
                init = assignmentExpression(!isStatement);
            }

            // Allocate var node.
            final VarNode var = new VarNode(source, varToken, finish, name, init);
            if (isStatement) {
                function.addDeclaration(var);
            }

            vars.add(var);
            // Add to current block.
            block.addStatement(var);

            if (type != COMMARIGHT) {
                break;
            }
            next();
        }

        // If is a statement then handle end of line.
        if (isStatement) {
            boolean semicolon = type == SEMICOLON;
            endOfLine();
            if (semicolon) {
                block.setFinish(finish);
            }
        }

        return vars;
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
        if (context._empty_statements) {
            block.addStatement(new EmptyNode(source, token,
                    Token.descPosition(token) + Token.descLength(token)));
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
        final long expressionToken = token;

        // Get expression and add as statement.
        final Node expression = expression();

        ExecuteNode executeNode = null;
        if (expression != null) {
            executeNode = new ExecuteNode(source, expressionToken, finish, expression);
            block.addStatement(executeNode);
        } else {
            expect(null);
        }

        endOfLine();

        if (executeNode != null) {
            executeNode.setFinish(finish);
            block.setFinish(finish);
        }
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
        final long ifToken = token;
         // IF tested in caller.
        next();

        expect(LPAREN);

        // Get the test expression.
        final Node test = expression();

        expect(RPAREN);

        // Get the pass statement.
        final Block pass = getStatement();

        // Assume no else.
        Block fail = null;

        if (type == ELSE) {
            next();

            // Get the else block.
            fail = getStatement();
        }

        // Construct and add new if node.
        final IfNode ifNode = new IfNode(source, ifToken, fail != null ? fail.getFinish() : pass.getFinish(), test, pass, fail);

        block.addStatement(ifNode);
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
    private void forStatement() {
        // Create FOR node, capturing FOR token.
        final ForNode forNode = new ForNode(source, token, Token.descPosition(token));

        pushControlNode(forNode);

        // Set up new block for scope of vars. Captures first token.
        final Block outer = newBlock();

        try {
            // FOR tested in caller.
            next();

            // Nashorn extension: for each expression.
            // iterate property values rather than property names.
            if (!context._no_syntax_extensions && type == IDENT && "each".equals(getValue())) {
                forNode.setIsForEach();
                next();
            }

            expect(LPAREN);

            /// Capture control information.
            forControl(forNode);

            expect(RPAREN);

            // Set the for body.
            final Block body = getStatement();
            forNode.setBody(body);
            forNode.setFinish(body.getFinish());
            outer.setFinish(body.getFinish());

            // Add for to current block.
            block.addStatement(forNode);
        } finally {
            restoreBlock();
            popControlNode();
        }

        block.addStatement(new ExecuteNode(source, outer.getToken(), outer.getFinish(), outer));
     }

    /**
     * ... IterationStatement :
     *           ...
     *           Expression[NoIn]?; Expression? ; Expression?
     *           var VariableDeclarationList[NoIn]; Expression? ; Expression?
     *           LeftHandSideExpression in Expression
     *           var VariableDeclaration[NoIn] in Expression
     *
     * See 12.6
     *
     * Parse the control section of a FOR statement.  Also used for
     * comprehensions.
     * @param forNode Owning FOR.
     */
    private void forControl(final ForNode forNode) {
        List<VarNode> vars = null;

        switch (type) {
        case VAR:
            // Var statements captured in for outer block.
            vars = variableStatement(false);
            break;

        case SEMICOLON:
            break;

        default:
            final Node expression = expression(unaryExpression(), COMMARIGHT.getPrecedence(), true);
            forNode.setInit(expression);
        }

        switch (type) {
        case SEMICOLON:
            // for (init; test; modify)
            expect(SEMICOLON);

            // Get the test expression.
            if (type != SEMICOLON) {
                forNode.setTest(expression());
            }

            expect(SEMICOLON);

            // Get the modify expression.
            if (type != RPAREN) {
                final Node expression = expression();
                forNode.setModify(expression);
            }

            break;

        case IN:
            forNode.setIsForIn();

            if (vars != null) {
                // for (var i in obj)
                if (vars.size() == 1) {
                    forNode.setInit(new IdentNode(vars.get(0).getName()));
                } else {
                    // for (var i, j in obj) is invalid
                    error(AbstractParser.message("many.vars.in.for.in.loop"), vars.get(1).getToken());
                }

            } else {
                // for (expr in obj)
                final Node init = forNode.getInit();
                assert init != null : "for..in init expression can not be null here";

                // check if initial expression is a valid L-value
                if (!(init instanceof AccessNode ||
                      init instanceof IndexNode ||
                      init instanceof IdentNode)) {
                    error(AbstractParser.message("not.lvalue.for.in.loop"), init.getToken());
                }

                if (init instanceof IdentNode) {
                    if (! checkIdentLValue((IdentNode)init)) {
                        error(AbstractParser.message("not.lvalue.for.in.loop"), init.getToken());
                    }
                    verifyStrictIdent((IdentNode)init, "for-in iterator");
                }
            }

            next();

            // Get the collection expression.
            forNode.setModify(expression());
            break;

        default:
            expect(SEMICOLON);
            break;
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
        // WHILE tested in caller.
        next();

        // Construct WHILE node.
        final WhileNode whileNode = new WhileNode(source, whileToken, Token.descPosition(whileToken));
        pushControlNode(whileNode);

        try {
            expect(LPAREN);

            // Get the test expression.
            final Node test = expression();
            whileNode.setTest(test);

            expect(RPAREN);

            // Get WHILE body.
            final Block statements = getStatement();
            whileNode.setBody(statements);
            whileNode.setFinish(statements.getFinish());

            // Add WHILE node.
            block.addStatement(whileNode);
        } finally {
            popControlNode();
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
        // DO tested in the caller.
        next();

        final WhileNode doWhileNode = new DoWhileNode(source, doToken, Token.descPosition(doToken));
        pushControlNode(doWhileNode);

        try {
           // Get DO body.
            final Block statements = getStatement();
            doWhileNode.setBody(statements);

            expect(WHILE);

            expect(LPAREN);

            // Get the test expression.
            final Node test = expression();
            doWhileNode.setTest(test);

            expect(RPAREN);

            if (type == SEMICOLON) {
                endOfLine();
            }

            doWhileNode.setFinish(finish);

            // Add DO node.
            block.addStatement(doWhileNode);
        } finally {
            popControlNode();
        }
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
        final long continueToken = token;
        // CONTINUE tested in caller.
        nextOrEOL();

        LabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = findLabel(ident);

            if (labelNode == null) {
                error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        final Node targetNode = labelNode != null ? labelNode.getContinueNode() : findControl(WhileNode.class);

        if (targetNode == null) {
            error(AbstractParser.message("illegal.continue.stmt"), continueToken);
        }

        endOfLine();

        // Construct and add CONTINUE node.
        final ContinueNode continueNode = new ContinueNode(source, continueToken, finish, labelNode, targetNode, findControl(TryNode.class));
        continueNode.setScopeNestingLevel(countControls(WithNode.class, targetNode));

        block.addStatement(continueNode);
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
        final long breakToken = token;
        // BREAK tested in caller.
        nextOrEOL();

        LabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = findLabel(ident);

            if (labelNode == null) {
                error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        final Node targetNode = labelNode != null ? labelNode.getBreakNode() : findControl(BreakableNode.class);

        if (targetNode == null) {
            error(AbstractParser.message("illegal.break.stmt"), breakToken);
        }

        endOfLine();

        // Construct and add BREAK node.
        final BreakNode breakNode = new BreakNode(source, breakToken, finish, labelNode, targetNode, findControl(TryNode.class));
        breakNode.setScopeNestingLevel(countControls(WithNode.class, targetNode));

        block.addStatement(breakNode);
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
        if (function.getKind() == FunctionNode.Kind.SCRIPT) {
            error(AbstractParser.message("invalid.return"));
        }

        // Capture RETURN token.
        final long returnToken = token;
        // RETURN tested in caller.
        nextOrEOL();

        Node expression = null;

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

        endOfLine();

        // Construct and add RETURN node.
        final ReturnNode returnNode = new ReturnNode(source, returnToken, finish, expression, findControl(TryNode.class));
        block.addStatement(returnNode);
    }

    /**
     * YieldStatement :
     *      yield Expression? ; // [no LineTerminator here]
     *
     * JavaScript 1.8
     *
     * Parse YIELD statement.
     */
    private void yieldStatement() {
        // Capture YIELD token.
        final long yieldToken = token;
        // YIELD tested in caller.
        nextOrEOL();

        Node expression = null;

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

        endOfLine();

        // Construct and add YIELD node.
        final ReturnNode yieldNode = new ReturnNode(source, yieldToken, finish, expression, findControl(TryNode.class));
        block.addStatement(yieldNode);
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
        final long withToken = token;
        // WITH tested in caller.
        next();

        // ECMA 12.10.1 strict mode restrictions
        if (isStrictMode) {
            error(AbstractParser.message("strict.no.with"), withToken);
        }

        // Get WITH expression.
        final WithNode withNode = new WithNode(source, withToken, finish, null, null);
        function.setHasWith();

        try {
            pushControlNode(withNode);

            expect(LPAREN);

            final Node expression = expression();
            withNode.setExpression(expression);

            expect(RPAREN);

            // Get WITH body.
            final Block statements = getStatement();
            withNode.setBody(statements);
            withNode.setFinish(finish);
        } finally {
            popControlNode(withNode);
        }

        block.addStatement(withNode);
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
        // Capture SWITCH token.
        final long switchToken = token;
        // SWITCH tested in caller.
        next();

        // Create and add switch statement.
        final SwitchNode switchNode = new SwitchNode(source, switchToken, Token.descPosition(switchToken));
        pushControlNode(switchNode);

        try {
            expect(LPAREN);

            // Get switch expression.
            final Node switchExpression = expression();
            switchNode.setExpression(switchExpression);

            expect(RPAREN);

            expect(LBRACE);

            // Prepare to accumulate cases.
            final List<CaseNode> cases = new ArrayList<>();
            CaseNode defaultCase = null;

            while (type != RBRACE) {
                // Prepare for next case.
                Node caseExpression = null;
                final long caseToken = token;

                switch (type) {
                case CASE:
                    next();

                    // Get case expression.
                    caseExpression = expression();

                    break;

                case DEFAULT:
                    if (defaultCase != null) {
                        error(AbstractParser.message("duplicate.default.in.switch"));
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
                final Block statements = getBlock(false);
                final CaseNode caseNode = new CaseNode(source, caseToken, finish, caseExpression, statements);
                statements.setFinish(finish);

                if (caseExpression == null) {
                    defaultCase = caseNode;
                }

                cases.add(caseNode);
            }

            switchNode.setCases(cases);
            switchNode.setDefaultCase(defaultCase);

            next();

            switchNode.setFinish(finish);

            block.addStatement(switchNode);
        } finally {
            popControlNode();
        }
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

        if (findLabel(ident) != null) {
            error(AbstractParser.message("duplicate.label", ident.getName()), labelToken);
        }

        try {
            // Create and add label.
            final LabelNode labelNode = new LabelNode(source, labelToken, finish, ident, null);
            pushLabel(labelNode);
            // Get and save body.
            final Block statements = getStatement();
            labelNode.setBody(statements);
            labelNode.setFinish(finish);

            block.addStatement(labelNode);
        } finally {
            // Remove label.
            popLabel();
        }
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
        final long throwToken = token;
        // THROW tested in caller.
        nextOrEOL();

        Node expression = null;

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
            error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        endOfLine();

        // Construct and add THROW node.
        final ThrowNode throwNode = new ThrowNode(source, throwToken, finish, expression, findControl(TryNode.class));
        block.addStatement(throwNode);
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
        final long tryToken = token;
        // TRY tested in caller.
        next();

        // Create try.
        final TryNode tryNode = new TryNode(source, tryToken, Token.descPosition(tryToken), findControl(TryNode.class));
        pushControlNode(tryNode);

        try {
            // Get TRY body.
            final Block tryBody = getBlock(true);

            // Prepare to accumulate catches.
            final List<Block> catchBlocks = new ArrayList<>();

            while (type == CATCH) {
                // Capture CATCH token.
                final long catchToken = token;
                next();

                expect(LPAREN);

                // Get exception ident.
                final IdentNode exception = getIdent();

                // ECMA 12.4.1 strict mode restrictions
                verifyStrictIdent(exception, "catch argument");

                // Check for conditional catch.
                Node ifExpression = null;

                if (type == IF) {
                    next();

                    // Get the exception condition.
                    ifExpression = expression();
                }

                expect(RPAREN);

                try {
                    final Block catchBlock = newBlock();

                    // Get CATCH body.
                    final Block catchBody = getBlock(true);

                    // Create and add catch.
                    final CatchNode catchNode = new CatchNode(source, catchToken, finish, exception, ifExpression, catchBody);
                    block.addStatement(catchNode);
                    catchBlocks.add(catchBlock);
                } finally {
                    restoreBlock();
                }

                // If unconditional catch then should to be the end.
                if (ifExpression == null) {
                    break;
                }
            }

            popControlNode();

            // Prepare to capture finally statement.
            Block finallyStatements = null;

            if (type == FINALLY) {
                next();

                // Get FINALLY body.
                finallyStatements = getBlock(true);
            }

            // Need at least one catch or a finally.
            if (catchBlocks.isEmpty() && finallyStatements == null) {
                error(AbstractParser.message("missing.catch.or.finally"), tryToken);
            }

            tryNode.setBody(tryBody);
            tryNode.setCatchBlocks(catchBlocks);
            tryNode.setFinallyBody(finallyStatements);
            tryNode.setFinish(finish);

            // Add try.
            block.addStatement(tryNode);
        } finally {
            popControlNode(tryNode);
        }
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
        final long debuggerToken = token;
        // DEBUGGER tested in caller.
        next();

        endOfLine();

        final RuntimeNode runtimeNode = new RuntimeNode(source, debuggerToken, finish, RuntimeNode.Request.DEBUGGER, new ArrayList<Node>());
        block.addStatement(runtimeNode);
    }

    /**
     * PrimaryExpression :
     *      this
     *      Identifier
     *      Literal
     *      ArrayLiteral
     *      ObjectLiteral
     *      ( Expression )
     *
     *  See 11.1
     *
     * Parse primary expression.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private Node primaryExpression() {
        // Capture first token.
        final long primaryToken = token;

        switch (type) {
        case THIS:
            final String name = type.getName();
            next();
            return new IdentNode(source, primaryToken, finish, name);
        case IDENT:
            final IdentNode ident = getIdent();
            if (ident == null) {
                break;
            }
            detectSpecialProperty(ident);
            return ident;
        case OCTAL:
            if (isStrictMode) {
               error(AbstractParser.message("strict.no.octal"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case FLOATING:
        case REGEX:
        case XML:
            return getLiteral();
        case EXECSTRING:
            return execString(primaryToken);
        case FALSE:
            next();
            return LiteralNode.newInstance(source, primaryToken, finish, false);
        case TRUE:
            next();
            return LiteralNode.newInstance(source, primaryToken, finish, true);
        case NULL:
            next();
            return LiteralNode.newInstance(source, primaryToken, finish);
        case LBRACKET:
            return arrayLiteral();
        case LBRACE:
            return objectLiteral();
        case LPAREN:
            next();

            final Node expression = expression();

            expect(RPAREN);

            return expression;

        default:
            // In this context some operator tokens mark the start of a literal.
            if (lexer.scanLiteral(primaryToken, type)) {
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
    Node execString(final long primaryToken) {
        // Synthesize an ident to call $EXEC.
        final IdentNode execIdent = new IdentNode(source, primaryToken, finish, ScriptingFunctions.EXEC_NAME);
        // Skip over EXECSTRING.
        next();
        // Set up argument list for call.
        final List<Node> arguments = new ArrayList<>();
        // Skip beginning of edit string expression.
        expect(LBRACE);
        // Add the following expression to arguments.
        arguments.add(expression());
        // Skip ending of edit string expression.
        expect(RBRACE);

        return new CallNode(source, primaryToken, finish, execIdent, arguments);
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
    private Node arrayLiteral() {
        // Capture LBRACKET token.
        final long arrayToken = token;
        // LBRACKET tested in caller.
        next();

        // Prepare to accummulating elements.
        final List<Node> elements = new ArrayList<>();
        // Track elisions.
        boolean elision = true;
loop:
        while (true) {
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

            default:
                if (! elision) {
                    error(AbstractParser.message("expected.comma", type.getNameOrType()));
                }
                // Add expression element.
                final Node expression = assignmentExpression(false);

                if (expression != null) {
                    elements.add(expression);
                } else {
                    expect(RBRACKET);
                }

                elision = false;
                break;
            }
        }

        return LiteralNode.newInstance(source, arrayToken, finish, elements);
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
    private Node objectLiteral() {
        // Capture LBRACE token.
        final long objectToken = token;
        // LBRACE tested in caller.
        next();

        // Object context.
        Block objectContext = null;
        // Prepare to accumulate elements.
        final List<Node> elements = new ArrayList<>();
        final Map<Object, PropertyNode> map = new HashMap<>();

        try {
            // Create a block for the object literal.
            objectContext = newBlock();

            boolean commaSeen = true;
loop:
            while (true) {
                switch (type) {
                case RBRACE:
                    next();
                    break loop;

                case COMMARIGHT:
                    next();
                    commaSeen = true;
                    break;

                default:
                    if (! commaSeen) {
                        error(AbstractParser.message("expected.comma", type.getNameOrType()));
                    }

                    commaSeen = false;
                    // Get and add the next property.
                    final PropertyNode property = propertyAssignment();
                    final Object key = property.getKeyName();
                    final PropertyNode existingProperty = map.get(key);

                    if (existingProperty != null) {
                        // ECMA section 11.1.5 Object Initialiser
                        // point # 4 on property assignment production
                        final Node value  = property.getValue();
                        final Node getter = property.getGetter();
                        final Node setter = property.getSetter();

                        final Node prevValue  = existingProperty.getValue();
                        final Node prevGetter = existingProperty.getGetter();
                        final Node prevSetter = existingProperty.getSetter();

                        boolean redefinitionOk = true;
                        // ECMA 11.1.5 strict mode restrictions
                        if (isStrictMode) {
                            if (value != null && prevValue != null) {
                                redefinitionOk = false;
                            }
                        }

                        final boolean isPrevAccessor = prevGetter != null || prevSetter != null;
                        final boolean isAccessor = getter != null || setter != null;

                        // data property redefined as accessor property
                        if (prevValue != null && isAccessor) {
                            redefinitionOk = false;
                        }

                        // accessor property redefined as data
                        if (isPrevAccessor && value != null) {
                            redefinitionOk = false;
                        }

                        if (isAccessor && isPrevAccessor) {
                            if (getter != null && prevGetter != null ||
                                setter != null && prevSetter != null) {
                                redefinitionOk = false;
                            }
                        }

                        if (! redefinitionOk) {
                            error(AbstractParser.message("property.redefinition", key.toString()), property.getToken());
                        }

                        if (value != null) {
                            final Node existingValue = existingProperty.getValue();

                            if (existingValue == null) {
                                existingProperty.setValue(value);
                            } else {
                                final long propertyToken = Token.recast(existingProperty.getToken(), COMMARIGHT);
                                existingProperty.setValue(new BinaryNode(source, propertyToken, existingValue, value));
                            }

                            existingProperty.setGetter(null);
                            existingProperty.setSetter(null);
                        }

                        if (getter != null) {
                            existingProperty.setGetter(getter);
                        }

                        if (setter != null) {
                            existingProperty.setSetter(setter);
                        }
                    } else {
                        map.put(key, property);
                        elements.add(property);
                    }

                    break;
                }
            }
        } finally {
            restoreBlock();
        }

        // Construct new object literal.
        objectContext.setFinish(finish);
        objectContext.setStart(Token.descPosition(objectToken));

        return new ObjectNode(source, objectToken, finish, objectContext, elements);
    }

    /**
     * PropertyName :
     *      IdentifierName
     *      StringLiteral
     *      NumericLiteral
     *
     * See 11.1.5
     *
     * @return PropertyName node
     */
    @SuppressWarnings("fallthrough")
    private PropertyKey propertyName() {
        switch (type) {
        case IDENT:
            return getIdent();
        case OCTAL:
            if (isStrictMode) {
                error(AbstractParser.message("strict.no.octal"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case FLOATING:
            return getLiteral();
        default:
            return getIdentifierName();
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

        FunctionNode functionNode;
        List<IdentNode> parameters;
        PropertyNode propertyNode;
        PropertyKey propertyName;

        if (type == IDENT) {
            // Get IDENT.
            final String ident = (String)expectValue(IDENT);

            if (type != COLON) {
                final long getSetToken = token;

                switch (ident) {
                case "get":
                    final PropertyKey getIdent = propertyName();
                    final String getterName = getIdent.getPropertyName();
                    final IdentNode getNameNode = new IdentNode(source, ((Node)getIdent).getToken(), finish, "get " + getterName);
                    expect(LPAREN);
                    expect(RPAREN);
                    parameters = new ArrayList<>();
                    functionNode = functionBody(getSetToken, getNameNode, parameters, FunctionNode.Kind.GETTER);
                    propertyNode = new PropertyNode(source, propertyToken, finish, getIdent, null);
                    propertyNode.setGetter(new ReferenceNode(source, propertyToken, finish, functionNode));
                    return propertyNode;

                case "set":
                    final PropertyKey setIdent = propertyName();
                    final String setterName = setIdent.getPropertyName();
                    final IdentNode setNameNode = new IdentNode(source, ((Node)setIdent).getToken(), finish, "set " + setterName);
                    expect(LPAREN);
                    final IdentNode argIdent = getIdent();
                    verifyStrictIdent(argIdent, "setter argument");
                    expect(RPAREN);
                    parameters = new ArrayList<>();
                    parameters.add(argIdent);
                    functionNode = functionBody(getSetToken, setNameNode, parameters, FunctionNode.Kind.SETTER);
                    propertyNode = new PropertyNode(source, propertyToken, finish, setIdent, null);
                    propertyNode.setSetter(new ReferenceNode(source, propertyToken, finish, functionNode));
                    return propertyNode;

                default:
                    break;
                }
            }

            propertyName =  new IdentNode(source, propertyToken, finish, ident);
        } else {
            propertyName = propertyName();
        }

        expect(COLON);

        final Node value = assignmentExpression(false);
        propertyNode =  new PropertyNode(source, propertyToken, finish, propertyName, value);
        return propertyNode;
    }

    /**
     * LeftHandSideExpression :
     *      NewExpression
     *      CallExpression
     *
     * CallExpression :
     *      MemberExpression Arguments
     *      CallExpression Arguments
     *      CallExpression [ Expression ]
     *      CallExpression . IdentifierName
     *
     * See 11.2
     *
     * Parse left hand side expression.
     * @return Expression node.
     */
    private Node leftHandSideExpression() {
        long callToken = token;

        Node lhs = memberExpression();

        if (type == LPAREN) {
            final List<Node> arguments = argumentList();

            // Catch special functions.
            if (lhs instanceof IdentNode) {
                detectSpecialFunction((IdentNode)lhs);
            }

            lhs = new CallNode(source, callToken, finish, lhs, arguments);
            if (isInWithBlock()) {
                ((CallNode)lhs).setInWithBlock();
            }
        }

loop:
        while (true) {
            // Capture token.
            callToken = token;

            switch (type) {
            case LPAREN:
                // Get NEW or FUNCTION arguments.
                final List<Node> arguments = argumentList();

                // Create call node.
                lhs = new CallNode(source, callToken, finish, lhs, arguments);
                if (isInWithBlock()) {
                    ((CallNode)lhs).setInWithBlock();
                }

                break;

            case LBRACKET:
                next();

                // Get array index.
                final Node rhs = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(source, callToken, finish, lhs, rhs);

                break;

            case PERIOD:
                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(source, callToken, finish, lhs, property);

                break;

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
    private Node newExpression() {
        final long newToken = token;
        // NEW is tested in caller.
        next();

        // Get function base.
        final Node constructor = memberExpression();
        if (constructor == null) {
            return null;
        }
        // Get arguments.
        List<Node> arguments;

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
        // The object literal following the "new Constructor()" expresssion
        // is passed as an additional (last) argument to the constructor.

        if (!context._no_syntax_extensions && type == LBRACE) {
            arguments.add(objectLiteral());
        }

        final CallNode callNode = new CallNode(source, constructor.getToken(), finish, constructor, arguments);
        if (isInWithBlock()) {
            callNode.setInWithBlock();
        }

        return new UnaryNode(source, newToken, callNode);
    }

    /**
     * MemberExpression :
     *      PrimaryExpression
     *      FunctionExpression
     *      MemberExpression [ Expression ]
     *      MemberExpression . IdentifierName
     *      new MemberExpression Arguments
     *
     * See 11.2
     *
     * Parse member expression.
     * @return Expression node.
     */
    private Node memberExpression() {
        // Prepare to build operation.
        Node lhs;

        switch (type) {
        case NEW:
            // Get new exppression.
            lhs = newExpression();
            break;

        case FUNCTION:
            // Get function expression.
            lhs = functionExpression(false);
            break;

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
            case LBRACKET:
                next();

                // Get array index.
                final Node index = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(source, callToken, finish, lhs, index);

                break;

            case PERIOD:
                if (lhs == null) {
                    error(AbstractParser.message("expected.operand", type.getNameOrType()));
                    return null;
                }

                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(source, callToken, finish, lhs, property);

                break;

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
     *      ArgumentList , AssignmentExpression
     *
     * See 11.2
     *
     * Parse function call arguments.
     * @return Argument list.
     */
    private List<Node> argumentList() {
        // Prepare to accumulate list of arguments.
        final List<Node> nodeList = new ArrayList<>();
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

            // Get argument expression.
            nodeList.add(assignmentExpression(false));
        }

        expect(RPAREN);

        return nodeList;
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
    private Node functionExpression(final boolean isStatement) {
        final LineNumberNode lineNumber = lineNumber();

        final long functionToken = token;
        // FUNCTION is tested in caller.
        next();

        IdentNode name = null;

        if (type == IDENT || isNonStrictModeIdent()) {
            name = getIdent();
            verifyStrictIdent(name, "function name");
        } else if (isStatement) {
            // Nashorn extension: anonymous function statements
            if (context._no_syntax_extensions || !context._anon_functions) {
                expect(IDENT);
            }
        }

        // name is null, generate anonymous name
        boolean isAnonymous = false;
        if (name == null) {
            final String tmpName = "_L" + source.getLine(Token.descPosition(token));
            name = new IdentNode(source, functionToken, Token.descPosition(functionToken), tmpName);
            isAnonymous = true;
        }

        expect(LPAREN);

        final List<IdentNode> parameters = formalParameterList();

        expect(RPAREN);

        final FunctionNode functionNode = functionBody(functionToken, name, parameters, FunctionNode.Kind.NORMAL);

        if (isStatement && !isInWithBlock()) {
            functionNode.setIsStatement();
            if(ARGUMENTS.tag().equals(name.getName())) {
                functionNode.findParentFunction().setDefinesArguments();
            }
        }

        if (isAnonymous) {
            functionNode.setIsAnonymous();
        }

        final ReferenceNode referenceNode = new ReferenceNode(source, functionToken, finish, functionNode);

        final int arity = parameters.size();

        final boolean strict = functionNode.isStrictMode();
        if (arity > 1) {
            final HashSet<String> parametersSet = new HashSet<>(arity);

            for (int i = arity - 1; i >= 0; i--) {
                final IdentNode parameter = parameters.get(i);
                String parameterName = parameter.getName();

                if (ARGUMENTS.tag().equals(parameterName)) {
                    functionNode.setDefinesArguments();
                }

                if (parametersSet.contains(parameterName)) {
                    // redefinition of parameter name
                    if (strict) {
                        error(AbstractParser.message("strict.param.redefinition", parameterName), parameter.getToken());
                    } else {
                        // rename in non-strict mode
                        parameterName = functionNode.uniqueName(parameterName);
                        final long parameterToken = parameter.getToken();
                        parameters.set(i, new IdentNode(source, parameterToken, Token.descPosition(parameterToken), functionNode.uniqueName(parameterName)));
                    }
                }

                parametersSet.add(parameterName);
            }
        } else if (arity == 1) {
            if (ARGUMENTS.tag().equals(parameters.get(0).getName())) {
                functionNode.setDefinesArguments();
            }
        }

        if (isStatement) {
            final VarNode var = new VarNode(source, functionToken, finish, name, referenceNode);
            if (isInWithBlock()) {
                function.addDeclaration(var);
                // Add to current block.
                block.addStatement(var);
            } else {
                functionNode.setFunctionVarNode(var, lineNumber);
            }
        }

        return referenceNode;
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
    private List<IdentNode> formalParameterList() {
        // Prepare to gather parameters.
        final List<IdentNode> parameters = new ArrayList<>();
        // Track commas.
        boolean first = true;

        while (type != RPAREN) {
            // Comma prior to every argument except the first.
            if (!first) {
                expect(COMMARIGHT);
            } else {
                first = false;
            }

            // Get and add parameter.
            final IdentNode ident = getIdent();

            // ECMA 13.1 strict mode restrictions
            verifyStrictIdent(ident, "function parameter");

            parameters.add(ident);
        }

        return parameters;
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
    private FunctionNode functionBody(final long firstToken, final IdentNode ident, final List<IdentNode> parameters, final FunctionNode.Kind kind) {
        FunctionNode functionNode = null;

        try {
            // Create a new function block.
            functionNode = newFunctionBlock(ident);
            functionNode.setParameters(parameters);
            functionNode.setKind(kind);
            functionNode.setFirstToken(firstToken);

            // Nashorn extension: expression closures
            if (!context._no_syntax_extensions && type != LBRACE) {
                /*
                 * Example:
                 *
                 * function square(x) x * x;
                 * print(square(3));
                 */

                // just expression as function body
                final Node expr = expression();

                // create a return statement - this creates code in itself and does not need to be
                // wrapped into an ExecuteNode
                final ReturnNode  returnNode = new ReturnNode(source, expr.getToken(), finish, expr, null);

                // add the return statement
                functionNode.addStatement(returnNode);
                functionNode.setLastToken(token);
                functionNode.setFinish(Token.descPosition(token) + Token.descLength(token));

            } else {
                expect(LBRACE);

                // Gather the function elements.
                sourceElements();

                functionNode.setLastToken(token);
                expect(RBRACE);
                functionNode.setFinish(finish);

            }
        } finally {
            restoreBlock();
        }

        // Add the body of the function to the current block.
        block.addFunction(functionNode);

        return functionNode;
    }

    private RuntimeNode referenceError(final Node lhs, final Node rhs) {
        final ArrayList<Node> args = new ArrayList<>();
        args.add(lhs);
        if (rhs == null) {
            args.add(LiteralNode.newInstance(source, lhs.getToken(), lhs.getFinish()));
        } else {
            args.add(rhs);
        }
        args.add(LiteralNode.newInstance(source, lhs.getToken(), lhs.getFinish(), lhs.toString()));
        final RuntimeNode runtimeNode = new RuntimeNode(source, lhs.getToken(),
                      lhs.getFinish(), RuntimeNode.Request.REFERENCE_ERROR, args);
        return runtimeNode;
    }

    /*
     * parse LHS [a, b, ..., c].
     *
     * JavaScript 1.8.
     */
    //private Node destructureExpression() {
    //    return null;
    //}

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
     *      Node UnaryExpression
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
    private Node unaryExpression() {
        final long unaryToken = token;

        switch (type) {
        case DELETE:
        case VOID:
        case TYPEOF:
        case ADD:
        case SUB:
        case BIT_NOT:
        case NOT:
            next();

            final Node expr = unaryExpression();

            /*
             // Not sure if "delete <ident>" is a compile-time error or a
             // runtime error in strict mode.

             if (isStrictMode) {
                 if (unaryTokenType == DELETE && expr instanceof IdentNode) {
                     error(message("strict.cant.delete.ident", ((IdentNode)expr).getName()), expr.getToken());
                 }
             }
             */
            return new UnaryNode(source, unaryToken, expr);

        case INCPREFIX:
        case DECPREFIX:
            final TokenType opType = type;
            next();

            final Node lhs = leftHandSideExpression();
            // ++, -- without operand..
            if (lhs == null) {
                // error would have been issued when looking for 'lhs'
                return null;
            }
            if (!(lhs instanceof AccessNode ||
                  lhs instanceof IndexNode ||
                  lhs instanceof IdentNode)) {
                return referenceError(lhs, null);
            }

            if (lhs instanceof IdentNode) {
                if (! checkIdentLValue((IdentNode)lhs)) {
                    return referenceError(lhs, null);
                }
                verifyStrictIdent((IdentNode)lhs, "operand for " + opType.getName() + " operator");
            }

            return incDecExpression(unaryToken, opType, lhs, false);

        default:
            break;
        }

        Node expression = leftHandSideExpression();

        if (last != EOL) {
            switch (type) {
            case INCPREFIX:
            case DECPREFIX:
                final TokenType opType = type;
                final Node lhs = expression;
                if (!(lhs instanceof AccessNode ||
                   lhs instanceof IndexNode ||
                   lhs instanceof IdentNode)) {
                    next();
                    return referenceError(lhs, null);
                }
                if (lhs instanceof IdentNode) {
                    if (! checkIdentLValue((IdentNode)lhs)) {
                        next();
                        return referenceError(lhs, null);
                    }
                    verifyStrictIdent((IdentNode)lhs, "operand for " + opType.getName() + " operator");
                }
                expression = incDecExpression(token, type, expression, true);
                next();
                break;
            default:
                break;
            }
        }

        if (expression == null) {
            error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        return expression;
    }

    /**
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
     *
     * Parse expression.
     * @return Expression node.
     */
    private Node expression() {
        // TODO - Destructuring array.
        // Include commas in expression parsing.
        return expression(unaryExpression(), COMMARIGHT.getPrecedence(), false);
    }
    private Node expression(final Node exprLhs, final int minPrecedence, final boolean noIn) {
        // Get the precedence of the next operator.
        int precedence = type.getPrecedence();
        Node lhs = exprLhs;

        // While greater precedence.
        while (type.isOperator(noIn) && precedence >= minPrecedence) {
            // Capture the operator token.
            final long op = token;

            if (type == TERNARY) {
                // Skip operator.
                next();

                // Pass expression. Middle expression of a conditional expression can be a "in"
                // expression - even in the contexts where "in" is not permitted.
                final Node rhs = expression(unaryExpression(), ASSIGN.getPrecedence(), false);

                expect(COLON);

                // Fail expression.
                final Node third = expression(unaryExpression(), ASSIGN.getPrecedence(), noIn);

                // Build up node.
                lhs = new TernaryNode(source, op, lhs, rhs, third);
            } else {
                // Skip operator.
                next();

                 // Get the next primary expression.
                Node rhs = unaryExpression();

                // Get precedence of next operator.
                int nextPrecedence = type.getPrecedence();

                // Subtask greater precedence.
                while (type.isOperator(noIn) &&
                       (nextPrecedence > precedence ||
                       nextPrecedence == precedence && !type.isLeftAssociative())) {
                    rhs = expression(rhs, nextPrecedence, noIn);
                    nextPrecedence = type.getPrecedence();
                }

                lhs = verifyAssignment(op, lhs, rhs);
            }

            precedence = type.getPrecedence();
        }

        return lhs;
    }

    private Node assignmentExpression(final boolean noIn) {
        // TODO - Handle decompose.
        // Exclude commas in expression parsing.
        return expression(unaryExpression(), ASSIGN.getPrecedence(), noIn);
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
     * Add a line number node at current position
     */
    private LineNumberNode lineNumber() {
        if (context._debug_lines) {
            return new LineNumberNode(source, token, line);
        }
        return null;
    }

}
