/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import jdk.internal.reflect.MethodAccessor;
import jdk.internal.reflect.ConstructorAccessor;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;

import java.lang.annotation.Native;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;
import sun.security.action.GetPropertyAction;

import static java.lang.StackStreamFactory.WalkerState.*;

/**
 * StackStreamFactory class provides static factory methods
 * to get different kinds of stack walker/traverser.
 *
 * AbstractStackWalker provides the basic stack walking support
 * fetching stack frames from VM in batches.
 *
 * AbstractStackWalker subclass is specialized for a specific kind of stack traversal
 * to avoid overhead of Stream/Lambda
 * 1. Support traversing Stream<StackFrame>
 * 2. StackWalker::getCallerClass
 * 3. AccessControlContext getting ProtectionDomain
 */
final class StackStreamFactory {
    private StackStreamFactory() {}

    // Stack walk implementation classes to be excluded during stack walking
    // lazily add subclasses when they are loaded.
    private static final Set<Class<?>> stackWalkImplClasses = init();

    // Number of elements in the buffer reserved for VM to use
    private static final int RESERVED_ELEMENTS = 1;
    private static final int MIN_BATCH_SIZE    = RESERVED_ELEMENTS + 2;
    private static final int SMALL_BATCH       = 8;
    private static final int BATCH_SIZE        = 32;
    private static final int LARGE_BATCH_SIZE  = 256;

    // These flags must match the values maintained in the VM
    @Native private static final int DEFAULT_MODE              = 0x0;
    @Native private static final int CLASS_INFO_ONLY           = 0x2;
    @Native private static final int SHOW_HIDDEN_FRAMES        = 0x20;  // LambdaForms are hidden by the VM
    @Native private static final int FILL_LIVE_STACK_FRAMES    = 0x100;

    /*
     * For Throwable to use StackWalker, set useNewThrowable to true.
     * Performance work and extensive testing is needed to replace the
     * VM built-in backtrace filled in Throwable with the StackWalker.
     */
    static final boolean isDebug =
            "true".equals(GetPropertyAction.privilegedGetProperty("stackwalk.debug"));

    static <T> StackFrameTraverser<T>
        makeStackTraverser(StackWalker walker, Function<? super Stream<StackFrame>, ? extends T> function)
    {
        if (walker.hasLocalsOperandsOption()) {
            return new LiveStackInfoTraverser<>(walker, function);
        } else {
            return new StackFrameTraverser<>(walker, function);
        }
    }

    /**
     * Gets a stack stream to find caller class.
     */
    static CallerClassFinder makeCallerFinder(StackWalker walker) {
        return new CallerClassFinder(walker);
    }

    enum WalkerState {
        NEW,     // the stream is new and stack walking has not started
        OPEN,    // the stream is open when it is being traversed.
        CLOSED;  // the stream is closed when the stack walking is done
    }

    private static int toStackWalkMode(StackWalker walker, int mode) {
        int newMode = mode;
        if (walker.hasOption(Option.DROP_METHOD_INFO))
            newMode |= CLASS_INFO_ONLY;
        if (walker.hasOption(Option.SHOW_HIDDEN_FRAMES))
            newMode |= SHOW_HIDDEN_FRAMES;
        if (walker.hasLocalsOperandsOption())
            newMode |= FILL_LIVE_STACK_FRAMES;
        return newMode;
    }

    /**
     * Subclass of AbstractStackWalker implements a specific stack walking logic.
     * It needs to set up the frame buffer and stack walking mode.
     *
     * It initiates the VM stack walking via the callStackWalk method that serves
     * as the anchored frame and VM will call up to AbstractStackWalker::doStackWalk.
     *
     * @param <R> the type of the result returned from stack walking
     * @param <T> the type of the data gathered for each frame.
     *            For example, StackFrameInfo for StackWalker::walk or
     *            Class<?> for StackWalker::getCallerClass
     */
    abstract static class AbstractStackWalker<R, T> {
        protected final StackWalker walker;
        protected final Thread thread;
        protected final int maxDepth;
        protected final int mode;
        protected int depth;    // traversed stack depth
        protected FrameBuffer<? extends T> frameBuffer;
        protected long anchor;
        protected final ContinuationScope contScope;
        protected Continuation continuation;

