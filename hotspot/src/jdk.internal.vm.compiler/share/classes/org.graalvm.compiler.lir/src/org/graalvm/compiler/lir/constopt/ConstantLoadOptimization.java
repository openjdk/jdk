/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.constopt;

import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.constopt.ConstantTree.Flags;
import org.graalvm.compiler.lir.constopt.ConstantTree.NodeCost;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase;
import org.graalvm.compiler.options.NestedBooleanOptionValue;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This optimization tries to improve the handling of constants by replacing a single definition of
 * a constant, which is potentially scheduled into a block with high probability, with one or more
 * definitions in blocks with a lower probability.
 */
public final class ConstantLoadOptimization extends PreAllocationOptimizationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable constant load optimization.", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptConstantLoadOptimization = new NestedBooleanOptionValue(LIROptimization, true);
        // @formatter:on
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
        LIRGeneratorTool lirGen = context.lirGen;
        new Optimization(lirGenRes.getLIR(), lirGen).apply();
    }

    private static final DebugCounter constantsTotal = Debug.counter("ConstantLoadOptimization[total]");
    private static final DebugCounter phiConstantsSkipped = Debug.counter("ConstantLoadOptimization[PhisSkipped]");
    private static final DebugCounter singleUsageConstantsSkipped = Debug.counter("ConstantLoadOptimization[SingleUsageSkipped]");
    private static final DebugCounter usageAtDefinitionSkipped = Debug.counter("ConstantLoadOptimization[UsageAtDefinitionSkipped]");
    private static final DebugCounter materializeAtDefinitionSkipped = Debug.counter("ConstantLoadOptimization[MaterializeAtDefinitionSkipped]");
    private static final DebugCounter constantsOptimized = Debug.counter("ConstantLoadOptimization[optimized]");

    private static final class Optimization {
        private final LIR lir;
        private final LIRGeneratorTool lirGen;
        private final VariableMap<DefUseTree> map;
        private final BitSet phiConstants;
        private final BitSet defined;
        private final BlockMap<List<UseEntry>> blockMap;
        private final BlockMap<LIRInsertionBuffer> insertionBuffers;

        private Optimization(LIR lir, LIRGeneratorTool lirGen) {
            this.lir = lir;
            this.lirGen = lirGen;
            this.map = new VariableMap<>();
            this.phiConstants = new BitSet();
            this.defined = new BitSet();
            this.insertionBuffers = new BlockMap<>(lir.getControlFlowGraph());
            this.blockMap = new BlockMap<>(lir.getControlFlowGraph());
        }

        @SuppressWarnings("try")
        private void apply() {
            try (Indent indent = Debug.logAndIndent("ConstantLoadOptimization")) {
                try (Scope s = Debug.scope("BuildDefUseTree")) {
                    // build DefUseTree
                    for (AbstractBlockBase<?> b : lir.getControlFlowGraph().getBlocks()) {
                        this.analyzeBlock(b);
                    }
                    // remove all with only one use
                    map.filter(t -> {
                        if (t.usageCount() > 1) {
                            return true;
                        } else {
                            singleUsageConstantsSkipped.increment();
                            return false;
                        }
                    });
                    // collect block map
                    map.forEach(tree -> tree.forEach(this::addUsageToBlockMap));
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }

                try (Scope s = Debug.scope("BuildConstantTree")) {
                    // create ConstantTree
                    map.forEach(this::createConstantTree);

                    // insert moves, delete null instructions and reset instruction ids
                    for (AbstractBlockBase<?> b : lir.getControlFlowGraph().getBlocks()) {
                        this.rewriteBlock(b);
                    }

                    assert verifyStates();
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }

        private boolean verifyStates() {
            map.forEach(this::verifyStateUsage);
            return true;
        }

        private void verifyStateUsage(DefUseTree tree) {
            Variable var = tree.getVariable();
            ValueConsumer stateConsumer = new ValueConsumer() {

                @Override
                public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    assert !operand.equals(var) : "constant usage through variable in frame state " + var;
                }
            };
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                    // set instruction id to the index in the lir instruction list
                    inst.visitEachState(stateConsumer);
                }
            }
        }

        private static boolean isConstantLoad(LIRInstruction inst) {
            if (!(inst instanceof LoadConstantOp)) {
                return false;
            }
            LoadConstantOp load = (LoadConstantOp) inst;
            return isVariable(load.getResult());
        }

        private void addUsageToBlockMap(UseEntry entry) {
            AbstractBlockBase<?> block = entry.getBlock();
            List<UseEntry> list = blockMap.get(block);
            if (list == null) {
                list = new ArrayList<>();
                blockMap.put(block, list);
            }
            list.add(entry);
        }

        /**
         * Collects def-use information for a {@code block}.
         */
        @SuppressWarnings("try")
        private void analyzeBlock(AbstractBlockBase<?> block) {
            try (Indent indent = Debug.logAndIndent("Block: %s", block)) {

                InstructionValueConsumer loadConsumer = (instruction, value, mode, flags) -> {
                    if (isVariable(value)) {
                        Variable var = (Variable) value;

                        if (!phiConstants.get(var.index)) {
                            if (!defined.get(var.index)) {
                                defined.set(var.index);
                                if (isConstantLoad(instruction)) {
                                    Debug.log("constant load: %s", instruction);
                                    map.put(var, new DefUseTree(instruction, block));
                                    constantsTotal.increment();
                                }
                            } else {
                                // Variable is redefined, this only happens for constant loads
                                // introduced by phi resolution -> ignore.
                                DefUseTree removed = map.remove(var);
                                if (removed != null) {
                                    phiConstantsSkipped.increment();
                                }
                                phiConstants.set(var.index);
                                Debug.log(Debug.VERBOSE_LOG_LEVEL, "Removing phi variable: %s", var);
                            }
                        } else {
                            assert defined.get(var.index) : "phi but not defined? " + var;
                        }
                    }
                };

                InstructionValueConsumer useConsumer = (instruction, value, mode, flags) -> {
                    if (isVariable(value)) {
                        Variable var = (Variable) value;
                        if (!phiConstants.get(var.index)) {
                            DefUseTree tree = map.get(var);
                            if (tree != null) {
                                tree.addUsage(block, instruction, value);
                                Debug.log("usage of %s : %s", var, instruction);
                            }
                        }
                    }
                };

                int opId = 0;
                for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                    // set instruction id to the index in the lir instruction list
                    inst.setId(opId++);
                    inst.visitEachOutput(loadConsumer);
                    inst.visitEachInput(useConsumer);
                    inst.visitEachAlive(useConsumer);

                }
            }
        }

        /**
         * Creates the dominator tree and searches for an solution.
         */
        @SuppressWarnings("try")
        private void createConstantTree(DefUseTree tree) {
            ConstantTree constTree = new ConstantTree(lir.getControlFlowGraph(), tree);
            constTree.set(Flags.SUBTREE, tree.getBlock());
            tree.forEach(u -> constTree.set(Flags.USAGE, u.getBlock()));

            if (constTree.get(Flags.USAGE, tree.getBlock())) {
                // usage in the definition block -> no optimization
                usageAtDefinitionSkipped.increment();
                return;
            }

            constTree.markBlocks();

            NodeCost cost = ConstantTreeAnalyzer.analyze(constTree, tree.getBlock());
            int usageCount = cost.getUsages().size();
            assert usageCount == tree.usageCount() : "Usage count differs: " + usageCount + " vs. " + tree.usageCount();

            if (Debug.isLogEnabled()) {
                try (Indent i = Debug.logAndIndent("Variable: %s, Block: %s, prob.: %f", tree.getVariable(), tree.getBlock(), tree.getBlock().probability())) {
                    Debug.log("Usages result: %s", cost);
                }

            }

            if (cost.getNumMaterializations() > 1 || cost.getBestCost() < tree.getBlock().probability()) {
                try (Scope s = Debug.scope("CLOmodify", constTree); Indent i = Debug.logAndIndent("Replacing %s = %s", tree.getVariable(), tree.getConstant().toValueString())) {
                    // mark original load for removal
                    deleteInstruction(tree);
                    constantsOptimized.increment();

                    // collect result
                    createLoads(tree, constTree, tree.getBlock());

                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            } else {
                // no better solution found
                materializeAtDefinitionSkipped.increment();
            }
            Debug.dump(Debug.INFO_LOG_LEVEL, constTree, "ConstantTree for %s", tree.getVariable());
        }

        private void createLoads(DefUseTree tree, ConstantTree constTree, AbstractBlockBase<?> startBlock) {
            Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();
            worklist.add(startBlock);
            while (!worklist.isEmpty()) {
                AbstractBlockBase<?> block = worklist.pollLast();
                if (constTree.get(Flags.CANDIDATE, block)) {
                    constTree.set(Flags.MATERIALIZE, block);
                    // create and insert load
                    insertLoad(tree.getConstant(), tree.getVariable().getValueKind(), block, constTree.getCost(block).getUsages());
                } else {
                    for (AbstractBlockBase<?> dominated : block.getDominated()) {
                        if (constTree.isMarked(dominated)) {
                            worklist.addLast(dominated);
                        }
                    }
                }
            }
        }

        private void insertLoad(Constant constant, ValueKind<?> kind, AbstractBlockBase<?> block, List<UseEntry> usages) {
            assert usages != null && usages.size() > 0 : String.format("No usages %s %s %s", constant, block, usages);
            // create variable
            Variable variable = lirGen.newVariable(kind);
            // create move
            LIRInstruction move = lirGen.getSpillMoveFactory().createLoad(variable, constant);
            // insert instruction
            getInsertionBuffer(block).append(1, move);
            Debug.log("new move (%s) and inserted in block %s", move, block);
            // update usages
            for (UseEntry u : usages) {
                u.setValue(variable);
                Debug.log("patched instruction %s", u.getInstruction());
            }
        }

        /**
         * Inserts the constant loads created in {@link #createConstantTree} and deletes the
         * original definition.
         */
        private void rewriteBlock(AbstractBlockBase<?> block) {
            // insert moves
            LIRInsertionBuffer buffer = insertionBuffers.get(block);
            if (buffer != null) {
                assert buffer.initialized() : "not initialized?";
                buffer.finish();
            }

            // delete instructions
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            boolean hasDead = false;
            for (LIRInstruction inst : instructions) {
                if (inst == null) {
                    hasDead = true;
                } else {
                    inst.setId(-1);
                }
            }
            if (hasDead) {
                // Remove null values from the list.
                instructions.removeAll(Collections.singleton(null));
            }
        }

        private void deleteInstruction(DefUseTree tree) {
            AbstractBlockBase<?> block = tree.getBlock();
            LIRInstruction instruction = tree.getInstruction();
            Debug.log("deleting instruction %s from block %s", instruction, block);
            lir.getLIRforBlock(block).set(instruction.id(), null);
        }

        private LIRInsertionBuffer getInsertionBuffer(AbstractBlockBase<?> block) {
            LIRInsertionBuffer insertionBuffer = insertionBuffers.get(block);
            if (insertionBuffer == null) {
                insertionBuffer = new LIRInsertionBuffer();
                insertionBuffers.put(block, insertionBuffer);
                assert !insertionBuffer.initialized() : "already initialized?";
                List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                insertionBuffer.init(instructions);
            }
            return insertionBuffer;
        }
    }
}
