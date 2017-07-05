/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.http.client;

import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * An object which represents an HTTP cookie.  Can be constructed by
 * parsing a string from the set-cookie: header.
 *
 * Syntax: Set-Cookie: NAME=VALUE; expires=DATE;
 *             path=PATH; domain=DOMAIN_NAME; secure
 *
 * All but the first field are optional.
 *
 * @author WS Development Team
 */
public class HttpCookie {

    private Date expirationDate = null;
    private String nameAndValue;
    private String path;
    private String domain;
    private boolean isSecure = false;

    public HttpCookie(String cookieString) {
        parseCookieString(cookieString);
    }

    //
    // Constructor for use by the bean
    //
    public HttpCookie(
        Date expirationDate,
        String nameAndValue,
        String path,
        String domain,
        boolean isSecure) {

        this.expirationDate = expirationDate;
        this.nameAndValue = nameAndValue;
        this.path = path;
        this.domain = stripPort(domain);
        this.isSecure = isSecure;
    }

    public HttpCookie(URL url, String cookieString) {
        parseCookieString(cookieString);
        applyDefaults(url);
    }

    /**
     * Fills in default values for domain, path, etc. from the URL
     * after creation of the cookie.
     */
    private void applyDefaults(URL url) {

        if (domain == null) {
            domain = url.getHost();

            // REMIND: record the port
        }

        if (path == null) {
            path = url.getFile();

            // The documentation for cookies say that the path is
            // by default, the path of the document, not the filename of the
            // document.  This could be read as not including that document
            // name itself, just its path (this is how NetScape inteprets it)
            // so amputate the document name!
            int last = path.lastIndexOf("/");

            if (last > -1) {
                path = path.substring(0, last);
            }
        }
    }

    private String stripPort(String domainName) {

        int index = domainName.indexOf(':');

        if (index == -1) {
            return domainName;
        }

        return domainName.substring(0, index);
    }

    /**
     * Parse the given string into its individual components, recording them
     * in the member variables of this object.
     */
    private void parseCookieString(String cookieString) {

        StringTokenizer tokens = new StringTokenizer(cookieString, ";");

        if (!tokens.hasMoreTokens()) {

            // REMIND: make this robust against parse errors
        }

        nameAndValue = tokens.nextToken().trim();

        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();

            if (token.equalsIgnoreCase("secure")) {
                isSecure = true;
            } else {
                int equIndex = token.indexOf("=");

                if (equIndex < 0) {
                    continue;

                    // REMIND: malformed cookie
                }

                String attr = token.substring(0, equIndex);
                String val = token.substring(equIndex + 1);

                if (attr.equalsIgnoreCase("path")) {
                    path = val;
                } else if (attr.equalsIgnoreCase("domain")) {
                    if (val.indexOf(".") == 0) {

                        // spec seems to allow for setting the domain in
                        // the form 'domain=.eng.sun.com'.  We want to
                        // trim off the leading '.' so we can allow for
                        // both leading dot and non leading dot forms
                        // without duplicate storage.
                        domain = stripPort(val.substring(1));
                    } else {
                        domain = stripPort(val);
                    }
                } else if (attr.equalsIgnoreCase("expires")) {
                    expirationDate = parseExpireDate(val);
                } else {

                    // unknown attribute -- do nothing
                }
            }
        }
    }

    //======================================================================
    //
    // Accessor functions
    //
    public String getNameValue() {
        return nameAndValue;
    }

    /**
     * Returns just the name part of the cookie
     */
    public String getName() {

        int index = nameAndValue.indexOf("=");

        return nameAndValue.substring(0, index);
    }

    /**
     * Returns the domain of the cookie as it was presented
     */
    public String getDomain() {

        // REMIND: add port here if appropriate
        return domain;
    }

    public String getPath() {
        return path;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    boolean hasExpired() {

        if (expirationDate == null) {
            return false;
        }

        return (expirationDate.getTime() <= System.currentTimeMillis());
    }

    /**
     * Returns true if the cookie has an expiration date (meaning it's
     * persistent), and if the date nas not expired;
     */
    boolean isSaveable() {
        return (expirationDate != null)
            && (expirationDate.getTime() > System.currentTimeMillis());
    }

    public boolean isSecure() {
        return isSecure;
    }

    private Date parseExpireDate(String dateString) {

        // format is wdy, DD-Mon-yyyy HH:mm:ss GMT
        RfcDateParser parser = new RfcDateParser(dateString);
        Date theDate = parser.getDate();

        return theDate;
    }

    public String toString() {

        String result = nameAndValue;

        if (expirationDate != null) {
            result += "; expires=" + expirationDate;
        }

        if (path != null) {
            result += "; path=" + path;
        }

        if (domain != null) {
            result += "; domain=" + domain;
        }

        if (isSecure) {
            result += "; secure";
        }

        return result;
    }
}
