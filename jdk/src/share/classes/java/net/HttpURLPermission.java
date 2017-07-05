/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.security.Permission;

/**
 * Represents permission to access a resource or set of resources defined by a
 * given http or https url, and for a given set of user-settable request methods
 * and request headers. The <i>name</i> of the permission is the url string.
 * The <i>actions</i> string is a concatenation of the request methods and headers.
 * The range of method and header names is not restricted by this class.
 * <p><b>The url</b><p>
 * The url string is also used to instantiate a {@link URI} object which is
 * used for comparison with other HttpURLPermission instances. Therefore, any
 * references in this specification to url, mean this URI object.
 * The path component of the url comprises a sequence of path segments, separated
 * by '/' characters. The path is specified in a similar way to the path
 * in {@link java.io.FilePermission}. There are three different ways
 * as the following examples show:
 * <table border>
 * <caption>URL Examples</caption>
 * <tr><th>Example url</th><th>Description</th></tr>
 * <tr><td style="white-space:nowrap;">http://www.oracle.com/a/b/c.html</td>
 *   <td>A url which identifies a specific (single) resource</td>
 * </tr>
 * <tr><td>http://www.oracle.com/a/b/*</td>
 *   <td>The '*' character refers to all resources in the same "directory" - in
 *       other words all resources with the same number of path components, and
 *       which only differ in the final path component, represented by the '*'.
 *   </td>
 * </tr>
 * <tr><td>http://www.oracle.com/a/b/-</td>
 *   <td>The '-' character refers to all resources recursively below the
 *       preceding path (eg. http://www.oracle.com/a/b/c/d/e.html matches this
 *       example).
 *   </td>
 * </tr>
 * </table>
 * <p>
 * The '*' and '-' may only be specified in the final segment of a path and must be
 * the only character in that segment. Any query or fragment components of the
 * url are ignored when constructing HttpURLPermissions.
 * <p>
 * As a special case, urls of the form, "http:*" or "https:*" are accepted to
 * mean any url of the given scheme.
 * <p><b>The actions string</b><p>
 * The actions string of a HttpURLPermission is a concatenation of the <i>method list</i>
 * and the <i>request headers list</i>. These are lists of the permitted HTTP request
 * methods and permitted request headers of the permission (respectively). The two lists
 * are separated by a colon ':' character and elements of each list are comma separated.
 * Some examples are:
 * <pre>
 *         "POST,GET,DELETE"
 *         "GET:X-Foo-Request,X-Bar-Request"
 *         "POST,GET:Header1,Header2"
 * </pre>
 * The first example specifies the methods: POST, GET and DELETE, but no request headers.
 * The second example specifies one request method and two headers. The third
 * example specifies two request methods, and two headers.
 * <p>
 * The colon separator need not be present if the request headers list is empty.
 * No white-space is permitted in the actions string. The action strings supplied to
 * the HttpURLPermission constructors are case-insensitive and are normalized by converting
 * method names to upper-case and header names to the form defines in RFC2616 (lower case
 * with initial letter of each word capitalized). Either list can contain a wild-card '*'
 * character which signifies all request methods or headers respectively.
 * <p>
 * Note. Depending on the context of use, some request methods and headers may be permitted
 * at all times, and others may not be permitted at any time. For example, the
 * HTTP protocol handler might disallow certain headers such as Content-Length
 * from being set by application code, regardless of whether the security policy
 * in force, permits it.
 *
 * @since 1.8
 */
public final class HttpURLPermission extends Permission {

    private static final long serialVersionUID = -2702463814894478682L;

    private transient URI uri;
    private transient List<String> methods;
    private transient List<String> requestHeaders;

    // serialized field
    private String actions;

    /**
     * Creates a new HttpURLPermission from a url string and which permits the given
     * request methods and user-settable request headers.
     * The name of the permission is its url string. Only the scheme, authority
     * and path components of the url are used. Any fragment or query
     * components are ignored. The permissions action string is as specified above.
     *
     * @param url the url string
     *
     * @param actions the actions string
     *
     * @throws    IllegalArgumentException if url does not result in a valid {@link URI},
     *            its scheme is not http or https, or if actions contains white-space.
     */
    public HttpURLPermission(String url, String actions) {
        super(url);
        init(actions);
    }

