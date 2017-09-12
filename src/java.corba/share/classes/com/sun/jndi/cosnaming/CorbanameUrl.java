/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.cosnaming;

import javax.naming.Name;
import javax.naming.NamingException;

import java.net.MalformedURLException;
import com.sun.jndi.toolkit.corba.CorbaUtils;

/**
 * Extract components of a "corbaname" URL.
 *
 * The format of an corbaname URL is defined in INS 99-12-03 as follows.
 * <pre>{@code
 * corbaname url = "corbaname:" <corbaloc_obj> ["#" <string_name>]
 * corbaloc_obj  = <obj_addr_list> ["/" <key_string>]
 * obj_addr_list = as defined in a corbaloc URL
 * key_string    = as defined in a corbaloc URL
 * string_name   = stringified COS name | empty_string
 * }</pre>
 * Characters in {@code <string_name>} are escaped as follows.
 * US-ASCII alphanumeric characters are not escaped. Any characters outside
 * of this range are escaped except for the following:
 * <pre>{@code
 *        ; / : ? @ & = + $ , - _ . ! ~ * ; ( )
 * }</pre>
 * Escaped characters is escaped by using a % followed by its 2 hexadecimal
 * numbers representing the octet.
 * <p>
 * The corbaname URL is parsed into two parts: a corbaloc URL and a COS name.
 * The corbaloc URL is constructed by concatenation {@code "corbaloc:"} with
 * {@code <corbaloc_obj>}.
 * The COS name is {@code <string_name>} with the escaped characters resolved.
 * <p>
 * A corbaname URL is resolved by:
 * <ol>
 * <li>Construct a corbaloc URL by concatenating {@code "corbaloc:"} and {@code <corbaloc_obj>}.
 * <li>Resolve the corbaloc URL to a NamingContext by using
 * <pre>{@code
 *     nctx = ORB.string_to_object(corbalocUrl);
 * }</pre>
 * <li>Resolve {@code <string_name>} in the NamingContext.
 * </ol>
 *
 * @author Rosanna Lee
 */

public final class CorbanameUrl {
    private String stringName;
    private String location;

    /**
     * Returns a possibly empty but non-null string that is the "string_name"
     * portion of the URL.
     */
    public String getStringName() {
        return stringName;
    }

    public Name getCosName() throws NamingException {
        return CNCtx.parser.parse(stringName);
    }

    public String getLocation() {
        return "corbaloc:" + location;
    }

    public CorbanameUrl(String url) throws MalformedURLException {

        if (!url.startsWith("corbaname:")) {
            throw new MalformedURLException("Invalid corbaname URL: " + url);
        }

        int addrStart = 10;  // "corbaname:"

        int addrEnd = url.indexOf('#', addrStart);
        if (addrEnd < 0) {
            addrEnd = url.length();
            stringName = "";
        } else {
            stringName = CorbaUtils.decode(url.substring(addrEnd+1));
        }
        location = url.substring(addrStart, addrEnd);

        int keyStart = location.indexOf('/');
        if (keyStart >= 0) {
            // Has key string
            if (keyStart == (location.length() -1)) {
                location += "NameService";
            }
        } else {
            location += "/NameService";
        }
    }
/*
    // for testing only
    public static void main(String[] args) {
        try {
            CorbanameUrl url = new CorbanameUrl(args[0]);

            System.out.println("location: " + url.getLocation());
            System.out.println("string name: " + url.getStringName());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
*/
}
