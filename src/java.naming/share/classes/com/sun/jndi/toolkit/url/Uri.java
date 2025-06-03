/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.toolkit.url;


import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import static jdk.internal.util.Exceptions.filterNonSocketInfo;
import static jdk.internal.util.Exceptions.formatMsg;


/**
 * A Uri object represents an absolute Uniform Resource Identifier
 * (URI) as defined by RFC 2396 and updated by RFC 2373 and RFC 2732.
 * The most commonly used form of URI is the Uniform Resource Locator (URL).
 *
 * <p> The java.net.URL class cannot be used to parse URIs since it
 * requires the installation of URL stream handlers that may not be
 * available.
 *
 * <p> The {@linkplain ParseMode#STRICT strict} parsing mode uses
 * the java.net.URI class to syntactically validate URI strings.
 * The {@linkplain ParseMode#COMPAT compat} mode validate the
 * URI authority and rejects URI fragments, but doesn't perform any
 * additional validation on path and query, other than that
 * which may be implemented in the concrete the Uri subclasses.
 * The {@linkplain ParseMode#LEGACY legacy} mode should not be
 * used unless the application is capable of validating all URI
 * strings before any constructors of this class is invoked.
 *
 * <p> The format of an absolute URI (see the RFCs mentioned above) is:
 * <blockquote><pre>{@code
 *      absoluteURI   = scheme ":" ( hier_part | opaque_part )
 *
 *      scheme        = alpha *( alpha | digit | "+" | "-" | "." )
 *
 *      hier_part     = ( net_path | abs_path ) [ "?" query ]
 *      opaque_part   = uric_no_slash *uric
 *
 *      net_path      = "//" authority [ abs_path ]
 *      abs_path      = "/"  path_segments
 *
 *      authority     = server | reg_name
 *      reg_name      = 1*( unreserved | escaped | "$" | "," |
 *                          ";" | ":" | "@" | "&" | "=" | "+" )
 *      server        = [ [ userinfo "@" ] hostport ]
 *      userinfo      = *( unreserved | escaped |
 *                         ";" | ":" | "&" | "=" | "+" | "$" | "," )
 *
 *      hostport      = host [ ":" port ]
 *      host          = hostname | IPv4address | IPv6reference
 *      port          = *digit
 *
 *      IPv6reference = "[" IPv6address "]"
 *      IPv6address   = hexpart [ ":" IPv4address ]
 *      IPv4address   = 1*3digit "." 1*3digit "." 1*3digit "." 1*3digit
 *      hexpart       = hexseq | hexseq "::" [ hexseq ] | "::" [ hexseq ]
 *      hexseq        = hex4 *( ":" hex4)
 *      hex4          = 1*4hex
 *
 *      path          = [ abs_path | opaque_part ]
 *      path_segments = segment *( "/" segment )
 *      segment       = *pchar *( ";" param )
 *      param         = *pchar
 *      pchar         = unreserved | escaped |
 *                      ":" | "@" | "&" | "=" | "+" | "$" | ","
 *
 *      query         = *uric
 *
 *      uric          = reserved | unreserved | escaped
 *      uric_no_slash = unreserved | escaped | ";" | "?" | ":" | "@" |
 *                      "&" | "=" | "+" | "$" | ","
 *      reserved      = ";" | "/" | "?" | ":" | "@" | "&" | "=" | "+" |
 *                      "$" | "," | "[" | "]"
 *      unreserved    = alphanum | mark
 *      mark          = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
 *      escaped       = "%" hex hex
 *      unwise        = "{" | "}" | "|" | "\" | "^" | "`"
 * }</pre></blockquote>
 *
 * <p> Currently URIs containing {@code userinfo} or {@code reg_name}
 * are not supported.
 * The {@code opaque_part} of a non-hierarchical URI is treated as if
 * if were a {@code path} without a leading slash.
 */


public class Uri {

