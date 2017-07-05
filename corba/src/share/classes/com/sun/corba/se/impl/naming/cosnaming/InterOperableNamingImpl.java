/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

import org.omg.CosNaming.NamingContextExtPackage.*;
import java.io.StringWriter;

// Import general CORBA classes
import org.omg.CORBA.SystemException;
import org.omg.CORBA.Object;

// Import org.omg.CosNaming types
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;


/**
 * Class InteroperableNamingImpl implements the methods defined
 * for NamingContextExt which is part of Interoperable Naming
 * Service specifications. This class is added for doing more
 * of Parsing and Building of Stringified names according to INS
 * Spec.
 */
public class InterOperableNamingImpl
{
   /**
     * Method which stringifies the Name Components given as the input
     * parameter.
     *
     * @param n Array of Name Components (Simple or Compound Names)
     * @return string which is the stringified reference.
     */
    public String convertToString( org.omg.CosNaming.NameComponent[]
                                   theNameComponents )
    {
        String theConvertedString =
            convertNameComponentToString( theNameComponents[0] );
        String temp;
        for( int i = 1; i < theNameComponents.length; i++ ) {
            temp = convertNameComponentToString( theNameComponents[i] );
            if( temp != null ) {
                 theConvertedString =
                 theConvertedString + "/" +  convertNameComponentToString(
                     theNameComponents[i] );
            }
        }
        return theConvertedString;
    }

   /** This method converts a single Namecomponent to String, By adding Escapes
    *  If neccessary.
    */
    private String convertNameComponentToString(
        org.omg.CosNaming.NameComponent theNameComponent )
    {
        if( ( ( theNameComponent.id == null )
            ||( theNameComponent.id.length() == 0 ) )
          &&( ( theNameComponent.kind == null )
            ||( theNameComponent.kind.length() == 0 ) ) )
        {
            return ".";
        }
        else if( ( theNameComponent.id == null )
               ||( theNameComponent.id.length() == 0 ) )
        {
            String kind = addEscape( theNameComponent.kind );
            return "." + kind;
        }
        else if( ( theNameComponent.kind == null )
               ||( theNameComponent.kind.length() == 0 ) )
        {
            String id = addEscape( theNameComponent.id );
            return id;
        }
        else {
            String id = addEscape( theNameComponent.id );
            String kind = addEscape( theNameComponent.kind );
            return (id + "." +  kind);
        }
    }


   /** This method adds escape '\' for the Namecomponent if neccessary
    */
   private String addEscape( String value )
   {
        StringBuffer theNewValue;
        if( (value != null) && ( (value.indexOf('.') != -1 ) ||
                                 (value.indexOf('/') != -1)))
        {
            char c;
            theNewValue = new StringBuffer( );
            for( int i = 0; i < value.length( ); i++ ) {
                c = value.charAt( i );
                if( ( c != '.' ) && (c != '/' ) )
                {
                    theNewValue.append( c );
                }
                else {
                    // Adding escape for the "."
                    theNewValue.append( '\\' );
                    theNewValue.append( c );
                }
            }
        }
        else {
            return value;
        }
        return new String( theNewValue );
   }

   /**
     * Method which converts the Stringified name into Array of Name Components.
     *
     * @param string which is the stringified name.
     * @return  Array of Name Components (Simple or Compound Names)
     */
   public org.omg.CosNaming.NameComponent[] convertToNameComponent(
       String theStringifiedName )
       throws org.omg.CosNaming.NamingContextPackage.InvalidName
   {
        String[] theStringifiedNameComponents =
                 breakStringToNameComponents( theStringifiedName );
        if( ( theStringifiedNameComponents == null )
         || (theStringifiedNameComponents.length == 0 ) )
        {
            return null;
        }
        NameComponent[] theNameComponents =
            new NameComponent[theStringifiedNameComponents.length];
        for( int i = 0; i < theStringifiedNameComponents.length; i++ ) {
            theNameComponents[i] = createNameComponentFromString(
                theStringifiedNameComponents[i] );
        }
        return theNameComponents;
   }

