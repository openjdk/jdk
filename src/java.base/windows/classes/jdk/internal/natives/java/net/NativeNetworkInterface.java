package jdk.internal.natives.java.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

public final class NativeNetworkInterface {

    private NativeNetworkInterface() {}

    static NetworkInterface getByName(String name) throws SocketException {
        return null;
    }

    static NetworkInterface getByIndex(int index) throws SocketException {
        return null;
    }

    static NetworkInterface getByInetAddress(InetAddress addr) throws SocketException {
        return null;
    }

    static NetworkInterface[] getAll() throws SocketException {
        return null;
    }

    static boolean boundInetAddress(InetAddress addr) throws SocketException {
        return false;
    }

    static boolean isUp(String name, int ind) throws SocketException {
        return false;
    }

    static boolean isLoopback(String name, int ind) throws SocketException {
        return false;
    }

    static boolean supportsMulticast(String name, int ind) throws SocketException {
        return false;
    }

    static boolean isP2P(String name, int ind) throws SocketException {
        return false;
    }

    static byte[] getMacAddr(byte[] inAddr, String name, int ind) throws SocketException {
        return null;
    }

    static int getMTU(String name, int ind) throws SocketException {
        return 0;
    }


}