    // three parsing modes
    public enum ParseMode {
        /**
         * Strict validation mode.
         * Validate the URI syntactically using {@link java.net.URI}.
         * Rejects URI fragments unless explicitly supported by the
         * subclass.
         */
        STRICT,
        /**
         * Compatibility mode. The URI authority is syntactically validated.
         * Rejects URI fragments unless explicitly supported by the
         * subclass.
         * This is the default.
         */
        COMPAT,
        /**
         * Legacy mode. In this mode, no validation is performed.
         */
        LEGACY
     }

    protected String uri;
    protected String scheme;
    protected String host = null;
    protected int port = -1;
    protected boolean hasAuthority;
    protected String path;
    protected String query = null;
    protected String fragment;


    /**
     * Creates a Uri object given a URI string.
     */
    public Uri(String uri) throws MalformedURLException {
        init(uri);
    }

    /**
     * Creates an uninitialized Uri object. The init() method must
     * be called before any other Uri methods.
     */
    protected Uri() {
    }

    /**
     * The parse mode for parsing this URI.
     * The default is {@link ParseMode#COMPAT}.
     * @return the parse mode for parsing this URI.
     */
    protected ParseMode parseMode() {
        return ParseMode.COMPAT;
    }

    /**
     * Initializes a Uri object given a URI string.
     * This method must be called exactly once, and before any other Uri
     * methods.
     */
    protected void init(String uri) throws MalformedURLException {
        this.uri = uri;
        parse(uri, parseMode());
    }

    /**
     * Returns the URI's scheme.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Returns the host from the URI's authority part, or null
     * if no host is provided.  If the host is an IPv6 literal, the
     * delimiting brackets are part of the returned value (see
     * {@link java.net.URI#getHost}).
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port from the URI's authority part, or -1 if
     * no port is provided.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the URI's path.  The path is never null.  Note that a
     * slash following the authority part (or the scheme if there is
     * no authority part) is part of the path.  For example, the path
     * of "http://host/a/b" is "/a/b".
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the URI's query part, or null if no query is provided.
     * Note that a query always begins with a leading "?".
     */
    public String getQuery() {
        return query;
    }

    /**
     * Returns the URI as a string.
     */
    public String toString() {
        return uri;
    }

    private void parse(String uri, ParseMode mode) throws MalformedURLException {
        switch (mode) {
            case STRICT -> parseStrict(uri);
            case COMPAT -> parseCompat(uri);
            case LEGACY -> parseLegacy(uri);
        }
    }

    private MalformedURLException newMalformedURLException(String prefix, String msg) {
        return new MalformedURLException(prefix +
                                         formatMsg(filterNonSocketInfo(msg)
                                             .prefixWith(prefix.isEmpty()? "" : ": ")));
    }

    /*
     * Parses a URI string and sets this object's fields accordingly.
     * Use java.net.URI to validate the uri string syntax
     */
    private void parseStrict(String uri) throws MalformedURLException {
        try {
            if (!isSchemeOnly(uri)) {
                URI u = new URI(uri);
                scheme = u.getScheme();
                if (scheme == null) throw newMalformedURLException("Invalid URI", uri);
                var auth = u.getRawAuthority();
                hasAuthority = auth != null;
                if (hasAuthority) {
                    var host = u.getHost();
                    var port = u.getPort();
                    if (host != null) this.host = host;
                    if (port != -1) this.port = port;
                    String hostport = (host == null ? "" : host)
                            + (port == -1 ? "" : (":" + port));
                    if (!hostport.equals(auth)) {
                        // throw if we have user info or regname
                        throw newMalformedURLException("unsupported authority", auth);
                    }
                }
                path = u.getRawPath();
                if (u.getRawQuery() != null) {
                    query = "?" + u.getRawQuery();
                }
                if (u.getRawFragment() != null) {
                    if (!acceptsFragment()) {
                        throw newMalformedURLException("URI fragments not supported", uri);
                    }
                    fragment = "#" + u.getRawFragment();
                }
            } else {
                // scheme-only URIs are not supported by java.net.URI
                // validate the URI by appending "/" to the uri string.
                var s = uri.substring(0, uri.indexOf(':'));
                URI u = new URI(uri + "/");
                if (!s.equals(u.getScheme())
                        || !checkSchemeOnly(uri, u.getScheme())) {
                    throw newInvalidURISchemeException(uri);
                }
                scheme = s;
                path = "";
            }
        } catch (URISyntaxException e) {
            var mue =  newMalformedURLException("", e.getMessage());
            mue.initCause(e);
            throw mue;
        }
    }