        // buffers to fill in stack frame information
        protected AbstractStackWalker(StackWalker walker, int mode) {
            this(walker, mode, Integer.MAX_VALUE);
        }
        protected AbstractStackWalker(StackWalker walker, int mode, int maxDepth) {
            this.thread = Thread.currentThread();
            this.mode = mode;
            this.walker = walker;
            this.maxDepth = maxDepth;
            this.depth = 0;
            ContinuationScope scope = walker.getContScope();
            if (scope == null && thread.isVirtual()) {
                this.contScope = VirtualThread.continuationScope();
                this.continuation = null;
            } else {
                this.contScope = scope;
                this.continuation = walker.getContinuation();
            }
        }

        /**
         * A callback method to consume the stack frames.  This method is invoked
         * once stack walking begins (i.e. it is only invoked when walkFrames is called).
         *
         * Each specialized AbstractStackWalker subclass implements the consumeFrames method
         * to control the following:
         * 1. fetch the subsequent batches of stack frames
         * 2. reuse or expand the allocated buffers
         * 3. create specialized StackFrame objects
         *
         * @return the number of consumed frames
         */
         protected abstract R consumeFrames();

        /**
         * Initialize FrameBuffer.  Subclass should implement this method to
         * create its custom frame buffers.
         */
         protected abstract void initFrameBuffer();

        /**
         * Returns the suggested next batch size.
         *
         * Subclass should override this method to change the batch size
         *
         * @param lastBatchSize last batch size
         * @return suggested batch size
         */
        protected abstract int batchSize(int lastBatchSize);

        /*
         * Returns the next batch size, always >= minimum batch size
         *
         * Subclass may override this method if the minimum batch size is different.
         */
        protected int getNextBatchSize() {
            int lastBatchSize = depth == 0 ? 0 : frameBuffer.currentBatchSize();
            int nextBatchSize = batchSize(lastBatchSize);
            if (isDebug) {
                System.err.println("last batch size = " + lastBatchSize +
                                   " next batch size = " + nextBatchSize);
            }
            return nextBatchSize >= MIN_BATCH_SIZE ? nextBatchSize : MIN_BATCH_SIZE;
        }

        /*
         * Checks if this stream is in the given state. Otherwise, throws IllegalStateException.
         *
         * VM also validates this stream if it's anchored for stack walking
         * when stack frames are fetched for each batch.
         */
        final void checkState(WalkerState state) {
            if (thread != Thread.currentThread()) {
                throw new IllegalStateException("Invalid thread walking this stack stream: " +
                        Thread.currentThread().getName() + " " + thread.getName());
            }
            switch (state) {
                case NEW:
                    if (anchor != 0) {
                        throw new IllegalStateException("This stack stream is being reused.");
                    }
                    break;
                case OPEN:
                    if (anchor == 0 || anchor == -1L) {
                        throw new IllegalStateException("This stack stream is not valid for walking.");
                    }
                    break;
                case CLOSED:
                    if (anchor != -1L) {
                        throw new IllegalStateException("This stack stream is not closed.");
                    }
            }
        }

        /*
         * Close this stream.  This stream becomes invalid to walk.
         */
        private void close() {
            this.anchor = -1L;
        }

        /*
         * Walks stack frames until {@link #consumeFrames} is done consuming
         * the frames it is interested in.
         */
        final R walk() {
            checkState(NEW);
            return (continuation != null)
                ? Continuation.wrapWalk(continuation, contScope, this::walkHelper)
                : walkHelper();
        }

        private final R walkHelper() {
            try {
                // VM will need to stabilize the stack before walking.  It will invoke
                // the AbstractStackWalker::doStackWalk method once it fetches the first batch.
                // the callback will be invoked within the scope of the callStackWalk frame.
                return beginStackWalk();
            } finally {
                close();  // done traversal; close the stream
            }
        }

        private boolean skipReflectionFrames() {
            return !walker.hasOption(Option.SHOW_REFLECT_FRAMES) &&
                       !walker.hasOption(Option.SHOW_HIDDEN_FRAMES);
        }

        /*
         * Returns {@code Class} object at the current frame;
         * or {@code null} if no more frame. If advanceToNextBatch is true,
         * it will only fetch the next batch.
         */
        final Class<?> peekFrame() {
            while (frameBuffer.isActive() && depth < maxDepth) {
                if (frameBuffer.isEmpty()) {
                    // fetch another batch of stack frames
                    getNextBatch();
                } else {
                    Class<?> c = frameBuffer.get();
                    if (skipReflectionFrames() && isReflectionFrame(c)) {
                        if (isDebug)
                            System.err.println("  skip: frame " + frameBuffer.getIndex() + " " + c);

                        frameBuffer.next();
                        depth++;
                        continue;
                    } else {
                        return c;
                    }
                }
            }
            return null;
        }

