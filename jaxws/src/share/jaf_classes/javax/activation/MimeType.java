/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.Locale;

/**
 * A Multipurpose Internet Mail Extension (MIME) type, as defined
 * in RFC 2045 and 2046.
 *
 * @since 1.6
 */
public class MimeType implements Externalizable {

    private String    primaryType;
    private String    subType;
    private MimeTypeParameterList parameters;

    /**
     * A string that holds all the special chars.
     */
    private static final String TSPECIALS = "()<>@,;:/[]?=\\\"";

    /**
     * Default constructor.
     */
    public MimeType() {
        primaryType = "application";
        subType = "*";
        parameters = new MimeTypeParameterList();
    }

    /**
     * Constructor that builds a MimeType from a String.
     *
     * @param rawdata   the MIME type string
     */
    public MimeType(String rawdata) throws MimeTypeParseException {
        parse(rawdata);
    }

    /**
     * Constructor that builds a MimeType with the given primary and sub type
     * but has an empty parameter list.
     *
     * @param primary   the primary MIME type
     * @param sub       the MIME sub-type
     * @exception       MimeTypeParseException  if the primary type or subtype
     *                                          is not a valid token
     */
    public MimeType(String primary, String sub) throws MimeTypeParseException {
        //    check to see if primary is valid
        if (isValidToken(primary)) {
            primaryType = primary.toLowerCase(Locale.ENGLISH);
        } else {
            throw new MimeTypeParseException("Primary type is invalid.");
        }

        //    check to see if sub is valid
        if (isValidToken(sub)) {
            subType = sub.toLowerCase(Locale.ENGLISH);
        } else {
            throw new MimeTypeParseException("Sub type is invalid.");
        }

        parameters = new MimeTypeParameterList();
    }

    /**
     * A routine for parsing the MIME type out of a String.
     */
    private void parse(String rawdata) throws MimeTypeParseException {
        int slashIndex = rawdata.indexOf('/');
        int semIndex = rawdata.indexOf(';');
        if ((slashIndex < 0) && (semIndex < 0)) {
            //    neither character is present, so treat it
            //    as an error
            throw new MimeTypeParseException("Unable to find a sub type.");
        } else if ((slashIndex < 0) && (semIndex >= 0)) {
            //    we have a ';' (and therefore a parameter list),
            //    but no '/' indicating a sub type is present
            throw new MimeTypeParseException("Unable to find a sub type.");
        } else if ((slashIndex >= 0) && (semIndex < 0)) {
            //    we have a primary and sub type but no parameter list
            primaryType = rawdata.substring(0, slashIndex).trim().
                                                toLowerCase(Locale.ENGLISH);
            subType = rawdata.substring(slashIndex + 1).trim().
                                                toLowerCase(Locale.ENGLISH);
            parameters = new MimeTypeParameterList();
        } else if (slashIndex < semIndex) {
            //    we have all three items in the proper sequence
            primaryType = rawdata.substring(0, slashIndex).trim().
                                                toLowerCase(Locale.ENGLISH);
            subType = rawdata.substring(slashIndex + 1, semIndex).trim().
                                                toLowerCase(Locale.ENGLISH);
            parameters = new MimeTypeParameterList(rawdata.substring(semIndex));
        } else {
            // we have a ';' lexically before a '/' which means we
            // have a primary type and a parameter list but no sub type
            throw new MimeTypeParseException("Unable to find a sub type.");
        }

        //    now validate the primary and sub types

        //    check to see if primary is valid
        if (!isValidToken(primaryType))
            throw new MimeTypeParseException("Primary type is invalid.");

        //    check to see if sub is valid
        if (!isValidToken(subType))
            throw new MimeTypeParseException("Sub type is invalid.");
    }

    /**
     * Retrieve the primary type of this object.
     *
     * @return  the primary MIME type
     */
    public String getPrimaryType() {
        return primaryType;
    }

    /**
     * Set the primary type for this object to the given String.
     *
     * @param primary   the primary MIME type
     * @exception       MimeTypeParseException  if the primary type
     *                                          is not a valid token
     */
    public void setPrimaryType(String primary) throws MimeTypeParseException {
        //    check to see if primary is valid
        if (!isValidToken(primaryType))
            throw new MimeTypeParseException("Primary type is invalid.");
        primaryType = primary.toLowerCase(Locale.ENGLISH);
    }

