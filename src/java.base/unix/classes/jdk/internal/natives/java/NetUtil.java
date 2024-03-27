package jdk.internal.natives.java;

public final class NetUtil {

    private NetUtil() {
    }

    // Todo: Implement this method properly. See net_util.c
    public static boolean ipv6_available() {
        return true;
    }

}
