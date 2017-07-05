/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.http.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.xml.internal.ws.handler.MessageContextImpl;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.server.DocInfo;
import com.sun.xml.internal.ws.server.WSDLPatcher;
import java.util.concurrent.Executor;
import javax.xml.ws.handler.MessageContext;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.server.Tie;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.spi.runtime.WebServiceContext;
import com.sun.xml.internal.ws.transport.Headers;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of HttpContext's HttpHandler. HttpServer calls this when there
 * is request that matches the context path. Then the handler processes
 * HttpExchange and sends appropriate response
 *
 * @author WS Development Team
 */
public class WSHttpHandler implements HttpHandler {

    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String HEAD_METHOD = "HEAD";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";
    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String XML_CONTENT_TYPE = "text/xml";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.http");
    private static final Localizer localizer = new Localizer();
    private static final LocalizableMessageFactory messageFactory =
        new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.httpserver");

    private final RuntimeEndpointInfo endpointInfo;
    private final Tie tie;
    private final Executor executor;

    public WSHttpHandler(Tie tie, RuntimeEndpointInfo endpointInfo, Executor executor) {
        this.tie = tie;
        this.endpointInfo = endpointInfo;
        this.executor = executor;
    }

    /**
     * Called by HttpServer when there is a matching request for the context
     */
    public void handle(HttpExchange msg) {
        logger.fine("Received HTTP request:"+msg.getRequestURI());
        if (executor != null) {
            // Use endpoint's Executor to handle request
            executor.execute(new HttpHandlerRunnable(msg));
        } else {
            handleExchange(msg);
        }
    }

    /**
     * Handles the HTTP request for GET, POST
     *
     */
    private void handleExchange(HttpExchange msg) {
        try {
            String method = msg.getRequestMethod();
            if (method.equals(GET_METHOD)) {
                String queryString = msg.getRequestURI().getQuery();
                logger.fine("Query String for request ="+queryString);
                if (queryString != null &&
                    (queryString.equals("WSDL") || queryString.equals("wsdl")
                    || queryString.startsWith("wsdl=") || queryString.startsWith("xsd="))) {
                    // Handles WSDL, Schema documents
                    processDocRequest(msg);
                } else {
                    process(msg);
                }
            } else if (method.equals(POST_METHOD) || method.equals(HEAD_METHOD)
                        || method.equals(PUT_METHOD) || method.equals(DELETE_METHOD)) {
                process(msg);
            } else {
                logger.warning(
                    localizer.localize(
                        messageFactory.getMessage(
                            "unexpected.http.method", method)));
                msg.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Wrapping the processing of request in a Runnable so that it can be
     * executed in Executor.
     */
    class HttpHandlerRunnable implements Runnable {
        final HttpExchange msg;

        HttpHandlerRunnable(HttpExchange msg) {
            this.msg = msg;
        }

        public void run() {
            handleExchange(msg);
        }
    }

    /**
     * Handles POST requests
     */
    private void process(HttpExchange msg) {
        WSConnection con = new ServerConnectionImpl(msg);
        try {
            MessageContext msgCtxt = new MessageContextImpl();
            WebServiceContext wsContext = endpointInfo.getWebServiceContext();
            wsContext.setMessageContext(msgCtxt);
            MessageContextUtil.setHttpRequestMethod(msgCtxt, msg.getRequestMethod());
            MessageContextUtil.setHttpRequestHeaders(msgCtxt, con.getHeaders());
            MessageContextUtil.setHttpExchange(msgCtxt, msg);
            URI requestUri = msg.getRequestURI();
            String query = requestUri.getQuery();
            if (query != null) {
                MessageContextUtil.setQueryString(msgCtxt, query);
            }
            String reqPath = requestUri.getPath();
            String ctxtPath = msg.getHttpContext().getPath();
            if (reqPath.length() > ctxtPath.length()) {
                String extraPath = reqPath.substring(ctxtPath.length());
                MessageContextUtil.setPathInfo(msgCtxt, extraPath);
            }
            tie.handle(con, endpointInfo);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            con.close();
        }
    }

    /**
     * Handles GET requests for WSDL and Schema docuemnts
     */
    public void processDocRequest(HttpExchange msg) {
        WSConnection con = new ServerConnectionImpl(msg);
        try {
            con.getInput();
            String queryString = msg.getRequestURI().getQuery();
            String inPath = endpointInfo.getPath(queryString);
            if (inPath == null) {
                String message =
                    localizer.localize(
                        messageFactory.getMessage("html.notFound",
                            "Invalid Request ="+msg.getRequestURI()));
                writeErrorPage(con, HttpURLConnection.HTTP_NOT_FOUND, message);
                return;
            }
            DocInfo docInfo = endpointInfo.getDocMetadata().get(inPath);
            if (docInfo == null) {
                String message =
                    localizer.localize(
                        messageFactory.getMessage("html.notFound",
                            "Invalid Request ="+msg.getRequestURI()));
                writeErrorPage(con, HttpURLConnection.HTTP_NOT_FOUND, message);
                return;
            }

            InputStream docStream = null;
            try {
                Map<String, List<String>> reqHeaders = con.getHeaders();
                List<String> hostHeader = reqHeaders.get("Host");

                Headers respHeaders = new Headers();
                respHeaders.add(CONTENT_TYPE_HEADER, XML_CONTENT_TYPE);
                con.setHeaders(respHeaders);
                con.setStatus(HttpURLConnection.HTTP_OK);
                OutputStream os = con.getOutput();

                List<RuntimeEndpointInfo> endpoints = new ArrayList<RuntimeEndpointInfo>();
                endpoints.add(endpointInfo);

                StringBuffer strBuf = new StringBuffer();
                strBuf.append((msg instanceof HttpsExchange) ? "https" : "http");
                strBuf.append("://");
                if (hostHeader != null) {
                    strBuf.append(hostHeader.get(0));   // Uses Host header
                } else {
                    strBuf.append(msg.getLocalAddress().getHostName());
                    strBuf.append(":");
                    strBuf.append(msg.getLocalAddress().getPort());
                }
                strBuf.append(msg.getRequestURI().getPath());
                String address = strBuf.toString();
                logger.fine("Address ="+address);
                WSDLPatcher patcher = new WSDLPatcher(docInfo, address,
                        endpointInfo, endpoints);
                docStream = docInfo.getDoc();
                patcher.patchDoc(docStream, os);
            } finally {
                closeInputStream(docStream);
                con.closeOutput();
            }
        } finally {
            con.close();
        }
    }

    /**
     * writes error html page
     */
    private void writeErrorPage(WSConnection con, int status, String message) {
        try {
            Map<String,List<String>> headers = new HashMap<String, List<String>>();
            List<String> ctHeader = new ArrayList<String>();
            ctHeader.add(HTML_CONTENT_TYPE);
            headers.put(CONTENT_TYPE_HEADER, ctHeader);
            con.setHeaders(headers);
            con.setStatus(status);
            OutputStream outputStream = con.getOutput();
            PrintWriter out = new PrintWriter(outputStream);
            out.println("<html><head><title>");
            out.println(
                localizer.localize(
                    messageFactory.getMessage("html.title")));
            out.println("</title></head><body>");
            out.println(message);
            out.println("</body></html>");
            out.close();
        } finally {
            con.closeOutput();
        }
    }

    private static void closeInputStream(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

}
