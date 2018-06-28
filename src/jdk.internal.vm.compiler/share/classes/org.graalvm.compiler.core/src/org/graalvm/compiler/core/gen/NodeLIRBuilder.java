/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.core.gen;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.core.common.GraalOptions.MatchExpressions;
import static org.graalvm.compiler.debug.DebugOptions.LogVerbose;
import static org.graalvm.compiler.lir.LIR.verifyBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.match.ComplexMatchValue;
import org.graalvm.compiler.core.match.MatchPattern;
import org.graalvm.compiler.core.match.MatchRuleRegistry;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.lir.FullInfopointOp;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.debug.LIRGenerationDebugContext;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGenerator.Options;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.BlockScope;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class NodeLIRBuilder implements NodeLIRBuilderTool, LIRGenerationDebugContext {

    private final NodeMap<Value> nodeOperands;
    private final DebugInfoBuilder debugInfoBuilder;
    private final int traceLIRGeneratorLevel;

    protected final LIRGenerator gen;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    private final NodeMatchRules nodeMatchRules;
    private EconomicMap<Class<? extends Node>, List<MatchStatement>> matchRules;

    public NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, NodeMatchRules nodeMatchRules) {
        this.gen = (LIRGenerator) gen;
        this.nodeMatchRules = nodeMatchRules;
        this.nodeOperands = graph.createNodeMap();
        this.debugInfoBuilder = createDebugInfoBuilder(graph, this);
        OptionValues options = graph.getOptions();
        if (MatchExpressions.getValue(options)) {
            matchRules = MatchRuleRegistry.lookup(nodeMatchRules.getClass(), options, graph.getDebug());
        }
        traceLIRGeneratorLevel = TTY.isSuppressed() ? 0 : Options.TraceLIRGeneratorLevel.getValue(options);

        assert nodeMatchRules.lirBuilder == null;
        nodeMatchRules.lirBuilder = this;
    }

    public NodeMatchRules getNodeMatchRules() {
        return nodeMatchRules;
    }

    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        return new DebugInfoBuilder(nodeValueMap, graph.getDebug());
    }

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction. It's a code
     * generation error to ask for the operand of ValueNode that doesn't have one yet.
     *
     * @param node A node that produces a result value.
     */
    @Override
    public Value operand(Node node) {
        Value operand = getOperand(node);
        assert operand != null : String.format("missing operand for %1s", node);
        return operand;
    }

    @Override
    public boolean hasOperand(Node node) {
        return getOperand(node) != null;
    }

    private Value getOperand(Node node) {
        if (nodeOperands == null) {
            return null;
        }
        return nodeOperands.get(node);
    }

    @Override
    public ValueNode valueForOperand(Value value) {
        assert nodeOperands != null;
        UnmodifiableMapCursor<Node, Value> cursor = nodeOperands.getEntries();
        while (cursor.advance()) {
            if (cursor.getValue().equals(value)) {
                return (ValueNode) cursor.getKey();
            }
        }
        return null;
    }

    @Override
    public Object getSourceForOperand(Value value) {
        return valueForOperand(value);
    }

    @Override
    public Value setResult(ValueNode x, Value operand) {
        assert (!isRegister(operand) || !gen.attributes(asRegister(operand)).isAllocatable());
        assert nodeOperands != null && (nodeOperands.get(x) == null || nodeOperands.get(x) instanceof ComplexMatchValue) : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
        return operand;
    }

    /**
     * Used by the {@link MatchStatement} machinery to override the generation LIR for some
     * ValueNodes.
     */
    public void setMatchResult(Node x, Value operand) {
        assert operand.equals(ComplexMatchValue.INTERIOR_MATCH) || operand instanceof ComplexMatchValue;
        assert operand instanceof ComplexMatchValue || MatchPattern.isSingleValueUser(x) : "interior matches must be single user";
        assert nodeOperands != null && nodeOperands.get(x) == null : "operand cannot be set twice";
        assert !(x instanceof VirtualObjectNode);
        nodeOperands.set(x, operand);
    }

    public LabelRef getLIRBlock(FixedNode b) {
        assert gen.getResult().getLIR().getControlFlowGraph() instanceof ControlFlowGraph;
        Block result = ((ControlFlowGraph) gen.getResult().getLIR().getControlFlowGraph()).blockFor(b);
        int suxIndex = 0;
        for (AbstractBlockBase<?> succ : gen.getCurrentBlock().getSuccessors()) {
            if (succ == result) {
                assert gen.getCurrentBlock() instanceof Block;
                return LabelRef.forSuccessor(gen.getResult().getLIR(), gen.getCurrentBlock(), suxIndex);
            }
            suxIndex++;
        }
        throw GraalError.shouldNotReachHere("Block not in successor list of current block");
    }

    public final void append(LIRInstruction op) {
        if (Options.PrintIRWithLIR.getValue(nodeOperands.graph().getOptions()) && !TTY.isSuppressed()) {
            if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
                lastInstructionPrinted = currentInstruction;
                InstructionPrinter ip = new InstructionPrinter(TTY.out());
                ip.printInstructionListing(currentInstruction);
            }
        }
        gen.append(op);
    }

    protected LIRKind getExactPhiKind(PhiNode phi) {
        LIRKind derivedKind = gen.toRegisterKind(gen.getLIRKind(phi.stamp(NodeView.DEFAULT)));
        /* Collect reference information. */
        for (int i = 0; i < phi.valueCount() && !derivedKind.isUnknownReference(); i++) {
            ValueNode node = phi.valueAt(i);
            Value value = getOperand(node);

            // get ValueKind for input
            final LIRKind valueKind;
            if (value != null) {
                valueKind = value.getValueKind(LIRKind.class);
            } else {
                assert isPhiInputFromBackedge(phi, i) : String.format("Input %s to phi node %s is not yet available although it is not coming from a loop back edge", node, phi);
                LIRKind kind = gen.getLIRKind(node.stamp(NodeView.DEFAULT));
                valueKind = gen.toRegisterKind(kind);
            }
            /* Merge the reference information of the derived kind and the input. */
            derivedKind = LIRKind.mergeReferenceInformation(derivedKind, valueKind);
        }
        return derivedKind;
    }

    private static boolean isPhiInputFromBackedge(PhiNode phi, int index) {
        AbstractMergeNode merge = phi.merge();
        AbstractEndNode end = merge.phiPredecessorAt(index);
        return end instanceof LoopEndNode && ((LoopEndNode) end).loopBegin().equals(merge);
    }

    private Value[] createPhiIn(AbstractMergeNode merge) {
        List<Value> values = new ArrayList<>();
        for (ValuePhiNode phi : merge.valuePhis()) {
            assert getOperand(phi) == null;
            Variable value = gen.newVariable(getExactPhiKind(phi));
            values.add(value);
            setResult(phi, value);
        }
        return values.toArray(new Value[values.size()]);
    }

    /**
     * @return {@code true} if object constant to stack moves are supported.
     */
    protected boolean allowObjectConstantToStackMove() {
        return true;
    }

    private Value[] createPhiOut(AbstractMergeNode merge, AbstractEndNode pred) {
        List<Value> values = new ArrayList<>();
        for (PhiNode phi : merge.valuePhis()) {
            ValueNode node = phi.valueAt(pred);
            Value value = operand(node);
            assert value != null;
            if (isRegister(value)) {
                /*
                 * Fixed register intervals are not allowed at block boundaries so we introduce a
                 * new Variable.
                 */
                value = gen.emitMove(value);
            } else if (!allowObjectConstantToStackMove() && node instanceof ConstantNode && !LIRKind.isValue(value)) {
                /*
                 * Some constants are not allowed as inputs for PHIs in certain backends. Explicitly
                 * create a copy of this value to force it into a register. The new variable is only
                 * used in the PHI.
                 */
                Variable result = gen.newVariable(value.getValueKind());
                gen.emitMove(result, value);
                value = result;
            }
            values.add(value);
        }
        return values.toArray(new Value[values.size()]);
    }

    public void doBlockPrologue(@SuppressWarnings("unused") Block block, @SuppressWarnings("unused") OptionValues options) {

    }

    @Override
    @SuppressWarnings("try")
    public void doBlock(Block block, StructuredGraph graph, BlockMap<List<Node>> blockMap) {

        OptionValues options = graph.getOptions();
        try (BlockScope blockScope = gen.getBlockScope(block)) {
            setSourcePosition(null);

            if (block == gen.getResult().getLIR().getControlFlowGraph().getStartBlock()) {
                assert block.getPredecessorCount() == 0;
                emitPrologue(graph);
            } else {
                assert block.getPredecessorCount() > 0;
                // create phi-in value array
                AbstractBeginNode begin = block.getBeginNode();
                if (begin instanceof AbstractMergeNode) {
                    AbstractMergeNode merge = (AbstractMergeNode) begin;
                    LabelOp label = (LabelOp) gen.getResult().getLIR().getLIRforBlock(block).get(0);
                    label.setPhiValues(createPhiIn(merge));
                    if (Options.PrintIRWithLIR.getValue(options) && !TTY.isSuppressed()) {
                        TTY.println("Created PhiIn: " + label);

                    }
                }
            }
            doBlockPrologue(block, options);

            List<Node> nodes = blockMap.get(block);

            // Allow NodeLIRBuilder subclass to specialize code generation of any interesting groups
            // of instructions
            matchComplexExpressions(nodes);

            boolean trace = traceLIRGeneratorLevel >= 3;
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                if (node instanceof ValueNode) {
                    DebugContext debug = node.getDebug();
                    ValueNode valueNode = (ValueNode) node;
                    if (trace) {
                        TTY.println("LIRGen for " + valueNode);
                    }
                    Value operand = getOperand(valueNode);
                    if (operand == null) {
                        if (!peephole(valueNode)) {
                            try {
                                doRoot(valueNode);
                            } catch (GraalError e) {
                                throw GraalGraphError.transformAndAddContext(e, valueNode);
                            } catch (Throwable e) {
                                throw new GraalGraphError(e).addContext(valueNode);
                            }
                        }
                    } else if (ComplexMatchValue.INTERIOR_MATCH.equals(operand)) {
                        // Doesn't need to be evaluated
                        debug.log("interior match for %s", valueNode);
                    } else if (operand instanceof ComplexMatchValue) {
                        debug.log("complex match for %s", valueNode);
                        ComplexMatchValue match = (ComplexMatchValue) operand;
                        operand = match.evaluate(this);
                        if (operand != null) {
                            setResult(valueNode, operand);
                        }
                    } else {
                        // There can be cases in which the result of an instruction is already set
                        // before by other instructions.
                    }
                }
            }

            if (!gen.hasBlockEnd(block)) {
                NodeIterable<Node> successors = block.getEndNode().successors();
                assert successors.count() == block.getSuccessorCount();
                if (block.getSuccessorCount() != 1) {
                    /*
                     * If we have more than one successor, we cannot just use the first one. Since
                     * successors are unordered, this would be a random choice.
                     */
                    throw new GraalError("Block without BlockEndOp: " + block.getEndNode());
                }
                gen.emitJump(getLIRBlock((FixedNode) successors.first()));
            }

            assert verifyBlock(gen.getResult().getLIR(), block);
        }
    }

    @SuppressWarnings("try")
    protected void matchComplexExpressions(List<Node> nodes) {
        if (matchRules != null) {
            DebugContext debug = gen.getResult().getLIR().getDebug();
            try (DebugContext.Scope s = debug.scope("MatchComplexExpressions")) {
                if (LogVerbose.getValue(nodeOperands.graph().getOptions())) {
                    int i = 0;
                    for (Node node : nodes) {
                        debug.log("%d: (%s) %1S", i++, node.getUsageCount(), node);
                    }
                }

                // Match the nodes in backwards order to encourage longer matches.
                for (int index = nodes.size() - 1; index >= 0; index--) {
                    Node node = nodes.get(index);
                    if (getOperand(node) != null) {
                        continue;
                    }
                    // See if this node is the root of any MatchStatements
                    List<MatchStatement> statements = matchRules.get(node.getClass());
                    if (statements != null) {
                        for (MatchStatement statement : statements) {
                            if (statement.generate(this, index, node, nodes)) {
                                // Found a match so skip to the next
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    protected abstract boolean peephole(ValueNode valueNode);

    private void doRoot(ValueNode instr) {
        if (traceLIRGeneratorLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;
        DebugContext debug = instr.getDebug();
        debug.log("Visiting %s", instr);
        emitNode(instr);
        debug.log("Operand for %s = %s", instr, getOperand(instr));
    }

    protected void emitNode(ValueNode node) {
        if (node.getDebug().isLogEnabled() && node.stamp(NodeView.DEFAULT).isEmpty()) {
            node.getDebug().log("This node has an empty stamp, we are emitting dead code(?): %s", node);
        }
        setSourcePosition(node.getNodeSourcePosition());
        if (node instanceof LIRLowerable) {
            ((LIRLowerable) node).generate(this);
        } else {
            throw GraalError.shouldNotReachHere("node is not LIRLowerable: " + node);
        }
    }

    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getResult().getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = incomingArguments.getArgument(i);
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }

        gen.emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Value paramValue = params[param.index()];
            assert paramValue.getValueKind().equals(getLIRGeneratorTool().getLIRKind(param.stamp(NodeView.DEFAULT))) : paramValue + " " +
                            getLIRGeneratorTool().getLIRKind(param.stamp(NodeView.DEFAULT));
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitMerge(AbstractMergeNode x) {
    }

    @Override
    public void visitEndNode(AbstractEndNode end) {
        AbstractMergeNode merge = end.merge();
        JumpOp jump = newJumpOp(getLIRBlock(merge));
        jump.setPhiValues(createPhiOut(merge, end));
        append(jump);
    }

    /**
     * Runtime specific classes can override this to insert a safepoint at the end of a loop.
     */
    @Override
    public void visitLoopEnd(LoopEndNode x) {
    }

    protected JumpOp newJumpOp(LabelRef ref) {
        return new JumpOp(ref);
    }

    protected LIRKind getPhiKind(PhiNode phi) {
        return gen.getLIRKind(phi.stamp(NodeView.DEFAULT));
    }

    @Override
    public void emitIf(IfNode x) {
        emitBranch(x.condition(), getLIRBlock(x.trueSuccessor()), getLIRBlock(x.falseSuccessor()), x.probability(x.trueSuccessor()));
    }

    public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        if (node instanceof IsNullNode) {
            emitNullCheckBranch((IsNullNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else if (node instanceof CompareNode) {
            emitCompareBranch((CompareNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else if (node instanceof LogicConstantNode) {
            emitConstantBranch(((LogicConstantNode) node).getValue(), trueSuccessor, falseSuccessor);
        } else if (node instanceof IntegerTestNode) {
            emitIntegerTestBranch((IntegerTestNode) node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else {
            throw GraalError.unimplemented(node.toString());
        }
    }

    private void emitNullCheckBranch(IsNullNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        LIRKind kind = gen.getLIRKind(node.getValue().stamp(NodeView.DEFAULT));
        Value nullValue = gen.emitConstant(kind, node.nullConstant());
        gen.emitCompareBranch(kind.getPlatformKind(), operand(node.getValue()), nullValue, Condition.EQ, false, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        PlatformKind kind = gen.getLIRKind(compare.getX().stamp(NodeView.DEFAULT)).getPlatformKind();
        gen.emitCompareBranch(kind, operand(compare.getX()), operand(compare.getY()), compare.condition().asCondition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor,
                        trueSuccessorProbability);
    }

    public void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        gen.emitIntegerTestBranch(operand(test.getX()), operand(test.getY()), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock) {
        LabelRef block = value ? trueSuccessorBlock : falseSuccessorBlock;
        gen.emitJump(block);
    }

    @Override
    public void emitConditional(ConditionalNode conditional) {
        Value tVal = operand(conditional.trueValue());
        Value fVal = operand(conditional.falseValue());
        setResult(conditional, emitConditional(conditional.condition(), tVal, fVal));
    }

    public Variable emitConditional(LogicNode node, Value trueValue, Value falseValue) {
        if (node instanceof IsNullNode) {
            IsNullNode isNullNode = (IsNullNode) node;
            LIRKind kind = gen.getLIRKind(isNullNode.getValue().stamp(NodeView.DEFAULT));
            Value nullValue = gen.emitConstant(kind, isNullNode.nullConstant());
            return gen.emitConditionalMove(kind.getPlatformKind(), operand(isNullNode.getValue()), nullValue, Condition.EQ, false, trueValue, falseValue);
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            PlatformKind kind = gen.getLIRKind(compare.getX().stamp(NodeView.DEFAULT)).getPlatformKind();
            return gen.emitConditionalMove(kind, operand(compare.getX()), operand(compare.getY()), compare.condition().asCondition(), compare.unorderedIsTrue(), trueValue, falseValue);
        } else if (node instanceof LogicConstantNode) {
            return gen.emitMove(((LogicConstantNode) node).getValue() ? trueValue : falseValue);
        } else if (node instanceof IntegerTestNode) {
            IntegerTestNode test = (IntegerTestNode) node;
            return gen.emitIntegerTestMove(operand(test.getX()), operand(test.getY()), trueValue, falseValue);
        } else {
            throw GraalError.unimplemented(node.toString());
        }
    }

    @Override
    public void emitInvoke(Invoke x) {
        LoweredCallTargetNode callTarget = (LoweredCallTargetNode) x.callTarget();
        FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
        CallingConvention invokeCc = frameMapBuilder.getRegisterConfig().getCallingConvention(callTarget.callType(), x.asNode().stamp(NodeView.DEFAULT).javaType(gen.getMetaAccess()),
                        callTarget.signature(), gen);
        frameMapBuilder.callsMethod(invokeCc);

        Value[] parameters = visitInvokeArguments(invokeCc, callTarget.arguments());

        LabelRef exceptionEdge = null;
        if (x instanceof InvokeWithExceptionNode) {
            exceptionEdge = getLIRBlock(((InvokeWithExceptionNode) x).exceptionEdge());
        }
        LIRFrameState callState = stateWithExceptionEdge(x, exceptionEdge);

        Value result = invokeCc.getReturn();
        if (callTarget instanceof DirectCallTargetNode) {
            emitDirectCall((DirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else if (callTarget instanceof IndirectCallTargetNode) {
            emitIndirectCall((IndirectCallTargetNode) callTarget, result, parameters, AllocatableValue.NONE, callState);
        } else {
            throw GraalError.shouldNotReachHere();
        }

        if (isLegal(result)) {
            setResult(x.asNode(), gen.emitMove(result));
        }

        if (x instanceof InvokeWithExceptionNode) {
            gen.emitJump(getLIRBlock(((InvokeWithExceptionNode) x).next()));
        }
    }

    protected abstract void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    protected abstract void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState);

    @Override
    public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
        // for each argument, load it into the correct location
        Value[] result = new Value[arguments.size()];
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                AllocatableValue operand = invokeCc.getArgument(j);
                gen.emitMove(operand, operand(arg));
                result[j] = operand;
                j++;
            } else {
                throw GraalError.shouldNotReachHere("I thought we no longer have null entries for two-slot types...");
            }
        }
        return result;
    }

    /**
     * This method tries to create a switch implementation that is optimal for the given switch. It
     * will either generate a sequential if/then/else cascade, a set of range tests or a table
     * switch.
     *
     * If the given switch does not contain int keys, it will always create a sequential
     * implementation.
     */
    @Override
    public void emitSwitch(SwitchNode x) {
        assert x.defaultSuccessor() != null;
        LabelRef defaultTarget = getLIRBlock(x.defaultSuccessor());
        int keyCount = x.keyCount();
        if (keyCount == 0) {
            gen.emitJump(defaultTarget);
        } else {
            Variable value = gen.load(operand(x.value()));
            if (keyCount == 1) {
                assert defaultTarget != null;
                double probability = x.probability(x.keySuccessor(0));
                LIRKind kind = gen.getLIRKind(x.value().stamp(NodeView.DEFAULT));
                Value key = gen.emitConstant(kind, x.keyAt(0));
                gen.emitCompareBranch(kind.getPlatformKind(), gen.load(operand(x.value())), key, Condition.EQ, false, getLIRBlock(x.keySuccessor(0)), defaultTarget, probability);
            } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                IntegerSwitchNode intSwitch = (IntegerSwitchNode) x;
                LabelRef[] keyTargets = new LabelRef[keyCount];
                JavaConstant[] keyConstants = new JavaConstant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                JavaKind keyKind = intSwitch.keyAt(0).getJavaKind();
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(intSwitch.keySuccessor(i));
                    keyConstants[i] = intSwitch.keyAt(i);
                    keyProbabilities[i] = intSwitch.keyProbability(i);
                    assert keyConstants[i].getJavaKind() == keyKind;
                }
                gen.emitStrategySwitch(keyConstants, keyProbabilities, keyTargets, defaultTarget, value);
            } else {
                // keyKind != JavaKind.Int || !x.isSorted()
                LabelRef[] keyTargets = new LabelRef[keyCount];
                Constant[] keyConstants = new Constant[keyCount];
                double[] keyProbabilities = new double[keyCount];
                for (int i = 0; i < keyCount; i++) {
                    keyTargets[i] = getLIRBlock(x.keySuccessor(i));
                    keyConstants[i] = x.keyAt(i);
                    keyProbabilities[i] = x.keyProbability(i);
                }

                // hopefully only a few entries
                gen.emitStrategySwitch(new SwitchStrategy.SequentialStrategy(keyProbabilities, keyConstants), value, keyTargets, defaultTarget);
            }
        }
    }

    public DebugInfoBuilder getDebugInfoBuilder() {
        assert debugInfoBuilder != null;
        return debugInfoBuilder;
    }

    private static FrameState getFrameState(DeoptimizingNode deopt) {
        if (deopt instanceof DeoptimizingNode.DeoptBefore) {
            assert !(deopt instanceof DeoptimizingNode.DeoptDuring || deopt instanceof DeoptimizingNode.DeoptAfter);
            return ((DeoptimizingNode.DeoptBefore) deopt).stateBefore();
        } else if (deopt instanceof DeoptimizingNode.DeoptDuring) {
            assert !(deopt instanceof DeoptimizingNode.DeoptAfter);
            return ((DeoptimizingNode.DeoptDuring) deopt).stateDuring();
        } else {
            assert deopt instanceof DeoptimizingNode.DeoptAfter;
            return ((DeoptimizingNode.DeoptAfter) deopt).stateAfter();
        }
    }

    @Override
    public LIRFrameState state(DeoptimizingNode deopt) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateFor(getFrameState(deopt));
    }

    public LIRFrameState stateWithExceptionEdge(DeoptimizingNode deopt, LabelRef exceptionEdge) {
        if (!deopt.canDeoptimize()) {
            return null;
        }
        return stateForWithExceptionEdge(getFrameState(deopt), exceptionEdge);
    }

    public LIRFrameState stateFor(FrameState state) {
        return stateForWithExceptionEdge(state, null);
    }

    public LIRFrameState stateForWithExceptionEdge(FrameState state, LabelRef exceptionEdge) {
        if (gen.needOnlyOopMaps()) {
            return new LIRFrameState(null, null, null);
        }
        assert state != null;
        return getDebugInfoBuilder().build(state, exceptionEdge);
    }

    @Override
    public void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp stamp, double probability) {
        LIRKind cmpKind = getLIRGeneratorTool().getLIRKind(stamp);
        gen.emitOverflowCheckBranch(getLIRBlock(overflowSuccessor), getLIRBlock(next), cmpKind, probability);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        append(new FullInfopointOp(stateFor(i.getState()), i.getReason()));
    }

    @Override
    public void setSourcePosition(NodeSourcePosition position) {
        gen.setSourcePosition(position);
    }

    @Override
    public LIRGeneratorTool getLIRGeneratorTool() {
        return gen;
    }
}
