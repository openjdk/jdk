/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils.resolver.implementations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverContext;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverException;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;

/**
 * A simple ResourceResolver for HTTP requests. This class handles only 'pure'
 * HTTP URIs which means without a fragment. The Fragment handling is done by the
 * {@link ResolverFragment} class.
 * <BR>
 * If the user has a corporate HTTP proxy which is to be used, the usage can be
 * switched on by setting properties for the resolver:
 * <PRE>
 * resourceResolver.setProperty("http.proxy.host", "proxy.company.com");
 * resourceResolver.setProperty("http.proxy.port", "8080");
 *
 * // if we need a password for the proxy
 * resourceResolver.setProperty("http.proxy.username", "proxyuser3");
 * resourceResolver.setProperty("http.proxy.password", "secretca");
 * </PRE>
 *
 * @see <A HREF="http://www.javaworld.com/javaworld/javatips/jw-javatip42_p.html">Java Tip 42: Write Java apps that work with proxy-based firewalls</A>
 * @see <A HREF="http://docs.oracle.com/javase/1.4.2/docs/guide/net/properties.html">SUN J2SE docs for network properties</A>
 * @see <A HREF="http://metalab.unc.edu/javafaq/javafaq.html#proxy">The JAVA FAQ Question 9.5: How do I make Java work with a proxy server?</A>
 */
public class ResolverDirectHTTP extends ResourceResolverSpi {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(ResolverDirectHTTP.class.getName());

    /** Field properties[] */
    private static final String properties[] = {
                                                 "http.proxy.host", "http.proxy.port",
                                                 "http.proxy.username", "http.proxy.password",
                                                 "http.basic.username", "http.basic.password"
                                               };

    /** Field HttpProxyHost */
    private static final int HttpProxyHost = 0;

    /** Field HttpProxyPort */
    private static final int HttpProxyPort = 1;

    /** Field HttpProxyUser */
    private static final int HttpProxyUser = 2;

    /** Field HttpProxyPass */
    private static final int HttpProxyPass = 3;

    /** Field HttpProxyUser */
    private static final int HttpBasicUser = 4;

    /** Field HttpProxyPass */
    private static final int HttpBasicPass = 5;

    @Override
    public boolean engineIsThreadSafe() {
        return true;
    }

    /**
     * Method resolve
     *
     * @param uri
     * @param baseURI
     *
     * @throws ResourceResolverException
     * @return
     * $todo$ calculate the correct URI from the attribute and the baseURI
     */
    @Override
    public XMLSignatureInput engineResolveURI(ResourceResolverContext context)
        throws ResourceResolverException {
        try {

            // calculate new URI
            URI uriNew = getNewURI(context.uriToResolve, context.baseUri);
            URL url = uriNew.toURL();
            URLConnection urlConnection;
            urlConnection = openConnection(url);

            // check if Basic authentication is required
            String auth = urlConnection.getHeaderField("WWW-Authenticate");

            if (auth != null && auth.startsWith("Basic")) {
                // do http basic authentication
                String user =
                    engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpBasicUser]);
                String pass =
                    engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpBasicPass]);

                if ((user != null) && (pass != null)) {
                    urlConnection = openConnection(url);

                    String password = user + ":" + pass;
                    String encodedPassword = Base64.encode(password.getBytes("ISO-8859-1"));

                    // set authentication property in the http header
                    urlConnection.setRequestProperty("Authorization",
                                                     "Basic " + encodedPassword);
                }
            }

            String mimeType = urlConnection.getHeaderField("Content-Type");
            InputStream inputStream = urlConnection.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte buf[] = new byte[4096];
            int read = 0;
            int summarized = 0;

            while ((read = inputStream.read(buf)) >= 0) {
                baos.write(buf, 0, read);
                summarized += read;
            }

            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "Fetched " + summarized + " bytes from URI " + uriNew.toString());
            }

            XMLSignatureInput result = new XMLSignatureInput(baos.toByteArray());

            result.setSourceURI(uriNew.toString());
            result.setMIMEType(mimeType);

            return result;
        } catch (URISyntaxException ex) {
            throw new ResourceResolverException("generic.EmptyMessage", ex, context.attr, context.baseUri);
        } catch (MalformedURLException ex) {
            throw new ResourceResolverException("generic.EmptyMessage", ex, context.attr, context.baseUri);
        } catch (IOException ex) {
            throw new ResourceResolverException("generic.EmptyMessage", ex, context.attr, context.baseUri);
        } catch (IllegalArgumentException e) {
            throw new ResourceResolverException("generic.EmptyMessage", e, context.attr, context.baseUri);
        }
    }

    private URLConnection openConnection(URL url) throws IOException {

        String proxyHostProp =
                engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpProxyHost]);
        String proxyPortProp =
                engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpProxyPort]);
        String proxyUser =
                engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpProxyUser]);
        String proxyPass =
                engineGetProperty(ResolverDirectHTTP.properties[ResolverDirectHTTP.HttpProxyPass]);

        Proxy proxy = null;
        if ((proxyHostProp != null) && (proxyPortProp != null)) {
            int port = Integer.parseInt(proxyPortProp);
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHostProp, port));
        }

        URLConnection urlConnection;
        if (proxy != null) {
            urlConnection = url.openConnection(proxy);

            if ((proxyUser != null) && (proxyPass != null)) {
                String password = proxyUser + ":" + proxyPass;
                String authString = "Basic " + Base64.encode(password.getBytes("ISO-8859-1"));

                urlConnection.setRequestProperty("Proxy-Authorization", authString);
            }
        } else {
            urlConnection = url.openConnection();
        }

        return urlConnection;
    }

    /**
     * We resolve http URIs <I>without</I> fragment...
     *
     * @param uri
     * @param baseURI
     * @return true if can be resolved
     */
    public boolean engineCanResolveURI(ResourceResolverContext context) {
        if (context.uriToResolve == null) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "quick fail, uri == null");
            }
            return false;
        }

        if (context.uriToResolve.equals("") || (context.uriToResolve.charAt(0)=='#')) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "quick fail for empty URIs and local ones");
            }
            return false;
        }

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "I was asked whether I can resolve " + context.uriToResolve);
        }

        if (context.uriToResolve.startsWith("http:") ||
            (context.baseUri != null && context.baseUri.startsWith("http:") )) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "I state that I can resolve " + context.uriToResolve);
            }
            return true;
        }

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "I state that I can't resolve " + context.uriToResolve);
        }

        return false;
    }

    /**
     * @inheritDoc
     */
    public String[] engineGetPropertyKeys() {
        return ResolverDirectHTTP.properties.clone();
    }

    private static URI getNewURI(String uri, String baseURI) throws URISyntaxException {
        URI newUri = null;
        if (baseURI == null || "".equals(baseURI)) {
            newUri = new URI(uri);
        } else {
            newUri = new URI(baseURI).resolve(uri);
        }

        // if the URI contains a fragment, ignore it
        if (newUri.getFragment() != null) {
            URI uriNewNoFrag =
                new URI(newUri.getScheme(), newUri.getSchemeSpecificPart(), null);
            return uriNewNoFrag;
        }
        return newUri;
    }

}
