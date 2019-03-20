/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.graph.iterators.NodePredicates.isNotA;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

@NodeInfo
public final class LoopBeginNode extends AbstractMergeNode implements IterableNodeType, LIRLowerable {

    public static final NodeClass<LoopBeginNode> TYPE = NodeClass.create(LoopBeginNode.class);
    protected double loopFrequency;
    protected double loopOrigFrequency;
    protected int nextEndIndex;
    protected int unswitches;
    protected int splits;
    protected int inversionCount;
    protected LoopType loopType;
    protected int unrollFactor;
    protected boolean osrLoop;

    public enum LoopType {
        SIMPLE_LOOP,
        PRE_LOOP,
        MAIN_LOOP,
        POST_LOOP
    }

    /** See {@link LoopEndNode#canSafepoint} for more information. */
    boolean canEndsSafepoint;

    @OptionalInput(InputType.Guard) GuardingNode overflowGuard;

    public LoopBeginNode() {
        super(TYPE);
        loopFrequency = 1;
        loopOrigFrequency = 1;
        unswitches = 0;
        splits = 0;
        this.canEndsSafepoint = true;
        loopType = LoopType.SIMPLE_LOOP;
        unrollFactor = 1;
    }

    public boolean isSimpleLoop() {
        return (loopType == LoopType.SIMPLE_LOOP);
    }

    public void setPreLoop() {
        assert isSimpleLoop();
        loopType = LoopType.PRE_LOOP;
    }

    public boolean isPreLoop() {
        return (loopType == LoopType.PRE_LOOP);
    }

    public void setMainLoop() {
        assert isSimpleLoop();
        loopType = LoopType.MAIN_LOOP;
    }

    public boolean isMainLoop() {
        return (loopType == LoopType.MAIN_LOOP);
    }

    public void setPostLoop() {
        assert isSimpleLoop();
        loopType = LoopType.POST_LOOP;
    }

    public boolean isPostLoop() {
        return (loopType == LoopType.POST_LOOP);
    }

    public int getUnrollFactor() {
        return unrollFactor;
    }

    public void setUnrollFactor(int currentUnrollFactor) {
        unrollFactor = currentUnrollFactor;
    }

    /** Disables safepoint for the whole loop, i.e., for all {@link LoopEndNode loop ends}. */
    public void disableSafepoint() {
        /* Store flag locally in case new loop ends are created later on. */
        this.canEndsSafepoint = false;
        /* Propagate flag to all existing loop ends. */
        for (LoopEndNode loopEnd : loopEnds()) {
            loopEnd.disableSafepoint();
        }
    }

    public double loopOrigFrequency() {
        return loopOrigFrequency;
    }

    public void setLoopOrigFrequency(double loopOrigFrequency) {
        assert loopOrigFrequency >= 0;
        this.loopOrigFrequency = loopOrigFrequency;
    }

    public double loopFrequency() {
        return loopFrequency;
    }

    public void setLoopFrequency(double loopFrequency) {
        assert loopFrequency >= 0;
        this.loopFrequency = loopFrequency;
    }

