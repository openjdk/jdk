import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.*;

import jdk.internal.misc.Unsafe;

// Run with
// java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -XX:CompileCommand=compileonly,TestReachabilityFenceWithBuffer::test -Xbatch TestReachabilityFenceWithBuffer.java

public class ReachabilityFence {

    static class MyBuffer {
        private static Unsafe UNSAFE;
        static {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                UNSAFE = (Unsafe)field.get(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static int current = 0;
        private static long payload[] = new long[10];

        private final int id;

        public MyBuffer(long size) {
            // Get a unique id, allocate memory and safe the address in the payload array
            id = current++;
            payload[id] = UNSAFE.allocateMemory(size);

            // Register a cleaner to free the memory when the buffer is garbage collected
            int lid = id; // Capture current value
            Cleaner.create().register(this, () -> { free(lid); });

            System.out.println("Created new buffer of size = " + size + " with id = " + id);
        }

        private static void free(int id) {
            System.out.println("Freeing buffer with id = " + id);
            UNSAFE.freeMemory(payload[id]);
            payload[id] = 0;
        }

        public void put(int offset, byte b) {
            UNSAFE.putByte(payload[id] + offset, b);
        }

        public byte get(int offset) {
            return UNSAFE.getByte(payload[id] + offset);
        }
    }

    static MyBuffer buffer = new MyBuffer(1000);
    static {
        // Initialize buffer
        for (int i = 0; i < 1000; ++i) {
            buffer.put(i, (byte)42);
        }
    }

    static void test(int limit) {
        for (long j = 0; j < limit; j++) {
            for (int i = 0; i < 100; i++) {
                MyBuffer myBuffer = buffer;
                if (myBuffer == null) return;
                byte b = myBuffer.get(i);
                if (b != 42) {
                    throw new RuntimeException("Unexpected value = " + b + ". Buffer was garbage collected before reachabilityFence was reached!");
                }
                // Keep the buffer live while we read from it
                Reference.reachabilityFence(buffer);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Warmup to trigger compilation
        for (int i = 0; i < 20; i++) {
            test(100);
        }

        // Clear reference to 'buffer' and make sure it's garbage collected
        Thread gcThread = new Thread() {
            public void run() {
                try {
                    buffer = null;
                    System.out.println("Buffer set to null. Waiting for garbage collection.");
                    while (true) {
                        Thread.sleep(50);
                        System.gc();
                    }
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        gcThread.setDaemon(true);
        gcThread.start();

        test(10_000_000);

        // Wait for garbage collection
        while (MyBuffer.payload[0] != 0) { }
    }
}
