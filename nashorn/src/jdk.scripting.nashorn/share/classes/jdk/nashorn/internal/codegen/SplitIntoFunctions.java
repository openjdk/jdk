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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.ir.Node.NO_FINISH;
import static jdk.nashorn.internal.ir.Node.NO_LINE_NUMBER;
import static jdk.nashorn.internal.ir.Node.NO_TOKEN;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockLexicalContext;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.GetSplitState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.JumpStatement;
import jdk.nashorn.internal.ir.JumpToInlinedFinally;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.SetSplitState;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.SplitReturn;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * A node visitor that replaces {@link SplitNode}s with anonymous function invocations and some additional constructs
 * to support control flow across splits. By using this transformation, split functions are translated into ordinary
 * JavaScript functions with nested anonymous functions. The transformations however introduce several AST nodes that
 * have no JavaScript source representations ({@link GetSplitState}, {@link SetSplitState}, and {@link SplitReturn}),
 * and therefore such function is no longer reparseable from its source. For that reason, split functions and their
 * fragments are serialized in-memory and deserialized when they need to be recompiled either for deoptimization or
 * for type specialization.
 * NOTE: all {@code leave*()} methods for statements are returning their input nodes. That way, they will not mutate
 * the original statement list in the block containing the statement, which is fine, as it'll be replaced by the
 * lexical context when the block is left. If we returned something else (e.g. null), we'd cause a mutation in the
 * enclosing block's statement list that is otherwise overwritten later anyway.
 */
final class SplitIntoFunctions extends NodeVisitor<BlockLexicalContext> {
    private static final int FALLTHROUGH_STATE = -1;
    private static final int RETURN_STATE = 0;
    private static final int BREAK_STATE = 1;
    private static final int FIRST_JUMP_STATE = 2;

    private static final String THIS_NAME = CompilerConstants.THIS.symbolName();
    private static final String RETURN_NAME = CompilerConstants.RETURN.symbolName();
    // Used as the name of the formal parameter for passing the current value of :return symbol into a split fragment.
    private static final String RETURN_PARAM_NAME = RETURN_NAME + "-in";

    private final Deque<FunctionState> functionStates = new ArrayDeque<>();
    private final Deque<SplitState> splitStates = new ArrayDeque<>();
    private final Namespace namespace;

    private boolean artificialBlock = false;

    // -1 is program; we need to use negative ones
    private int nextFunctionId = -2;

    public SplitIntoFunctions(final Compiler compiler) {
        super(new BlockLexicalContext() {
            @Override
            protected Block afterSetStatements(final Block block) {
                for(Statement stmt: block.getStatements()) {
                    assert !(stmt instanceof SplitNode);
                }
                return block;
            }
        });
        namespace = new Namespace(compiler.getScriptEnvironment().getNamespace());
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        functionStates.push(new FunctionState(functionNode));
        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        functionStates.pop();
        return functionNode;
    }

    @Override
    protected Node leaveDefault(final Node node) {
        if (node instanceof Statement) {
            appendStatement((Statement)node);
        }
        return node;
    }

    @Override
    public boolean enterSplitNode(final SplitNode splitNode) {
        getCurrentFunctionState().splitDepth++;
        splitStates.push(new SplitState(splitNode));
        return true;
    }