        /*
         * This method is only invoked by VM.
         *
         * It will invoke the consumeFrames method to start the stack walking
         * with the first batch of stack frames.  Each specialized AbstractStackWalker
         * subclass implements the consumeFrames method to control the following:
         * 1. fetch the subsequent batches of stack frames
         * 2. reuse or expand the allocated buffers
         * 3. create specialized StackFrame objects
         */
        private Object doStackWalk(long anchor, int skipFrames, int numFrames,
                                   int bufStartIndex, int bufEndIndex) {
            checkState(NEW);

            frameBuffer.check(skipFrames);

            if (isDebug) {
                System.err.format("doStackWalk: skip %d start %d end %d nframes %d%n",
                        skipFrames, bufStartIndex, bufEndIndex, numFrames);
            }

            this.anchor = anchor;  // set anchor for this bulk stack frame traversal
            frameBuffer.setBatch(depth, bufStartIndex, numFrames);

            // traverse all frames and perform the action on the stack frames, if specified
            return consumeFrames();
        }

        /*
         * Get next batch of stack frames.
         */
        private int getNextBatch() {
            if (!frameBuffer.isActive()
                    || (depth == maxDepth)
                    || (frameBuffer.isAtBottom() && !hasMoreContinuations())) {
                if (isDebug) {
                    System.out.format("  more stack walk done%n");
                }
                frameBuffer.freeze();   // stack walk done
                return 0;
            }

            // VM ends the batch when it reaches the bottom of a continuation
            // i.e. Continuation::enter.  The stack walker will set the continuation
            // to its parent to continue.
            // Note that the current batch could have no stack frame filled.  This could
            // happen when Continuation::enter is the last element of the frame buffer
            // filled in the last batch and it needs to fetch another batch in order to
            // detect reaching the bottom.
            if (frameBuffer.isAtBottom() && hasMoreContinuations()) {
                if (isDebug) {
                    System.out.format("  set continuation to %s%n", continuation.getParent());
                }
                setContinuation(continuation.getParent());
            }

            int numFrames = fetchStackFrames();
            if (numFrames == 0 && !hasMoreContinuations()) {
                frameBuffer.freeze(); // done stack walking
            }
            return numFrames;
        }

        private boolean hasMoreContinuations() {
            return (continuation != null)
                    && (continuation.getScope() != contScope)
                    && (continuation.getParent() != null);
        }

        private void setContinuation(Continuation cont) {
            this.continuation = cont;
            setContinuation(anchor, frameBuffer.frames(), cont);
        }

        /*
         * This method traverses the next stack frame and returns the Class
         * invoking that stack frame.
         *
         * This method can only be called during the walk method.  This is intended
         * to be used to walk the stack frames in one single invocation and
         * this stack stream will be invalidated once walk is done.
         *
         * @see #tryNextFrame
         */
        final Class<?> nextFrame() {
            if (!hasNext()) {
                return null;
            }

            Class<?> c = frameBuffer.next();
            depth++;
            return c;
        }

        /*
         * Returns true if there is next frame to be traversed.
         * This skips hidden frames unless this StackWalker has
         * {@link Option#SHOW_REFLECT_FRAMES}
         */
        final boolean hasNext() {
            return peekFrame() != null;
        }

        /**
         * Begin stack walking - pass the allocated arrays to the VM to fill in
         * stack frame information.
         *
         * VM first anchors the frame of the current thread.  A traversable stream
         * on this thread's stack will be opened.  The VM will fetch the first batch
         * of stack frames and call AbstractStackWalker::doStackWalk to invoke the
         * stack walking function on each stack frame.
         *
         * If all fetched stack frames are traversed, AbstractStackWalker::fetchStackFrames will
         * fetch the next batch of stack frames to continue.
         */
        private R beginStackWalk() {
            // initialize buffers for VM to fill the stack frame info
            initFrameBuffer();

            return callStackWalk(mode, 0,
                                 contScope, continuation,
                                 frameBuffer.currentBatchSize(),
                                 frameBuffer.startIndex(),
                                 frameBuffer.frames());
        }

