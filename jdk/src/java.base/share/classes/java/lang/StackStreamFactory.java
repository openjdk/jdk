/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.VM;

import java.io.PrintStream;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;

import java.lang.annotation.Native;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
 * 3. Throwable::init and Throwable::getStackTrace
 * 4. AccessControlContext getting ProtectionDomain
 */
final class StackStreamFactory {
    private StackStreamFactory() {}

    // Stack walk implementation classes to be excluded during stack walking
    // lazily add subclasses when they are loaded.
    private final static Set<Class<?>> stackWalkImplClasses = init();

    private static final int SMALL_BATCH       = 8;
    private static final int BATCH_SIZE        = 32;
    private static final int LARGE_BATCH_SIZE  = 256;
    private static final int MIN_BATCH_SIZE    = SMALL_BATCH;

    // These flags must match the values maintained in the VM
    @Native private static final int DEFAULT_MODE              = 0x0;
    @Native private static final int FILL_CLASS_REFS_ONLY      = 0x2;
    @Native private static final int FILTER_FILL_IN_STACKTRACE = 0x10;
    @Native private static final int SHOW_HIDDEN_FRAMES        = 0x20;  // LambdaForms are hidden by the VM
    @Native private static final int FILL_LIVE_STACK_FRAMES    = 0x100;

    /*
     * For Throwable to use StackWalker, set useNewThrowable to true.
     * Performance work and extensive testing is needed to replace the
     * VM built-in backtrace filled in Throwable with the StackWalker.
     */
    final static boolean useNewThrowable = getProperty("stackwalk.newThrowable", false);
    final static boolean isDebug = getProperty("stackwalk.debug", false);

    static <T> StackFrameTraverser<T>
        makeStackTraverser(StackWalker walker, Function<? super Stream<StackFrame>, ? extends T> function)
    {
        if (walker.hasLocalsOperandsOption())
            return new LiveStackInfoTraverser<T>(walker, function);
        else
            return new StackFrameTraverser<T>(walker, function);
    }

    /**
     * Gets a stack stream to find caller class.
     */
    static CallerClassFinder makeCallerFinder(StackWalker walker) {
        return new CallerClassFinder(walker);
    }

    static boolean useStackTrace(Throwable t) {
        if (t instanceof VirtualMachineError)
            return false;

        return VM.isBooted() && StackStreamFactory.useNewThrowable;
    }

    /*
     * This should only be used by Throwable::<init>.
     */
    static StackTrace makeStackTrace(Throwable ex) {
        return StackTrace.dump(ex);
    }

    /*
     * This creates StackTrace for Thread::dumpThread to use.
     */
    static StackTrace makeStackTrace() {
        return StackTrace.dump();
    }

    enum WalkerState {
        NEW,     // the stream is new and stack walking has not started
        OPEN,    // the stream is open when it is being traversed.
        CLOSED;  // the stream is closed when the stack walking is done
    }

    static abstract class AbstractStackWalker<T> {
        protected final StackWalker walker;
        protected final Thread thread;
        protected final int maxDepth;
        protected final long mode;
        protected int depth;                 // traversed stack depth
        protected FrameBuffer frameBuffer;   // buffer for VM to fill in
        protected long anchor;

        // buffers to fill in stack frame information
        protected AbstractStackWalker(StackWalker walker, int mode) {
            this(walker, mode, Integer.MAX_VALUE);
        }
        protected AbstractStackWalker(StackWalker walker, int mode, int maxDepth) {
            this.thread = Thread.currentThread();
            this.mode = toStackWalkMode(walker, mode);
            this.walker = walker;
            this.maxDepth = maxDepth;
            this.depth = 0;
        }

        private int toStackWalkMode(StackWalker walker, int mode) {
            int newMode = mode;
            if (walker.hasOption(Option.SHOW_HIDDEN_FRAMES) &&
                    !fillCallerClassOnly(newMode) /* don't show hidden frames for getCallerClass */)
                newMode |= SHOW_HIDDEN_FRAMES;
            if (walker.hasLocalsOperandsOption())
                newMode |= FILL_LIVE_STACK_FRAMES;
            return newMode;
        }

