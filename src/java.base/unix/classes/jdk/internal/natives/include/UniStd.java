package jdk.internal.natives.include;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.LookupUtil.downcall;
import static jdk.internal.foreign.support.LookupUtil.downcallCapturingError;
import static jdk.internal.foreign.support.InvokeUtil.newInternalError;
import static jdk.internal.natives.CLayouts.C_INT;

public final class UniStd {

    private UniStd() {
    }

    private static final MethodHandle CLOSE = downcallCapturingError("close", C_INT, C_INT);
    private static final MethodHandle CLOSE_IGNORING_ERRNO = downcall("close", C_INT, C_INT);

    /**
     * {@snippet :
     * int close(int);
     * }
     */
    // https://man7.org/linux/man-pages/man2/close.2.html
    public static int close(MemorySegment errorSegment, int fd) {
        try {
            return (int) CLOSE.invokeExact(errorSegment, fd);
        } catch (Throwable ex$) {
            throw newInternalError(CLOSE, ex$);
        }
    }

    /**
     * {@snippet :
     * int close(int);
     * }
     */
    // https://man7.org/linux/man-pages/man2/close.2.html
    public static int close(int fd) {
        try {
            return (int) CLOSE_IGNORING_ERRNO.invokeExact(fd);
        } catch (Throwable ex$) {
            throw newInternalError(CLOSE_IGNORING_ERRNO, ex$);
        }
    }

}
