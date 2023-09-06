/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Gatherer.Integrator;

/**
 * Runtime machinery for evaluating Gatherers under different modes.
 * The performance-critical code below contains some more complicated encodings:
 * therefore, make sure to run benchmarks to verify changes to prevent regressions.
 * @since 22
 */
final class GathererOp<T, A, R> extends ReferencePipeline<T, R> {

    // When this is `true`, consecutive invocations to `Stream.gather` will fuse into a single,
    // composed, gatherer operation.
    private final static boolean AUTOCOMPOSITION_ENABLED = true;

    static <P_IN, P_OUT, R> Stream<R> of(ReferencePipeline<P_IN,P_OUT> upstream, Gatherer<P_OUT,?,R> gatherer) {
        if (AUTOCOMPOSITION_ENABLED && upstream.getClass() == GathererOp.class) {
            final var upStreamGathererOp = (GathererOp<P_IN,?,P_OUT>)upstream;
            // Subsequent .gather()-invocations uses this method to pre-compose the Gatherers and package them as a single step
            final var fusedGatherer = upStreamGathererOp.gatherer.andThen(gatherer);
            return new GathererOp<>(upStreamGathererOp, fusedGatherer);
        } else
            return new GathererOp<>(upstream, gatherer);
    }

    // GathererOp.NodeBuilder is a lazy accumulator of elements with O(1) `append`, and O(8) `join` (concat).
    // First `append` inflates a growable Builder, the O(8) for `join` is because we prefer to delegate to `append`
    // for small concatenations to avoid excessive indirections (unbalanced Concat-trees) when joining many NodeBuilders together.
    final static class NodeBuilder<X> implements Consumer<X> {
        final static class Builder<X> extends SpinedBuffer<X> implements Node<X> { Builder() {} }

        NodeBuilder() {}

        private Builder<X> rightMost;
        private Node<X> leftMost;

        private boolean isEmpty() { return rightMost == null && leftMost == null; }

        @Override
        public void accept(X x) {
            final var b = rightMost;
            (b == null ? (rightMost = new NodeBuilder.Builder<>()) : b).accept(x);
        }

        public NodeBuilder<X> join(NodeBuilder<X> that) {
            if (isEmpty())
                return that;

            if (!that.isEmpty()) {
                final var tb = that.build();
                if (rightMost != null && tb instanceof NodeBuilder.Builder<X> && tb.count() < 8) // TODO revisit cutoff point
                    tb.forEach(this); // Append small builders directly to avoid conc for small nodes
                else
                    leftMost = Nodes.conc(StreamShape.REFERENCE, this.build(), tb);
            }

            return this;
        }

        public Node<X> build() {
            if (isEmpty())
                return Nodes.emptyNode(StreamShape.REFERENCE);

            final var rm = rightMost;

            if (rm != null) {
                rightMost = null; // We can't write to this instance anymore, will be concat:ed onto leftMost below
                final var lm = leftMost;
                leftMost = (lm == null) ? rm : Nodes.conc(StreamShape.REFERENCE, lm, rm);
            }

            return leftMost;
        }
    }

    final static class GatherSink<T,A,R> implements Sink<T>, Gatherer.Sink<R> {
        private final Sink<R> downstream;
        private final Gatherer<T,A,R> gatherer;
        private final Integrator<A, T, R> integrator; // Optimization to avoid fetching the integrator for every element
        private A state;
        private boolean proceed = true; // Every GatherSink needs to presume that it can accept input initially

        GatherSink(Gatherer<T,A,R> gatherer, Sink<R> downstream) {
            this.gatherer = gatherer;
            this.downstream = downstream;
            this.integrator = gatherer.integrator(); // Cache this instance for performance reasons
        }

        // java.util.stream.Sink contract below:

        @Override public void begin(long size) {
            final var initializer = gatherer.initializer();
            if (initializer != Gatherers.initializerNotNeeded) // Optimization
                state = initializer.get();
            downstream.begin(size);
        }

