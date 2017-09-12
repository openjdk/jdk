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

/*
 * @(#)ContentType.java       1.7 02/03/27
 */



package com.sun.xml.internal.ws.encoding;

import javax.xml.ws.WebServiceException;

/**
 * This class represents a MIME ContentType value. It provides
 * methods to parse a ContentType string into individual components
 * and to generate a MIME style ContentType string.
 *
 * @version 1.7, 02/03/27
 * @author  John Mani
 */
public final class ContentType {

    private String primaryType; // primary type
    private String subType;     // subtype
    private ParameterList list; // parameter list

    /**
     * Constructor that takes a Content-Type string. The String
     * is parsed into its constituents: primaryType, subType
     * and parameters. A ParseException is thrown if the parse fails.
     *
     * @param   s       the Content-Type string.
     * @exception WebServiceException if the parse fails.
     */
    public ContentType(String s) throws WebServiceException {
        HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);
        HeaderTokenizer.Token tk;

        // First "type" ..
        tk = h.next();
        if (tk.getType() != HeaderTokenizer.Token.ATOM)
            throw new WebServiceException();
        primaryType = tk.getValue();

        // The '/' separator ..
        tk = h.next();
        if ((char)tk.getType() != '/')
            throw new WebServiceException();

        // Then "subType" ..
        tk = h.next();
        if (tk.getType() != HeaderTokenizer.Token.ATOM)
            throw new WebServiceException();
        subType = tk.getValue();

        // Finally parameters ..
        String rem = h.getRemainder();
        if (rem != null)
            list = new ParameterList(rem);
    }


    /**
     * Return the primary type.
     * @return the primary type
     */
    public String getPrimaryType() {
            return primaryType;
    }

    /**
     * Return the subType.
     * @return the subType
     */
    public String getSubType() {
            return subType;
    }

    /**
     * Return the MIME type string, without the parameters.
     * The returned value is basically the concatenation of
     * the primaryType, the '/' character and the secondaryType.
     *
     * @return the type
     */
    public String getBaseType() {
            return primaryType + '/' + subType;
    }

    /**
     * Return the specified parameter value. Returns <code>null</code>
     * if this parameter is absent.
     *
     * @param name parameter name
     * @return  parameter value
     */
    public String getParameter(String name) {
        if (list == null)
            return null;

        return list.get(name);
    }

    /**
     * Return a ParameterList object that holds all the available
     * parameters. Returns null if no parameters are available.
     *
     * @return  ParameterList
     */
    public ParameterList getParameterList() {
            return list;
    }

}