    /*
     * Parses a URI string and sets this object's fields accordingly.
     * Compatibility mode. Use java.net.URI to validate the syntax of
     * the uri string authority.
     */
    private void parseCompat(String uri) throws MalformedURLException {
        int i;  // index into URI

        i = uri.indexOf(':');                           // parse scheme
        int slash = uri.indexOf('/');
        int qmark = uri.indexOf('?');
        int fmark = uri.indexOf('#');
        if (i < 0 || slash > 0 && i > slash || qmark > 0 && i > qmark || fmark > 0 && i > fmark) {
            throw newMalformedURLException("Invalid URI", uri);
        }
        if (fmark > -1) {
            if (!acceptsFragment()) {
                throw newMalformedURLException("URI fragments not supported", uri);
            }
        }
        if (i == uri.length() - 1) {
            if (!isSchemeOnly(uri)) {
                throw newInvalidURISchemeException(uri);
            }
        }
        scheme = uri.substring(0, i);
        i++;                                            // skip past ":"

        hasAuthority = uri.startsWith("//", i);
        if (fmark > -1 && qmark > fmark) qmark = -1;
        int endp = qmark > -1 ? qmark : fmark > -1 ? fmark : uri.length();
        if (hasAuthority) {                             // parse "//host:port"
            i += 2;                                     // skip past "//"
            int starta = i;
            // authority ends at the first appearance of /, ?, or #
            int enda = uri.indexOf('/', i);
            if (enda == -1 || qmark > -1 && qmark < enda) enda = qmark;
            if (enda == -1 || fmark > -1 && fmark < enda) enda = fmark;
            if (enda < 0) {
                enda = uri.length();
            }
            if (uri.startsWith(":", i)) {
                // LdapURL supports empty host.
                i++;
                host = "";
                if (enda > i) {
                    port = Integer.parseInt(uri.substring(i, enda));
                }
            } else {
                // Use URI to parse authority
                try {
                    // URI requires at least one char after authority:
                    // we use "/" and expect that the resulting URI path
                    // will be exactly "/".
                    URI u = new URI(uri.substring(0, enda) + "/");
                    String auth = uri.substring(starta, enda);
                    host = u.getHost();
                    port = u.getPort();
                    String p = u.getRawPath();
                    String q = u.getRawQuery();
                    String f = u.getRawFragment();
                    String ui = u.getRawUserInfo();
                    if (ui != null) {
                        throw newMalformedURLException("user info not supported in authority", ui);
                    }
                    if (!"/".equals(p)) {
                        throw newMalformedURLException("invalid authority", auth);
                    }
                    if (q != null) {
                        throw newMalformedURLException("invalid trailing characters in authority '?'", "?" + q);
                    }
                    if (f != null) {
                        throw newMalformedURLException("invalid trailing characters in authority: '#'", "#" + f);
                    }
                    String hostport = (host == null ? "" : host)
                            + (port == -1?"":(":" + port));
                    if (!auth.equals(hostport)) {
                        // throw if we have user info or regname
                        throw newMalformedURLException("Authority component is not server-based, " +
                                              "or contains user info. Unsupported authority", auth);
                    }
                } catch (URISyntaxException e) {
                    var mue = newMalformedURLException("", e.getMessage());
                    mue.initCause(e);
                    throw mue;
                }
            }
            i = enda;
        }
        path = uri.substring(i, endp);
        // look for query
        if (qmark > -1) {
            if (fmark > -1) {
                query = uri.substring(qmark, fmark);
            } else {
                query = uri.substring(qmark);
            }
        }
        if (fmark > -1) {
            fragment = uri.substring(fmark);
        }
    }

