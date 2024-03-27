package jdk.internal.natives.include.sys;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.LookupUtil.*;
import static jdk.internal.foreign.support.InvokeUtil.newInternalError;
import static jdk.internal.natives.CLayouts.C_INT;

// Generated partly via: jextract --source -t jdk.internal.natives.include.sys -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h
public final class SocketUtil {

    private SocketUtil() {
    }

    // AF

    /**
     * {@snippet :
     * #define AF_UNSPEC 0
     *}
     */
    public static int AF_UNSPEC = 0;

    /**
     * {@snippet :
     * #define AF_UNIX 1
     *}
     */
    public static int AF_UNIX = 1;

    /**
     * {@snippet :
     * #define AF_INET 2
     *}
     */
    public static int AF_INET = 2;

    /**
     * {@snippet :
     * #define AF_INET6 30
     * }
     */
    public static int AF_INET6 = 30;


    // SOCK

    /**
     * {@snippet :
     * #define SOCK_STREAM 1
     * }
     */
    public static int SOCK_STREAM = 1;

    /**
     * {@snippet :
     * #define SOCK_DGRAM 2
     * }
     */
    public static int SOCK_DGRAM = 2;

    /**
     * {@snippet :
     * #define SOCK_RAW 3
     * }
     */
    public static int SOCK_RAW = 3;

    /**
     * {@snippet :
     * #define SOCK_RDM 4
     * }
     */
    public static int SOCK_RDM = 4;

    /**
     * {@snippet :
     * #define SOCK_SEQPACKET 5
     * }
     */
    public static int SOCK_SEQPACKET= 5;

    private static final MethodHandle SOCKET = downcallCapturingError("socket", C_INT, C_INT, C_INT, C_INT);

    private static final MethodHandle SOCKET_IGNORING_ERRNO = downcall("socket", C_INT, C_INT, C_INT, C_INT);

    /**
     * {@snippet :
     * int socket(int, int, int);
     *}
     */
    // https://man7.org/linux/man-pages/man2/socket.2.html
    public static ErrNo.Fd socket(MemorySegment errorSegment, int domain, int type, int protocol) {
        try {
            int result = (int) SOCKET.invokeExact(errorSegment, domain, type, protocol);
            return ErrNo.Fd.of(result, errorSegment);
        } catch (Throwable ex$) {
            throw newInternalError(SOCKET, ex$);
        }
    }

    /**
     * {@snippet :
     * int socket(int, int, int);
     *}
     */
    // https://man7.org/linux/man-pages/man2/socket.2.html
    public static int socket(int domain, int type, int protocol) {
        try {
            return (int) SOCKET_IGNORING_ERRNO.invokeExact(domain, type, protocol);
        } catch (Throwable ex$) {
            throw newInternalError(SOCKET, ex$);
        }
    }

}
