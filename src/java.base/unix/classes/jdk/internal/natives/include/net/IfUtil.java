package jdk.internal.natives.include.net;

import jdk.internal.foreign.support.LookupUtil;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.InvokeUtil.newInternalError;
import static jdk.internal.natives.CLayouts.C_INT;
import static jdk.internal.natives.CLayouts.C_STRING;

public final class IfUtil {

    private IfUtil() {}

    /**
     * {@snippet :
     * #define IFF_UP 1
     * }
     */
    public static int IFF_UP = 1;

    /**
     * {@snippet :
     * #define IFF_BROADCAST 2
     * }
     */
    public static int IFF_BROADCAST= 2;

    /**
     * {@snippet :
     * #define IFF_DEBUG 4
     * }
     */
    public static int IFF_DEBUG = 4;

    /**
     * {@snippet :
     * #define IFF_LOOPBACK 8
     * }
     */
    public static int IFF_LOOPBACK = 8;

    /**
     * {@snippet :
     * #define IFF_POINTOPOINT 16
     * }
     */
    public static int IFF_POINTOPOINT = 16;

    /**
     * {@snippet :
     * #define IFF_NOTRAILERS 32
     * }
     */
    public static int IFF_NOTRAILERS = 32;

    /**
     * {@snippet :
     * #define IFF_RUNNING 64
     * }
     */
    public static int IFF_RUNNING = 64;

    /**
     * {@snippet :
     * #define IFF_NOARP 128
     * }
     */
    public static int IFF_NOARP = 128;

    /**
     * {@snippet :
     * #define IFF_PROMISC 256
     * }
     */
    public static int IFF_PROMISC = 256;

    /**
     * {@snippet :
     * #define IFF_ALLMULTI 512
     * }
     */
    public static int IFF_ALLMULTI = 512;

    /**
     * {@snippet :
     * #define IFF_OACTIVE 1024
     * }
     */
    public static int IFF_OACTIVE= 1024;

    /**
     * {@snippet :
     * #define IFF_SIMPLEX 2048
     * }
     */
    public static int IFF_SIMPLEX = 2048;

    /**
     * {@snippet :
     * #define IFF_LINK0 4096
     * }
     */
    public static int IFF_LINK0 = 4096;

    /**
     * {@snippet :
     * #define IFF_LINK1 8192
     * }
     */
    public static int IFF_LINK1 = 8192;

    /**
     * {@snippet :
     * #define IFF_LINK2 16384
     * }
     */
    public static int IFF_LINK2 = 16384;

    /**
     * {@snippet :
     * #define IFF_MULTICAST 32768
     * }
     */
    public static int IFF_MULTICAST = 32768;

    private static final MethodHandle IF_NAME_TO_INDEX = LookupUtil.downcall("if_nametoindex", C_INT, C_STRING);

    public static int  if_nametoindex(MemorySegment segment) {
        try {
            return (int) IF_NAME_TO_INDEX.invokeExact(segment);
        } catch (Throwable ex$) {
            throw newInternalError(IF_NAME_TO_INDEX, ex$);
        }
    }

}
