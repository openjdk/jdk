/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.proxy;

import java.io.*;
import java.net.*;
import java.security.PrivilegedAction;

import sun.rmi.runtime.Log;

/**
 * The HttpSendSocket class extends the java.net.Socket class
 * by enclosing the data output stream in, then extracting the input
 * stream from, an HTTP protocol transmission.
 *
 * NOTES:
 *
 * Since the length of the output request must be known before the
 * HTTP header can be completed, all of the output is buffered by
 * an HttpOutputStream object until either an attempt is made to
 * read from this socket, or the socket is explicitly closed.
 *
 * On the first read attempt to read from this socket, the buffered
 * output is sent to the destination as the body of an HTTP POST
 * request.  All reads will then acquire data from the body of
 * the response.  A subsequent attempt to write to this socket will
 * throw an IOException.
 */
class HttpSendSocket extends Socket implements RMISocketInfo {

    /** the host to connect to */
    protected String host;

    /** the port to connect to */
    protected int port;

    /** the URL to forward through */
    protected URL url;

    /** the object managing this connection through the URL */
    protected URLConnection conn = null;

    /** internal input stream for this socket */
    protected InputStream in = null;

    /** internal output stream for this socket */
    protected OutputStream out = null;

    /** the notifying input stream returned to users */
    protected HttpSendInputStream inNotifier;

    /** the notifying output stream returned to users */
    protected HttpSendOutputStream outNotifier;

    /**
     * Line separator string.  This is the value of the line.separator
     * property at the moment that the socket was created.
     */
    private String lineSeparator =
        java.security.AccessController.doPrivileged(
            (PrivilegedAction<String>) () -> System.getProperty("line.separator"));

    /**
     * Create a stream socket and connect it to the specified port on
     * the specified host.
     * @param host the host
     * @param port the port
     */
    public HttpSendSocket(String host, int port, URL url) throws IOException
    {
        super((SocketImpl)null);        // no underlying SocketImpl for this object

        if (RMIMasterSocketFactory.proxyLog.isLoggable(Log.VERBOSE)) {
            RMIMasterSocketFactory.proxyLog.log(Log.VERBOSE,
                "host = " + host + ", port = " + port + ", url = " + url);
        }

        this.host = host;
        this.port = port;
        this.url = url;

        inNotifier = new HttpSendInputStream(null, this);
        outNotifier = new HttpSendOutputStream(writeNotify(), this);
    }

    /**
     * Create a stream socket and connect it to the specified port on
     * the specified host.
     * @param host the host
     * @param port the port
     */
    public HttpSendSocket(String host, int port) throws IOException
    {
        this(host, port, new URL("http", host, port, "/"));
    }

    /**
     * Create a stream socket and connect it to the specified address on
     * the specified port.
     * @param address the address
     * @param port the port
     */
    public HttpSendSocket(InetAddress address, int port) throws IOException
    {
        this(address.getHostName(), port);
    }

    /**
     * Indicate that this socket is not reusable.
     */
    public boolean isReusable()
    {
        return false;
    }

    /**
     * Create a new socket connection to host (or proxy), and prepare to
     * send HTTP transmission.
     */
    public synchronized OutputStream writeNotify() throws IOException
    {
        if (conn != null) {
            throw new IOException("attempt to write on HttpSendSocket after " +
                                  "request has been sent");
        }

        conn = url.openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-type", "application/octet-stream");

        inNotifier.deactivate();
        in = null;

        return out = conn.getOutputStream();
    }

    /**
     * Send HTTP output transmission and prepare to receive response.
     */
    public synchronized InputStream readNotify() throws IOException
    {
        RMIMasterSocketFactory.proxyLog.log(Log.VERBOSE,
            "sending request and activating input stream");

        outNotifier.deactivate();
        out.close();
        out = null;

        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            RMIMasterSocketFactory.proxyLog.log(Log.BRIEF,
                "failed to get input stream, exception: ", e);

            throw new IOException("HTTP request failed");
        }

        /*
         * If an HTTP error response is returned, sometimes an IOException
         * is thrown, which is handled above, and other times it isn't, and
         * the error response body will be available for reading.
         * As a safety net to catch any such unexpected HTTP behavior, we
         * verify that the content type of the response is what the
         * HttpOutputStream generates: "application/octet-stream".
         * (Servers' error responses will generally be "text/html".)
         * Any error response body is printed to the log.
         */
        String contentType = conn.getContentType();
        if (contentType == null ||
            !conn.getContentType().equals("application/octet-stream"))
        {
            if (RMIMasterSocketFactory.proxyLog.isLoggable(Log.BRIEF)) {
                String message;
                if (contentType == null) {
                    message = "missing content type in response" +
                        lineSeparator;
                } else {
                    message = "invalid content type in response: " +
                        contentType + lineSeparator;
                }

                message += "HttpSendSocket.readNotify: response body: ";
                try {
                    BufferedReader din = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = din.readLine()) != null)
                        message += line + lineSeparator;
                } catch (IOException e) {
                }
                RMIMasterSocketFactory.proxyLog.log(Log.BRIEF, message);
            }

            throw new IOException("HTTP request failed");
        }

        return in;
    }

    /**
     * Get the address to which the socket is connected.
     */
    public InetAddress getInetAddress()
    {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;        // null if couldn't resolve destination host
        }
    }

    /**
     * Get the local address to which the socket is bound.
     */
    public InetAddress getLocalAddress()
    {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return null;        // null if couldn't determine local host
        }
    }

    /**
     * Get the remote port to which the socket is connected.
     */
    public int getPort()
    {
        return port;
    }

    /**
     * Get the local port to which the socket is connected.
     */
    public int getLocalPort()
    {
        return -1;      // request not applicable to this socket type
    }

    /**
     * Get an InputStream for this socket.
     */
    public InputStream getInputStream() throws IOException
    {
        return inNotifier;
    }

    /**
     * Get an OutputStream for this socket.
     */
    public OutputStream getOutputStream() throws IOException
    {
        return outNotifier;
    }

    /**
     * Enable/disable TCP_NODELAY.
     * This operation has no effect for an HttpSendSocket.
     */
    public void setTcpNoDelay(boolean on) throws SocketException
    {
    }

    /**
     * Retrieve whether TCP_NODELAY is enabled.
     */
    public boolean getTcpNoDelay() throws SocketException
    {
        return false;   // imply option is disabled
    }

    /**
     * Enable/disable SO_LINGER with the specified linger time.
     * This operation has no effect for an HttpSendSocket.
     */
    public void setSoLinger(boolean on, int val) throws SocketException
    {
    }

    /**
     * Retrive setting for SO_LINGER.
     */
    public int getSoLinger() throws SocketException
    {
        return -1;      // imply option is disabled
    }

    /**
     * Enable/disable SO_TIMEOUT with the specified timeout
     * This operation has no effect for an HttpSendSocket.
     */
    public synchronized void setSoTimeout(int timeout) throws SocketException
    {
    }

    /**
     * Retrive setting for SO_TIMEOUT.
     */
    public synchronized int getSoTimeout() throws SocketException
    {
        return 0;       // imply option is disabled
    }

    /**
     * Close the socket.
     */
    public synchronized void close() throws IOException
    {
        if (out != null) // push out transmission if not done
            out.close();
    }

    /**
     * Return string representation of this pseudo-socket.
     */
    public String toString()
    {
        return "HttpSendSocket[host=" + host +
               ",port=" + port +
               ",url=" + url + "]";
    }
}
