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

package com.sun.xml.internal.bind.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Converts aribitrary strings into Java identifiers.
 *
 * @author
 *    <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface NameConverter
{
    /**
     * converts a string into an identifier suitable for classes.
     *
     * In general, this operation should generate "NamesLikeThis".
     */
    String toClassName( String token );

    /**
     * converts a string into an identifier suitable for interfaces.
     *
     * In general, this operation should generate "NamesLikeThis".
     * But for example, it can prepend every interface with 'I'.
     */
    String toInterfaceName( String token );

    /**
     * converts a string into an identifier suitable for properties.
     *
     * In general, this operation should generate "NamesLikeThis",
     * which will be used with known prefixes like "get" or "set".
     */
    String toPropertyName( String token );

    /**
     * converts a string into an identifier suitable for constants.
     *
     * In the standard Java naming convention, this operation should
     * generate "NAMES_LIKE_THIS".
     */
    String toConstantName( String token );

    /**
     * Converts a string into an identifier suitable for variables.
     *
     * In general it should generate "namesLikeThis".
     */
    String toVariableName( String token );

    /**
     * Converts a namespace URI into a package name.
     * This method should expect strings like
     * "http://foo.bar.zot/org", "urn:abc:def:ghi" "", or even "###"
     * (basically anything) and expected to return a package name,
     * liks "org.acme.foo".
     *
     */
    String toPackageName( String namespaceUri );

    /**
     * The name converter implemented by Code Model.
     *
     * This is the standard name conversion for JAXB.
     */
    public static final NameConverter standard = new Standard();

    static class Standard extends NameUtil implements NameConverter {
        public String toClassName(String s) {
            return toMixedCaseName(toWordList(s), true);
        }
        public String toVariableName(String s) {
            return toMixedCaseName(toWordList(s), false);
        }
        public String toInterfaceName( String token ) {
            return toClassName(token);
        }
        public String toPropertyName(String s) {
            String prop = toClassName(s);
            // property name "Class" with collide with Object.getClass,
            // so escape this.
            if(prop.equals("Class"))
                prop = "Clazz";
            return prop;
        }
        public String toConstantName( String token ) {
            return super.toConstantName(token);
        }
        /**
         * Computes a Java package name from a namespace URI,
         * as specified in the spec.
         *
         * @return
         *      null if it fails to derive a package name.
         */
        public String toPackageName( String nsUri ) {
            // remove scheme and :, if present
            // spec only requires us to remove 'http' and 'urn'...
            int idx = nsUri.indexOf(':');
            String scheme = "";
            if(idx>=0) {
                scheme = nsUri.substring(0,idx);
                if( scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("urn") )
                    nsUri = nsUri.substring(idx+1);
            }

            // tokenize string
            ArrayList<String> tokens = tokenize( nsUri, "/: " );
            if( tokens.size() == 0 ) {
                return null;
            }

            // remove trailing file type, if necessary
            if( tokens.size() > 1 ) {
                // for uri's like "www.foo.com" and "foo.com", there is no trailing
                // file, so there's no need to look at the last '.' and substring
                // otherwise, we loose the "com" (which would be wrong)
                String lastToken = tokens.get( tokens.size()-1 );
                idx = lastToken.lastIndexOf( '.' );
                if( idx > 0 ) {
                    lastToken = lastToken.substring( 0, idx );
                    tokens.set( tokens.size()-1, lastToken );
                }
            }

            // tokenize domain name and reverse.  Also remove :port if it exists
            String domain = tokens.get( 0 );
            idx = domain.indexOf(':');
            if( idx >= 0) domain = domain.substring(0, idx);
            ArrayList<String> r = reverse( tokenize( domain, scheme.equals("urn")?".-":"." ) );
            if( r.get( r.size()-1 ).equalsIgnoreCase( "www" ) ) {
                // remove leading www
                r.remove( r.size()-1 );
            }

            // replace the domain name with tokenized items
            tokens.addAll( 1, r );
            tokens.remove( 0 );

            // iterate through the tokens and apply xml->java name algorithm
            for( int i = 0; i < tokens.size(); i++ ) {

                // get the token and remove illegal chars
                String token = tokens.get( i );
                token = removeIllegalIdentifierChars( token );

                // this will check for reserved keywords
                if( !NameUtil.isJavaIdentifier( token ) ) {
                    token = '_' + token;
                }

                tokens.set( i, token.toLowerCase() );
            }

            // concat all the pieces and return it
            return combine( tokens, '.' );
        }


        private static String removeIllegalIdentifierChars(String token) {
            StringBuffer newToken = new StringBuffer();
            for( int i = 0; i < token.length(); i++ ) {
                char c = token.charAt( i );

                if( i ==0 && !Character.isJavaIdentifierStart( c ) ) {
                    // prefix an '_' if the first char is illegal
                    newToken.append('_').append(c);
                } else if( !Character.isJavaIdentifierPart( c ) ) {
                    // replace the char with an '_' if it is illegal
                    newToken.append( '_' );
                } else {
                    // add the legal char
                    newToken.append( c );
                }
            }
            return newToken.toString();
        }


        private static ArrayList<String> tokenize( String str, String sep ) {
            StringTokenizer tokens = new StringTokenizer(str,sep);
            ArrayList<String> r = new ArrayList<String>();

            while(tokens.hasMoreTokens())
                r.add( tokens.nextToken() );

            return r;
        }

        private static <T> ArrayList<T> reverse( List<T> a ) {
            ArrayList<T> r = new ArrayList<T>();

            for( int i=a.size()-1; i>=0; i-- )
                r.add( a.get(i) );

            return r;
        }

        private static String combine( List r, char sep ) {
            StringBuilder buf = new StringBuilder(r.get(0).toString());

            for( int i=1; i<r.size(); i++ ) {
                buf.append(sep);
                buf.append(r.get(i));
            }

            return buf.toString();
        }
    }

    /**
     * JAX-PRC compatible name converter implementation.
     *
     * The only difference is that we treat '_' as a valid character
     * and not as a word separator.
     */
    public static final NameConverter jaxrpcCompatible = new Standard() {
        protected boolean isPunct(char c) {
            return (c == '.' || c == '-' || c == ';' /*|| c == '_'*/ || c == '\u00b7'
                    || c == '\u0387' || c == '\u06dd' || c == '\u06de');
        }
        protected boolean isLetter(char c) {
            return super.isLetter(c) || c=='_';
        }

        protected int classify(char c0) {
            if(c0=='_') return NameUtil.OTHER_LETTER;
            return super.classify(c0);
        }
    };

    /**
     * Smarter converter used for RELAX NG support.
     */
    public static final NameConverter smart = new Standard() {
        public String toConstantName( String token ) {
            String name = super.toConstantName(token);
            if( NameUtil.isJavaIdentifier(name) )
                return name;
            else
                return '_'+name;
        }
    };
}
