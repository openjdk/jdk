/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.url.rmi;

import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Hashtable;
import java.util.Locale;

import javax.naming.*;
import javax.naming.spi.ResolveResult;
import com.sun.jndi.toolkit.url.GenericURLContext;
import com.sun.jndi.rmi.registry.RegistryContext;
import com.sun.jndi.toolkit.url.Uri.ParseMode;


/**
 * An RMI URL context resolves names that are URLs of the form
 * <pre>
 *   rmi://[host][:port][/[object]]
 * or
 *   rmi:[/][object]
 * </pre>
 * If an object is specified, the URL resolves to the named object.
 * Otherwise, the URL resolves to the specified RMI registry.
 *
 * @author Scott Seligman
 */
public class rmiURLContext extends GenericURLContext {

    private static final String PARSE_MODE_PROP = "com.sun.jndi.rmiURLParsing";
    private static final ParseMode DEFAULT_PARSE_MODE = ParseMode.COMPAT;

    public static final ParseMode PARSE_MODE;
    static {
        PrivilegedAction<String> action = () ->
                System.getProperty(PARSE_MODE_PROP, DEFAULT_PARSE_MODE.toString());
        ParseMode parseMode = DEFAULT_PARSE_MODE;
        try {
            @SuppressWarnings("removal")
            String mode = AccessController.doPrivileged(action);
            parseMode = ParseMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            parseMode = DEFAULT_PARSE_MODE;
        } finally {
            PARSE_MODE = parseMode;
        }
    }

    public rmiURLContext(Hashtable<?,?> env) {
        super(env);
    }

    public static class Parser {
        final String url;
        final ParseMode mode;
        String host = null;
        int port = -1;
        String objName = null;
        public Parser(String url) {
            this(url, PARSE_MODE);
        }
        public Parser(String url, ParseMode mode) {
            this.url = url;
            this.mode = mode;
        }

        public String url() {return url;}
        public String host() {return host;}
        public int port() {return port;}
        public String objName() {return objName;}
        public ParseMode mode() {return mode;}

        public void parse() throws NamingException {
            if (!url.startsWith("rmi:")) {
                throw (new IllegalArgumentException(
                        "rmiURLContext: name is not an RMI URL: " + url));
            }

            switch (mode) {
                case STRICT -> parseStrict();
                case COMPAT -> parseCompat();
                case LEGACY -> parseLegacy();
            }

        }

        private void parseStrict() throws NamingException {
            assert url.startsWith("rmi:");

            if (url.equals("rmi:") || url.equals("rmi://")) return;

            // index into url, following the "rmi:"
            int i = 4;

            if (url.startsWith("//", i)) {
                i += 2;
                try {
                    URI uri = URI.create(url);
                    host = uri.getHost();
                    port = uri.getPort();
                    String auth = uri.getRawAuthority();
                    String hostport = (host == null ? "" : host)
                            + (port == -1 ? "" : ":" + port);
                    if (!hostport.equals(auth)) {
                        boolean failed = true;
                        if (hostport.equals("") && auth.startsWith(":")) {
                            // supports missing host
                            try {
                                port = Integer.parseInt(auth.substring(1));
                                failed = false;
                            } catch (NumberFormatException x) {
                                failed = true;
                            }
                        }
                        if (failed) {
                            throw newNamingException(new IllegalArgumentException("invalid authority: "
                                    + auth));
                        }
                    }
                    i += auth.length();
                } catch (IllegalArgumentException iae) {
                    throw newNamingException(iae);
                }
            }
            int fmark = url.indexOf('#', i);
            if (fmark > -1) {
                if (!acceptsFragment()) {
                    throw newNamingException(new IllegalArgumentException("URI fragments not supported: " + url));
                }
            }

            if ("".equals(host)) {
                host = null;
            }
            if (url.startsWith("/", i)) {           // skip "/" before object name
                i++;
            }
            if (i < url.length()) {
                objName = url.substring(i);
            }
        }