        /*
         * Fetches a new batch of stack frames.  This method returns
         * the number of stack frames filled in this batch.
         *
         * When it reaches the bottom of a continuation, i.e. Continuation::enter,
         * VM ends the batch and let the stack walker to set the continuation
         * to its parent and continue the stack walking.  It may return zero.
         */
        private int fetchStackFrames() {
            int startIndex = frameBuffer.startIndex();

            // If the last batch didn't fetch any frames, keep the current batch size.
            int lastBatchFrameCount = frameBuffer.numFrames();
            int batchSize = getNextBatchSize();
            frameBuffer.resize(batchSize);

            int numFrames = fetchStackFrames(mode, anchor, lastBatchFrameCount,
                                             batchSize, startIndex,
                                             frameBuffer.frames());
            if (isDebug) {
                System.out.format("  more stack walk got %d frames start %d batch size %d%n",
                                  numFrames, frameBuffer.startIndex(), batchSize);
            }
            frameBuffer.setBatch(depth, startIndex, numFrames);
            return numFrames;
        }

        /**
         * Begins stack walking.  This method anchors this frame and invokes
         * AbstractStackWalker::doStackWalk after fetching the first batch of stack frames.
         *
         * @param mode        mode of stack walking
         * @param skipframes  number of frames to be skipped before filling the frame buffer.
         * @param contScope   the continuation scope to walk.
         * @param continuation the continuation to walk, or {@code null} if walking a thread.
         * @param bufferSize  the buffer size
         * @param startIndex  start index of the frame buffers to be filled.
         * @param frames      Either a {@link ClassFrameInfo} array, if mode is {@link #CLASS_INFO_ONLY}
         *                    or a {@link StackFrameInfo} (or derivative) array otherwise.
         * @return            Result of AbstractStackWalker::doStackWalk
         */
        private native R callStackWalk(int mode, int skipframes,
                                       ContinuationScope contScope, Continuation continuation,
                                       int bufferSize, int startIndex,
                                       T[] frames);

        /**
         * Fetch the next batch of stack frames.
         *
         * @param mode        mode of stack walking
         * @param anchor
         * @param lastBatchFrameCount the number of frames filled in the last batch.
         * @param bufferSize  the buffer size
         * @param startIndex  start index of the frame buffers to be filled.
         * @param frames      Either a {@link ClassFrameInfo} array, if mode is {@link #CLASS_INFO_ONLY}
         *                    or a {@link StackFrameInfo} (or derivative) array otherwise.
         *
         * @return the number of frames filled in this batch
         */
        private native int fetchStackFrames(int mode, long anchor, int lastBatchFrameCount,
                                            int bufferSize, int startIndex,
                                            T[] frames);

        private native void setContinuation(long anchor, T[] frames, Continuation cont);
    }

    /*
     * This StackFrameTraverser supports {@link Stream} traversal.
     *
     * This class implements Spliterator::forEachRemaining and Spliterator::tryAdvance.
     */
    static class StackFrameTraverser<T> extends AbstractStackWalker<T, StackFrame>
            implements Spliterator<StackFrame>
    {
        static {
            stackWalkImplClasses.add(StackFrameTraverser.class);
        }
        private static final int CHARACTERISTICS = Spliterator.ORDERED | Spliterator.IMMUTABLE;

        final Function<? super Stream<StackFrame>, ? extends T> function;  // callback

        StackFrameTraverser(StackWalker walker,
                            Function<? super Stream<StackFrame>, ? extends T> function) {
            super(walker, toStackWalkMode(walker, DEFAULT_MODE));
            this.function = function;
        }

        /**
         * Returns next StackFrame object in the current batch of stack frames;
         * or null if no more stack frame.
         */
        StackFrame nextStackFrame() {
            if (!hasNext()) {
                return null;
            }

            StackFrame frame = frameBuffer.nextStackFrame();
            depth++;
            return frame;
        }

        @Override
        protected T consumeFrames() {
            checkState(OPEN);
            Stream<StackFrame> stream = StreamSupport.stream(this, false);
            if (function != null) {
                return function.apply(stream);
            } else
                throw new UnsupportedOperationException();
        }

        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = walker.hasOption(Option.DROP_METHOD_INFO)
                                    ? new ClassFrameBuffer(walker, getNextBatchSize())
                                    : new StackFrameBuffer<>(StackFrameInfo.class, walker, getNextBatchSize());
        }