        @Override public void accept(T t) {
            /* Benchmarks have indicated that doing an unconditional write to `proceed` is more efficient
            * than branching. We use `&=` here to prevent flips from `false` -> `true`.
            * As of writing this, it hasn't been proven faster to special-case GREEDY or STATELESS here.
            */
            proceed &= integrator.integrate(state, t, this);
        }

        @Override public boolean cancellationRequested() { return cancellationRequested(proceed); }

        private boolean cancellationRequested(boolean knownProceed) {
            return !(knownProceed && (!downstream.cancellationRequested() || (proceed = false)));
        }

        @Override public void end() {
            final var finisher = gatherer.finisher();
            if (finisher != Gatherers.finisherNotNeeded) // Optimization
                finisher.accept(state, this);
            downstream.end();
            state = null; // GC assistance
        }

        // Gatherer.Sink contract below:

        @Override public boolean isKnownDone() { return !proceed; }

        @Override public boolean flush(R r) {
            var p = proceed;
            if (p)
                downstream.accept(r);
            return !cancellationRequested(p);
        }
    }

    private static int opFlagsFor(Integrator<?,?,?> integrator) {
        return integrator instanceof Integrator.Greedy<?,?,?>
                ? GREEDY_FLAGS : SHORT_CIRCUIT_FLAGS;
    }


    private final static int DEFAULT_FLAGS =
            StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT |
            StreamOpFlag.NOT_SIZED;

    private final static int SHORT_CIRCUIT_FLAGS =
            DEFAULT_FLAGS | StreamOpFlag.IS_SHORT_CIRCUIT;

    private final static int GREEDY_FLAGS =
            DEFAULT_FLAGS;

    final Gatherer<T,A,R> gatherer;
    final AbstractPipeline<?, T, ?> previousStage; // FIXME replace with accessing super.previousStage

    // This constructor is used for initial .gather() invocations
    private GathererOp(AbstractPipeline<?, T, ?> upstream, Gatherer<T, A, R> gatherer) {
        super(upstream, opFlagsFor(gatherer.integrator())); // TODO change to not call integrator() more than once
        this.gatherer = gatherer;
        this.previousStage = upstream;
    }

    // This constructor is used when fusing subsequent .gather() invocations
    @SuppressWarnings("unchecked")
    private GathererOp(GathererOp<T,?,?> upstream, Gatherer<T, A, R> gatherer) {
        super(upstream.previousStage, (AbstractPipeline<?, T, ?>) upstream, opFlagsFor(gatherer.integrator()));
        this.gatherer = gatherer;
        this.previousStage = upstream.previousStage;
    }

    // This allows internal access to the previous stage,
    // which we need since we want to be able to fuse `gather` followed by `collect`.
   // @SuppressWarnings("unchecked")
    private AbstractPipeline<?, T, ?> upstream() {
        return previousStage;
    }

    @Override boolean opIsStateful() {
        //return !(gatherer.initializer() == Gatherers.initializerNotNeeded && gatherer.combiner() != Gatherers.combinerNotPossible && gatherer.finisher() == Gatherers.finisherNotNeeded);
        return true;
    }

    @Override
    Sink<T> opWrapSink(int flags, Sink<R> downstream) {
        return new GatherSink<>(gatherer, downstream);
    }

    // This is used when evaluating .gather() operations interspersed with other Stream operations (in parallel)
    @SuppressWarnings("unchecked")
    @Override
    <I> Node<R> opEvaluateParallel(PipelineHelper<R> unused1, Spliterator<I> spliterator, IntFunction<R[]> unused2) {
        return this.<T,A,R,NodeBuilder<R>,Node<R>>evaluate(
                upstream().wrapSpliterator(spliterator),
                true,
                gatherer,
                NodeBuilder::new,
                NodeBuilder::accept,
                NodeBuilder::join,
                NodeBuilder::build
        );
    }

