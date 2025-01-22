package jdk.internal.foreign.abi;

import jdk.internal.foreign.SlicingAllocator;
import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.concurrent.locks.ReentrantLock;

public class BufferStack {
    private final long size;

    public BufferStack(long size) {
        this.size = size;
    }

    private final TerminatingThreadLocal<PerThread> tl = new TerminatingThreadLocal<>() {
        @Override
        protected PerThread initialValue() {
            return new PerThread(size);
        }

        @Override
        protected void threadTerminated(PerThread value) {
            value.close();
        }
    };

    @ForceInline
    public Arena pushFrame(long size, long byteAlignment) {
        return tl.get().pushFrame(size, byteAlignment);
    }

    private static final class PerThread {
        private final ReentrantLock lock = new ReentrantLock();
        private final Arena owner = Arena.ofConfined();
        private final SlicingAllocator stack;

        public PerThread(long size) {
            this.stack = new SlicingAllocator(owner.allocate(size));
        }

        void close() {
            owner.close();
        }

        @ForceInline
        public Arena pushFrame(long size, long byteAlignment) {
            boolean needsLock = Thread.currentThread().isVirtual() && !lock.isHeldByCurrentThread();
            if (needsLock && !lock.tryLock()) {
                // Rare: another virtual thread on the same carrier competed for acquisition.
                return Arena.ofConfined();
            }
            if (!stack.canAllocate(size, byteAlignment)) {
                if (needsLock) lock.unlock();
                return Arena.ofConfined();
            }

            return new Frame(needsLock, size, byteAlignment);
        }

        private class Frame implements Arena {
            private final boolean locked;
            private final long parentOffset;
            private final long tos;
            private final Arena scope = Arena.ofConfined();
            private final SegmentAllocator frame;

            @SuppressWarnings("restricted")
            public Frame(boolean locked, long byteSize, long byteAlignment) {
                this.locked = locked;

                parentOffset = stack.currentOffset();
                MemorySegment frameSegment = stack.allocate(byteSize, byteAlignment);
                tos = stack.currentOffset();
                frame = new SlicingAllocator(frameSegment.reinterpret(scope, null));
            }

            private void assertOrder() {
                if (tos != stack.currentOffset())
                    throw new IllegalStateException("Out of order access: frame not TOS");
            }

            @Override
            @SuppressWarnings("restricted")
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                assertOrder();
                return frame.allocate(byteSize, byteAlignment);
            }

            @Override
            public MemorySegment.Scope scope() {
                return scope.scope();
            }

            @Override
            public void close() {
                assertOrder();
                scope.close();
                stack.resetTo(parentOffset);
                if (locked) {
                    lock.unlock();
                }
            }
        }
    }
}