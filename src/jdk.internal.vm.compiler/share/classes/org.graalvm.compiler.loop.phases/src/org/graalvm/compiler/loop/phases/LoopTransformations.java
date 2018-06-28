/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop.phases;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.loop.MathUtil.add;
import static org.graalvm.compiler.loop.MathUtil.sub;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.loop.CountedLoopInfo;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.InductionVariable.Direction;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopFragmentInside;
import org.graalvm.compiler.loop.LoopFragmentWhole;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public abstract class LoopTransformations {

    private LoopTransformations() {
        // does not need to be instantiated
    }

    public static void peel(LoopEx loop) {
        loop.inside().duplicate().insertBefore(loop);
        loop.loopBegin().setLoopFrequency(Math.max(0.0, loop.loopBegin().loopFrequency() - 1));
    }

    public static void fullUnroll(LoopEx loop, PhaseContext context, CanonicalizerPhase canonicalizer) {
        // assert loop.isCounted(); //TODO (gd) strengthen : counted with known trip count
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = loopBegin.graph();
        int initialNodeCount = graph.getNodeCount();
        while (!loopBegin.isDeleted()) {
            Mark mark = graph.getMark();
            peel(loop);
            canonicalizer.applyIncremental(graph, context, mark);
            loop.invalidateFragments();
            if (graph.getNodeCount() > initialNodeCount + MaximumDesiredSize.getValue(graph.getOptions()) * 2) {
                throw new RetryableBailoutException("FullUnroll : Graph seems to grow out of proportion");
            }
        }
    }

    public static void unswitch(LoopEx loop, List<ControlSplitNode> controlSplitNodeSet) {
        ControlSplitNode firstNode = controlSplitNodeSet.iterator().next();
        LoopFragmentWhole originalLoop = loop.whole();
        StructuredGraph graph = firstNode.graph();

        loop.loopBegin().incrementUnswitches();

        // create new control split out of loop
        ControlSplitNode newControlSplit = (ControlSplitNode) firstNode.copyWithInputs();
        originalLoop.entryPoint().replaceAtPredecessor(newControlSplit);

        /*
         * The code below assumes that all of the control split nodes have the same successor
         * structure, which should have been enforced by findUnswitchable.
         */
        Iterator<Position> successors = firstNode.successorPositions().iterator();
        assert successors.hasNext();
        // original loop is used as first successor
        Position firstPosition = successors.next();
        AbstractBeginNode originalLoopBegin = BeginNode.begin(originalLoop.entryPoint());
        firstPosition.set(newControlSplit, originalLoopBegin);
        originalLoopBegin.setNodeSourcePosition(firstPosition.get(firstNode).getNodeSourcePosition());

        while (successors.hasNext()) {
            Position position = successors.next();
            // create a new loop duplicate and connect it.
            LoopFragmentWhole duplicateLoop = originalLoop.duplicate();
            AbstractBeginNode newBegin = BeginNode.begin(duplicateLoop.entryPoint());
            newBegin.setNodeSourcePosition(position.get(firstNode).getNodeSourcePosition());
            position.set(newControlSplit, newBegin);

            // For each cloned ControlSplitNode, simplify the proper path
            for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
                ControlSplitNode duplicatedControlSplit = duplicateLoop.getDuplicatedNode(controlSplitNode);
                if (duplicatedControlSplit.isAlive()) {
                    AbstractBeginNode survivingSuccessor = (AbstractBeginNode) position.get(duplicatedControlSplit);
                    survivingSuccessor.replaceAtUsages(InputType.Guard, newBegin);
                    graph.removeSplitPropagate(duplicatedControlSplit, survivingSuccessor);
                }
            }
        }
        // original loop is simplified last to avoid deleting controlSplitNode too early
        for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
            if (controlSplitNode.isAlive()) {
                AbstractBeginNode survivingSuccessor = (AbstractBeginNode) firstPosition.get(controlSplitNode);
                survivingSuccessor.replaceAtUsages(InputType.Guard, originalLoopBegin);
                graph.removeSplitPropagate(controlSplitNode, survivingSuccessor);
            }
        }

        // TODO (gd) probabilities need some amount of fixup.. (probably also in other transforms)
    }

    public static void partialUnroll(LoopEx loop) {
        assert loop.loopBegin().isMainLoop();
        loop.loopBegin().graph().getDebug().log("LoopPartialUnroll %s", loop);

        LoopFragmentInside newSegment = loop.inside().duplicate();
        newSegment.insertWithinAfter(loop);

    }

    // This function splits candidate loops into pre, main and post loops,
    // dividing the iteration space to facilitate the majority of iterations
    // being executed in a main loop, which will have RCE implemented upon it.
    // The initial loop form is constrained to single entry/exit, but can have
    // flow. The translation looks like:
    //
    //  @formatter:off
    //
    //       (Simple Loop entry)                   (Pre Loop Entry)
    //                |                                  |
    //         (LoopBeginNode)                    (LoopBeginNode)
    //                |                                  |
    //       (Loop Control Test)<------   ==>  (Loop control Test)<------
    //         /               \       \         /               \       \
    //    (Loop Exit)      (Loop Body) |    (Loop Exit)      (Loop Body) |
    //        |                |       |        |                |       |
    // (continue code)     (Loop End)  |  if (M < length)*   (Loop End)  |
    //                         \       /       /      \           \      /
    //                          ----->        /       |            ----->
    //                                       /  if ( ... )*
    //                                      /     /       \
    //                                     /     /         \
    //                                    /     /           \
    //                                   |     /     (Main Loop Entry)
    //                                   |    |             |
    //                                   |    |      (LoopBeginNode)
    //                                   |    |             |
    //                                   |    |     (Loop Control Test)<------
    //                                   |    |      /               \        \
    //                                   |    |  (Loop Exit)      (Loop Body) |
    //                                    \   \      |                |       |
    //                                     \   \     |            (Loop End)  |
    //                                      \   \    |                \       /
    //                                       \   \   |                 ------>
    //                                        \   \  |
    //                                      (Main Loop Merge)*
    //                                               |
    //                                      (Post Loop Entry)
    //                                               |
    //                                        (LoopBeginNode)
    //                                               |
    //                                       (Loop Control Test)<-----
    //                                        /               \       \
    //                                    (Loop Exit)     (Loop Body) |
    //                                        |               |       |
    //                                 (continue code)    (Loop End)  |
    //                                                         \      /
    //                                                          ----->
    //
    // Key: "*" = optional.
    // @formatter:on
    //
    // The value "M" is the maximal value of the loop trip for the original
    // loop. The value of "length" is applicable to the number of arrays found
    // in the loop but is reduced if some or all of the arrays are known to be
    // the same length as "M". The maximum number of tests can be equal to the
    // number of arrays in the loop, where multiple instances of an array are
    // subsumed into a single test for that arrays length.
    //
    // If the optional main loop entry tests are absent, the Pre Loop exit
    // connects to the Main loops entry and there is no merge hanging off the
    // main loops exit to converge flow from said tests. All split use data
    // flow is mitigated through phi(s) in the main merge if present and
    // passed through the main and post loop phi(s) from the originating pre
    // loop with final phi(s) and data flow patched to the "continue code".
    // The pre loop is constrained to one iteration for now and will likely
    // be updated to produce vector alignment if applicable.

    public static LoopBeginNode insertPrePostLoops(LoopEx loop) {
        StructuredGraph graph = loop.loopBegin().graph();
        graph.getDebug().log("LoopTransformations.insertPrePostLoops %s", loop);
        LoopFragmentWhole preLoop = loop.whole();
        CountedLoopInfo preCounted = loop.counted();
        IfNode preLimit = preCounted.getLimitTest();
        assert preLimit != null;
        LoopBeginNode preLoopBegin = loop.loopBegin();
        InductionVariable preIv = preCounted.getCounter();
        LoopExitNode preLoopExitNode = preLoopBegin.getSingleLoopExit();
        FixedNode continuationNode = preLoopExitNode.next();

        // Each duplication is inserted after the original, ergo create the post loop first
        LoopFragmentWhole mainLoop = preLoop.duplicate();
        LoopFragmentWhole postLoop = preLoop.duplicate();
        preLoopBegin.incrementSplits();
        preLoopBegin.incrementSplits();
        preLoopBegin.setPreLoop();
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "After duplication");
        LoopBeginNode mainLoopBegin = mainLoop.getDuplicatedNode(preLoopBegin);
        mainLoopBegin.setMainLoop();
        LoopBeginNode postLoopBegin = postLoop.getDuplicatedNode(preLoopBegin);
        postLoopBegin.setPostLoop();

        EndNode postEndNode = getBlockEndAfterLoopExit(postLoopBegin);
        AbstractMergeNode postMergeNode = postEndNode.merge();
        LoopExitNode postLoopExitNode = postLoopBegin.getSingleLoopExit();

        // Update the main loop phi initialization to carry from the pre loop
        for (PhiNode prePhiNode : preLoopBegin.phis()) {
            PhiNode mainPhiNode = mainLoop.getDuplicatedNode(prePhiNode);
            mainPhiNode.setValueAt(0, prePhiNode);
        }

        EndNode mainEndNode = getBlockEndAfterLoopExit(mainLoopBegin);
        AbstractMergeNode mainMergeNode = mainEndNode.merge();
        AbstractEndNode postEntryNode = postLoopBegin.forwardEnd();

        // In the case of no Bounds tests, we just flow right into the main loop
        AbstractBeginNode mainLandingNode = BeginNode.begin(postEntryNode);
        LoopExitNode mainLoopExitNode = mainLoopBegin.getSingleLoopExit();
        mainLoopExitNode.setNext(mainLandingNode);
        preLoopExitNode.setNext(mainLoopBegin.forwardEnd());

        // Add and update any phi edges as per merge usage as needed and update usages
        processPreLoopPhis(loop, mainLoop, postLoop);
        continuationNode.predecessor().clearSuccessors();
        postLoopExitNode.setNext(continuationNode);
        cleanupMerge(postMergeNode, postLoopExitNode);
        cleanupMerge(mainMergeNode, mainLandingNode);

        // Change the preLoop to execute one iteration for now
        updateMainLoopLimit(preLimit, preIv, mainLoop);
        updatePreLoopLimit(preLimit, preIv, preCounted);
        preLoopBegin.setLoopFrequency(1);
        mainLoopBegin.setLoopFrequency(Math.max(0.0, mainLoopBegin.loopFrequency() - 2));
        postLoopBegin.setLoopFrequency(Math.max(0.0, postLoopBegin.loopFrequency() - 1));

        // The pre and post loops don't require safepoints at all
        for (SafepointNode safepoint : preLoop.nodes().filter(SafepointNode.class)) {
            graph.removeFixed(safepoint);
        }
        for (SafepointNode safepoint : postLoop.nodes().filter(SafepointNode.class)) {
            graph.removeFixed(safepoint);
        }
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "InsertPrePostLoops %s", loop);
        return mainLoopBegin;
    }

    /**
     * Cleanup the merge and remove the predecessors too.
     */
    private static void cleanupMerge(AbstractMergeNode mergeNode, AbstractBeginNode landingNode) {
        for (EndNode end : mergeNode.cfgPredecessors().snapshot()) {
            mergeNode.removeEnd(end);
            end.safeDelete();
        }
        mergeNode.prepareDelete(landingNode);
        mergeNode.safeDelete();
    }

    private static void processPreLoopPhis(LoopEx preLoop, LoopFragmentWhole mainLoop, LoopFragmentWhole postLoop) {
        // process phis for the post loop
        LoopBeginNode preLoopBegin = preLoop.loopBegin();
        for (PhiNode prePhiNode : preLoopBegin.phis()) {
            PhiNode postPhiNode = postLoop.getDuplicatedNode(prePhiNode);
            PhiNode mainPhiNode = mainLoop.getDuplicatedNode(prePhiNode);
            postPhiNode.setValueAt(0, mainPhiNode);

            // Build a work list to update the pre loop phis to the post loops phis
            for (Node usage : prePhiNode.usages().snapshot()) {
                if (usage == mainPhiNode) {
                    continue;
                }
                if (preLoop.isOutsideLoop(usage)) {
                    usage.replaceFirstInput(prePhiNode, postPhiNode);
                }
            }
        }
        for (Node node : preLoop.inside().nodes()) {
            for (Node externalUsage : node.usages().snapshot()) {
                if (preLoop.isOutsideLoop(externalUsage)) {
                    Node postUsage = postLoop.getDuplicatedNode(node);
                    assert postUsage != null;
                    externalUsage.replaceFirstInput(node, postUsage);
                }
            }
        }
    }

    /**
     * Find the end of the block following the LoopExit.
     */
    private static EndNode getBlockEndAfterLoopExit(LoopBeginNode curLoopBegin) {
        FixedNode node = curLoopBegin.getSingleLoopExit().next();
        // Find the last node after the exit blocks starts
        return getBlockEnd(node);
    }

    private static EndNode getBlockEnd(FixedNode node) {
        FixedNode curNode = node;
        while (curNode instanceof FixedWithNextNode) {
            curNode = ((FixedWithNextNode) curNode).next();
        }
        return (EndNode) curNode;
    }

    private static void updateMainLoopLimit(IfNode preLimit, InductionVariable preIv, LoopFragmentWhole mainLoop) {
        // Update the main loops limit test to be different than the post loop
        StructuredGraph graph = preLimit.graph();
        IfNode mainLimit = mainLoop.getDuplicatedNode(preLimit);
        LogicNode ifTest = mainLimit.condition();
        CompareNode compareNode = (CompareNode) ifTest;
        ValueNode prePhi = preIv.valueNode();
        ValueNode mainPhi = mainLoop.getDuplicatedNode(prePhi);
        ValueNode preStride = preIv.strideNode();
        ValueNode mainStride;
        if (preStride instanceof ConstantNode) {
            mainStride = preStride;
        } else {
            mainStride = mainLoop.getDuplicatedNode(preStride);
        }
        // Fetch the bounds to pose lowering the range by one
        ValueNode ub = null;
        if (compareNode.getX() == mainPhi) {
            ub = compareNode.getY();
        } else if (compareNode.getY() == mainPhi) {
            ub = compareNode.getX();
        } else {
            throw GraalError.shouldNotReachHere();
        }

        // Preloop always performs at least one iteration, so remove that from the main loop.
        ValueNode newLimit = sub(graph, ub, mainStride);

        // Re-wire the condition with the new limit
        compareNode.replaceFirstInput(ub, newLimit);
    }

    private static void updatePreLoopLimit(IfNode preLimit, InductionVariable preIv, CountedLoopInfo preCounted) {
        // Update the pre loops limit test
        StructuredGraph graph = preLimit.graph();
        LogicNode ifTest = preLimit.condition();
        CompareNode compareNode = (CompareNode) ifTest;
        ValueNode prePhi = preIv.valueNode();
        // Make new limit one iteration
        ValueNode initIv = preCounted.getStart();
        ValueNode newLimit = add(graph, initIv, preIv.strideNode());

        // Fetch the variable we are not replacing and configure the one we are
        ValueNode ub;
        if (compareNode.getX() == prePhi) {
            ub = compareNode.getY();
        } else if (compareNode.getY() == prePhi) {
            ub = compareNode.getX();
        } else {
            throw GraalError.shouldNotReachHere();
        }
        // Re-wire the condition with the new limit
        if (preIv.direction() == Direction.Up) {
            compareNode.replaceFirstInput(ub, graph.unique(new ConditionalNode(graph.unique(new IntegerLessThanNode(newLimit, ub)), newLimit, ub)));
        } else {
            compareNode.replaceFirstInput(ub, graph.unique(new ConditionalNode(graph.unique(new IntegerLessThanNode(ub, newLimit)), newLimit, ub)));
        }
    }

    public static List<ControlSplitNode> findUnswitchable(LoopEx loop) {
        List<ControlSplitNode> controls = null;
        ValueNode invariantValue = null;
        for (IfNode ifNode : loop.whole().nodes().filter(IfNode.class)) {
            if (loop.isOutsideLoop(ifNode.condition())) {
                if (controls == null) {
                    invariantValue = ifNode.condition();
                    controls = new ArrayList<>();
                    controls.add(ifNode);
                } else if (ifNode.condition() == invariantValue) {
                    controls.add(ifNode);
                }
            }
        }
        if (controls == null) {
            SwitchNode firstSwitch = null;
            for (SwitchNode switchNode : loop.whole().nodes().filter(SwitchNode.class)) {
                if (switchNode.successors().count() > 1 && loop.isOutsideLoop(switchNode.value())) {
                    if (controls == null) {
                        firstSwitch = switchNode;
                        invariantValue = switchNode.value();
                        controls = new ArrayList<>();
                        controls.add(switchNode);
                    } else if (switchNode.value() == invariantValue && firstSwitch.structureEquals(switchNode)) {
                        // Only collect switches which test the same values in the same order
                        controls.add(switchNode);
                    }
                }
            }
        }
        return controls;
    }

    public static boolean isUnrollableLoop(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().getCounter().isConstantStride() || !loop.loop().getChildren().isEmpty()) {
            return false;
        }
        LoopBeginNode loopBegin = loop.loopBegin();
        LogicNode condition = loop.counted().getLimitTest().condition();
        if (!(condition instanceof CompareNode)) {
            return false;
        }
        if (((CompareNode) condition).condition() == CanonicalCondition.EQ) {
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s condition unsupported %s ", loopBegin, ((CompareNode) condition).condition());
            return false;
        }
        if (loopBegin.isMainLoop() || loopBegin.isSimpleLoop()) {
            // Flow-less loops to partial unroll for now. 3 blocks corresponds to an if that either
            // exits or continues the loop. There might be fixed and floating work within the loop
            // as well.
            if (loop.loop().getBlocks().size() < 3) {
                return true;
            }
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s too large to unroll %s ", loopBegin, loop.loop().getBlocks().size());
        }
        return false;
    }
}
