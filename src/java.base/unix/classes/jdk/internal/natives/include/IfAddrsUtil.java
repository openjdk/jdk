package jdk.internal.natives.include;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.InvokeUtil.newInternalError;
import static jdk.internal.foreign.support.LookupUtil.downcall;
import static jdk.internal.natives.CLayouts.C_INT;
import static jdk.internal.natives.CLayouts.C_POINTER;

public final class IfAddrsUtil {

    private IfAddrsUtil() {
    }

    private static final MethodHandle GET_IF_ADDRS = downcall("getifaddrs", C_INT, C_POINTER);
    private static final MethodHandle FREE_IF_ADDRS = downcall("freeifaddrs", C_INT, C_POINTER);

    /**
     * {@snippet :
     * int getifaddrs(struct ifaddrs**);
     * }
     */
    // https://man7.org/linux/man-pages/man3/getifaddrs.3.html
    public static int getifaddrs(MemorySegment ifaddrs) {
        try {
            return (int) GET_IF_ADDRS.invokeExact(ifaddrs);
        } catch (Throwable ex$) {
            throw newInternalError(GET_IF_ADDRS, ex$);
        }
    }

    /**
     * {@snippet :
     * void freeifaddrs(struct ifaddrs*);
     * }
     */
    // https://man7.org/linux/man-pages/man3/getifaddrs.3.html
    public static int freeifaddrs(IfAddrs head) {
        try {
            return (int) FREE_IF_ADDRS.invokeExact(head.segment());
        } catch (Throwable ex$) {
            throw newInternalError(FREE_IF_ADDRS, ex$);
        }
    }

}
