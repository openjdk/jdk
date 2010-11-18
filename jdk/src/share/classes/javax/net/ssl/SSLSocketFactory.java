/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package javax.net.ssl;

import java.net.*;
import javax.net.SocketFactory;
import java.io.IOException;
import java.security.*;
import java.util.Locale;

import sun.security.action.GetPropertyAction;

/**
 * <code>SSLSocketFactory</code>s create <code>SSLSocket</code>s.
 *
 * @since 1.4
 * @see SSLSocket
 * @author David Brownell
 */
public abstract class SSLSocketFactory extends SocketFactory
{
    private static SSLSocketFactory theFactory;

    private static boolean propertyChecked;

    static final boolean DEBUG;

    static {
        String s = java.security.AccessController.doPrivileged(
            new GetPropertyAction("javax.net.debug", "")).toLowerCase(
                                                            Locale.ENGLISH);
        DEBUG = s.contains("all") || s.contains("ssl");
    }

    private static void log(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

    /**
     * Constructor is used only by subclasses.
     */
    public SSLSocketFactory() {
    }

    /**
     * Returns the default SSL socket factory.
     *
     * <p>The first time this method is called, the security property
     * "ssl.SocketFactory.provider" is examined. If it is non-null, a class by
     * that name is loaded and instantiated. If that is successful and the
     * object is an instance of SSLSocketFactory, it is made the default SSL
     * socket factory.
     *
     * <p>Otherwise, this method returns
     * <code>SSLContext.getDefault().getSocketFactory()</code>. If that
     * call fails, an inoperative factory is returned.
     *
     * @return the default <code>SocketFactory</code>
     * @see SSLContext#getDefault
     */
    public static synchronized SocketFactory getDefault() {
        if (theFactory != null) {
            return theFactory;
        }

        if (propertyChecked == false) {
            propertyChecked = true;
            String clsName = getSecurityProperty("ssl.SocketFactory.provider");
            if (clsName != null) {
                log("setting up default SSLSocketFactory");
                try {
                    Class cls = null;
                    try {
                        cls = Class.forName(clsName);
                    } catch (ClassNotFoundException e) {
                        ClassLoader cl = ClassLoader.getSystemClassLoader();
                        if (cl != null) {
                            cls = cl.loadClass(clsName);
                        }
                    }
                    log("class " + clsName + " is loaded");
                    SSLSocketFactory fac = (SSLSocketFactory)cls.newInstance();
                    log("instantiated an instance of class " + clsName);
                    theFactory = fac;
                    return fac;
                } catch (Exception e) {
                    log("SSLSocketFactory instantiation failed: " + e.toString());
                    theFactory = new DefaultSSLSocketFactory(e);
                    return theFactory;
                }
            }
        }

        try {
            return SSLContext.getDefault().getSocketFactory();
        } catch (NoSuchAlgorithmException e) {
            return new DefaultSSLSocketFactory(e);
        }
    }

    static String getSecurityProperty(final String name) {
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                String s = java.security.Security.getProperty(name);
                if (s != null) {
                    s = s.trim();
                    if (s.length() == 0) {
                        s = null;
                    }
                }
                return s;
            }
        });
    }

    /**
     * Returns the list of cipher suites which are enabled by default.
     * Unless a different list is enabled, handshaking on an SSL connection
     * will use one of these cipher suites.  The minimum quality of service
     * for these defaults requires confidentiality protection and server
     * authentication (that is, no anonymous cipher suites).
     *
     * @see #getSupportedCipherSuites()
     * @return array of the cipher suites enabled by default
     */
    public abstract String [] getDefaultCipherSuites();

    /**
     * Returns the names of the cipher suites which could be enabled for use
     * on an SSL connection.  Normally, only a subset of these will actually
     * be enabled by default, since this list may include cipher suites which
     * do not meet quality of service requirements for those defaults.  Such
     * cipher suites are useful in specialized applications.
     *
     * @see #getDefaultCipherSuites()
     * @return an array of cipher suite names
     */
    public abstract String [] getSupportedCipherSuites();

    /**
     * Returns a socket layered over an existing socket connected to the named
     * host, at the given port.  This constructor can be used when tunneling SSL
     * through a proxy or when negotiating the use of SSL over an existing
     * socket. The host and port refer to the logical peer destination.
     * This socket is configured using the socket options established for
     * this factory.
     *
     * @param s the existing socket
     * @param host the server host
     * @param port the server port
     * @param autoClose close the underlying socket when this socket is closed
     * @return a socket connected to the specified host and port
     * @throws IOException if an I/O error occurs when creating the socket
     * @throws NullPointerException if the parameter s is null
     */
    public abstract Socket createSocket(Socket s, String host,
                                        int port, boolean autoClose)
    throws IOException;
}


// file private
class DefaultSSLSocketFactory extends SSLSocketFactory
{
    private Exception reason;

    DefaultSSLSocketFactory(Exception reason) {
        this.reason = reason;
    }

    private Socket throwException() throws SocketException {
        throw (SocketException)
            new SocketException(reason.toString()).initCause(reason);
    }

    public Socket createSocket()
    throws IOException
    {
        return throwException();
    }

    public Socket createSocket(String host, int port)
    throws IOException
    {
        return throwException();
    }

    public Socket createSocket(Socket s, String host,
                                int port, boolean autoClose)
    throws IOException
    {
        return throwException();
    }

    public Socket createSocket(InetAddress address, int port)
    throws IOException
    {
        return throwException();
    }

    public Socket createSocket(String host, int port,
        InetAddress clientAddress, int clientPort)
    throws IOException
    {
        return throwException();
    }

    public Socket createSocket(InetAddress address, int port,
        InetAddress clientAddress, int clientPort)
    throws IOException
    {
        return throwException();
    }

    public String [] getDefaultCipherSuites() {
        return new String[0];
    }

    public String [] getSupportedCipherSuites() {
        return new String[0];
    }
}
