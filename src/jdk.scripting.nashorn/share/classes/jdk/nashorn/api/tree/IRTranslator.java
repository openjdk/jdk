/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.api.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.ir.AccessNode;
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
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.TemplateLiteral;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.parser.TokenType;

/**
 * This class translates from nashorn IR Node objects
 * to nashorn parser API Tree objects.
 */
final class IRTranslator extends SimpleNodeVisitor {

    public IRTranslator() {
    }

    // currently translated Statement
    private StatementTreeImpl curStat;
    // currently translated Expression
    private ExpressionTreeImpl curExpr;

    // entry point for translator
    CompilationUnitTree translate(final FunctionNode node) {
        if (node == null) {
            return null;
        }

        assert node.getKind() == FunctionNode.Kind.SCRIPT ||
                node.getKind() == FunctionNode.Kind.MODULE :
                "script or module function expected";

        final Block body = node.getBody();
        return new CompilationUnitTreeImpl(node,
                translateStats(body != null? getOrderedStatements(body.getStatements()) : null),
                translateModule(node));
    }

    @Override
    public boolean enterAccessNode(final AccessNode accessNode) {
        curExpr = new MemberSelectTreeImpl(accessNode, translateExpr(accessNode.getBase()));
        return false;
    }

    @Override
    public boolean enterBlock(final Block block) {
        return handleBlock(block, false);
    }

    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        if (binaryNode.isAssignment()) {
            final ExpressionTree srcTree = translateExpr(binaryNode.getAssignmentSource());
            final ExpressionTree destTree = translateExpr(binaryNode.getAssignmentDest());

            if (binaryNode.isTokenType(TokenType.ASSIGN)) {
                curExpr = new AssignmentTreeImpl(binaryNode, destTree, srcTree);
            } else {
                curExpr = new CompoundAssignmentTreeImpl(binaryNode, destTree, srcTree);
            }
        } else {
            final ExpressionTree leftTree = translateExpr(binaryNode.lhs());
            final ExpressionTree rightTree = translateExpr(binaryNode.rhs());

            if (binaryNode.isTokenType(TokenType.INSTANCEOF)) {
                curExpr = new InstanceOfTreeImpl(binaryNode, leftTree, rightTree);
            } else {
                curExpr = new BinaryTreeImpl(binaryNode, leftTree, rightTree);
            }
        }