   /** Step1 in converting Stringified name into  array of Name Component
     * is breaking the String into multiple name components
     */
   private String[] breakStringToNameComponents( String theStringifiedName ) {
       int[] theIndices = new int[100];
       int theIndicesIndex = 0;

       for(int index = 0; index <= theStringifiedName.length(); ) {
           theIndices[theIndicesIndex] = theStringifiedName.indexOf( '/',
                index );
           if( theIndices[theIndicesIndex] == -1 ) {
               // This is the end of all the occurence of '/' and hence come
               // out of the loop
               index = theStringifiedName.length()+1;
           }
           else {
               // If the '/' is found, first check whether it is
               // preceded by escape '\'
               // If not then set theIndices and increment theIndicesIndex
               // and also set the index else just ignore the '/'
               if( (theIndices[theIndicesIndex] > 0 )
               && (theStringifiedName.charAt(
                   theIndices[theIndicesIndex]-1) == '\\') )
               {
                  index = theIndices[theIndicesIndex] + 1;
                  theIndices[theIndicesIndex] = -1;
               }
               else {
                  index = theIndices[theIndicesIndex] + 1;
                  theIndicesIndex++;
               }
           }
        }
        if( theIndicesIndex == 0 ) {
            String[] tempString = new String[1];
            tempString[0] = theStringifiedName;
            return tempString;
        }
        if( theIndicesIndex != 0 ) {
            theIndicesIndex++;
        }
        return StringComponentsFromIndices( theIndices, theIndicesIndex,
                                            theStringifiedName );
    }

   /** This method breaks one big String into multiple substrings based
     * on the array of index passed in.
     */
   private String[] StringComponentsFromIndices( int[] theIndices,
          int indicesCount, String theStringifiedName )
   {
       String[] theStringComponents = new String[indicesCount];
       int firstIndex = 0;
       int lastIndex = theIndices[0];
       for( int i = 0; i < indicesCount; i++ ) {
           theStringComponents[i] = theStringifiedName.substring( firstIndex,
             lastIndex );
           if( ( theIndices[i] < theStringifiedName.length() - 1 )
             &&( theIndices[i] != -1 ) )
           {
               firstIndex = theIndices[i]+1;
           }
           else {
               firstIndex = 0;
               i = indicesCount;
           }
           if( (i+1 < theIndices.length)
            && (theIndices[i+1] < (theStringifiedName.length() - 1))
            && (theIndices[i+1] != -1) )
           {
               lastIndex = theIndices[i+1];
           }
           else {
               i = indicesCount;
           }
           // This is done for the last component
           if( firstIndex != 0 && i == indicesCount ) {
               theStringComponents[indicesCount-1] =
               theStringifiedName.substring( firstIndex );
           }
       }
       return theStringComponents;
   }

   /** Step 2: After Breaking the Stringified name into set of NameComponent
     * Strings, The next step is to create Namecomponents from the substring
     * by removing the escapes if there are any.
     */
   private NameComponent createNameComponentFromString(
        String theStringifiedNameComponent )
        throws org.omg.CosNaming.NamingContextPackage.InvalidName

