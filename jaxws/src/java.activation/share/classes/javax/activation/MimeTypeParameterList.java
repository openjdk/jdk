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

package javax.activation;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Locale;

/**
 * A parameter list of a MimeType
 * as defined in RFC 2045 and 2046. The Primary type of the
 * object must already be stripped off.
 *
 * @see javax.activation.MimeType
 *
 * @since 1.6
 */
public class MimeTypeParameterList {
    private Hashtable parameters;

    /**
     * A string that holds all the special chars.
     */
    private static final String TSPECIALS = "()<>@,;:/[]?=\\\"";


    /**
     * Default constructor.
     */
    public MimeTypeParameterList() {
        parameters = new Hashtable();
    }

    /**
     * Constructs a new MimeTypeParameterList with the passed in data.
     *
     * @param parameterList an RFC 2045, 2046 compliant parameter list.
     * @exception       MimeTypeParseException  if the MIME type can't be parsed
     */
    public MimeTypeParameterList(String parameterList)
                                        throws MimeTypeParseException {
        parameters = new Hashtable();

        //    now parse rawdata
        parse(parameterList);
    }

    /**
     * A routine for parsing the parameter list out of a String.
     *
     * @param parameterList an RFC 2045, 2046 compliant parameter list.
     * @exception       MimeTypeParseException  if the MIME type can't be parsed
     */
    protected void parse(String parameterList) throws MimeTypeParseException {
        if (parameterList == null)
            return;

        int length = parameterList.length();
        if (length <= 0)
            return;

        int i;
        char c;
        for (i = skipWhiteSpace(parameterList, 0);
                i < length && (c = parameterList.charAt(i)) == ';';
                i = skipWhiteSpace(parameterList, i)) {
            int lastIndex;
            String name;
            String value;

            //    eat the ';'
            i++;

            //    now parse the parameter name

            //    skip whitespace
            i = skipWhiteSpace(parameterList, i);

            // tolerate trailing semicolon, even though it violates the spec
            if (i >= length)
                return;

            //    find the end of the token char run
            lastIndex = i;
            while ((i < length) && isTokenChar(parameterList.charAt(i)))
                i++;

            name = parameterList.substring(lastIndex, i).
                                                toLowerCase(Locale.ENGLISH);

            //    now parse the '=' that separates the name from the value
            i = skipWhiteSpace(parameterList, i);

            if (i >= length || parameterList.charAt(i) != '=')
                throw new MimeTypeParseException(
                    "Couldn't find the '=' that separates a " +
                    "parameter name from its value.");

            //    eat it and parse the parameter value
            i++;
            i = skipWhiteSpace(parameterList, i);

            if (i >= length)
                throw new MimeTypeParseException(
                        "Couldn't find a value for parameter named " + name);

            //    now find out whether or not we have a quoted value
            c = parameterList.charAt(i);
            if (c == '"') {
                //    yup it's quoted so eat it and capture the quoted string
                i++;
                if (i >= length)
                    throw new MimeTypeParseException(
                            "Encountered unterminated quoted parameter value.");

                lastIndex = i;

                //    find the next unescaped quote
                while (i < length) {
                    c = parameterList.charAt(i);
                    if (c == '"')
                        break;
                    if (c == '\\') {
                        //    found an escape sequence
                        //    so skip this and the
                        //    next character
                        i++;
                    }
                    i++;
                }
                if (c != '"')
                    throw new MimeTypeParseException(
                        "Encountered unterminated quoted parameter value.");

                value = unquote(parameterList.substring(lastIndex, i));
                //    eat the quote
                i++;
            } else if (isTokenChar(c)) {
                //    nope it's an ordinary token so it
                //    ends with a non-token char
                lastIndex = i;
                while (i < length && isTokenChar(parameterList.charAt(i)))
                    i++;
                value = parameterList.substring(lastIndex, i);
            } else {
                //    it ain't a value
                throw new MimeTypeParseException(
                        "Unexpected character encountered at index " + i);
            }

            //    now put the data into the hashtable
            parameters.put(name, value);
        }
        if (i < length) {
            throw new MimeTypeParseException(
                "More characters encountered in input than expected.");
        }
    }

