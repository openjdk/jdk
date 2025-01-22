package jdk.internal.foreign.abi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.locks.ReentrantLock;

public class BufferStack {
    private final MemorySegment backingSegment;
    private final ReentrantLock lock = new ReentrantLock();
    private long offset = 0;

    public BufferStack(MemorySegment backingSegment) {
        this.backingSegment = backingSegment;
    }

    public Arena reserve(long size) {
        if (!lock.tryLock()) {
            // Rare: another virtual thread on the same carrier was preparing or just
            // finished an FFM call, but got unscheduled while holding this stack.
            return Arena.ofConfined();
        }
        if (offset + size > backingSegment.byteSize()) {
            // Rare: we've running out of stack space due to recursion or unusually large buffers.
            lock.unlock();
            return Arena.ofConfined();
        }

        return new Frame();
    }

    private class Frame implements Arena {
        final long parentOffset = offset;
        final Arena scope = Arena.ofConfined();

        @Override
        @SuppressWarnings("restricted")
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            MemorySegment slice = backingSegment.asSlice(offset, byteSize, byteAlignment);
            offset += byteSize;
            return slice.reinterpret(scope, null);
        }

        @Override
        public MemorySegment.Scope scope() {
            return scope.scope();
        }

        @Override
        public void close() {
            scope.close();
            offset = parentOffset;
            lock.unlock();
        }
    }
}
