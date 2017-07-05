/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package java.net;

import java.io.IOException;
import java.io.FileDescriptor;
import sun.misc.SharedSecrets;
import sun.misc.JavaIOFileDescriptorAccess;

/**
 * This class defines the plain DatagramSocketImpl that is used on
 * Windows platforms greater than or equal to Windows Vista. These
 * platforms have a dual layer TCP/IP stack and can handle both IPv4
 * and IPV6 through a single file descriptor.
 * <p>
 * Note: Multicasting on a dual layer TCP/IP stack is always done with
 * TwoStacksPlainDatagramSocketImpl. This is to overcome the lack
 * of behavior defined for multicasting over a dual layer socket by the RFC.
 *
 * @author Chris Hegarty
 */

class DualStackPlainDatagramSocketImpl extends AbstractPlainDatagramSocketImpl
{
    static JavaIOFileDescriptorAccess fdAccess = SharedSecrets.getJavaIOFileDescriptorAccess();

    protected void datagramSocketCreate() throws SocketException {
        if (fd == null)
            throw new SocketException("Socket closed");

        int newfd = socketCreate(false /* v6Only */);

        fdAccess.set(fd, newfd);
    }

    protected synchronized void bind0(int lport, InetAddress laddr)
        throws SocketException {
        int nativefd = checkAndReturnNativeFD();

        if (laddr == null)
            throw new NullPointerException("argument address");

        socketBind(nativefd, laddr, lport);
        if (lport == 0) {
            localPort = socketLocalPort(nativefd);
        } else {
            localPort = lport;
        }
    }

    protected synchronized int peek(InetAddress address) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (address == null)
            throw new NullPointerException("Null address in peek()");

        // Use peekData()
        DatagramPacket peekPacket = new DatagramPacket(new byte[1], 1);
        int peekPort = peekData(peekPacket);
        address = peekPacket.getAddress();
        return peekPort;
    }

    protected synchronized int peekData(DatagramPacket p) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (p == null)
            throw new NullPointerException("packet");
        if (p.getData() == null)
            throw new NullPointerException("packet buffer");

        return socketReceiveOrPeekData(nativefd, p, timeout, connected, true /*peek*/);
    }

    protected synchronized void receive0(DatagramPacket p) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (p == null)
            throw new NullPointerException("packet");
        if (p.getData() == null)
            throw new NullPointerException("packet buffer");

        socketReceiveOrPeekData(nativefd, p, timeout, connected, false /*receive*/);
    }

    protected void send(DatagramPacket p) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (p == null)
            throw new NullPointerException("null packet");

        if (p.getAddress() == null ||p.getData() ==null)
            throw new NullPointerException("null address || null buffer");

        socketSend(nativefd, p.getData(), p.getOffset(), p.getLength(),
                   p.getAddress(), p.getPort(), connected);
    }

    protected void connect0(InetAddress address, int port) throws SocketException {
        int nativefd = checkAndReturnNativeFD();

        if (address == null)
            throw new NullPointerException("address");

        socketConnect(nativefd, address, port);
    }

    protected void disconnect0(int family /*unused*/) {
        if (fd == null || !fd.valid())
            return;   // disconnect doesn't throw any exceptions

        socketDisconnect(fdAccess.get(fd));
    }

    protected void datagramSocketClose() {
        if (fd == null || !fd.valid())
            return;   // close doesn't throw any exceptions

        socketClose(fdAccess.get(fd));
        fdAccess.set(fd, -1);
    }

    protected void socketSetOption(int opt, Object val) throws SocketException {
        int nativefd = checkAndReturnNativeFD();

        int optionValue = 0;

        switch(opt) {
            case IP_TOS :
            case SO_RCVBUF :
            case SO_SNDBUF :
                optionValue = ((Integer)val).intValue();
                break;
            case SO_REUSEADDR :
            case SO_BROADCAST :
                optionValue = ((Boolean)val).booleanValue() ? 1 : 0;
                break;
            default: /* shouldn't get here */
                throw new SocketException("Option not supported");
        }

        socketSetIntOption(nativefd, opt, optionValue);
    }

    protected Object socketGetOption(int opt) throws SocketException {
        int nativefd = checkAndReturnNativeFD();

         // SO_BINDADDR is not a socket option.
        if (opt == SO_BINDADDR) {
            return socketLocalAddress(nativefd);
        }

        int value = socketGetIntOption(nativefd, opt);
        Object returnValue = null;

        switch (opt) {
            case SO_REUSEADDR :
            case SO_BROADCAST :
                returnValue =  (value == 0) ? Boolean.FALSE : Boolean.TRUE;
                break;
            case IP_TOS :
            case SO_RCVBUF :
            case SO_SNDBUF :
                returnValue = new Integer(value);
                break;
            default: /* shouldn't get here */
                throw new SocketException("Option not supported");
        }

        return returnValue;
    }

    /* Multicast specific methods.
     * Multicasting on a dual layer TCP/IP stack is always done with
     * TwoStacksPlainDatagramSocketImpl. This is to overcome the lack
     * of behavior defined for multicasting over a dual layer socket by the RFC.
     */
    protected void join(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException {
        throw new IOException("Method not implemented!");
    }

    protected void leave(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException {
        throw new IOException("Method not implemented!");
    }

    protected void setTimeToLive(int ttl) throws IOException {
        throw new IOException("Method not implemented!");
    }

    protected int getTimeToLive() throws IOException {
        throw new IOException("Method not implemented!");
    }


    protected void setTTL(byte ttl) throws IOException {
        throw new IOException("Method not implemented!");
    }

    protected byte getTTL() throws IOException {
        throw new IOException("Method not implemented!");
    }
    /* END Multicast specific methods */

    private int checkAndReturnNativeFD() throws SocketException {
        if (fd == null || !fd.valid())
            throw new SocketException("Socket closed");

        return fdAccess.get(fd);
    }

    /* Native methods */

    private static native void initIDs();

    private static native int socketCreate(boolean v6Only);

    private static native void socketBind(int fd, InetAddress localAddress, int localport)
        throws SocketException;

    private static native void socketConnect(int fd, InetAddress address, int port)
        throws SocketException;

    private static native void socketDisconnect(int fd);

    private static native void socketClose(int fd);

    private static native int socketLocalPort(int fd) throws SocketException;

    private static native Object socketLocalAddress(int fd) throws SocketException;

    private static native int socketReceiveOrPeekData(int fd, DatagramPacket packet,
        int timeout, boolean connected, boolean peek) throws IOException;

    private static native void socketSend(int fd, byte[] data, int offset, int length,
        InetAddress address, int port, boolean connected) throws IOException;

    private static native void socketSetIntOption(int fd, int cmd,
        int optionValue) throws SocketException;

    private static native int socketGetIntOption(int fd, int cmd) throws SocketException;
}