    /**
     * Return the number of name-value pairs in this list.
     *
     * @return  the number of parameters
     */
    public int size() {
        return parameters.size();
    }

    /**
     * Determine whether or not this list is empty.
     *
     * @return  true if there are no parameters
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }

    /**
     * Retrieve the value associated with the given name, or null if there
     * is no current association.
     *
     * @param name      the parameter name
     * @return          the parameter's value
     */
    public String get(String name) {
        return (String)parameters.get(name.trim().toLowerCase(Locale.ENGLISH));
    }

    /**
     * Set the value to be associated with the given name, replacing
     * any previous association.
     *
     * @param name      the parameter name
     * @param value     the parameter's value
     */
    public void set(String name, String value) {
        parameters.put(name.trim().toLowerCase(Locale.ENGLISH), value);
    }

    /**
     * Remove any value associated with the given name.
     *
     * @param name      the parameter name
     */
    public void remove(String name) {
        parameters.remove(name.trim().toLowerCase(Locale.ENGLISH));
    }

    /**
     * Retrieve an enumeration of all the names in this list.
     *
     * @return  an enumeration of all parameter names
     */
    public Enumeration getNames() {
        return parameters.keys();
    }

    /**
     * Return a string representation of this object.
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.ensureCapacity(parameters.size() * 16);
                        //    heuristic: 8 characters per field

        Enumeration keys = parameters.keys();
        while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            buffer.append("; ");
            buffer.append(key);
            buffer.append('=');
            buffer.append(quote((String)parameters.get(key)));
        }

        return buffer.toString();
    }

    //    below here be scary parsing related things

    /**
     * Determine whether or not a given character belongs to a legal token.
     */
    private static boolean isTokenChar(char c) {
        return ((c > 040) && (c < 0177)) && (TSPECIALS.indexOf(c) < 0);
    }

    /**
     * return the index of the first non white space character in
     * rawdata at or after index i.
     */
    private static int skipWhiteSpace(String rawdata, int i) {
        int length = rawdata.length();
        while ((i < length) && Character.isWhitespace(rawdata.charAt(i)))
            i++;
        return i;
    }

    /**
     * A routine that knows how and when to quote and escape the given value.
     */
    private static String quote(String value) {
        boolean needsQuotes = false;

        //    check to see if we actually have to quote this thing
        int length = value.length();
        for (int i = 0; (i < length) && !needsQuotes; i++) {
            needsQuotes = !isTokenChar(value.charAt(i));
        }

        if (needsQuotes) {
            StringBuffer buffer = new StringBuffer();
            buffer.ensureCapacity((int)(length * 1.5));

            //    add the initial quote
            buffer.append('"');

            //    add the properly escaped text
            for (int i = 0; i < length; ++i) {
                char c = value.charAt(i);
                if ((c == '\\') || (c == '"'))
                    buffer.append('\\');
                buffer.append(c);
            }

            //    add the closing quote
            buffer.append('"');

            return buffer.toString();
        } else {
            return value;
        }
    }

    /**
     * A routine that knows how to strip the quotes and
     * escape sequences from the given value.
     */
    private static String unquote(String value) {
        int valueLength = value.length();
        StringBuffer buffer = new StringBuffer();
        buffer.ensureCapacity(valueLength);

        boolean escaped = false;
        for (int i = 0; i < valueLength; ++i) {
            char currentChar = value.charAt(i);
            if (!escaped && (currentChar != '\\')) {
                buffer.append(currentChar);
            } else if (escaped) {
                buffer.append(currentChar);
                escaped = false;
            } else {
                escaped = true;
            }
        }

        return buffer.toString();
    }
}
