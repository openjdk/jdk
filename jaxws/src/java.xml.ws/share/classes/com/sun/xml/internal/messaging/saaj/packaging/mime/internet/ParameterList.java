/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @(#)ParameterList.java     1.10 03/02/12
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class holds MIME parameters (attribute-value pairs).
 *
 * @version 1.10, 03/02/12
 * @author  John Mani
 */

public final class ParameterList {

    private final HashMap<String, String> list;

    /**
     * No-arg Constructor.
     */
    public ParameterList() {
        this.list = new HashMap<>();
    }

    private ParameterList(HashMap<String, String> m) {
        this.list = m;
    }

    /**
     * Constructor that takes a parameter-list string. The String
     * is parsed and the parameters are collected and stored internally.
     * A ParseException is thrown if the parse fails.
     * Note that an empty parameter-list string is valid and will be
     * parsed into an empty ParameterList.
     *
     * @param   s       the parameter-list string.
     * @exception       ParseException if the parse fails.
     */
    public ParameterList(String s) throws ParseException {
        HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);
        HeaderTokenizer.Token tk;
        int type;
        String name;

        list = new HashMap<>();
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
                    throw new ParseException();
                name = tk.getValue().toLowerCase();

                // expect '='
                tk = h.next();
                if ((char)tk.getType() != '=')
                    throw new ParseException();

                // expect parameter value
                tk = h.next();
                type = tk.getType();
                // parameter value must be a MIME Atom or Quoted String
                if (type != HeaderTokenizer.Token.ATOM &&
                    type != HeaderTokenizer.Token.QUOTEDSTRING)
                    throw new ParseException();

                list.put(name, tk.getValue());
            } else
                throw new ParseException();
        }
    }

    /**
     * Return the number of parameters in this list.
     *
     * @return  number of parameters.
     */
    public int size() {
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
    public String get(String name) {
        return list.get(name.trim().toLowerCase());
    }

    /**
     * Set a parameter. If this parameter already exists, it is
     * replaced by this new value.
     *
     * @param   name    name of the parameter.
     * @param   value   value of the parameter.
     */
    public void set(String name, String value) {
        list.put(name.trim().toLowerCase(), value);
    }

    /**
     * Removes the specified parameter from this ParameterList.
     * This method does nothing if the parameter is not present.
     *
     * @param   name    name of the parameter.
     */
    public void remove(String name) {
        list.remove(name.trim().toLowerCase());
    }

    /**
     * Return an enumeration of the names of all parameters in this
     * list.
     *
     * @return Enumeration of all parameter names in this list.
     */
    public Iterator<String> getNames() {
        return list.keySet().iterator();
    }


    /**
     * Convert this ParameterList into a MIME String. If this is
     * an empty list, an empty string is returned.
     *
     * @return          String
     */
    @Override
    public String toString() {
        return toString(0);
    }

    /**
     * Convert this ParameterList into a MIME String. If this is
     * an empty list, an empty string is returned.
     *
     * The 'used' parameter specifies the number of character positions
     * already taken up in the field into which the resulting parameter
     * list is to be inserted. It's used to determine where to fold the
     * resulting parameter list.
     *
     * @param used      number of character positions already used, in
     *                  the field into which the parameter list is to
     *                  be inserted.
     * @return          String
     */
    public String toString(int used) {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, String>> itr = list.entrySet().iterator();

        while (itr.hasNext()) {
            Map.Entry<String, String> e = itr.next();
            String name = e.getKey();
            String value = quote(e.getValue());
            sb.append("; ");
            used += 2;
            int len = name.length() + value.length() + 1;
            if (used + len > 76) { // overflows ...
                sb.append("\r\n\t"); // .. start new continuation line
                used = 8; // account for the starting <tab> char
            }
            sb.append(name).append('=');
            used += name.length() + 1;
            if (used + value.length() > 76) { // still overflows ...
                // have to fold value
                String s = MimeUtility.fold(used, value);
                sb.append(s);
                int lastlf = s.lastIndexOf('\n');
                if (lastlf >= 0)        // always true
                    used += s.length() - lastlf - 1;
                else
                    used += s.length();
            } else {
                sb.append(value);
                used += value.length();
            }
        }

        return sb.toString();
    }

    // Quote a parameter value token if required.
    private String quote(String value) {
        if ("".equals(value))
                return "\"\"";
        return MimeUtility.quote(value, HeaderTokenizer.MIME);
    }

    public ParameterList copy() {
        return new ParameterList((HashMap<String, String>)list.clone());
    }
}