        return false;
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        curStat = new BreakTreeImpl(breakNode);
        return false;
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        curExpr = null;
        callNode.getFunction().accept(this);
        final ExpressionTree funcTree = curExpr;
        final List<? extends ExpressionTree> argTrees = translateExprs(callNode.getArgs());
        curExpr = new FunctionCallTreeImpl(callNode, funcTree, argTrees);
        return false;
    }

    @Override
    public boolean enterCaseNode(final CaseNode caseNode) {
        assert false : "should not reach here!";
        return false;
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        assert false : "should not reach here";
        return false;
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        curStat = new ContinueTreeImpl(continueNode);
        return false;
    }

    @Override
    public boolean enterDebuggerNode(final DebuggerNode debuggerNode) {
        curStat = new DebuggerTreeImpl(debuggerNode);
        return false;
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        curStat = new EmptyStatementTreeImpl(emptyNode);
        return false;
    }

    @Override
    public boolean enterErrorNode(final ErrorNode errorNode) {
        curExpr = new ErroneousTreeImpl(errorNode);
        return false;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        if (expressionStatement.destructuringDeclarationType() != null) {
            final ExpressionTree expr = translateExpr(expressionStatement.getExpression());
            assert expr instanceof AssignmentTree : "destructuring decl. statement does not have assignment";
            final AssignmentTree assign = (AssignmentTree)expr;
            curStat = new DestructuringDeclTreeImpl(expressionStatement, assign.getVariable(), assign.getExpression());
        } else {
            curStat = new ExpressionStatementTreeImpl(expressionStatement,
                translateExpr(expressionStatement.getExpression()));
        }
        return false;
    }

    @Override
    public boolean enterBlockStatement(final BlockStatement blockStatement) {
        final Block block = blockStatement.getBlock();
        if (blockStatement.isSynthetic()) {
            assert block != null && block.getStatements() != null && block.getStatements().size() == 1;
            curStat = translateStat(block.getStatements().get(0));
        } else {
            curStat = new BlockTreeImpl(blockStatement,
                translateStats(block != null? block.getStatements() : null));
        }
        return false;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if (forNode.isForIn()) {
            curStat = new ForInLoopTreeImpl(forNode,
                    translateExpr(forNode.getInit()),
                    translateExpr(forNode.getModify()),
                    translateBlock(forNode.getBody()));
        } else if (forNode.isForOf()) {
            curStat = new ForOfLoopTreeImpl(forNode,
                    translateExpr(forNode.getInit()),
                    translateExpr(forNode.getModify()),
                    translateBlock(forNode.getBody()));
        } else {
            curStat = new ForLoopTreeImpl(forNode,
                    translateExpr(forNode.getInit()),
                    translateExpr(forNode.getTest()),
                    translateExpr(forNode.getModify()),
                    translateBlock(forNode.getBody()));
        }

        return false;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        assert !functionNode.isDeclared() || functionNode.isAnonymous() : "should not reach here for function declaration";

        final List<? extends ExpressionTree> paramTrees = translateParameters(functionNode);
        final BlockTree blockTree = (BlockTree) translateBlock(functionNode.getBody(), true);
        curExpr = new FunctionExpressionTreeImpl(functionNode, paramTrees, blockTree);

        return false;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        curExpr = new IdentifierTreeImpl(identNode);
        return false;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        curStat = new IfTreeImpl(ifNode,
                translateExpr(ifNode.getTest()),
                translateBlock(ifNode.getPass()),
                translateBlock(ifNode.getFail()));
        return false;
    }

    @Override
    public boolean enterIndexNode(final IndexNode indexNode) {
        curExpr = new ArrayAccessTreeImpl(indexNode,
                translateExpr(indexNode.getBase()),
                translateExpr(indexNode.getIndex()));
        return false;
    }

    @Override
    public boolean enterLabelNode(final LabelNode labelNode) {
        curStat = new LabeledStatementTreeImpl(labelNode,
                translateBlock(labelNode.getBody()));
        return false;
    }

    @Override
    public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
        final Object value = literalNode.getValue();
        if (value instanceof Lexer.RegexToken) {
            curExpr = new RegExpLiteralTreeImpl(literalNode);
        } else if (literalNode.isArray()) {
            final List<Expression> exprNodes = literalNode.getElementExpressions();
            final List<ExpressionTreeImpl> exprTrees = new ArrayList<>(exprNodes.size());
            for (final Node node : exprNodes) {
                if (node == null) {
                    exprTrees.add(null);
                } else {
                    curExpr = null;
                    node.accept(this);
                    assert curExpr != null : "null for " + node;
                    exprTrees.add(curExpr);
                }
            }
            curExpr = new ArrayLiteralTreeImpl(literalNode, exprTrees);
        } else {
            curExpr = new LiteralTreeImpl(literalNode);
        }

        return false;
    }

    @Override
    public boolean enterObjectNode(final ObjectNode objectNode) {
        final List<PropertyNode> propNodes = objectNode.getElements();
        final List<? extends PropertyTree> propTrees = translateProperties(propNodes);
        curExpr = new ObjectLiteralTreeImpl(objectNode, propTrees);
        return false;
    }

    @Override
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        assert false : "should not reach here!";
        return false;
    }

    @Override
    public boolean enterReturnNode(final ReturnNode returnNode) {
        curStat = new ReturnTreeImpl(returnNode,
                translateExpr(returnNode.getExpression()));
        return false;
    }

    @Override
    public boolean enterRuntimeNode(final RuntimeNode runtimeNode) {
        assert false : "should not reach here: RuntimeNode";
        return false;
    }

    @Override
    public boolean enterSplitNode(final SplitNode splitNode) {
        assert false : "should not reach here!";
        return false;
    }

    @Override
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        final List<CaseNode> caseNodes = switchNode.getCases();
        final List<CaseTreeImpl> caseTrees = new ArrayList<>(caseNodes.size());
        for (final CaseNode caseNode : caseNodes) {
            final Block body = caseNode.getBody();
            caseTrees.add(
                    new CaseTreeImpl(caseNode,
                            translateExpr(caseNode.getTest()),
                            translateStats(body != null? body.getStatements() : null)));
        }

        curStat = new SwitchTreeImpl(switchNode,
                translateExpr(switchNode.getExpression()),
                caseTrees);
        return false;
    }

    @Override
    public boolean enterTemplateLiteral(final TemplateLiteral templateLiteral) {
        curExpr = new TemplateLiteralTreeImpl(templateLiteral, translateExprs(templateLiteral.getExpressions()));
        return false;
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        curExpr = new ConditionalExpressionTreeImpl(ternaryNode,
                translateExpr(ternaryNode.getTest()),
                translateExpr(ternaryNode.getTrueExpression()),
                translateExpr(ternaryNode.getFalseExpression()));
        return false;
    }

    @Override
    public boolean enterThrowNode(final ThrowNode throwNode) {
        curStat = new ThrowTreeImpl(throwNode,
                translateExpr(throwNode.getExpression()));
        return false;
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        final List<? extends CatchNode> catchNodes = tryNode.getCatches();
        final List<CatchTreeImpl> catchTrees = new ArrayList<>(catchNodes.size());
        for (final CatchNode catchNode : catchNodes) {
            catchTrees.add(new CatchTreeImpl(catchNode,
                    translateExpr(catchNode.getException()),
                    (BlockTree) translateBlock(catchNode.getBody()),
                    translateExpr(catchNode.getExceptionCondition())));
        }

        curStat = new TryTreeImpl(tryNode,
                (BlockTree) translateBlock(tryNode.getBody()),
                catchTrees,
                (BlockTree) translateBlock(tryNode.getFinallyBody()));

        return false;
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        if (unaryNode.isTokenType(TokenType.NEW)) {
            curExpr = new NewTreeImpl(unaryNode,
                    translateExpr(unaryNode.getExpression()));
        } else if (unaryNode.isTokenType(TokenType.YIELD) ||
                unaryNode.isTokenType(TokenType.YIELD_STAR)) {
            curExpr = new YieldTreeImpl(unaryNode,
                    translateExpr(unaryNode.getExpression()));
        } else if (unaryNode.isTokenType(TokenType.SPREAD_ARGUMENT) ||
                unaryNode.isTokenType(TokenType.SPREAD_ARRAY)) {
            curExpr = new SpreadTreeImpl(unaryNode,
                    translateExpr(unaryNode.getExpression()));
        } else {
            curExpr = new UnaryTreeImpl(unaryNode,
                    translateExpr(unaryNode.getExpression()));
        }
        return false;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        final Expression initNode = varNode.getInit();
        if (initNode instanceof FunctionNode && ((FunctionNode)initNode).isDeclared()) {
            final FunctionNode funcNode = (FunctionNode) initNode;

            final List<? extends ExpressionTree> paramTrees = translateParameters(funcNode);
            final BlockTree blockTree = (BlockTree) translateBlock(funcNode.getBody(), true);
            curStat = new FunctionDeclarationTreeImpl(varNode, paramTrees, blockTree);
        } else if (initNode instanceof ClassNode && ((ClassNode)initNode).isStatement()) {
            final ClassNode classNode = (ClassNode) initNode;

            curStat = new ClassDeclarationTreeImpl(varNode,
                    translateIdent(classNode.getIdent()),
                    translateExpr(classNode.getClassHeritage()),
                    translateProperty(classNode.getConstructor()),
                    translateProperties(classNode.getClassElements()));
        } else {
            curStat = new VariableTreeImpl(varNode, translateIdent(varNode.getName()), translateExpr(initNode));
        }

        return false;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        final ExpressionTree condTree = translateExpr(whileNode.getTest());
        final StatementTree statTree = translateBlock(whileNode.getBody());

        if (whileNode.isDoWhile()) {
            curStat = new DoWhileLoopTreeImpl(whileNode, condTree, statTree);
        } else {
            curStat = new WhileLoopTreeImpl(whileNode, condTree, statTree);
        }

        return false;
    }

    @Override
    public boolean enterWithNode(final WithNode withNode) {
        curStat = new WithTreeImpl(withNode,
                translateExpr(withNode.getExpression()),
                translateBlock(withNode.getBody()));

        return false;
    }

    /**
     * Callback for entering a ClassNode
     *
     * @param  classNode  the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    @Override
    public boolean enterClassNode(final ClassNode classNode) {
        assert !classNode.isStatement(): "should not reach here for class declaration";

        curExpr = new ClassExpressionTreeImpl(classNode,
            translateIdent(classNode.getIdent()),
            translateExpr(classNode.getClassHeritage()),
            translateProperty(classNode.getConstructor()),
            translateProperties(classNode.getClassElements()));

        return false;
    }

    private StatementTree translateBlock(final Block blockNode) {
        return translateBlock(blockNode, false);
    }

    private StatementTree translateBlock(final Block blockNode, final boolean sortStats) {
        if (blockNode == null) {
            return null;
        }
        curStat = null;
        handleBlock(blockNode, sortStats);
        return curStat;
    }

    private boolean handleBlock(final Block block, final boolean sortStats) {
        // FIXME: revisit this!
        if (block.isSynthetic()) {
            final int statCount = block.getStatementCount();
            switch (statCount) {
                case 0: {
                    final EmptyNode emptyNode = new EmptyNode(-1, block.getToken(), block.getFinish());
                    curStat = new EmptyStatementTreeImpl(emptyNode);
                    return false;
                }
                case 1: {
                    curStat = translateStat(block.getStatements().get(0));
                    return false;
                }
                default: {
                    // fall through
                    break;
                }
            }
        }

        final List<? extends Statement> stats = block.getStatements();
        curStat = new BlockTreeImpl(block,
            translateStats(sortStats? getOrderedStatements(stats) : stats));
        return false;
    }

    private List<? extends Statement> getOrderedStatements(final List<? extends Statement> stats) {
        final List<? extends Statement> statList = new ArrayList<>(stats);
        statList.sort(Comparator.comparingInt(Node::getSourceOrder));
        return statList;
    }

    private List<? extends StatementTree> translateStats(final List<? extends Statement> stats) {
        if (stats == null) {
            return null;
        }
        final List<StatementTreeImpl> statTrees = new ArrayList<>(stats.size());
        for (final Statement stat : stats) {
            curStat = null;
            stat.accept(this);
            assert curStat != null;
            statTrees.add(curStat);
        }
        return statTrees;
    }

    private List<? extends ExpressionTree> translateParameters(final FunctionNode func) {
        final Map<IdentNode, Expression> paramExprs = func.getParameterExpressions();
        if (paramExprs != null) {
            final List<IdentNode> params = func.getParameters();
            final List<ExpressionTreeImpl> exprTrees = new ArrayList<>(params.size());
            for (final IdentNode ident : params) {
                final Expression expr = paramExprs.containsKey(ident)? paramExprs.get(ident) : ident;
                curExpr = null;
                expr.accept(this);
                assert curExpr != null;
                exprTrees.add(curExpr);
            }
            return exprTrees;
        } else {
            return translateExprs(func.getParameters());
        }
    }

    private List<? extends ExpressionTree> translateExprs(final List<? extends Expression> exprs) {
        if (exprs == null) {
            return null;
        }
        final List<ExpressionTreeImpl> exprTrees = new ArrayList<>(exprs.size());
        for (final Expression expr : exprs) {
            curExpr = null;
            expr.accept(this);
            assert curExpr != null;
            exprTrees.add(curExpr);
        }
        return exprTrees;
    }

    private ExpressionTreeImpl translateExpr(final Expression expr) {
        if (expr == null) {
            return null;
        }

        curExpr = null;
        expr.accept(this);
        assert curExpr != null : "null for " + expr;
        return curExpr;
    }

    private StatementTreeImpl translateStat(final Statement stat) {
        if (stat == null) {
            return null;
        }

        curStat = null;
        stat.accept(this);
        assert curStat != null : "null for " + stat;
        return curStat;
    }

    private static IdentifierTree translateIdent(final IdentNode ident) {
        return new IdentifierTreeImpl(ident);
    }

    private List<? extends PropertyTree> translateProperties(final List<PropertyNode> propNodes) {
        final List<PropertyTree> propTrees = new ArrayList<>(propNodes.size());
        for (final PropertyNode propNode : propNodes) {
            propTrees.add(translateProperty(propNode));
        }
        return propTrees;
    }

    private PropertyTree translateProperty(final PropertyNode propNode) {
        return new PropertyTreeImpl(propNode,
                    translateExpr(propNode.getKey()),
                    translateExpr(propNode.getValue()),
                    (FunctionExpressionTree) translateExpr(propNode.getGetter()),
                    (FunctionExpressionTree) translateExpr(propNode.getSetter()));
    }

    private ModuleTree translateModule(final FunctionNode func) {
        return func.getKind() == FunctionNode.Kind.MODULE?
            ModuleTreeImpl.create(func) : null;
    }
}
