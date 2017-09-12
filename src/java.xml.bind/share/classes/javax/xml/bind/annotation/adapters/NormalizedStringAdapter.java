/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.annotation.adapters;



/**
 * {@link XmlAdapter} to handle {@code xs:normalizedString}.
 *
 * <p>
 * Replaces any tab, CR, and LF by a whitespace character ' ',
 * as specified in <a href="http://www.w3.org/TR/xmlschema-2/#rf-whiteSpace">the whitespace facet 'replace'</a>
 *
 * @author Kohsuke Kawaguchi, Martin Grebac
 * @since 1.6, JAXB 2.0
 */
public final class NormalizedStringAdapter extends XmlAdapter<String,String> {
    /**
     * Replace any tab, CR, and LF by a whitespace character ' ',
     * as specified in <a href="http://www.w3.org/TR/xmlschema-2/#rf-whiteSpace">the whitespace facet 'replace'</a>
     */
    public String unmarshal(String text) {
        if(text==null)      return null;    // be defensive

        int i=text.length()-1;

        // look for the first whitespace char.
        while( i>=0 && !isWhiteSpaceExceptSpace(text.charAt(i)) )
            i--;

        if( i<0 )
            // no such whitespace. replace(text)==text.
            return text;

        // we now know that we need to modify the text.
        // allocate a char array to do it.
        char[] buf = text.toCharArray();

        buf[i--] = ' ';
        for( ; i>=0; i-- )
            if( isWhiteSpaceExceptSpace(buf[i]))
                buf[i] = ' ';

        return new String(buf);
    }

    /**
     * No-op.
     *
     * Just return the same string given as the parameter.
     */
        public String marshal(String s) {
            return s;
        }


    /**
     * Returns true if the specified char is a white space character
     * but not 0x20.
     */
    protected static boolean isWhiteSpaceExceptSpace(char ch) {
        // most of the characters are non-control characters.
        // so check that first to quickly return false for most of the cases.
        if( ch>=0x20 )   return false;

        // other than we have to do four comparisons.
        return ch == 0x9 || ch == 0xA || ch == 0xD;
    }
}