        @Override
        protected int batchSize(int lastBatchSize) {
            if (lastBatchSize == 0) {
                // First batch, use estimateDepth if not exceed the large batch size
                return walker.estimateDepth() == 0
                        ? SMALL_BATCH
                        : Math.min(walker.estimateDepth() + RESERVED_ELEMENTS, LARGE_BATCH_SIZE);
            } else {
                // expand only if last batch was full and the buffer size <= 32
                // to minimize the number of unneeded frames decoded.
                return (lastBatchSize > BATCH_SIZE || !frameBuffer.isFull())
                        ? lastBatchSize
                        : Math.min(lastBatchSize*2, BATCH_SIZE);
            }
        }

        // ------- Implementation of Spliterator

        @Override
        public Spliterator<StackFrame> trySplit() {
            return null;   // ordered stream and do not allow to split
        }

        @Override
        public long estimateSize() {
            return maxDepth;
        }

        @Override
        public int characteristics() {
            return CHARACTERISTICS;
        }

        @Override
        public void forEachRemaining(Consumer<? super StackFrame> action) {
            checkState(OPEN);
            for (int n = 0; n < maxDepth; n++) {
                StackFrame frame = nextStackFrame();
                if (frame == null) break;

                action.accept(frame);
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super StackFrame> action) {
            checkState(OPEN);

            int index = frameBuffer.getIndex();
            if (hasNext()) {
                StackFrame frame = nextStackFrame();
                action.accept(frame);
                if (isDebug) {
                    System.err.println("tryAdvance: " + index + " " + frame);
                }
                return true;
            }
            if (isDebug) {
                System.err.println("tryAdvance: " + index + " NO element");
            }
            return false;
        }
    }

    static class StackFrameBuffer<T extends ClassFrameInfo> extends FrameBuffer<T> {
        final StackWalker walker;
        private final Class<T> type;
        private final Constructor<T> ctor;
        private T[] stackFrames;
        StackFrameBuffer(Class<T> type, StackWalker walker, int initialBatchSize) {
            super(initialBatchSize);
            this.walker = walker;
            this.type = type;
            try {
                this.ctor = type.getDeclaredConstructor(StackWalker.class);
            } catch (NoSuchMethodException e) {
                throw new InternalError(e);
            }
            this.stackFrames = fill(allocateArray(initialBatchSize), START_POS, initialBatchSize);
        }

        @Override
        T[] frames() {
            return stackFrames;
        }

        @SuppressWarnings("unchecked")
        T[] allocateArray(int size) {
            return (T[])Array.newInstance(type, size);
        }

        T[] fill(T[] array, int startIndex, int size) {
            try {
                for (int i = startIndex; i < size; i++) {
                    array[i] = ctor.newInstance(walker);
                }
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
            return array;
        }

        @Override
        void resize(int size) {
            if (!isActive())
                throw new IllegalStateException("inactive frame buffer can't be resized");

            assert startIndex() == START_POS :
                    "bad start index " + startIndex() + " expected " + START_POS;

            if (stackFrames.length < size) {
                T[] newFrames = allocateArray(size);
                // copy initial magic...
                System.arraycopy(stackFrames, 0, newFrames, 0, startIndex());
                stackFrames = newFrames;
            }
            fill(stackFrames, startIndex(), size);
            currentBatchSize = size;
        }

        @Override
        T nextStackFrame() {
            if (isEmpty()) {
                throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
            }

            T frame = stackFrames[origin];
            origin++;
            return frame;
        }

        @Override
        final Class<?> at(int index) {
            return stackFrames[index].declaringClass();
        }
    }

    /*
     * Buffer for ClassFrameInfo.  It allocates ClassFrameInfo via bytecode
     * invocation instead of via core reflection to minimize the overhead.
     */
    static class ClassFrameBuffer extends StackFrameBuffer<ClassFrameInfo> {
        ClassFrameBuffer(StackWalker walker, int initialBatchSize) {
            super(ClassFrameInfo.class, walker, initialBatchSize);
        }

        @Override
        ClassFrameInfo[] allocateArray(int size) {
            return new ClassFrameInfo[size];
        }

        @Override
        ClassFrameInfo[] fill(ClassFrameInfo[] array, int startIndex, int size) {
            for (int i = startIndex; i < size; i++) {
                array[i] = new ClassFrameInfo(walker);
            }
            return array;
        }
    }