    @Override
    public Node leaveSplitNode(final SplitNode splitNode) {
        // Replace the split node with an anonymous function expression call.

        final FunctionState fnState = getCurrentFunctionState();

        final String name = splitNode.getName();
        Block body = splitNode.getBody();
        final int firstLineNumber = body.getFirstStatementLineNumber();
        final long token = body.getToken();
        final int finish = body.getFinish();

        final FunctionNode originalFn = fnState.fn;
        assert originalFn == lc.getCurrentFunction();
        final boolean isProgram = originalFn.isProgram();

        // Change SplitNode({...}) into "function () { ... }", or "function (:return-in) () { ... }" (for program)
        final long newFnToken = Token.toDesc(TokenType.FUNCTION, nextFunctionId--, 0);
        final FunctionNode fn = new FunctionNode(
                originalFn.getSource(),
                body.getFirstStatementLineNumber(),
                newFnToken,
                finish,
                newFnToken,
                NO_TOKEN,
                namespace,
                createIdent(name),
                originalFn.getName() + "$" + name,
                isProgram ? Collections.singletonList(createReturnParamIdent()) : Collections.<IdentNode>emptyList(),
                FunctionNode.Kind.NORMAL,
                // We only need IS_SPLIT conservatively, in case it contains any array units so that we force
                // the :callee's existence, to force :scope to never be in a slot lower than 2. This is actually
                // quite a horrible hack to do with CodeGenerator.fixScopeSlot not trampling other parameters
                // and should go away once we no longer have array unit handling in codegen. Note however that
                // we still use IS_SPLIT as the criteria in CompilationPhase.SERIALIZE_SPLIT_PHASE.
                FunctionNode.IS_ANONYMOUS | FunctionNode.USES_ANCESTOR_SCOPE | FunctionNode.IS_SPLIT,
                body,
                null
        )
        .setCompileUnit(lc, splitNode.getCompileUnit());

        // Call the function:
        //     either "(function () { ... }).call(this)"
        //     or     "(function (:return-in) { ... }).call(this, :return)"
        // NOTE: Function.call() has optimized linking that basically does a pass-through to the function being invoked.
        // NOTE: CompilationPhase.PROGRAM_POINT_PHASE happens after this, so these calls are subject to optimistic
        // assumptions on their return value (when they return a value), as they should be.
        final IdentNode thisIdent = createIdent(THIS_NAME);
        final CallNode callNode = new CallNode(firstLineNumber, token, finish, new AccessNode(NO_TOKEN, NO_FINISH, fn, "call"),
                isProgram ? Arrays.<Expression>asList(thisIdent, createReturnIdent())
                          : Collections.<Expression>singletonList(thisIdent),
                false);

        final SplitState splitState = splitStates.pop();
        fnState.splitDepth--;

        final Expression callWithReturn;
        final boolean hasReturn = splitState.hasReturn;
        if (hasReturn && fnState.splitDepth > 0) {
            final SplitState parentSplit = splitStates.peek();
            if (parentSplit != null) {
                // Propagate hasReturn to parent split
                parentSplit.hasReturn = true;
            }
        }
        if (hasReturn || isProgram) {
            // capture return value: ":return = (function () { ... })();"
            callWithReturn = new BinaryNode(Token.recast(token, TokenType.ASSIGN), createReturnIdent(), callNode);
        } else {
            // no return value, just call : "(function () { ... })();"
            callWithReturn = callNode;
        }
        appendStatement(new ExpressionStatement(firstLineNumber, token, finish, callWithReturn));

        Statement splitStateHandler;

        final List<JumpStatement> jumpStatements = splitState.jumpStatements;
        final int jumpCount = jumpStatements.size();
        // There are jumps (breaks or continues) that need to be propagated outside the split node. We need to
        // set up a switch statement for them:
        // switch(:scope.getScopeState()) { ... }
        if (jumpCount > 0) {
            final List<CaseNode> cases = new ArrayList<>(jumpCount + (hasReturn ? 1 : 0));
            if (hasReturn) {
                // If the split node also contained a return, we'll slip it as a case in the switch statement
                addCase(cases, RETURN_STATE, createReturnFromSplit());
            }
            int i = FIRST_JUMP_STATE;
            for (final JumpStatement jump: jumpStatements) {
                addCase(cases, i++, enblockAndVisit(jump));
            }
            splitStateHandler = new SwitchNode(NO_LINE_NUMBER, token, finish, GetSplitState.INSTANCE, cases, null);
        } else {
            splitStateHandler = null;
        }

        // As the switch statement itself is breakable, an unlabelled break can't be in the switch statement,
        // so we need to test for it separately.
        if (splitState.hasBreak) {
            // if(:scope.getScopeState() == Scope.BREAK) { break; }
            splitStateHandler = makeIfStateEquals(firstLineNumber, token, finish, BREAK_STATE,
                    enblockAndVisit(new BreakNode(NO_LINE_NUMBER, token, finish, null)), splitStateHandler);
        }

        // Finally, if the split node had a return statement, but there were no external jumps, we didn't have
        // the switch statement to handle the return, so we need a separate if for it.
        if (hasReturn && jumpCount == 0) {
            // if (:scope.getScopeState() == Scope.RETURN) { return :return; }
            splitStateHandler = makeIfStateEquals(NO_LINE_NUMBER, token, finish, RETURN_STATE,
                    createReturnFromSplit(), splitStateHandler);
        }

        if (splitStateHandler != null) {
            appendStatement(splitStateHandler);
        }

        return splitNode;
    }

    private static void addCase(final List<CaseNode> cases, final int i, final Block body) {
        cases.add(new CaseNode(NO_TOKEN, NO_FINISH, intLiteral(i), body));
    }

    private static LiteralNode<Number> intLiteral(final int i) {
        return LiteralNode.newInstance(NO_TOKEN, NO_FINISH, i);
    }

    private static Block createReturnFromSplit() {
        return new Block(NO_TOKEN, NO_FINISH, createReturnReturn());
    }

    private static ReturnNode createReturnReturn() {
        return new ReturnNode(NO_LINE_NUMBER, NO_TOKEN, NO_FINISH, createReturnIdent());
    }

    private static IdentNode createReturnIdent() {
        return createIdent(RETURN_NAME);
    }

    private static IdentNode createReturnParamIdent() {
        return createIdent(RETURN_PARAM_NAME);
    }

    private static IdentNode createIdent(final String name) {
        return new IdentNode(NO_TOKEN, NO_FINISH, name);
    }

    private Block enblockAndVisit(final JumpStatement jump) {
        artificialBlock = true;
        final Block block = (Block)new Block(NO_TOKEN, NO_FINISH, jump).accept(this);
        artificialBlock = false;
        return block;
    }

