/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * A SinglePassNodeIterator iterates the fixed nodes of the graph in post order starting from its
 * start node. Unlike in iterative dataflow analysis, a single pass is performed, which allows
 * keeping a smaller working set of pending {@link MergeableState}. This iteration scheme requires:
 * <ul>
 * <li>{@link MergeableState#merge(AbstractMergeNode, List)} to always return <code>true</code> (an
 * assertion checks this)</li>
 * <li>{@link #controlSplit(ControlSplitNode)} to always return all successors (otherwise, not all
 * associated {@link EndNode} will be visited. In turn, visiting all the end nodes for a given
 * {@link AbstractMergeNode} is a precondition before that merge node can be visited)</li>
 * </ul>
 *
 * <p>
 * For this iterator the CFG is defined by the classical CFG nodes (
 * {@link org.graalvm.compiler.nodes.ControlSplitNode},
 * {@link org.graalvm.compiler.nodes.AbstractMergeNode} ...) and the
 * {@link org.graalvm.compiler.nodes.FixedWithNextNode#next() next} pointers of
 * {@link org.graalvm.compiler.nodes.FixedWithNextNode}.
 * </p>
 *
 * <p>
 * The lifecycle that single-pass node iterators go through is described in {@link #apply()}
 * </p>
 *
 * @param <T> the type of {@link MergeableState} handled by this SinglePassNodeIterator
 */
public abstract class SinglePassNodeIterator<T extends MergeableState<T>> {

    private final NodeBitMap visitedEnds;

    /**
     * @see SinglePassNodeIterator.PathStart
     */
    private final Deque<PathStart<T>> nodeQueue;

    /**
     * The keys in this map may be:
     * <ul>
     * <li>loop-begins and loop-ends, see {@link #finishLoopEnds(LoopEndNode)}</li>
     * <li>forward-ends of merge-nodes, see {@link #queueMerge(EndNode)}</li>
     * </ul>
     *
     * <p>
     * It's tricky to answer whether the state an entry contains is the pre-state or the post-state
     * for the key in question, because states are mutable. Thus an entry may be created to contain
     * a pre-state (at the time, as done for a loop-begin in {@link #apply()}) only to make it a
     * post-state soon after (continuing with the loop-begin example, also in {@link #apply()}). In
     * any case, given that keys are limited to the nodes mentioned in the previous paragraph, in
     * all cases an entry can be considered to hold a post-state by the time such entry is
     * retrieved.
     * </p>
     *
     * <p>
     * The only method that makes this map grow is {@link #keepForLater(FixedNode, MergeableState)}
     * and the only one that shrinks it is {@link #pruneEntry(FixedNode)}. To make sure no entry is
     * left behind inadvertently, asserts in {@link #finished()} are in place.
     * </p>
     */
    private final Map<FixedNode, T> nodeStates;

    private final StartNode start;

    protected T state;

    /**
     * An item queued in {@link #nodeQueue} can be used to continue with the single-pass visit after
     * the previous path can't be followed anymore. Such items are:
     * <ul>
     * <li>de-queued via {@link #nextQueuedNode()}</li>
     * <li>en-queued via {@link #queueMerge(EndNode)} and {@link #queueSuccessors(FixedNode)}</li>
     * </ul>
     *
     * <p>
     * Correspondingly each item may stand for:
     * <ul>
     * <li>a {@link AbstractMergeNode} whose pre-state results from merging those of its
     * forward-ends, see {@link #nextQueuedNode()}</li>
     * <li>a successor of a control-split node, in which case the state on entry to it (the
     * successor) is also stored in the item, see {@link #nextQueuedNode()}</li>
     * </ul>
     * </p>
     */
    private static final class PathStart<U> {
        private final AbstractBeginNode node;
        private final U stateOnEntry;

        private PathStart(AbstractBeginNode node, U stateOnEntry) {
            this.node = node;
            this.stateOnEntry = stateOnEntry;
            assert repOK();
        }

        /**
         * @return true iff this instance is internally consistent (ie, its "representation is OK")
         */
        private boolean repOK() {
            if (node == null) {
                return false;
            }
            if (node instanceof AbstractMergeNode) {
                return stateOnEntry == null;
            }
            return (stateOnEntry != null);
        }
    }

    public SinglePassNodeIterator(StartNode start, T initialState) {
        StructuredGraph graph = start.graph();
        visitedEnds = graph.createNodeBitMap();
        nodeQueue = new ArrayDeque<>();
        nodeStates = Node.newIdentityMap();
        this.start = start;
        this.state = initialState;
    }

    /**
     * Performs a single-pass iteration.
     *
     * <p>
     * After this method has been invoked, the {@link SinglePassNodeIterator} instance can't be used
     * again. This saves clearing up fields in {@link #finished()}, the assumption being that this
     * instance will be garbage-collected soon afterwards.
     * </p>
     */
    public void apply() {
        FixedNode current = start;

        do {
            if (current instanceof InvokeWithExceptionNode) {
                invoke((Invoke) current);
                queueSuccessors(current);
                current = nextQueuedNode();
            } else if (current instanceof LoopBeginNode) {
                state.loopBegin((LoopBeginNode) current);
                keepForLater(current, state);
                state = state.clone();
                loopBegin((LoopBeginNode) current);
                current = ((LoopBeginNode) current).next();
                assert current != null;
            } else if (current instanceof LoopEndNode) {
                loopEnd((LoopEndNode) current);
                finishLoopEnds((LoopEndNode) current);
                current = nextQueuedNode();
            } else if (current instanceof AbstractMergeNode) {
                merge((AbstractMergeNode) current);
                current = ((AbstractMergeNode) current).next();
                assert current != null;
            } else if (current instanceof FixedWithNextNode) {
                FixedNode next = ((FixedWithNextNode) current).next();
                assert next != null : current;
                node(current);
                current = next;
            } else if (current instanceof EndNode) {
                end((EndNode) current);
                queueMerge((EndNode) current);
                current = nextQueuedNode();
            } else if (current instanceof ControlSinkNode) {
                node(current);
                current = nextQueuedNode();
            } else if (current instanceof ControlSplitNode) {
                controlSplit((ControlSplitNode) current);
                queueSuccessors(current);
                current = nextQueuedNode();
            } else {
                assert false : current;
            }
        } while (current != null);
        finished();
    }

    /**
     * Two methods enqueue items in {@link #nodeQueue}. Of them, only this method enqueues items
     * with non-null state (the other method being {@link #queueMerge(EndNode)}).
     *
     * <p>
     * A space optimization is made: the state is cloned for all successors except the first. Given
     * that right after invoking this method, {@link #nextQueuedNode()} is invoked, that single
     * non-cloned state instance is in effect "handed over" to its next owner (thus realizing an
     * owner-is-mutator access protocol).
     * </p>
     */
    private void queueSuccessors(FixedNode x) {
        T startState = state;
        T curState = startState;
        for (Node succ : x.successors()) {
            if (succ != null) {
                if (curState == null) {
                    // the current state isn't cloned for the first successor
                    // conceptually, the state is handed over to it
                    curState = startState.clone();
                }
                AbstractBeginNode begin = (AbstractBeginNode) succ;
                nodeQueue.addFirst(new PathStart<>(begin, curState));
            }
        }
    }

    /**
     * This method is invoked upon not having a (single) next {@link FixedNode} to visit. This
     * method picks such next-node-to-visit from {@link #nodeQueue} and updates {@link #state} with
     * the pre-state for that node.
     *
     * <p>
     * Upon reaching a {@link AbstractMergeNode}, some entries are pruned from {@link #nodeStates}
     * (ie, the entries associated to forward-ends for that merge-node).
     * </p>
     */
    private FixedNode nextQueuedNode() {
        if (nodeQueue.isEmpty()) {
            return null;
        }
        PathStart<T> elem = nodeQueue.removeFirst();
        if (elem.node instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) elem.node;
            state = pruneEntry(merge.forwardEndAt(0));
            ArrayList<T> states = new ArrayList<>(merge.forwardEndCount() - 1);
            for (int i = 1; i < merge.forwardEndCount(); i++) {
                T other = pruneEntry(merge.forwardEndAt(i));
                states.add(other);
            }
            boolean ready = state.merge(merge, states);
            assert ready : "Not a single-pass iterator after all";
            return merge;
        } else {
            AbstractBeginNode begin = elem.node;
            assert begin.predecessor() != null;
            state = elem.stateOnEntry;
            state.afterSplit(begin);
            return begin;
        }
    }

    /**
     * Once all loop-end-nodes for a given loop-node have been visited.
     * <ul>
     * <li>the state for that loop-node is updated based on the states of the loop-end-nodes</li>
     * <li>entries in {@link #nodeStates} are pruned for the loop (they aren't going to be looked up
     * again, anyway)</li>
     * </ul>
     *
     * <p>
     * The entries removed by this method were inserted:
     * <ul>
     * <li>for the loop-begin, by {@link #apply()}</li>
     * <li>for loop-ends, by (previous) invocations of this method</li>
     * </ul>
     * </p>
     */
    private void finishLoopEnds(LoopEndNode end) {
        assert !visitedEnds.isMarked(end);
        visitedEnds.mark(end);
        keepForLater(end, state);
        LoopBeginNode begin = end.loopBegin();
        boolean endsVisited = true;
        for (LoopEndNode le : begin.loopEnds()) {
            if (!visitedEnds.isMarked(le)) {
                endsVisited = false;
                break;
            }
        }
        if (endsVisited) {
            ArrayList<T> states = new ArrayList<>(begin.loopEnds().count());
            for (LoopEndNode le : begin.orderedLoopEnds()) {
                T leState = pruneEntry(le);
                states.add(leState);
            }
            T loopBeginState = pruneEntry(begin);
            loopBeginState.loopEnds(begin, states);
        }
    }

    /**
     * Once all end-nodes for a given merge-node have been visited, that merge-node is added to the
     * {@link #nodeQueue}
     *
     * <p>
     * {@link #nextQueuedNode()} is in charge of pruning entries (held by {@link #nodeStates}) for
     * the forward-ends inserted by this method.
     * </p>
     */
    private void queueMerge(EndNode end) {
        assert !visitedEnds.isMarked(end);
        visitedEnds.mark(end);
        keepForLater(end, state);
        AbstractMergeNode merge = end.merge();
        boolean endsVisited = true;
        for (int i = 0; i < merge.forwardEndCount(); i++) {
            if (!visitedEnds.isMarked(merge.forwardEndAt(i))) {
                endsVisited = false;
                break;
            }
        }
        if (endsVisited) {
            nodeQueue.add(new PathStart<>(merge, null));
        }
    }

    protected abstract void node(FixedNode node);

    protected void end(EndNode endNode) {
        node(endNode);
    }

    protected void merge(AbstractMergeNode merge) {
        node(merge);
    }

    protected void loopBegin(LoopBeginNode loopBegin) {
        node(loopBegin);
    }

    protected void loopEnd(LoopEndNode loopEnd) {
        node(loopEnd);
    }

    protected void controlSplit(ControlSplitNode controlSplit) {
        node(controlSplit);
    }

    protected void invoke(Invoke invoke) {
        node(invoke.asNode());
    }

    /**
     * The lifecycle that single-pass node iterators go through is described in {@link #apply()}
     *
     * <p>
     * When overriding this method don't forget to invoke this implementation, otherwise the
     * assertions will be skipped.
     * </p>
     */
    protected void finished() {
        assert nodeQueue.isEmpty();
        assert nodeStates.isEmpty();
    }

    private void keepForLater(FixedNode x, T s) {
        assert !nodeStates.containsKey(x);
        assert (x instanceof LoopBeginNode) || (x instanceof LoopEndNode) || (x instanceof EndNode);
        assert s != null;
        nodeStates.put(x, s);
    }

    private T pruneEntry(FixedNode x) {
        T result = nodeStates.remove(x);
        assert result != null;
        return result;
    }
}