    @Override
    <P_IN> Spliterator<R> opEvaluateParallelLazy(PipelineHelper<R> helper, Spliterator<P_IN> spliterator) {
        // There's a very small subset of possible Gatherers which would be expressible as Spliterators directly,
        // specifically STATELESS + `finisherNotNeeded` + having a `combiner` which is *NOT* `combinerNotPossible`.
        return opEvaluateParallel(null, spliterator, null).spliterator();
    }

    // evaluate(...) is the primary execution mechanism besides opWrapSink()
    // and implements both sequential, hybrid parallel-sequential, and parallel evaluation
    private <T,A,R,CA,CR> CR evaluate(
                                      final Spliterator<T> spliterator,
                                      final boolean parallel,
                                      final Gatherer<T,A,R> gatherer,
                                      final Supplier<CA> collectorSupplier,
                                      final BiConsumer<CA,? super R> collectorAccumulator,
                                      final BinaryOperator<CA> collectorCombiner,
                                      final Function<CA,CR> collectorFinisher) {

        // There are two main sections here: first the sequential, then the parallel

        final var initializer = gatherer.initializer();
        final var integrator = gatherer.integrator();

        final boolean greedy    = integrator instanceof Integrator.Greedy<A,T,R>; // Optimization

        // Sequential section starts here:
        final class Sequential implements Consumer<T>, Gatherer.Sink<R> {
            A state;
            CA collectorState;
            boolean proceed;

            Sequential() {
                if (initializer != Gatherers.initializerNotNeeded)
                    state = initializer.get();
                collectorState = collectorSupplier.get();
                proceed = true;
            }

            @ForceInline
            Sequential evaluateUsing(Spliterator<T> spliterator) {
                if (greedy)
                    spliterator.forEachRemaining(this);
                else
                    do {} while (proceed && spliterator.tryAdvance(this));

                return this;
            }

            // No need to override isKnownDone() as the default is `false` and collectors can never short-circuit
            @Override public boolean flush(R r) { collectorAccumulator.accept(collectorState, r); return true; }

            @Override public void accept(T t) {
                // TODO explain why code looks as it does
                var ignore = integrator.integrate(state, t, this) || (!greedy && (proceed = false));
                //proceed &= integrator.integrate(state, t, this);
            }

            @SuppressWarnings("unchecked")
            public CR get() {
                final var finisher = gatherer.finisher();
                if (finisher != Gatherers.finisherNotNeeded)
                    finisher.accept(state, this);
                return (collectorFinisher == null) ? (CR) collectorState : collectorFinisher.apply(collectorState);
            }
        }

        if (!parallel)
            return new Sequential().evaluateUsing(spliterator).get();

        // Parallel section starts here:

        final var combiner = gatherer.combiner();

        // The following implementation of hybrid parallel-sequential Gatherer processing borrows heavily from ForeachOrderedTask
        @SuppressWarnings("serial")
        final class Hybrid extends CountedCompleter<Sequential> {
            private final long targetSize;
            private final Hybrid leftPredecessor;
            private final AtomicBoolean cancelled;
            private final Sequential localResult;

            private Spliterator<T> spliterator;
            private Hybrid next;

            private static final VarHandle NEXT;
            static {
                try {
                    MethodHandles.Lookup l = MethodHandles.lookup();
                    NEXT = l.findVarHandle(Hybrid.class, "next", Hybrid.class);
                } catch (Exception e) {
                    throw new InternalError(e);
                }
            }

            protected Hybrid(Spliterator<T> spliterator) {
                super(null);
                this.spliterator = spliterator;
                this.targetSize = AbstractTask.suggestTargetSize(spliterator.estimateSize());
                this.localResult = new Sequential();
                this.cancelled = greedy ? null : new AtomicBoolean(false);
                this.leftPredecessor = null;
            }

            Hybrid(Hybrid parent, Spliterator<T> spliterator, Hybrid leftPredecessor) {
                super(parent);
                this.spliterator = spliterator;
                this.targetSize = parent.targetSize;
                this.localResult = parent.localResult;
                this.cancelled = parent.cancelled;
                this.leftPredecessor = leftPredecessor;
            }

            @Override public Sequential getRawResult() { return localResult; }
            @Override public void setRawResult(Sequential result) { if (result != null) throw new IllegalStateException(); }

            @Override
            public void compute() {
                var task = this;
                Spliterator<T> rightSplit = task.spliterator, leftSplit;
                long sizeThreshold = task.targetSize;
                boolean forkRight = false;
                while ((greedy || !cancelled.get()) && rightSplit.estimateSize() > sizeThreshold && (leftSplit = rightSplit.trySplit()) != null) {
                    var leftChild = new Hybrid(task, leftSplit, task.leftPredecessor);
                    var rightChild = new Hybrid(task, rightSplit, leftChild);

                    // leftChild and rightChild were just created and not fork():ed
                    // yet so no need for a volatile write
                    leftChild.next = rightChild;

                    // Fork the parent task
                    // Completion of the left and right children "happens-before"
                    // completion of the parent
                    task.addToPendingCount(1);
                    // Completion of the left child "happens-before" completion of
                    // the right child
                    rightChild.addToPendingCount(1);

                    // If task is not on the left spine
                    if (task.leftPredecessor != null) {
                        /*
                         * Completion of left-predecessor, or left subtree,
                         * "happens-before" completion of left-most leaf node of
                         * right subtree.
                         * The left child's pending count needs to be updated before
                         * it is associated in the completion map, otherwise the
                         * left child can complete prematurely and violate the
                         * "happens-before" constraint.
                         */
                        leftChild.addToPendingCount(1);
                        // Update association of left-predecessor to left-most
                        // leaf node of right subtree
                        if (NEXT.compareAndSet(task.leftPredecessor, task, leftChild)) {
                            // If replaced, adjust the pending count of the parent
                            // to complete when its children complete
                            task.addToPendingCount(-1);
                        } else {
                            // Left-predecessor has already completed, parent's
                            // pending count is adjusted by left-predecessor;
                            // left child is ready to complete
                            leftChild.addToPendingCount(-1);
                        }
                    }

                    if (forkRight) {
                        rightSplit = leftSplit;
                        task = leftChild;
                        rightChild.fork();
                    }
                    else {
                        task = rightChild;
                        leftChild.fork();
                    }
                    forkRight = !forkRight;
                }

                /*
                 * Task's pending count is either 0 or 1.  If 1 then the completion
                 * map will contain a value that is task, and two calls to
                 * tryComplete are required for completion, one below and one
                 * triggered by the completion of task's left-predecessor in
                 * onCompletion.  Therefore there is no data race within the if
                 * block.
                 */
                if (greedy && task.getPendingCount() > 0) {
                // was if (greedy && proceed && !cancelled.get() && task.getPendingCount() > 0) { // FIXME reconsider if it should be done for non-greedy operations
                    // Cannot complete just yet so buffer elements into a Spliterator for use when completion occurs
                    var builder = Nodes.<T>builder(rightSplit.getExactSizeIfKnown(), Nodes.castingArray());
                    rightSplit.forEachRemaining(builder); // Run the upstream into the builder
                    task.spliterator = builder.build().spliterator();
                }
                task.tryComplete();
            }

            @Override
            public void onCompletion(CountedCompleter<?> caller) {
                var s = spliterator;
                spliterator = null; // GC assistance

                if (s != null && (greedy || !cancelled.get()) && !localResult.evaluateUsing(s).proceed && !greedy)
                    cancelled.set(true);

                // The completion of this task *and* the dumping of elements
                // "happens-before" completion of the associated left-most leaf task
                // of right subtree (if any, which can be this task's right sibling)
                var leftDescendant = (Hybrid) NEXT.getAndSet(this, null); // FIXME double-check if tryComplete HB onCompletion
                if (leftDescendant != null) {
                    leftDescendant.tryComplete();
                }
            }
        }

        // The following implementation of parallel Gatherer processing borrows heavily from AbstractShortCircuitTask
        @SuppressWarnings("serial")
        final class Parallel extends CountedCompleter<Sequential> {
            private Spliterator<T> spliterator;
            private Parallel leftChild; // The left child. null if no children. if non-null rightChild is non-null
            private Parallel rightChild; // The right child. null if no children. if non-null leftChild is non-null
            private Sequential localResult;
            private volatile boolean canceled;
            private long targetSize; // lazily initialized

            private Parallel(Parallel parent, Spliterator<T> spliterator) {
                super(parent);
                this.targetSize  = parent.targetSize;
                this.spliterator = spliterator;
            }

            Parallel(Spliterator<T> spliterator) {
                super(null);
                this.targetSize  = 0L;
                this.spliterator = spliterator;
            }

            private long getTargetSize(long sizeEstimate) {
                long s;
                return ((s = targetSize) != 0 ? s : (targetSize = AbstractTask.suggestTargetSize(sizeEstimate)));
            }

            @Override public Sequential getRawResult() { return localResult; }
            @Override public void setRawResult(Sequential result) { if (result != null) throw new IllegalStateException(); }

            private void doProcess() {
                if(!(localResult = new Sequential()).evaluateUsing(spliterator).proceed && !greedy)
                    cancelLaterTasks();
            }

            @Override
            public void compute() {
                Spliterator<T> rs = spliterator, ls;
                long sizeEstimate = rs.estimateSize();
                final long sizeThreshold = getTargetSize(sizeEstimate);
                Parallel task = this;
                boolean forkRight = false;
                boolean proceed;
                while((proceed = (greedy || !task.isRequestedToCancel())) && sizeEstimate > sizeThreshold && (ls = rs.trySplit()) != null) {
                    final var leftChild = task.leftChild = new Parallel(task, ls);
                    final var rightChild = task.rightChild = new Parallel(task, rs);
                    task.setPendingCount(1);
                    if (forkRight) {
                        rs = ls;
                        task = leftChild;
                        rightChild.fork();
                    } else {
                        task = rightChild;
                        leftChild.fork();
                    }
                    forkRight = !forkRight;
                    sizeEstimate = rs.estimateSize();
                }
                if (proceed)
                    task.doProcess();
                task.tryComplete();
            }

            Sequential merge(Sequential l, Sequential r) {
                // Only join the right if the left side didn't short-circuit, or when greedy
                if (greedy || (l != null && r != null && l.proceed)) {
                    l.state = combiner.apply(l.state, r.state);
                    l.collectorState = collectorCombiner.apply(l.collectorState, r.collectorState);
                    l.proceed = r.proceed;
                    return l;
                }

                return (l != null) ? l : r;
            }

            @Override
            public void onCompletion(CountedCompleter<?> caller) {
                spliterator = null; // GC assistance
                if (leftChild != null) {
                    // Results can only be null in the case where there's short-circuiting
                    // Or when Gatherers are not marked as STATELESS but still uses `null` as their state
                    localResult = merge(leftChild.localResult, rightChild.localResult);
                    leftChild = rightChild = null; // GC assistance
                }
            }

            @SuppressWarnings("unchecked")
            private Parallel getParent() { return (Parallel)getCompleter(); }

            private boolean isRequestedToCancel() {
                boolean cancel = canceled;
                if (!cancel) {
                    for (Parallel parent = getParent(); !cancel && parent != null; parent = parent.getParent())
                        cancel = parent.canceled;
                }
                return cancel;
            }

            private void cancelLaterTasks() { // Go up the tree, cancel right siblings of this node and all parents
                for (Parallel parent = getParent(), node = this; parent != null; node = parent, parent = parent.getParent()) {
                    if (parent.leftChild == node) // If node is a left child of parent, then has a right sibling
                        parent.rightChild.canceled = true;
                }
            }
        }

        if (combiner != Gatherers.combinerNotPossible)
            return new Parallel(spliterator).invoke().get();
        else
            return new Hybrid(spliterator).invoke().get();
    }
}