   {
        String id = null;
        String kind = null;
        if( ( theStringifiedNameComponent == null )
         || ( theStringifiedNameComponent.length( ) == 0)
         || ( theStringifiedNameComponent.endsWith(".") ) )
        {
            // If any of the above is true, then we create an invalid Name
            // Component to indicate that it is an invalid name.
            throw new org.omg.CosNaming.NamingContextPackage.InvalidName( );
        }

        int index = theStringifiedNameComponent.indexOf( '.', 0 );
        // The format could be XYZ (Without kind)
        if( index == -1 ) {
            id = theStringifiedNameComponent;
        }
        // The format is .XYZ (Without ID)
        else if( index == 0 ) {
            // This check is for the Namecomponent which is just "." meaning Id
            // and Kinds are null
            if( theStringifiedNameComponent.length( ) != 1 ) {
                kind = theStringifiedNameComponent.substring(1);
            }
        }
        else
        {
            if( theStringifiedNameComponent.charAt(index-1) != '\\' ) {
                id = theStringifiedNameComponent.substring( 0, index);
                kind = theStringifiedNameComponent.substring( index + 1 );
            }
            else {
                boolean kindfound = false;
                while( (index < theStringifiedNameComponent.length() )
                     &&( kindfound != true ) )
                {
                    index = theStringifiedNameComponent.indexOf( '.',index + 1);
                    if( index > 0 ) {
                        if( theStringifiedNameComponent.charAt(
                                index - 1 ) != '\\' )
                        {
                            kindfound = true;
                        }
                    }
                    else
                    {
                        // No more '.', which means there is no Kind
                        index = theStringifiedNameComponent.length();
                    }
                }
                if( kindfound == true ) {
                    id = theStringifiedNameComponent.substring( 0, index);
                    kind = theStringifiedNameComponent.substring(index + 1 );
                }
                else {
                    id = theStringifiedNameComponent;
                }
            }
        }
        id = cleanEscapeCharacter( id );
        kind = cleanEscapeCharacter( kind );
        if( id == null ) {
                id = "";
        }
        if( kind == null ) {
                kind = "";
        }
        return new NameComponent( id, kind );
   }


   /** This method cleans the escapes in the Stringified name and returns the
     * correct String
     */
   private String cleanEscapeCharacter( String theString )
   {
        if( ( theString == null ) || (theString.length() == 0 ) ) {
                return theString;
        }
        int index = theString.indexOf( '\\' );
        if( index == 0 ) {
            return theString;
        }
        else {
            StringBuffer src = new StringBuffer( theString );
            StringBuffer dest = new StringBuffer( );
            char c;
            for( int i = 0; i < theString.length( ); i++ ) {
                c = src.charAt( i );
                if( c != '\\' ) {
                    dest.append( c );
                } else {
                    if( i+1 < theString.length() ) {
                        char d = src.charAt( i + 1 );
                        // If there is a AlphaNumeric character after a \
                        // then include slash, as it is not intended as an
                        // escape character.
                        if( Character.isLetterOrDigit(d) ) {
                            dest.append( c );
                        }
                    }
                }
            }
            return new String(dest);
        }
   }

   /**
     * Method which converts the Stringified name  and Host Name Address into
     * a URL based Name
     *
     * @param address which is ip based host name
     * @param name which is the stringified name.
     * @return  url based Name.
     */
    public String createURLBasedAddress( String address, String name )
        throws InvalidAddress
    {
        String theurl = null;
        if( ( address == null )
          ||( address.length() == 0 ) ) {
            throw new InvalidAddress();
        }
        else {
            theurl = "corbaname:" + address + "#" + encode( name );
        }
        return theurl;
    }

    /** Encodes the string according to RFC 2396 IETF spec required by INS.
     */
    private String encode( String stringToEncode ) {
        StringWriter theStringAfterEscape = new StringWriter();
        int byteCount = 0;
        for( int i = 0; i < stringToEncode.length(); i++ )
        {
            char c = stringToEncode.charAt( i ) ;
            if( Character.isLetterOrDigit( c ) ) {
                theStringAfterEscape.write( c );
            }
            // Do no Escape for characters in this list
            // RFC 2396
            else if((c == ';') || (c == '/') || (c == '?')
            || (c == ':') || (c == '@') || (c == '&') || (c == '=')
            || (c == '+') || (c == '$') || (c == ';') || (c == '-')
            || (c == '_') || (c == '.') || (c == '!') || (c == '~')
            || (c == '*') || (c == ' ') || (c == '(') || (c == ')') )
            {
                theStringAfterEscape.write( c );
            }
            else {
                // Add escape
                theStringAfterEscape.write( '%' );
                String hexString = Integer.toHexString( (int) c );
                theStringAfterEscape.write( hexString );
            }
        }
        return theStringAfterEscape.toString();
    }

}