        private boolean fillCallerClassOnly(int mode) {
            return (mode|FILL_CLASS_REFS_ONLY) != FILL_CLASS_REFS_ONLY;
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
         protected abstract T consumeFrames();

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
         * @param lastBatchFrameCount number of frames in the last batch; or zero
         * @return suggested batch size
         */
        protected abstract int batchSize(int lastBatchFrameCount);

        /*
         * Returns the next batch size, always >= minimum batch size (32)
         *
         * Subclass may override this method if the minimum batch size is different.
         */
        protected int getNextBatchSize() {
            int lastBatchSize = depth == 0 ? 0 : frameBuffer.curBatchFrameCount();
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
        final T walk() {
            checkState(NEW);
            try {
                // VM will need to stablize the stack before walking.  It will invoke
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
        private Object doStackWalk(long anchor, int skipFrames, int batchSize,
                                                int bufStartIndex, int bufEndIndex) {
            checkState(NEW);

            frameBuffer.check(skipFrames);

            if (isDebug) {
                System.err.format("doStackWalk: skip %d start %d end %d%n",
                        skipFrames, bufStartIndex, bufEndIndex);
            }

            this.anchor = anchor;  // set anchor for this bulk stack frame traversal
            frameBuffer.setBatch(bufStartIndex, bufEndIndex);

            // traverse all frames and perform the action on the stack frames, if specified
            return consumeFrames();
        }

        /*
         * Get next batch of stack frames.
         */
        private int getNextBatch() {
            int nextBatchSize = Math.min(maxDepth - depth, getNextBatchSize());
            if (!frameBuffer.isActive() || nextBatchSize <= 0) {
                if (isDebug) {
                    System.out.format("  more stack walk done%n");
                }
                frameBuffer.freeze();   // stack walk done
                return 0;
            }

            return fetchStackFrames(nextBatchSize);
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
        private T beginStackWalk() {
            // initialize buffers for VM to fill the stack frame info
            initFrameBuffer();

            return callStackWalk(mode, 0,
                                 frameBuffer.curBatchFrameCount(),
                                 frameBuffer.startIndex(),
                                 frameBuffer.classes,
                                 frameBuffer.stackFrames);
        }

        /*
         * Fetches stack frames.
         *
         * @params batchSize number of elements of the frame  buffers for this batch
         * @returns number of frames fetched in this batch
         */
        private int fetchStackFrames(int batchSize) {
            int startIndex = frameBuffer.startIndex();
            frameBuffer.resize(startIndex, batchSize);

            int endIndex = fetchStackFrames(mode, anchor, batchSize,
                                            startIndex,
                                            frameBuffer.classes,
                                            frameBuffer.stackFrames);
            if (isDebug) {
                System.out.format("  more stack walk requesting %d got %d to %d frames%n",
                                  batchSize, frameBuffer.startIndex(), endIndex);
            }
            int numFrames = endIndex - startIndex;
            if (numFrames == 0) {
                frameBuffer.freeze(); // done stack walking
            } else {
                frameBuffer.setBatch(startIndex, endIndex);
            }
            return numFrames;
        }

        /**
         * Begins stack walking.  This method anchors this frame and invokes
         * AbstractStackWalker::doStackWalk after fetching the firt batch of stack frames.
         *
         * @param mode        mode of stack walking
         * @param skipframes  number of frames to be skipped before filling the frame buffer.
         * @param batchSize   the batch size, max. number of elements to be filled in the frame buffers.
         * @param startIndex  start index of the frame buffers to be filled.
         * @param classes     Classes buffer of the stack frames
         * @param frames      StackFrame buffer, or null
         * @return            Result of AbstractStackWalker::doStackWalk
         */
        private native T callStackWalk(long mode, int skipframes,
                                       int batchSize, int startIndex,
                                       Class<?>[] classes,
                                       StackFrame[] frames);

        /**
         * Fetch the next batch of stack frames.
         *
         * @param mode        mode of stack walking
         * @param anchor
         * @param batchSize   the batch size, max. number of elements to be filled in the frame buffers.
         * @param startIndex  start index of the frame buffers to be filled.
         * @param classes     Classes buffer of the stack frames
         * @param frames      StackFrame buffer, or null
         *
         * @return the end index to the frame buffers
         */
        private native int fetchStackFrames(long mode, long anchor,
                                            int batchSize, int startIndex,
                                            Class<?>[] classes,
                                            StackFrame[] frames);


        /*
         * Frame buffer
         *
         * Each specialized AbstractStackWalker subclass may subclass the FrameBuffer.
         */
        class FrameBuffer {
            static final int START_POS = 2;     // 0th and 1st elements are reserved

            // buffers for VM to fill stack frame info
            int currentBatchSize;    // current batch size
            Class<?>[] classes;      // caller class for fast path

            StackFrame[] stackFrames;

            int origin;         // index to the current traversed stack frame
            int fence;          // index to the last frame in the current batch

            FrameBuffer(int initialBatchSize) {
                if (initialBatchSize < MIN_BATCH_SIZE) {
                    throw new IllegalArgumentException(initialBatchSize + " < minimum batch size: " + MIN_BATCH_SIZE);
                }
                this.origin = START_POS;
                this.fence = 0;
                this.currentBatchSize = initialBatchSize;
                this.classes = new Class<?>[currentBatchSize];
            }

            int curBatchFrameCount() {
                return currentBatchSize-START_POS;
            }

            /*
             * Tests if this frame buffer is empty.  All frames are fetched.
             */
            final boolean isEmpty() {
                return origin >= fence || (origin == START_POS && fence == 0);
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
             * it is done for traversal.  All stack frames have been traversed.
             */
            final boolean isActive() {
                return origin > 0 && (fence == 0 || origin < fence || fence == currentBatchSize);
            }

            /**
             * Gets the class at the current frame and move to the next frame.
             */
            final Class<?> next() {
                if (isEmpty()) {
                    throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
                }
                Class<?> c = classes[origin++];
                if (isDebug) {
                    int index = origin-1;
                    System.out.format("  next frame at %d: %s (origin %d fence %d)%n", index,
                                      Objects.toString(c), index, fence);
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
                return classes[origin];
            }

            /*
             * Returns the index of the current frame.
             */
            final int getIndex() {
                return origin;
            }

            /*
             * Set the start and end index of a new batch of stack frames that have
             * been filled in this frame buffer.
             */
            final void setBatch(int startIndex, int endIndex) {
                if (startIndex <= 0 || endIndex <= 0)
                    throw new IllegalArgumentException("startIndex=" + startIndex + " endIndex=" + endIndex);

                this.origin = startIndex;
                this.fence = endIndex;
                if (depth == 0 && fence > 0) {
                    // filter the frames due to the stack stream implementation
                    for (int i = START_POS; i < fence; i++) {
                        Class<?> c = classes[i];
                        if (isDebug) System.err.format("  frame %d: %s%n", i, c);
                        if (filterStackWalkImpl(c)) {
                            origin++;
                        } else {
                            break;
                        }
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

            // ------ subclass may override the following methods -------
            /**
             * Resizes the buffers for VM to fill in the next batch of stack frames.
             * The next batch will start at the given startIndex with the maximum number
             * of elements.
             *
             * <p> Subclass may override this method to manage the allocated buffers.
             *
             * @param startIndex the start index for the first frame of the next batch to fill in.
             * @param elements the number of elements for the next batch to fill in.
             *
             */
            void resize(int startIndex, int elements) {
                if (!isActive())
                    throw new IllegalStateException("inactive frame buffer can't be resized");

                int size = startIndex+elements;
                if (classes.length < size) {
                    // copy the elements in classes array to the newly allocated one.
                    // classes[0] is a Thread object
                    Class<?>[] prev = classes;
                    classes = new Class<?>[size];
                    System.arraycopy(prev, 0, classes, 0, START_POS);
                }
                currentBatchSize = size;
            }

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
            StackFrame nextStackFrame() {
                throw new InternalError("should not reach here");
            }
        }
    }

    /*
     * This StackFrameTraverser supports {@link Stream} traversal.
     *
     * This class implements Spliterator::forEachRemaining and Spliterator::tryAdvance.
     */
    static class StackFrameTraverser<T> extends AbstractStackWalker<T>
            implements Spliterator<StackFrame>
    {
        static {
            stackWalkImplClasses.add(StackFrameTraverser.class);
        }
        private static final int CHARACTERISTICS = Spliterator.ORDERED | Spliterator.IMMUTABLE;
        class Buffer extends FrameBuffer {
            Buffer(int initialBatchSize) {
                super(initialBatchSize);

                this.stackFrames = new StackFrame[initialBatchSize];
                for (int i = START_POS; i < initialBatchSize; i++) {
                    stackFrames[i] = new StackFrameInfo(walker);
                }
            }

            @Override
            void resize(int startIndex, int elements) {
                super.resize(startIndex, elements);

                int size = startIndex+elements;
                if (stackFrames.length < size) {
                    stackFrames = new StackFrame[size];
                }
                for (int i = startIndex(); i < size; i++) {
                    stackFrames[i] = new StackFrameInfo(walker);
                }
            }

            @Override
            StackFrame nextStackFrame() {
                if (isEmpty()) {
                    throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
                }

                StackFrame frame = stackFrames[origin];
                origin++;
                return frame;
            }
        }

        final Function<? super Stream<StackFrame>, ? extends T> function;  // callback

        StackFrameTraverser(StackWalker walker,
                            Function<? super Stream<StackFrame>, ? extends T> function) {
            this(walker, function, DEFAULT_MODE);
        }
        StackFrameTraverser(StackWalker walker,
                            Function<? super Stream<StackFrame>, ? extends T> function,
                            int mode) {
            super(walker, mode);
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
            this.frameBuffer = new Buffer(getNextBatchSize());
        }

        @Override
        protected int batchSize(int lastBatchFrameCount) {
            if (lastBatchFrameCount == 0) {
                // First batch, use estimateDepth if not exceed the large batch size
                // and not too small
                int initialBatchSize = Math.max(walker.estimateDepth(), SMALL_BATCH);
                return Math.min(initialBatchSize, LARGE_BATCH_SIZE);
            } else {
                if (lastBatchFrameCount > BATCH_SIZE) {
                    return lastBatchFrameCount;
                } else {
                    return Math.min(lastBatchFrameCount*2, BATCH_SIZE);
                }
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

    /*
     * CallerClassFinder is specialized to return Class<?> for each stack frame.
     * StackFrame is not requested.
     */
    static class CallerClassFinder extends AbstractStackWalker<Integer> {
        static {
            stackWalkImplClasses.add(CallerClassFinder.class);
        }

        private Class<?> caller;

        CallerClassFinder(StackWalker walker) {
            super(walker, FILL_CLASS_REFS_ONLY);
        }

        Class<?> findCaller() {
            walk();
            return caller;
        }

        @Override
        protected Integer consumeFrames() {
            checkState(OPEN);
            int n = 0;
            Class<?>[] frames = new Class<?>[2];
            // skip the API calling this getCallerClass method
            // 0: StackWalker::getCallerClass
            // 1: caller-sensitive method
            // 2: caller class
            while (n < 2 && (caller = nextFrame()) != null) {
                if (isMethodHandleFrame(caller)) continue;
                frames[n++] = caller;
            }

            if (frames[1] == null)
                throw new IllegalStateException("no caller frame");
            return n;
        }

        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = new FrameBuffer(getNextBatchSize());
        }

        @Override
        protected int batchSize(int lastBatchFrameCount) {
            return MIN_BATCH_SIZE;
        }

        @Override
        protected int getNextBatchSize() {
            return MIN_BATCH_SIZE;
        }
    }

    /*
     * StackTrace caches all frames in the buffer.  StackTraceElements are
     * created lazily when Throwable::getStackTrace is called.
     */
    static class StackTrace extends AbstractStackWalker<Integer> {
        static {
            stackWalkImplClasses.add(StackTrace.class);
        }

        class GrowableBuffer extends FrameBuffer {
            GrowableBuffer(int initialBatchSize) {
                super(initialBatchSize);

                this.stackFrames = new StackFrame[initialBatchSize];
                for (int i = START_POS; i < initialBatchSize; i++) {
                    stackFrames[i] = new StackFrameInfo(walker);
                }
            }

            /*
             * Returns the next index to fill
             */
            @Override
            int startIndex() {
                return origin;
            }

            /**
             * Initialize the buffers for VM to fill in the stack frame information.
             * The next batch will start at the given startIndex to
             * the length of the buffer.
             */
            @Override
            void resize(int startIndex, int elements) {
                // Expand the frame buffer.
                // Do not call super.resize that will reuse the filled elements
                // in this frame buffer
                int size = startIndex+elements;
                if (classes.length < size) {
                    // resize the frame buffer
                    classes = Arrays.copyOf(classes, size);
                    stackFrames = Arrays.copyOf(stackFrames, size);
                }
                for (int i = startIndex; i < size; i++) {
                    stackFrames[i] = new StackFrameInfo(walker);
                }
                currentBatchSize = size;
            }

            StackTraceElement get(int index) {
                return new StackTraceElement(classes[index].getName(), "unknown", null, -1);
            }

            /**
             * Returns an array of StackTraceElement for all stack frames cached in
             * this StackTrace object.
             * <p>
             * This method is intended for Throwable::getOurStackTrace use only.
             */
            StackTraceElement[] toStackTraceElements() {
                int startIndex = START_POS;
                for (int i = startIndex; i < classes.length; i++) {
                    if (classes[i] != null && filterStackWalkImpl(classes[i])) {
                        startIndex++;
                    } else {
                        break;
                    }
                }

                // VM fills in the method name, filename, line number info
                StackFrameInfo.fillInStackFrames(0, stackFrames, startIndex, startIndex + depth);

                StackTraceElement[] stes = new StackTraceElement[depth];
                for (int i = startIndex, j = 0; i < classes.length && j < depth; i++, j++) {
                    if (isDebug) {
                        System.err.println("StackFrame: " + i + " " + stackFrames[i]);
                    }
                    stes[j] = stackFrames[i].toStackTraceElement();
                }
                return stes;
            }
        }

        private static final int MAX_STACK_FRAMES = 1024;
        private static final StackWalker STACKTRACE_WALKER =
            StackWalker.newInstanceNoCheck(EnumSet.of(Option.SHOW_REFLECT_FRAMES));

        private StackTraceElement[] stes;
        static StackTrace dump() {
            return new StackTrace();
        }

        static StackTrace dump(Throwable ex) {
            return new StackTrace(ex);
        }

        private StackTrace() {
            this(STACKTRACE_WALKER, DEFAULT_MODE);
        }

        /*
         * Throwable::fillInStackTrace and <init> of Throwable and subclasses
         * are filtered in the VM.
         */
        private StackTrace(Throwable ex) {
            this(STACKTRACE_WALKER, FILTER_FILL_IN_STACKTRACE);  // skip Throwable::init frames
            if (isDebug) {
                System.err.println("dump stack for " + ex.getClass().getName());
            }
        }

        StackTrace(StackWalker walker, int mode) {
            super(walker, mode, MAX_STACK_FRAMES);

            // snapshot the stack trace
            walk();
        }

        @Override
        protected Integer consumeFrames() {
            // traverse all frames and perform the action on the stack frames, if specified
            int n = 0;
            while (n < maxDepth && nextFrame() != null) {
                n++;
            }
            return n;
        }

        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = new GrowableBuffer(getNextBatchSize());
        }

        // TODO: implement better heuristic
        @Override
        protected int batchSize(int lastBatchFrameCount) {
            // chunk size of VM backtrace is 32
            return lastBatchFrameCount == 0 ? 32 : 32;
        }

        /**
         * Returns an array of StackTraceElement for all stack frames cached in
         * this StackTrace object.
         * <p>
         * This method is intended for Throwable::getOurStackTrace use only.
         */
        synchronized StackTraceElement[] getStackTraceElements() {
            if (stes == null) {
                stes = ((GrowableBuffer) frameBuffer).toStackTraceElements();
                // release the frameBuffer memory
                frameBuffer = null;
            }
            return stes;
        }

        /*
         * Prints stack trace to the given PrintStream.
         *
         * Further implementation could skip creating StackTraceElement objects
         * print directly to the PrintStream.
         */
        void printStackTrace(PrintStream s) {
            StackTraceElement[] stes = getStackTraceElements();
            synchronized (s) {
                s.println("Stack trace");
                for (StackTraceElement traceElement : stes)
                    s.println("\tat " + traceElement);
            }
        }
    }

    static class LiveStackInfoTraverser<T> extends StackFrameTraverser<T> {
        static {
            stackWalkImplClasses.add(LiveStackInfoTraverser.class);
        }
        // VM will fill in all method info and live stack info directly in StackFrameInfo
        class Buffer extends FrameBuffer {
            Buffer(int initialBatchSize) {
                super(initialBatchSize);
                this.stackFrames = new StackFrame[initialBatchSize];
                for (int i = START_POS; i < initialBatchSize; i++) {
                    stackFrames[i] = new LiveStackFrameInfo(walker);
                }
            }

            @Override
            void resize(int startIndex, int elements) {
                super.resize(startIndex, elements);
                int size = startIndex + elements;

                if (stackFrames.length < size) {
                    this.stackFrames = new StackFrame[size];
                }

                for (int i = startIndex(); i < size; i++) {
                    stackFrames[i] = new LiveStackFrameInfo(walker);
                }
            }

            @Override
            StackFrame nextStackFrame() {
                if (isEmpty()) {
                    throw new NoSuchElementException("origin=" + origin + " fence=" + fence);
                }

                StackFrame frame = stackFrames[origin];
                origin++;
                return frame;
            }
        }

        LiveStackInfoTraverser(StackWalker walker,
                               Function<? super Stream<StackFrame>, ? extends T> function) {
            super(walker, function, DEFAULT_MODE);
        }

        @Override
        protected void initFrameBuffer() {
            this.frameBuffer = new Buffer(getNextBatchSize());
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
                c.getName().startsWith("java.util.stream.");
    }

    // MethodHandle frames are not hidden and CallerClassFinder has
    // to filter them out
    private static boolean isMethodHandleFrame(Class<?> c) {
        return c.getName().startsWith("java.lang.invoke.");
    }

    private static boolean isReflectionFrame(Class<?> c) {
        if (c.getName().startsWith("sun.reflect") &&
                !sun.reflect.MethodAccessor.class.isAssignableFrom(c)) {
            throw new InternalError("Not sun.reflect.MethodAccessor: " + c.toString());
        }
        // ## should filter all @Hidden frames?
        return c == Method.class ||
                sun.reflect.MethodAccessor.class.isAssignableFrom(c) ||
                c.getName().startsWith("java.lang.invoke.LambdaForm");
    }

    private static boolean getProperty(String key, boolean value) {
        String s = AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public String run() {
                return System.getProperty(key);
            }
        });
        if (s != null) {
            return Boolean.valueOf(s);
        }
        return value;
    }
}
