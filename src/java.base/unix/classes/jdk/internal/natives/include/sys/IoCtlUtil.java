package jdk.internal.natives.include.sys;

import jdk.internal.foreign.support.LookupUtil;
import jdk.internal.natives.HasSegment;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.InvokeUtil.newInternalError;
import static jdk.internal.natives.CLayouts.*;

// Generated partly via: jextract --source -t jdk.internal.natives.include.sys \
//                       -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/ioctl.h \
//                       /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/ioctl.h
public final class IoCtlUtil {

    private IoCtlUtil() {
    }

    private static final FunctionDescriptor IOCTL_FUNC = FunctionDescriptor.of(C_INT,C_INT, C_LONG_LONG);

    private static final MethodHandle IOCTL_IGNORING_ERRNO = LookupUtil.downcallVararg("ioctl", IOCTL_FUNC);
    // private static final MethodHandle IOCTL = LookupUtil.downcallVararg("ioctl", IOCTL_FUNC);


/*
    private static final MethodHandle IOCTL_NO_ARG = downcallCapturingError("close", C_INT, C_INT, C_LONG);
    private static final MethodHandle IOCTL_NO_ARG_IGNORING_ERRNO = downcall("close", C_INT, C_INT, C_LONG);
*/


    public static int ioctl(int fd, long request, HasSegment hasSegment) {
        try {
            return (int) IOCTL_IGNORING_ERRNO.invokeExact(fd, request, new MemorySegment[]{hasSegment.segment()});
        } catch (Throwable ex$) {
            throw newInternalError(IOCTL_IGNORING_ERRNO, ex$);
        }
    }

    /**
     * {@snippet :
     * int ioctl(int, unsigned long,...);
     * }
     */
    // https://man7.org/linux/man-pages/man2/ioctl.2.html
    public static int ioctl(int fd, long request, Object... x2) {
        try {
            return (int) IOCTL_IGNORING_ERRNO.invokeExact(fd, request, x2);
        } catch (Throwable ex$) {
            throw newInternalError(IOCTL_IGNORING_ERRNO, ex$);
        }
    }

    /**
     * {@snippet :
     * int ioctl(int, unsigned long,...);
     * }
     */
    // https://man7.org/linux/man-pages/man2/ioctl.2.html
    public static int ioctl(MemorySegment errorSegment, int fd, long request, Object... x2) {
        throw new UnsupportedOperationException();
    }

}
