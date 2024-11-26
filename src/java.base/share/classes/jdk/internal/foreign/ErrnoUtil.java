package jdk.internal.foreign;

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public final class ErrnoUtil {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup ARGUS = MethodHandles.lookup();
    private static final ErrnoTerminatingThreadLocal TL = new ErrnoTerminatingThreadLocal();

    private static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
        private static final VarHandle ERRNO_HANDLE =
            CAPTURE_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));

    private static final MethodHandle ACQUIRE_MH =
            MhUtil.findStatic(ARGUS, "acquire",
                    MethodType.methodType(MemorySegment.class));
    private static final MethodHandle RETURN_FILTER_MH =
            MhUtil.findStatic(ARGUS, "returnFilter",
                    MethodType.methodType(ResultErrno.class, int.class));

    private ErrnoUtil() {}

    // Used reflectively via ACQUIRE_MH
    private static MemorySegment acquire() {
        MemorySegment segment = TL.get();
        if (segment == null) {
            TL.set(segment = malloc());
        }
        return segment;
    }

    // Used reflectively via RETURN_FILTER_MH
    private static ResultErrno returnFilter(int result) {
        if (result >= 0) {
            return new ResultErrno(result, 0);
        } else {
            MemorySegment capturedState = acquire();
            final int errno = (int) ERRNO_HANDLE.get(capturedState, 0L);
            return new ResultErrno(result, errno);
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment malloc() {
        long address = UNSAFE.allocateMemory(CAPTURE_STATE_LAYOUT.byteSize());
        return MemorySegment.ofAddress(address).reinterpret(CAPTURE_STATE_LAYOUT.byteSize());
    }

    private static void free(MemorySegment segment) {
        UNSAFE.freeMemory(segment.address());
    }

    private static final class ErrnoTerminatingThreadLocal extends TerminatingThreadLocal<MemorySegment> {

        @Override
        protected void threadTerminated(MemorySegment value) {
            free(value);
        }
    }

    /**
     * {@return a new MethodHandle that returns a {@linkplain ResultErrno} record}
     *
     * @param target that returns an {@code int} and has an errno MemorySegment as
     *               the first parameter
     * @throws IllegalArgumentException if the provided {@code target}'s return type
     *         is not {@code int}
     * @throws IllegalArgumentException if the provided {@code target}'s first parameter
     *         type is not {@linkplain MemorySegment}
     */
    public static MethodHandle adapt(MethodHandle target) {
        if (target.type().returnType() != int.class) {
            throw new IllegalArgumentException("The provided target " + target
                    + " does not return an int");
        }
        if (target.type().parameterType(0) != MemorySegment.class) {
            throw new IllegalArgumentException("The provided target " + target
                    + " does not have a MemorySegment as the first parameter");
        }
        // (MemorySegment, C*)int -> (C*)int
        target = MethodHandles.collectArguments(target, 0, ACQUIRE_MH);
        // (C*)int -> (C*)ResultErrno
        target = MethodHandles.filterReturnValue(target, RETURN_FILTER_MH);
        return target;
    }

}
