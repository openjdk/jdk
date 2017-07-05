/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.protocol.mailto;

import java.net.URL;
import java.net.InetAddress;
import java.net.SocketPermission;
import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.security.Permission;
import sun.net.www.*;
import sun.net.smtp.SmtpClient;
import sun.net.www.ParseUtil;


/**
 * Handle mailto URLs. To send mail using a mailto URLConnection,
 * call <code>getOutputStream</code>, write the message to the output
 * stream, and close it.
 *
 */
public class MailToURLConnection extends URLConnection {
    InputStream is = null;
    OutputStream os = null;

    SmtpClient client;
    Permission permission;
    private int connectTimeout = -1;
    private int readTimeout = -1;

    MailToURLConnection(URL u) {
        super(u);

        MessageHeader props = new MessageHeader();
        props.add("content-type", "text/html");
        setProperties(props);
    }

    /**
     * Get the user's full email address - stolen from
     * HotJavaApplet.getMailAddress().
     */
    String getFromAddress() {
        String str = System.getProperty("user.fromaddr");
        if (str == null) {
            str = System.getProperty("user.name");
            if (str != null) {
                String host = System.getProperty("mail.host");
                if (host == null) {
                    try {
                        host = InetAddress.getLocalHost().getHostName();
                    } catch (java.net.UnknownHostException e) {
                    }
                }
                str += "@" + host;
            } else {
                str = "";
            }
        }
        return str;
    }

    public void connect() throws IOException {
        System.err.println("connect. Timeout = " + connectTimeout);
        client = new SmtpClient(connectTimeout);
        client.setReadTimeout(readTimeout);
    }

    public synchronized OutputStream getOutputStream() throws IOException {
        if (os != null) {
            return os;
        } else if (is != null) {
            throw new IOException("Cannot write output after reading input.");
        }
        connect();

        String to = ParseUtil.decode(url.getPath());
        client.from(getFromAddress());
        client.to(to);

        os = client.startMessage();
        return os;
    }

    public Permission getPermission() throws IOException {
        if (permission == null) {
            connect();
            String host = client.getMailHost() + ":" + 25;
            permission = new SocketPermission(host, "connect");
        }
        return permission;
    }

    public void setConnectTimeout(int timeout) {
        if (timeout < 0)
            throw new IllegalArgumentException("timeouts can't be negative");
        connectTimeout = timeout;
    }

    public int getConnectTimeout() {
        return (connectTimeout < 0 ? 0 : connectTimeout);
    }

    public void setReadTimeout(int timeout) {
        if (timeout < 0)
            throw new IllegalArgumentException("timeouts can't be negative");
        readTimeout = timeout;
    }

    public int getReadTimeout() {
        return readTimeout < 0 ? 0 : readTimeout;
    }
}