    private void init(String actions) {
        URI uri = parseURI(getName());
        int colon = actions.indexOf(':');
        if (actions.lastIndexOf(':') != colon) {
            throw new IllegalArgumentException("invalid actions string");
        }

        String methods, headers;
        if (colon == -1) {
            methods = actions;
            headers = "";
        } else {
            methods = actions.substring(0, colon);
            headers = actions.substring(colon+1);
        }

        List<String> l = normalizeMethods(methods);
        Collections.sort(l);
        this.methods = Collections.unmodifiableList(l);

        l = normalizeHeaders(headers);
        Collections.sort(l);
        this.requestHeaders = Collections.unmodifiableList(l);

        this.actions = actions();
        this.uri = uri;
    }

    /**
     * Creates a HttpURLPermission with the given url string and unrestricted
     * methods and request headers by invoking the two argument
     * constructor as follows: HttpURLPermission(url, "*:*")
     *
     * @param url the url string
     *
     * @throws    IllegalArgumentException if url does not result in a valid {@link URI}
     */
    public HttpURLPermission(String url) {
        this(url, "*:*");
    }

    /**
     * Returns the normalized method list and request
     * header list, in the form:
     * <pre>
     *      "method-names : header-names"
     * </pre>
     * <p>
     * where method-names is the list of methods separated by commas
     * and header-names is the list of permitted headers separated by commas.
     * There is no white space in the returned String. If header-names is empty
     * then the colon separator will not be present.
     */
    public String getActions() {
        return actions;
    }

    /**
     * Checks if this HttpURLPermission implies the given permission.
     * Specifically, the following checks are done as if in the
     * following sequence:
     * <p><ul>
     * <li>if 'p' is not an instance of HttpURLPermission return false</li>
     * <li>if any of p's methods are not in this's method list, and if
     *     this's method list is not equal to "*", then return false.</li>
     * <li>if any of p's headers are not in this's request header list, and if
     *     this's request header list is not equal to "*", then return false.</li>
     * <li>if this's url is equal to p's url , then return true</li>
     * <li>if this's url scheme is not equal to p's url scheme return false</li>
     * <li>if the scheme specific part of this's url is '*' return true</li>
     * <li>if this's url authority is not equal to p's url authority
     *     return false</li>
     * <li>if the path or paths specified by p's url are contained in the
     *     set of paths specified by this's url, then return true
     * <li>otherwise, return false</li>
     * </ul>
     * <p>Some examples of how paths are matched are shown below:
     * <p><table border>
     * <caption>Examples of Path Matching</caption>
     * <tr><th>this's path</th><th>p's path</th><th>match</th></tr>
     * <tr><td>/a/b</td><td>/a/b</td><td>yes</td></tr>
     * <tr><td>/a/b/*</td><td>/a/b/c</td><td>yes</td></tr>
     * <tr><td>/a/b/*</td><td>/a/b/c/d</td><td>no</td></tr>
     * <tr><td>/a/b/-</td><td>/a/b/c/d</td><td>yes</td></tr>
     * <tr><td>/a/b/-</td><td>/a/b/c/d/e</td><td>yes</td></tr>
     * <tr><td>/a/b/-</td><td>/a/b/c/*</td><td>yes</td></tr>
     * <tr><td>/a/b/*</td><td>/a/b/c/-</td><td>no</td></tr>
     * </table>
     */
    public boolean implies(Permission p) {
        if (! (p instanceof HttpURLPermission)) {
            return false;
        }

        HttpURLPermission that = (HttpURLPermission)p;

        if (!this.methods.get(0).equals("*") &&
                Collections.indexOfSubList(this.methods, that.methods) == -1) {
            return false;
        }

        if (this.requestHeaders.isEmpty() && !that.requestHeaders.isEmpty()) {
            return false;
        }

        if (!this.requestHeaders.isEmpty() &&
            !this.requestHeaders.get(0).equals("*") &&
             Collections.indexOfSubList(this.requestHeaders,
                                        that.requestHeaders) == -1) {
            return false;
        }

        if (this.uri.equals(that.uri)) {
            return true;
        }

        if (!this.uri.getScheme().equals(that.uri.getScheme())) {
            return false;
        }

        if (this.uri.getSchemeSpecificPart().equals("*")) {
            return true;
        }

        String thisAuthority = this.uri.getAuthority();

            if (thisAuthority != null &&
                    !thisAuthority.equals(that.uri.getAuthority())) {
            return false;
        }

        String thispath = this.uri.getPath();
        String thatpath = that.uri.getPath();

        if (thispath.endsWith("/-")) {
            String thisprefix = thispath.substring(0, thispath.length() - 1);
            return thatpath.startsWith(thisprefix);
            }

        if (thispath.endsWith("/*")) {
            String thisprefix = thispath.substring(0, thispath.length() - 1);
            if (!thatpath.startsWith(thisprefix)) {
                return false;
            }
            String thatsuffix = thatpath.substring(thisprefix.length());
            // suffix must not contain '/' chars
            if (thatsuffix.indexOf('/') != -1) {
                return false;
            }
            if (thatsuffix.equals("-")) {
                return false;
            }
            return true;
        }
        return false;
    }