    /**
     * Retrieve the subtype of this object.
     *
     * @return  the MIME subtype
     */
    public String getSubType() {
        return subType;
    }

    /**
     * Set the subtype for this object to the given String.
     *
     * @param sub       the MIME subtype
     * @exception       MimeTypeParseException  if the subtype
     *                                          is not a valid token
     */
    public void setSubType(String sub) throws MimeTypeParseException {
        //    check to see if sub is valid
        if (!isValidToken(subType))
            throw new MimeTypeParseException("Sub type is invalid.");
        subType = sub.toLowerCase(Locale.ENGLISH);
    }

    /**
     * Retrieve this object's parameter list.
     *
     * @return  a MimeTypeParameterList object representing the parameters
     */
    public MimeTypeParameterList getParameters() {
        return parameters;
    }

    /**
     * Retrieve the value associated with the given name, or null if there
     * is no current association.
     *
     * @param name      the parameter name
     * @return          the paramter's value
     */
    public String getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Set the value to be associated with the given name, replacing
     * any previous association.
     *
     * @param name      the parameter name
     * @param value     the paramter's value
     */
    public void setParameter(String name, String value) {
        parameters.set(name, value);
    }

    /**
     * Remove any value associated with the given name.
     *
     * @param name      the parameter name
     */
    public void removeParameter(String name) {
        parameters.remove(name);
    }

    /**
     * Return the String representation of this object.
     */
    public String toString() {
        return getBaseType() + parameters.toString();
    }

    /**
     * Return a String representation of this object
     * without the parameter list.
     *
     * @return  the MIME type and sub-type
     */
    public String getBaseType() {
        return primaryType + "/" + subType;
    }

    /**
     * Determine if the primary and sub type of this object is
     * the same as what is in the given type.
     *
     * @param type      the MimeType object to compare with
     * @return          true if they match
     */
    public boolean match(MimeType type) {
        return primaryType.equals(type.getPrimaryType())
                    && (subType.equals("*")
                            || type.getSubType().equals("*")
                            || (subType.equals(type.getSubType())));
    }

    /**
     * Determine if the primary and sub type of this object is
     * the same as the content type described in rawdata.
     *
     * @param rawdata   the MIME type string to compare with
     * @return          true if they match
     */
    public boolean match(String rawdata) throws MimeTypeParseException {
        return match(new MimeType(rawdata));
    }

    /**
     * The object implements the writeExternal method to save its contents
     * by calling the methods of DataOutput for its primitive values or
     * calling the writeObject method of ObjectOutput for objects, strings
     * and arrays.
     *
     * @param out       the ObjectOutput object to write to
     * @exception IOException Includes any I/O exceptions that may occur
     */
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(toString());
        out.flush();
    }

    /**
     * The object implements the readExternal method to restore its
     * contents by calling the methods of DataInput for primitive
     * types and readObject for objects, strings and arrays.  The
     * readExternal method must read the values in the same sequence
     * and with the same types as were written by writeExternal.
     *
     * @param in        the ObjectInput object to read from
     * @exception ClassNotFoundException If the class for an object being
     *              restored cannot be found.
     */
    public void readExternal(ObjectInput in)
                                throws IOException, ClassNotFoundException {
        try {
            parse(in.readUTF());
        } catch (MimeTypeParseException e) {
            throw new IOException(e.toString());
        }
    }

    //    below here be scary parsing related things

    /**
     * Determine whether or not a given character belongs to a legal token.
     */
    private static boolean isTokenChar(char c) {
        return ((c > 040) && (c < 0177)) && (TSPECIALS.indexOf(c) < 0);
    }

    /**
     * Determine whether or not a given string is a legal token.
     */
    private boolean isValidToken(String s) {
        int len = s.length();
        if (len > 0) {
            for (int i = 0; i < len; ++i) {
                char c = s.charAt(i);
                if (!isTokenChar(c)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * A simple parser test,
     * for debugging...
     *
    public static void main(String[] args)
                                throws MimeTypeParseException, IOException {
        for (int i = 0; i < args.length; ++i) {
            System.out.println("Original: " + args[i]);

            MimeType type = new MimeType(args[i]);

            System.out.println("Short:    " + type.getBaseType());
            System.out.println("Parsed:   " + type.toString());
            System.out.println();
        }
    }
    */
}