        private void parseCompat() throws NamingException {
            assert url.startsWith("rmi:");

            int i = 4;              // index into url, following the "rmi:"
            boolean hasAuthority = url.startsWith("//", i);
            if (hasAuthority) i += 2;  // skip past "//"
            int slash = url.indexOf('/', i);
            int qmark = url.indexOf('?', i);
            int fmark = url.indexOf('#', i);
            if (fmark > -1 && qmark > fmark) qmark = -1;
            if (fmark > -1 && slash > fmark) slash = -1;
            if (qmark > -1 && slash > qmark) slash = -1;

            // The end of the authority component is either the
            // slash (slash will be -1 if it doesn't come before
            // query or fragment), or the question mark (qmark will
            // be -1 if it doesn't come before the fragment), or
            // the fragment separator, or the end of the URI
            // string if there is no path, no query, and no fragment.
            int enda = slash > -1 ? slash
                    : (qmark > -1 ? qmark
                    : (fmark > -1 ? fmark
                    : url.length()));
            if (fmark > -1) {
                if (!acceptsFragment()) {
                    throw newNamingException(new IllegalArgumentException("URI fragments not supported: " + url));
                }
            }

            if (hasAuthority && enda > i) {          // parse "//host:port"
                if (url.startsWith(":", i)) {
                    // LdapURL supports empty host.
                    i++;
                    host = "";
                    if (enda > i) {
                        port = Integer.parseInt(url.substring(i, enda));
                    }
                } else {
                    try {
                        URI uri = URI.create(url.substring(0, enda));
                        host = uri.getHost();
                        port = uri.getPort();
                        String hostport = (host == null ? "" : host)
                                + (port == -1 ? "" : ":" + port);
                        if (!hostport.equals(uri.getRawAuthority())) {
                            throw newNamingException(new IllegalArgumentException("invalid authority: "
                                    + uri.getRawAuthority()));
                        }
                    } catch (IllegalArgumentException iae) {
                        throw newNamingException(iae);
                    }
                }
                i = enda;
            }
            if ("".equals(host)) {
                host = null;
            }
            if (url.startsWith("/", i)) {           // skip "/" before object name
                i++;
            }
            if (i < url.length()) {
                objName = url.substring(i);
            }

        }

        // The legacy parsing used to only throw IllegalArgumentException
        // and continues to do so
        private void parseLegacy() {
            assert url.startsWith("rmi:");

            // Parse the URL.
            int i = 4;              // index into url, following the "rmi:"

            if (url.startsWith("//", i)) {          // parse "//host:port"
                i += 2;                             // skip past "//"
                int slash = url.indexOf('/', i);
                if (slash < 0) {
                    slash = url.length();
                }
                if (url.startsWith("[", i)) {               // at IPv6 literal
                    int brac = url.indexOf(']', i + 1);
                    if (brac < 0 || brac > slash) {
                        throw new IllegalArgumentException(
                                "rmiURLContext: name is an Invalid URL: " + url);
                    }
                    host = url.substring(i, brac + 1);      // include brackets
                    i = brac + 1;                           // skip past "[...]"
                } else {                                    // at host name or IPv4
                    int colon = url.indexOf(':', i);
                    int hostEnd = (colon < 0 || colon > slash)
                            ? slash
                            : colon;
                    if (i < hostEnd) {
                        host = url.substring(i, hostEnd);
                    }
                    i = hostEnd;                            // skip past host
                }
                if ((i + 1 < slash)) {
                    if ( url.startsWith(":", i)) {       // parse port
                        i++;                             // skip past ":"
                        port = Integer.parseInt(url.substring(i, slash));
                    } else {
                        throw new IllegalArgumentException(
                                "rmiURLContext: name is an Invalid URL: " + url);
                    }
                }
                i = slash;
            }
            if ("".equals(host)) {
                host = null;
            }
            if (url.startsWith("/", i)) {           // skip "/" before object name
                i++;
            }
            if (i < url.length()) {
                objName = url.substring(i);
            }
        }

        NamingException newNamingException(Throwable cause) {
            NamingException ne = new InvalidNameException(cause.getMessage());
            ne.initCause(cause);
            return ne;
        }

        protected boolean acceptsFragment() {
            return true;
        }
    }

    /**
     * Resolves the registry portion of "url" to the corresponding
     * RMI registry, and returns the atomic object name as the
     * remaining name.
     */
    protected ResolveResult getRootURLContext(String url, Hashtable<?,?> env)
            throws NamingException
    {
        Parser parser = new Parser(url);
        parser.parse();
        String host = parser.host;
        int port = parser.port;
        String objName = parser.objName;

        // Represent object name as empty or single-component composite name.
        CompositeName remaining = new CompositeName();
        if (objName != null) {
            remaining.add(objName);
        }

        // Debug
        //System.out.println("host=" + host + " port=" + port +
        //                 " objName=" + remaining.toString() + "\n");

        // Create a registry context.
        Context regCtx = new RegistryContext(host, port, env);

        return (new ResolveResult(regCtx, remaining));
    }
}
