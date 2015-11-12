/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.client.p2p;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.security.*;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.util.*;

/**
 * This represents a "connection" to the simple HTTP-based provider.
 *
 * @author Anil Vijendran (akv@eng.sun.com)
 * @author Rajiv Mordani (rajiv.mordani@sun.com)
 * @author Manveen Kaur (manveen.kaur@sun.com)
 *
 */
class HttpSOAPConnection extends SOAPConnection {

    public static final String vmVendor = SAAJUtil.getSystemProperty("java.vendor.url");
    private static final String ibmVmVendor = "http://www.ibm.com/";
    private static final boolean isIBMVM = ibmVmVendor.equals(vmVendor) ? true : false;
    private static final String JAXM_URLENDPOINT="javax.xml.messaging.URLEndpoint";

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.HTTP_CONN_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.client.p2p.LocalStrings");


    MessageFactory messageFactory = null;

    boolean closed = false;

    public HttpSOAPConnection() throws SOAPException {

        try {
            messageFactory = MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
        } catch (NoSuchMethodError ex) {
            //fallback to default SOAP 1.1 in this case for backward compatibility
            messageFactory = MessageFactory.newInstance();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "SAAJ0001.p2p.cannot.create.msg.factory", ex);
            throw new SOAPExceptionImpl("Unable to create message factory", ex);
        }
    }

    public void close() throws SOAPException {
        if (closed) {
            log.severe("SAAJ0002.p2p.close.already.closed.conn");
            throw new SOAPExceptionImpl("Connection already closed");
        }

        messageFactory = null;
        closed = true;
    }

   public SOAPMessage call(SOAPMessage message, Object endPoint)
        throws SOAPException {
        if (closed) {
            log.severe("SAAJ0003.p2p.call.already.closed.conn");
            throw new SOAPExceptionImpl("Connection is closed");
        }

        Class<?> urlEndpointClass = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            if (loader != null) {
                urlEndpointClass = loader.loadClass(JAXM_URLENDPOINT);
            } else {
                urlEndpointClass = Class.forName(JAXM_URLENDPOINT);
            }
        } catch (ClassNotFoundException ex) {
            //Do nothing. URLEndpoint is available only when JAXM is there.
            if (log.isLoggable(Level.FINEST))
                log.finest("SAAJ0090.p2p.endpoint.available.only.for.JAXM");
        }

        if (urlEndpointClass != null) {
            if (urlEndpointClass.isInstance(endPoint)) {
                String url = null;

                try {
                    Method m = urlEndpointClass.getMethod("getURL", (Class[])null);
                    url = (String) m.invoke(endPoint, (Object[])null);
                } catch (Exception ex) {
                    // TBD -- exception chaining
                    log.log(Level.SEVERE,"SAAJ0004.p2p.internal.err",ex);
                    throw new SOAPExceptionImpl(
                        "Internal error: " + ex.getMessage());
                }
                try {
                    endPoint = new URL(url);
                } catch (MalformedURLException mex) {
                    log.log(Level.SEVERE,"SAAJ0005.p2p.", mex);
                    throw new SOAPExceptionImpl("Bad URL: " + mex.getMessage());
                }
            }
        }

        if (endPoint instanceof java.lang.String) {
            try {
                endPoint = new URL((String) endPoint);
            } catch (MalformedURLException mex) {
                log.log(Level.SEVERE, "SAAJ0006.p2p.bad.URL", mex);
                throw new SOAPExceptionImpl("Bad URL: " + mex.getMessage());
            }
        }

        if (endPoint instanceof URL)
            try {
                SOAPMessage response = post(message, (URL)endPoint);
                return response;
            } catch (Exception ex) {
                // TBD -- chaining?
                throw new SOAPExceptionImpl(ex);
            } else {
            log.severe("SAAJ0007.p2p.bad.endPoint.type");
            throw new SOAPExceptionImpl("Bad endPoint type " + endPoint);
        }
    }

    SOAPMessage post(SOAPMessage message, URL endPoint) throws SOAPException, IOException {
        boolean isFailure = false;

        URL url = null;
        HttpURLConnection httpConnection = null;

        int responseCode = 0;
        try {
            if (endPoint.getProtocol().equals("https"))
                //if(!setHttps)
                initHttps();
            // Process the URL
            URI uri = new URI(endPoint.toString());
            String userInfo = uri.getRawUserInfo();

            url = endPoint;

            if (dL > 0)
                d("uri: " + userInfo + " " + url + " " + uri);

            // TBD
            //    Will deal with https later.
            if (!url.getProtocol().equalsIgnoreCase("http")
                && !url.getProtocol().equalsIgnoreCase("https")) {
                log.severe("SAAJ0052.p2p.protocol.mustbe.http.or.https");
                throw new IllegalArgumentException(
                    "Protocol "
                        + url.getProtocol()
                        + " not supported in URL "
                        + url);
            }
            httpConnection = (HttpURLConnection) createConnection(url);

            httpConnection.setRequestMethod("POST");

            httpConnection.setDoOutput(true);
            httpConnection.setDoInput(true);
            httpConnection.setUseCaches(false);
            httpConnection.setInstanceFollowRedirects(true);

            if (message.saveRequired())
                message.saveChanges();

            MimeHeaders headers = message.getMimeHeaders();

            Iterator<?> it = headers.getAllHeaders();
            boolean hasAuth = false; // true if we find explicit Auth header
            while (it.hasNext()) {
                MimeHeader header = (MimeHeader) it.next();

                String[] values = headers.getHeader(header.getName());
                if (values.length == 1)
                    httpConnection.setRequestProperty(
                        header.getName(),
                        header.getValue());
                else {
                    StringBuilder concat = new StringBuilder();
                    int i = 0;
                    while (i < values.length) {
                        if (i != 0)
                            concat.append(',');
                        concat.append(values[i]);
                        i++;
                    }

                    httpConnection.setRequestProperty(
                        header.getName(),
                        concat.toString());
                }

                if ("Authorization".equals(header.getName())) {
                    hasAuth = true;
                    if (log.isLoggable(Level.FINE))
                        log.fine("SAAJ0091.p2p.https.auth.in.POST.true");
                }
            }

            if (!hasAuth && userInfo != null) {
                initAuthUserInfo(httpConnection, userInfo);
            }

            OutputStream out = httpConnection.getOutputStream();
            try {
                message.writeTo(out);
                out.flush();
            } finally {
                out.close();
            }

            httpConnection.connect();

            try {

                responseCode = httpConnection.getResponseCode();

                // let HTTP_INTERNAL_ERROR (500) through because it is used for SOAP faults
                if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    isFailure = true;
                }
                //else if (responseCode != HttpURLConnection.HTTP_OK)
                //else if (!(responseCode >= HttpURLConnection.HTTP_OK && responseCode < 207))
                else if ((responseCode / 100) != 2) {
                    log.log(Level.SEVERE,
                            "SAAJ0008.p2p.bad.response",
                            new String[] {httpConnection.getResponseMessage()});
                    throw new SOAPExceptionImpl(
                        "Bad response: ("
                            + responseCode
                            + httpConnection.getResponseMessage());

                }
            } catch (IOException e) {
                // on JDK1.3.1_01, we end up here, but then getResponseCode() succeeds!
                responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    isFailure = true;
                } else {
                    throw e;
                }

            }

        } catch (SOAPException ex) {
            throw ex;
        } catch (Exception ex) {
            log.severe("SAAJ0009.p2p.msg.send.failed");
            throw new SOAPExceptionImpl("Message send failed", ex);
        }

        SOAPMessage response = null;
        InputStream httpIn = null;
        if (responseCode == HttpURLConnection.HTTP_OK || isFailure) {
            try {
                MimeHeaders headers = new MimeHeaders();

                String key, value;

                // Header field 0 is the status line so we skip it.

                int i = 1;

                while (true) {
                    key = httpConnection.getHeaderFieldKey(i);
                    value = httpConnection.getHeaderField(i);

                    if (key == null && value == null)
                        break;

                    if (key != null) {
                        StringTokenizer values =
                            new StringTokenizer(value, ",");
                        while (values.hasMoreTokens())
                            headers.addHeader(key, values.nextToken().trim());
                    }
                    i++;
                }

                httpIn =
                    (isFailure
                        ? httpConnection.getErrorStream()
                        : httpConnection.getInputStream());

                byte[] bytes = readFully(httpIn);

                int length =
                    httpConnection.getContentLength() == -1
                        ? bytes.length
                        : httpConnection.getContentLength();

                // If no reply message is returned,
                // content-Length header field value is expected to be zero.
                if (length == 0) {
                    response = null;
                    log.warning("SAAJ0014.p2p.content.zero");
                } else {
                    ByteInputStream in = new ByteInputStream(bytes, length);
                    response = messageFactory.createMessage(headers, in);
                }

            } catch (SOAPException ex) {
                throw ex;
            } catch (Exception ex) {
                log.log(Level.SEVERE,"SAAJ0010.p2p.cannot.read.resp", ex);
                throw new SOAPExceptionImpl(
                    "Unable to read response: " + ex.getMessage());
            } finally {
               if (httpIn != null)
                   httpIn.close();
               httpConnection.disconnect();
            }
        }
        return response;
    }

    // Object identifies where the request should be sent.
    // It is required to support objects of type String and java.net.URL.

    public SOAPMessage get(Object endPoint) throws SOAPException {
        if (closed) {
            log.severe("SAAJ0011.p2p.get.already.closed.conn");
            throw new SOAPExceptionImpl("Connection is closed");
        }
        Class<?> urlEndpointClass = null;

        try {
            urlEndpointClass = Class.forName("javax.xml.messaging.URLEndpoint");
        } catch (Exception ex) {
            //Do nothing. URLEndpoint is available only when JAXM is there.
        }

        if (urlEndpointClass != null) {
            if (urlEndpointClass.isInstance(endPoint)) {
                String url = null;

                try {
                    Method m = urlEndpointClass.getMethod("getURL", (Class[])null);
                    url = (String) m.invoke(endPoint, (Object[])null);
                } catch (Exception ex) {
                    log.severe("SAAJ0004.p2p.internal.err");
                    throw new SOAPExceptionImpl(
                        "Internal error: " + ex.getMessage());
                }
                try {
                    endPoint = new URL(url);
                } catch (MalformedURLException mex) {
                    log.severe("SAAJ0005.p2p.");
                    throw new SOAPExceptionImpl("Bad URL: " + mex.getMessage());
                }
            }
        }

        if (endPoint instanceof java.lang.String) {
            try {
                endPoint = new URL((String) endPoint);
            } catch (MalformedURLException mex) {
                log.severe("SAAJ0006.p2p.bad.URL");
                throw new SOAPExceptionImpl("Bad URL: " + mex.getMessage());
            }
        }

        if (endPoint instanceof URL)
            try {
                SOAPMessage response = doGet((URL)endPoint);
                return response;
            } catch (Exception ex) {
                throw new SOAPExceptionImpl(ex);
            } else
            throw new SOAPExceptionImpl("Bad endPoint type " + endPoint);
    }

    SOAPMessage doGet(URL endPoint) throws SOAPException, IOException {
        boolean isFailure = false;

        URL url = null;
        HttpURLConnection httpConnection = null;

        int responseCode = 0;
        try {
            /// Is https GET allowed??
            if (endPoint.getProtocol().equals("https"))
                initHttps();
            // Process the URL
            URI uri = new URI(endPoint.toString());
            String userInfo = uri.getRawUserInfo();

            url = endPoint;

            if (dL > 0)
                d("uri: " + userInfo + " " + url + " " + uri);

            // TBD
            //    Will deal with https later.
            if (!url.getProtocol().equalsIgnoreCase("http")
                && !url.getProtocol().equalsIgnoreCase("https")) {
                log.severe("SAAJ0052.p2p.protocol.mustbe.http.or.https");
                throw new IllegalArgumentException(
                    "Protocol "
                        + url.getProtocol()
                        + " not supported in URL "
                        + url);
            }
            httpConnection = (HttpURLConnection) createConnection(url);

            httpConnection.setRequestMethod("GET");

            httpConnection.setDoOutput(true);
            httpConnection.setDoInput(true);
            httpConnection.setUseCaches(false);
            httpConnection.setInstanceFollowRedirects(true);

            httpConnection.connect();

            try {

                responseCode = httpConnection.getResponseCode();

                // let HTTP_INTERNAL_ERROR (500) through because it is used for SOAP faults
                if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    isFailure = true;
                } else if ((responseCode / 100) != 2) {
                    log.log(Level.SEVERE,
                            "SAAJ0008.p2p.bad.response",
                            new String[] { httpConnection.getResponseMessage()});
                    throw new SOAPExceptionImpl(
                        "Bad response: ("
                            + responseCode
                            + httpConnection.getResponseMessage());

                }
            } catch (IOException e) {
                // on JDK1.3.1_01, we end up here, but then getResponseCode() succeeds!
                responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    isFailure = true;
                } else {
                    throw e;
                }

            }

        } catch (SOAPException ex) {
            throw ex;
        } catch (Exception ex) {
            log.severe("SAAJ0012.p2p.get.failed");
            throw new SOAPExceptionImpl("Get failed", ex);
        }

        SOAPMessage response = null;
        InputStream httpIn = null;
        if (responseCode == HttpURLConnection.HTTP_OK || isFailure) {
            try {
                MimeHeaders headers = new MimeHeaders();

                String key, value;

                // Header field 0 is the status line so we skip it.

                int i = 1;

                while (true) {
                    key = httpConnection.getHeaderFieldKey(i);
                    value = httpConnection.getHeaderField(i);

                    if (key == null && value == null)
                        break;

                    if (key != null) {
                        StringTokenizer values =
                            new StringTokenizer(value, ",");
                        while (values.hasMoreTokens())
                            headers.addHeader(key, values.nextToken().trim());
                    }
                    i++;
                }

                httpIn =
                        (isFailure
                        ? httpConnection.getErrorStream()
                        : httpConnection.getInputStream());
                // If no reply message is returned,
                // content-Length header field value is expected to be zero.
                // java SE 6 documentation says :
                // available() : an estimate of the number of bytes that can be read
                //(or skipped over) from this input stream without blocking
                //or 0 when it reaches the end of the input stream.
                if ((httpIn == null )
                        || (httpConnection.getContentLength() == 0)
                        || (httpIn.available() == 0)) {
                    response = null;
                    log.warning("SAAJ0014.p2p.content.zero");
                } else {
                    response = messageFactory.createMessage(headers, httpIn);
                }

            } catch (SOAPException ex) {
                throw ex;
            } catch (Exception ex) {
                log.log(Level.SEVERE,
                        "SAAJ0010.p2p.cannot.read.resp",
                        ex);
                throw new SOAPExceptionImpl(
                    "Unable to read response: " + ex.getMessage());
            } finally {
               if (httpIn != null)
                   httpIn.close();
               httpConnection.disconnect();
            }
        }
        return response;
    }

    private byte[] readFully(InputStream istream) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int num = 0;

        while ((num = istream.read(buf)) != -1) {
            bout.write(buf, 0, num);
        }

        byte[] ret = bout.toByteArray();

        return ret;
    }

    //private static String SSL_PKG = "com.sun.net.ssl.internal.www.protocol";
    //private static String SSL_PROVIDER =
      //  "com.sun.net.ssl.internal.ssl.Provider";
    private static final String SSL_PKG;
    private static final String SSL_PROVIDER;

    static {
        if (isIBMVM) {
            SSL_PKG ="com.ibm.net.ssl.internal.www.protocol";
            SSL_PROVIDER ="com.ibm.net.ssl.internal.ssl.Provider";
        } else {
            //if not IBM VM default to Sun.
            SSL_PKG = "com.sun.net.ssl.internal.www.protocol";
            SSL_PROVIDER ="com.sun.net.ssl.internal.ssl.Provider";
        }
    }

    private void initHttps() {
        //if(!setHttps) {
        String pkgs = SAAJUtil.getSystemProperty("java.protocol.handler.pkgs");
        if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, "SAAJ0053.p2p.providers", new String[] { pkgs });

        if (pkgs == null || pkgs.indexOf(SSL_PKG) < 0) {
            if (pkgs == null)
                pkgs = SSL_PKG;
            else
                pkgs = pkgs + "|" + SSL_PKG;
            System.setProperty("java.protocol.handler.pkgs", pkgs);
            if (log.isLoggable(Level.FINE))
                log.log(Level.FINE, "SAAJ0054.p2p.set.providers",
                        new String[] { pkgs });
            try {
                Class<?> c = Class.forName(SSL_PROVIDER);
                Provider p = (Provider) c.newInstance();
                Security.addProvider(p);
                if (log.isLoggable(Level.FINE))
                    log.log(Level.FINE, "SAAJ0055.p2p.added.ssl.provider",
                            new String[] { SSL_PROVIDER });
                //System.out.println("Added SSL_PROVIDER " + SSL_PROVIDER);
                //setHttps = true;
            } catch (Exception ex) {
            }
        }
        //}
    }

    private void initAuthUserInfo(HttpURLConnection conn, String userInfo) {
        String user;
        String password;
        if (userInfo != null) { // get the user and password
            //System.out.println("UserInfo= " + userInfo );
            int delimiter = userInfo.indexOf(':');
            if (delimiter == -1) {
                user = ParseUtil.decode(userInfo);
                password = null;
            } else {
                user = ParseUtil.decode(userInfo.substring(0, delimiter++));
                password = ParseUtil.decode(userInfo.substring(delimiter));
            }

            String plain = user + ":";
            byte[] nameBytes = plain.getBytes();
            byte[] passwdBytes = (password == null ? new byte[0] : password
                    .getBytes());

            // concatenate user name and password bytes and encode them
            byte[] concat = new byte[nameBytes.length + passwdBytes.length];

            System.arraycopy(nameBytes, 0, concat, 0, nameBytes.length);
            System.arraycopy(
                passwdBytes,
                0,
                concat,
                nameBytes.length,
                passwdBytes.length);
            String auth = "Basic " + new String(Base64.encode(concat));
            conn.setRequestProperty("Authorization", auth);
            if (dL > 0)
                d("Adding auth " + auth);
        }
    }

    private static final int dL = 0;
    private void d(String s) {
        log.log(Level.SEVERE,
                "SAAJ0013.p2p.HttpSOAPConnection",
                new String[] { s });
        System.err.println("HttpSOAPConnection: " + s);
    }

    private java.net.HttpURLConnection createConnection(URL endpoint)
        throws IOException {
        return (HttpURLConnection) endpoint.openConnection();
    }

}