    /**
     * Returns the <b>unordered</b> set of {@link LoopEndNode} that correspond to back-edges for
     * this loop. The order of the back-edges is unspecified, if you need to get an ordering
     * compatible for {@link PhiNode} creation, use {@link #orderedLoopEnds()}.
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public NodeIterable<LoopEndNode> loopEnds() {
        return usages().filter(LoopEndNode.class);
    }

    public NodeIterable<LoopExitNode> loopExits() {
        return usages().filter(LoopExitNode.class);
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(isNotA(LoopEndNode.class).nor(LoopExitNode.class));
    }

    /**
     * Returns the set of {@link LoopEndNode} that correspond to back-edges for this loop, in
     * increasing {@link #phiPredecessorIndex} order. This method is suited to create new loop
     * {@link PhiNode}.<br>
     *
     * For example a new PhiNode may be added as follow:
     *
     * <pre>
     * PhiNode phi = new ValuePhiNode(stamp, loop);
     * phi.addInput(forwardEdgeValue);
     * for (LoopEndNode loopEnd : loop.orderedLoopEnds()) {
     *     phi.addInput(backEdgeValue(loopEnd));
     * }
     * </pre>
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public LoopEndNode[] orderedLoopEnds() {
        LoopEndNode[] result = new LoopEndNode[this.getLoopEndCount()];
        for (LoopEndNode end : loopEnds()) {
            result[end.endIndex()] = end;
        }
        return result;
    }

    public boolean isSingleEntryLoop() {
        return (forwardEndCount() == 1);
    }

    public AbstractEndNode forwardEnd() {
        assert forwardEndCount() == 1;
        return forwardEndAt(0);
    }

    public int splits() {
        return splits;
    }

    public void incrementSplits() {
        splits++;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to emit, since this is node is used for structural purposes only.
    }

    @Override
    protected void deleteEnd(AbstractEndNode end) {
        if (end instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) end;
            loopEnd.setLoopBegin(null);
            int idx = loopEnd.endIndex();
            for (LoopEndNode le : loopEnds()) {
                int leIdx = le.endIndex();
                assert leIdx != idx;
                if (leIdx > idx) {
                    le.setEndIndex(leIdx - 1);
                }
            }
            nextEndIndex--;
        } else {
            super.deleteEnd(end);
        }
    }

    @Override
    public int phiPredecessorCount() {
        return forwardEndCount() + loopEnds().count();
    }

    @Override
    public int phiPredecessorIndex(AbstractEndNode pred) {
        if (pred instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) pred;
            if (loopEnd.loopBegin() == this) {
                assert loopEnd.endIndex() < loopEnds().count() : "Invalid endIndex : " + loopEnd;
                return loopEnd.endIndex() + forwardEndCount();
            }
        } else {
            return super.forwardEndIndex((EndNode) pred);
        }
        throw ValueNodeUtil.shouldNotReachHere("unknown pred : " + pred);
    }

    @Override
    public AbstractEndNode phiPredecessorAt(int index) {
        if (index < forwardEndCount()) {
            return forwardEndAt(index);
        }
        for (LoopEndNode end : loopEnds()) {
            int idx = index - forwardEndCount();
            assert idx >= 0;
            if (end.endIndex() == idx) {
                return end;
            }
        }
        throw ValueNodeUtil.shouldNotReachHere();
    }

    @Override
    public boolean verify() {
        assertTrue(loopEnds().isNotEmpty(), "missing loopEnd");
        return super.verify();
    }

    int nextEndIndex() {
        return nextEndIndex++;
    }

    public int getLoopEndCount() {
        return nextEndIndex;
    }

    public int unswitches() {
        return unswitches;
    }

    public void incrementUnswitches() {
        unswitches++;
    }

    public int getInversionCount() {
        return inversionCount;
    }

    public void setInversionCount(int count) {
        inversionCount = count;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        canonicalizePhis(tool);
    }

    public boolean isLoopExit(AbstractBeginNode begin) {
        return begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == this;
    }

    public LoopEndNode getSingleLoopEnd() {
        assert loopEnds().count() == 1;
        return loopEnds().first();
    }

    @SuppressWarnings("try")
    public void removeExits() {
        for (LoopExitNode loopexit : loopExits().snapshot()) {
            try (DebugCloseable position = graph().withNodeSourcePosition(loopexit)) {
                loopexit.removeExit();
            }
        }
    }

    public GuardingNode getOverflowGuard() {
        return overflowGuard;
    }

    public void setOverflowGuard(GuardingNode overflowGuard) {
        updateUsagesInterface(this.overflowGuard, overflowGuard);
        this.overflowGuard = overflowGuard;
    }

    private static final int NO_INCREMENT = Integer.MIN_VALUE;

    /**
     * Returns an array with one entry for each input of the phi, which is either
     * {@link #NO_INCREMENT} or the increment, i.e., the value by which the phi is incremented in
     * the corresponding branch.
     */
    private static int[] getSelfIncrements(PhiNode phi) {
        int[] selfIncrement = new int[phi.valueCount()];
        for (int i = 0; i < phi.valueCount(); i++) {
            ValueNode input = phi.valueAt(i);
            long increment = NO_INCREMENT;
            if (input != null && input instanceof AddNode && input.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                AddNode add = (AddNode) input;
                if (add.getX() == phi && add.getY().isConstant()) {
                    increment = add.getY().asJavaConstant().asLong();
                } else if (add.getY() == phi && add.getX().isConstant()) {
                    increment = add.getX().asJavaConstant().asLong();
                }
            } else if (input == phi) {
                increment = 0;
            }
            if (increment < Integer.MIN_VALUE || increment > Integer.MAX_VALUE || increment == NO_INCREMENT) {
                increment = NO_INCREMENT;
            }
            selfIncrement[i] = (int) increment;
        }
        return selfIncrement;
    }

    /**
     * Coalesces loop phis that represent the same value (which is not handled by normal Global
     * Value Numbering).
     */
    public void canonicalizePhis(SimplifierTool tool) {
        int phiCount = phis().count();
        if (phiCount > 1) {
            int phiInputCount = phiPredecessorCount();
            int phiIndex = 0;
            int[][] selfIncrement = new int[phiCount][];
            PhiNode[] phis = this.phis().snapshot().toArray(new PhiNode[phiCount]);

            for (phiIndex = 0; phiIndex < phiCount; phiIndex++) {
                PhiNode phi = phis[phiIndex];
                if (phi != null) {
                    nextPhi: for (int otherPhiIndex = phiIndex + 1; otherPhiIndex < phiCount; otherPhiIndex++) {
                        PhiNode otherPhi = phis[otherPhiIndex];
                        if (otherPhi == null || phi.getNodeClass() != otherPhi.getNodeClass() || !phi.valueEquals(otherPhi)) {
                            continue nextPhi;
                        }
                        if (selfIncrement[phiIndex] == null) {
                            selfIncrement[phiIndex] = getSelfIncrements(phi);
                        }
                        if (selfIncrement[otherPhiIndex] == null) {
                            selfIncrement[otherPhiIndex] = getSelfIncrements(otherPhi);
                        }
                        int[] phiIncrement = selfIncrement[phiIndex];
                        int[] otherPhiIncrement = selfIncrement[otherPhiIndex];
                        for (int inputIndex = 0; inputIndex < phiInputCount; inputIndex++) {
                            if (phiIncrement[inputIndex] == NO_INCREMENT) {
                                if (phi.valueAt(inputIndex) != otherPhi.valueAt(inputIndex)) {
                                    continue nextPhi;
                                }
                            }
                            if (phiIncrement[inputIndex] != otherPhiIncrement[inputIndex]) {
                                continue nextPhi;
                            }
                        }
                        if (tool != null) {
                            tool.addToWorkList(otherPhi.usages());
                        }
                        otherPhi.replaceAtUsages(phi);
                        GraphUtil.killWithUnusedFloatingInputs(otherPhi);
                        phis[otherPhiIndex] = null;
                    }
                }
            }
        }
    }

    public void markOsrLoop() {
        osrLoop = true;
    }

    public boolean isOsrLoop() {
        return osrLoop;
    }
}