    private static IfNode makeIfStateEquals(final int lineNumber, final long token, final int finish,
            final int value, final Block pass, final Statement fail) {
        return new IfNode(lineNumber, token, finish,
                new BinaryNode(Token.recast(token, TokenType.EQ_STRICT),
                        GetSplitState.INSTANCE, intLiteral(value)),
                pass,
                fail == null ? null : new Block(NO_TOKEN, NO_FINISH, fail));
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        if (!inSplitNode()) {
            return super.enterVarNode(varNode);
        }
        assert !varNode.isBlockScoped(); //TODO: we must handle these too, but we currently don't

        final Expression init = varNode.getInit();

        // Move a declaration-only var statement to the top of the outermost function.
        getCurrentFunctionState().varStatements.add(varNode.setInit(null));
        // If it had an initializer, replace it with an assignment expression statement. Note that "var" is a
        // statement, so it doesn't contribute to :return of the programs, therefore we are _not_ adding a
        // ":return = ..." assignment around the original assignment.
        if (init != null) {
            final long token = Token.recast(varNode.getToken(), TokenType.ASSIGN);
            new ExpressionStatement(varNode.getLineNumber(), token, varNode.getFinish(),
                    new BinaryNode(token, varNode.getName(), varNode.getInit())).accept(this);
        }

        return false;
    }

    @Override
    public Node leaveBlock(final Block block) {
        if (!artificialBlock) {
            if (lc.isFunctionBody()) {
                // Prepend declaration-only var statements to the top of the statement list.
                lc.prependStatements(getCurrentFunctionState().varStatements);
            } else if (lc.isSplitBody()) {
                appendSplitReturn(FALLTHROUGH_STATE, NO_LINE_NUMBER);
                if (getCurrentFunctionState().fn.isProgram()) {
                    // If we're splitting the program, make sure every shard ends with "return :return" and
                    // begins with ":return = :return-in;".
                    lc.prependStatement(new ExpressionStatement(NO_LINE_NUMBER, NO_TOKEN, NO_FINISH,
                            new BinaryNode(Token.toDesc(TokenType.ASSIGN, 0, 0), createReturnIdent(), createReturnParamIdent())));
                }
            }
        }
        return block;
    }

    @Override
    public Node leaveBreakNode(final BreakNode breakNode) {
        return leaveJumpNode(breakNode);
    }

    @Override
    public Node leaveContinueNode(final ContinueNode continueNode) {
        return leaveJumpNode(continueNode);
    }

    @Override
    public Node leaveJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        return leaveJumpNode(jumpToInlinedFinally);
    }

    private JumpStatement leaveJumpNode(final JumpStatement jump) {
        if (inSplitNode()) {
            final SplitState splitState = getCurrentSplitState();
            final SplitNode splitNode = splitState.splitNode;
            if (lc.isExternalTarget(splitNode, jump.getTarget(lc))) {
                appendSplitReturn(splitState.getSplitStateIndex(jump), jump.getLineNumber());
                return jump;
            }
        }
        appendStatement(jump);
        return jump;
    }

    private void appendSplitReturn(final int splitState, final int lineNumber) {
        appendStatement(new SetSplitState(splitState, lineNumber));
        if (getCurrentFunctionState().fn.isProgram()) {
            // If we're splitting the program, make sure every fragment passes back :return
            appendStatement(createReturnReturn());
        } else {
            appendStatement(SplitReturn.INSTANCE);
        }
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        if(inSplitNode()) {
            appendStatement(new SetSplitState(RETURN_STATE, returnNode.getLineNumber()));
            getCurrentSplitState().hasReturn = true;
        }
        appendStatement(returnNode);
        return returnNode;
    }

    private void appendStatement(final Statement statement) {
        lc.appendStatement(statement);
    }

    private boolean inSplitNode() {
        return getCurrentFunctionState().splitDepth > 0;
    }

    private FunctionState getCurrentFunctionState() {
        return functionStates.peek();
    }

    private SplitState getCurrentSplitState() {
        return splitStates.peek();
    }

    private static class FunctionState {
        final FunctionNode fn;
        final List<Statement> varStatements = new ArrayList<>();
        int splitDepth;

        FunctionState(final FunctionNode fn) {
            this.fn = fn;
        }
    }

    private static class SplitState {
        final SplitNode splitNode;
        boolean hasReturn;
        boolean hasBreak;

        final List<JumpStatement> jumpStatements = new ArrayList<>();

        int getSplitStateIndex(final JumpStatement jump) {
            if (jump instanceof BreakNode && jump.getLabelName() == null) {
                // Unlabelled break is a special case
                hasBreak = true;
                return BREAK_STATE;
            }

            int i = 0;
            for(final JumpStatement exJump: jumpStatements) {
                if (jump.getClass() == exJump.getClass() && Objects.equals(jump.getLabelName(), exJump.getLabelName())) {
                    return i + FIRST_JUMP_STATE;
                }
                ++i;
            }
            jumpStatements.add(jump);
            return i + FIRST_JUMP_STATE;
        }

        SplitState(final SplitNode splitNode) {
            this.splitNode = splitNode;
        }
    }
}