    /*
     * CallerClassFinder is specialized to return Class<?> for each stack frame.
     * StackFrame is not requested.
     */
    static final class CallerClassFinder extends AbstractStackWalker<Integer, ClassFrameInfo> {
        static {
            stackWalkImplClasses.add(CallerClassFinder.class);
        }

        private Class<?> caller;

        CallerClassFinder(StackWalker walker) {
            super(walker, toStackWalkMode(walker, CLASS_INFO_ONLY));
        }

        Class<?> findCaller() {
            walk();
            return caller;
        }

        @Override
        protected Integer consumeFrames() {
            checkState(OPEN);
            int n = 0;
            ClassFrameInfo curFrame = null;
            // StackWalker::getCallerClass method
            // 0: caller-sensitive method
            // 1: caller class
            ClassFrameInfo[] frames = new ClassFrameInfo[2];
            while (n < 2 && hasNext() && (curFrame = frameBuffer.nextStackFrame()) != null) {
                caller = curFrame.declaringClass();
                if (curFrame.isHidden() || isReflectionFrame(caller) || isMethodHandleFrame(caller)) {
                    if (isDebug)
                        System.err.println("  skip: frame " + frameBuffer.getIndex() + " " + curFrame);
                    continue;
                }
                frames[n++] = curFrame;
            }
            if (isDebug) {
                System.err.println("0: " + frames[0]);
                System.err.println("1: " + frames[1]);
            }
            if (frames[1] == null) {
                throw new IllegalCallerException("no caller frame: " + Arrays.toString(frames));
            }
            if (frames[0].isCallerSensitive()) {
                throw new UnsupportedOperationException("StackWalker::getCallerClass called from @CallerSensitive "
                        + Arrays.toString(frames));
            }
            return n;
        }

        /*
         * Typically finding the caller class only needs to walk two stack frames
         * 0: StackWalker::getCallerClass
         * 1: API
         * 2: caller class
         *
         * So start the initial batch size with the minimum size.
         */
        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = new ClassFrameBuffer(walker, MIN_BATCH_SIZE);
        }

        @Override
        protected int batchSize(int lastBatchSize) {
            // this method is only called when the caller class is not found in
            // the first batch. getCallerClass may be invoked via core reflection.
            // So increase the next batch size as there may be implementation-specific
            // frames before reaching the caller class's frame.
            return SMALL_BATCH;
        }

        @Override
        protected int getNextBatchSize() {
            return SMALL_BATCH;
        }
    }

    static final class LiveStackInfoTraverser<T> extends StackFrameTraverser<T> {
        static {
            stackWalkImplClasses.add(LiveStackInfoTraverser.class);
        }