    /**
     * A subclass of {@code Uri} that supports scheme only
     * URIs can override this method and return true in the
     * case where the URI string is a scheme-only URI that
     * the subclass supports.
     * @implSpec
     * The default implementation of this method returns false,
     * always.
     * @param uri An URI string
     * @return if this is a scheme-only URI supported by the subclass
     */
    protected boolean isSchemeOnly(String uri) {
        return false;
    }

    /**
     * Checks whether the given uri string should be considered
     * as a scheme-only URI. For some protocols - e.g. DNS, we
     * might accept "dns://" as a valid URL denoting default DNS.
     * For others - we might only accept "scheme:".
     * @implSpec
     * The default implementation of this method returns true if
     * the URI is of the form {@code "<scheme>:"} with nothing
     * after the scheme delimiter.
     * @param uri the URI
     * @param scheme the scheme
     * @return true if the URI should be considered as a scheme-only
     *         URI supported by this URI scheme.
     */
    protected boolean checkSchemeOnly(String uri, String scheme) {
        return uri.equals(scheme + ":");
    }

    /**
     * Creates a {@code MalformedURLException} to be thrown when the
     * URI scheme is not supported.
     *
     * @param uri the URI string
     * @return a {@link MalformedURLException}
     */
    protected MalformedURLException newInvalidURISchemeException(String uri) {
        return new MalformedURLException("Invalid URI scheme: " + uri);
    }

    /**
     * Whether fragments are supported.
     * @implSpec
     * The default implementation of this method retturns false, always.
     * @return true if fragments are supported.
     */
    protected boolean acceptsFragment() {
        return parseMode() == ParseMode.LEGACY;
    }

    /*
     * Parses a URI string and sets this object's fields accordingly.
     * Legacy parsing mode.
     */
    private void parseLegacy(String uri) throws MalformedURLException {
        int i;  // index into URI

        i = uri.indexOf(':');                           // parse scheme
        if (i < 0) {
            throw new MalformedURLException("Invalid URI: " + uri);
        }
        scheme = uri.substring(0, i);
        i++;                                            // skip past ":"

        hasAuthority = uri.startsWith("//", i);
        if (hasAuthority) {                             // parse "//host:port"
            i += 2;                                     // skip past "//"
            int slash = uri.indexOf('/', i);
            if (slash < 0) {
                slash = uri.length();
            }
            if (uri.startsWith("[", i)) {               // at IPv6 literal
                int brac = uri.indexOf(']', i + 1);
                if (brac < 0 || brac > slash) {
                    throw new MalformedURLException("Invalid URI: " + uri);
                }
                host = uri.substring(i, brac + 1);      // include brackets
                i = brac + 1;                           // skip past "[...]"
            } else {                                    // at host name or IPv4
                int colon = uri.indexOf(':', i);
                int hostEnd = (colon < 0 || colon > slash)
                    ? slash
                    : colon;
                if (i < hostEnd) {
                    host = uri.substring(i, hostEnd);
                }
                i = hostEnd;                            // skip past host
            }

            if ((i + 1 < slash) &&
                        uri.startsWith(":", i)) {       // parse port
                i++;                                    // skip past ":"
                port = Integer.parseInt(uri.substring(i, slash));
            }
            i = slash;                                  // skip to path
        }
        int qmark = uri.indexOf('?', i);                // look for query
        if (qmark < 0) {
            path = uri.substring(i);
        } else {
            path = uri.substring(i, qmark);
            query = uri.substring(qmark);
        }
    }

/*
    // Debug
    public static void main(String args[]) throws MalformedURLException {
        for (int i = 0; i < args.length; i++) {
            Uri uri = new Uri(args[i]);

            String h = (uri.getHost() != null) ? uri.getHost() : "";
            String p = (uri.getPort() != -1) ? (":" + uri.getPort()) : "";
            String a = uri.hasAuthority ? ("//" + h + p) : "";
            String q = (uri.getQuery() != null) ? uri.getQuery() : "";

            String str = uri.getScheme() + ":" + a + uri.getPath() + q;
            if (! uri.toString().equals(str)) {
                System.out.println(str);
            }
            System.out.println(h);
        }
    }
*/
}
