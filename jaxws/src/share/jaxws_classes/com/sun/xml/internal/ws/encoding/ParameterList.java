/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.encoding;

import javax.xml.ws.WebServiceException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class holds MIME parameters (attribute-value pairs).
 *
 * @version 1.10, 03/02/12
 * @author  John Mani
 */

final class ParameterList {

    private final Map<String, String> list;

    /**
     * Constructor that takes a parameter-list string. The String
     * is parsed and the parameters are collected and stored internally.
     * A ParseException is thrown if the parse fails.
     * Note that an empty parameter-list string is valid and will be
     * parsed into an empty ParameterList.
     *
     * @param   s       the parameter-list string.
     * @exception WebServiceException if the parse fails.
     */
    ParameterList(String s) {
        HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);
        HeaderTokenizer.Token tk;
        int type;
        String name;

        list = new HashMap<String, String>();
        while (true) {
            tk = h.next();
            type = tk.getType();

            if (type == HeaderTokenizer.Token.EOF) // done
            return;

            if ((char)type == ';') {
                // expect parameter name
                tk = h.next();
                // tolerate trailing semicolon, even though it violates the spec
                if (tk.getType() == HeaderTokenizer.Token.EOF)
                    return;
                // parameter name must be a MIME Atom
                if (tk.getType() != HeaderTokenizer.Token.ATOM)
                    throw new WebServiceException();
                name = tk.getValue().toLowerCase();

                // expect '='
                tk = h.next();
                if ((char)tk.getType() != '=')
                    throw new WebServiceException();

                // expect parameter value
                tk = h.next();
                type = tk.getType();
                // parameter value must be a MIME Atom or Quoted String
                if (type != HeaderTokenizer.Token.ATOM &&
                    type != HeaderTokenizer.Token.QUOTEDSTRING)
                    throw new WebServiceException();

                list.put(name, tk.getValue());
            } else
                throw new WebServiceException();
        }
    }

    /**
     * Return the number of parameters in this list.
     *
     * @return  number of parameters.
     */
    int size() {
            return list.size();
    }

    /**
     * Returns the value of the specified parameter. Note that
     * parameter names are case-insensitive.
     *
     * @param name      parameter name.
     * @return          Value of the parameter. Returns
     *                  <code>null</code> if the parameter is not
     *                  present.
     */
    String get(String name) {
            return list.get(name.trim().toLowerCase());
    }


    /**
     * Return an enumeration of the names of all parameters in this
     * list.
     *
     * @return Enumeration of all parameter names in this list.
     */
    Iterator<String> getNames() {
            return list.keySet().iterator();
    }

}