        LiveStackInfoTraverser(StackWalker walker,
                               Function<? super Stream<StackFrame>, ? extends T> function) {
            super(walker, function);
        }

        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = new StackFrameBuffer<>(LiveStackFrameInfo.class, walker, getNextBatchSize());
        }
    }

    /*
     * Frame buffer
     *
     * Each specialized AbstractStackWalker subclass may subclass the FrameBuffer.
     */
    abstract static class FrameBuffer<F> {
        static final int START_POS = RESERVED_ELEMENTS;

        // buffers for VM to fill stack frame info
        int currentBatchSize;    // current batch size
        int origin;         // index to the current traversed stack frame
        int fence;          // index past the last frame of the current batch
        FrameBuffer(int initialBatchSize) {
            if (initialBatchSize < MIN_BATCH_SIZE) {
                throw new IllegalArgumentException(initialBatchSize +
                        " < minimum batch size: " + MIN_BATCH_SIZE);
            }
            this.origin = START_POS;
            this.fence = 0;
            this.currentBatchSize = initialBatchSize;
        }

        /**
         * Returns an array of frames that may be used to store frame objects
         * when walking the stack.
         *
         * May be an array of {@code Class<?>} if the {@code AbstractStackWalker}
         * mode is {@link #CLASS_INFO_ONLY}, or an array of
         * {@link StackFrameInfo} (or derivative) array otherwise.
         *
         * @return An array of frames that may be used to store frame objects
         * when walking the stack. Must not be null.
         */
        abstract F[] frames(); // must not return null

        /**
         * Resizes the buffers for VM to fill in the next batch of stack frames.
         *
         * <p> Subclass may override this method to manage the allocated buffers.
         *
         * @param size new batch size
         *
         */
        abstract void resize(int size);

        /**
         * Return the class at the given position in the current batch.
         * @param index the position of the frame.
         * @return the class at the given position in the current batch.
         */
        abstract Class<?> at(int index);

        // ------ subclass may override the following methods -------

        /*
         * Returns the start index for this frame buffer is refilled.
         *
         * This implementation reuses the allocated buffer for the next batch
         * of stack frames.  For subclass to retain the fetched stack frames,
         * it should override this method to return the index at which the frame
         * should be filled in for the next batch.
         */
        int startIndex() {
            return START_POS;
        }

        /**
         * Returns next StackFrame object in the current batch of stack frames
         */
        F nextStackFrame() {
            throw new InternalError("should not reach here");
        }

        // ------ FrameBuffer implementation ------

        final int currentBatchSize() {
            return currentBatchSize;
        }

        /*
         * Tests if this frame buffer is empty.  All frames are fetched.
         */
        final boolean isEmpty() {
            return origin >= fence || (origin == START_POS && fence == 0);
        }

        /*
         * Returns the number of stack frames filled in the current batch
         */
        final int numFrames() {
            if (!isActive())
                throw new IllegalStateException();
            return fence - startIndex();
        }

        /*
         * Freezes this frame buffer.  The stack stream source is done fetching.
         */
        final void freeze() {
            origin = 0;
            fence = 0;
        }

        /*
         * Tests if this frame buffer is active.  It is inactive when
         * it is done for traversal.
         */
        final boolean isActive() {
            return origin > 0;
        }

        final boolean isFull() {
            return fence == currentBatchSize;
        }

        /*
         * Tests if this frame buffer is at the end of the stack
         * and all frames have been traversed.
         */
        final boolean isAtBottom() {
            return origin > 0 && origin >= fence && fence < currentBatchSize;
        }

        /**
         * Gets the class at the current frame and move to the next frame.
         */
        final Class<?> next() {
            if (isEmpty()) {
                throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
            }
            Class<?> c = at(origin);
            origin++;
            if (isDebug) {
                int index = origin-1;
                System.out.format("  next frame at %d: %s (origin %d fence %d)%n", index,
                        c.getName(), index, fence);
            }
            return c;
        }

        /**
         * Gets the class at the current frame.
         */
        final Class<?> get() {
            if (isEmpty()) {
                throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
            }
            return at(origin);
        }

        /*
         * Returns the index of the current frame.
         */
        final int getIndex() {
            return origin;
        }

        /*
         * Set a new batch of stack frames that have been filled in this frame buffer.
         */
        final void setBatch(int depth, int startIndex, int numFrames) {
            if (startIndex <= 0 || numFrames < 0)
                throw new IllegalArgumentException("startIndex=" + startIndex
                        + " numFrames=" + numFrames);

            this.origin = startIndex;
            this.fence = startIndex + numFrames;
            for (int i = startIndex; i < fence; i++) {
                if (isDebug) System.err.format("  frame %d: %s%n", i, at(i));
                if (depth == 0 && filterStackWalkImpl(at(i))) { // filter the frames due to the stack stream implementation
                    origin++;
                } else {
                    break;
                }
            }
        }

        /*
         * Checks if the origin is the expected start index.
         */
        final void check(int skipFrames) {
            int index = skipFrames + START_POS;
            if (origin != index) {
                // stack walk must continue with the previous frame depth
                throw new IllegalStateException("origin " + origin + " != " + index);
            }
        }
    }

    private static native boolean checkStackWalkModes();

    // avoid loading other subclasses as they may not be used
    private static Set<Class<?>> init() {
        if (!checkStackWalkModes()) {
            throw new InternalError("StackWalker mode values do not match with JVM");
        }

        Set<Class<?>> classes = new HashSet<>();
        classes.add(StackWalker.class);
        classes.add(StackStreamFactory.class);
        classes.add(AbstractStackWalker.class);
        return classes;
    }

    private static boolean filterStackWalkImpl(Class<?> c) {
        return stackWalkImplClasses.contains(c) ||
                c.getPackageName().equals("java.util.stream");
    }

    // MethodHandle frames are not hidden and CallerClassFinder has
    // to filter them out
    private static boolean isMethodHandleFrame(Class<?> c) {
        return c.getPackageName().equals("java.lang.invoke");
    }

    private static boolean isReflectionFrame(Class<?> c) {
        return c == Method.class ||
               c == Constructor.class ||
               MethodAccessor.class.isAssignableFrom(c) ||
               ConstructorAccessor.class.isAssignableFrom(c);
    }

}
