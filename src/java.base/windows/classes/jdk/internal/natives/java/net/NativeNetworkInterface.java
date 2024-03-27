package jdk.internal.natives.java.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

public final class NativeNetworkInterface {

    private NativeNetworkInterface() {
    }

    public static NetworkInterface getByName(String name) throws SocketException {
        throw unsupported();
    }

    public static NetworkInterface getByIndex(int index) throws SocketException {
        throw unsupported();
    }

    public static NetworkInterface getByInetAddress(InetAddress address) throws SocketException {
        throw unsupported();
    }

    public static NetworkInterface[] getAll() throws SocketException {
        throw unsupported();
    }

    public static boolean boundInetAddress(InetAddress address) throws SocketException {
        throw unsupported();
    }

    public static boolean isUp(String name, int ind) throws SocketException {
        throw unsupported();
    }

    public static boolean isLoopback(String name, int ind) throws SocketException {
        throw unsupported();
    }

    public static boolean supportsMulticast(String name, int ind) throws SocketException {
        throw unsupported();
    }

    public static boolean isP2P(String name, int ind) throws SocketException {
        throw unsupported();
    }

    public static byte[] getMacAddr(byte[] inAddr, String name, int ind) throws SocketException {
        throw unsupported();
    }

    public static int getMTU(String name, int ind) throws SocketException {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Implement me!");
    }

}
