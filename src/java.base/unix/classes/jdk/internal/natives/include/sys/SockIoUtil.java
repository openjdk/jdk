package jdk.internal.natives.include.sys;

import jdk.internal.natives.include.IfConf;
import jdk.internal.natives.include.IfReq;

import java.lang.foreign.MemoryLayout;

/*
  Generated partly via:
  jextract --source -t jdk.internal.natives.include.sys \
           -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/net/if.h \
           /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/sockio.h

 */
public final class SockIoUtil {

    private SockIoUtil() {
    }

    /**
     * {@snippet :
     * #define IOCPARM_MASK 8191
     * }
     */
    public static int IOCPARM_MASK = 8191;

    /**
     * {@snippet :
     * #define IOC_VOID 536870912
     * }
     */
    public static int IOC_VOID = 536870912;

    /**
     * {@snippet :
     * #define IOC_OUT 1073741824
     * }
     */
    public static int IOC_OUT = 1073741824;

    /**
     * {@snippet :
     * #define IOC_IN 2147483648
     * }
     */
    public static int IOC_IN = (int)2147483648L;

    /**
     * {@snippet :
     * #define IOC_INOUT 3221225472
     * }
     */
    public static int IOC_INOUT = (int)3221225472L;

    // Macros

    public static int SIOCGIFFLAGS   = _IOWR('i', 17, IfReq.LAYOUT);    /* get ifnet flags */
    public static int SIOCGIFBRDADDR = _IOWR('i', 35, IfReq.LAYOUT);    /* get broadcast addr */
    public static int SIOCGIFCONF    = _IOWR('i', 36, IfConf.LAYOUT);   /* get ifnet list */
    public static int SIOCGIFNETMASK = _IOWR('i', 37, IfConf.LAYOUT);   /* get net addr mask */
    public static int SIOCGIFMTU     = _IOWR('i', 51, IfReq.LAYOUT);    /* get IF mtu */


    private static int _IOWR(char type, int number, MemoryLayout data_type) {
        return _IOC(IOC_INOUT, (byte) type, (byte) number, Math.toIntExact(data_type.byteSize()));
    }

    private static int _IOC(int inout, byte group, byte num, int len) {
        return inout | (len & IOCPARM_MASK) << 16 | (group << 8) | num;
    }

}