    /**
     * Returns true if, this.getActions().equals(p.getActions())
     * and p's url equals this's url.  Returns false otherwise.
     */
    public boolean equals(Object p) {
        if (!(p instanceof HttpURLPermission)) {
            return false;
        }
        HttpURLPermission that = (HttpURLPermission)p;
        return this.getActions().equals(that.getActions()) &&
                  this.uri.equals(that.uri);
    }

    /**
     * Returns a hashcode calculated from the hashcode of the
     * actions String and the url
     */
    public int hashCode() {
        return getActions().hashCode() + uri.hashCode();
    }


    private List<String> normalizeMethods(String methods) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (int i=0; i<methods.length(); i++) {
            char c = methods.charAt(i);
            if (c == ',') {
                String s = b.toString();
                if (s.length() > 0)
                    l.add(s);
                b = new StringBuilder();
            } else if (c == ' ' || c == '\t') {
                throw new IllegalArgumentException("white space not allowed");
            } else {
                if (c >= 'a' && c <= 'z') {
                    c += 'A' - 'a';
                }
                b.append(c);
            }
        }
        String s = b.toString();
        if (s.length() > 0)
            l.add(s);
        return l;
    }

    private List<String> normalizeHeaders(String headers) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i=0; i<headers.length(); i++) {
            char c = headers.charAt(i);
            if (c >= 'a' && c <= 'z') {
                if (capitalizeNext) {
                    c += 'A' - 'a';
                    capitalizeNext = false;
                }
                b.append(c);
            } else if (c == ' ' || c == '\t') {
                throw new IllegalArgumentException("white space not allowed");
            } else if (c == '-') {
                    capitalizeNext = true;
                b.append(c);
            } else if (c == ',') {
                String s = b.toString();
                if (s.length() > 0)
                    l.add(s);
                b = new StringBuilder();
                capitalizeNext = true;
            } else {
                capitalizeNext = false;
                b.append(c);
            }
        }
        String s = b.toString();
        if (s.length() > 0)
            l.add(s);
        return l;
    }

    private URI parseURI(String url) {
        URI u = URI.create(url);
        String scheme = u.getScheme();
        if (!(scheme.equalsIgnoreCase("http") ||
             scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException ("unexpected URL scheme");
        }
        if (!u.getSchemeSpecificPart().equals("*")) {
            u = URI.create(scheme + "://" + u.getRawAuthority() + u.getRawPath());
        }
        return u;
    }

    private String actions() {
        StringBuilder b = new StringBuilder();
        for (String s : methods) {
            b.append(s);
        }
        b.append(":");
        for (String s : requestHeaders) {
            b.append(s);
        }
        return b.toString();
    }
    /**
     * restore the state of this object from stream
     */
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField fields = s.readFields();
        String actions = (String)fields.get("actions", null);

        init(actions);
    }
}
