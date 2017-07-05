/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.namingutil;

import java.util.*;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.NamingSystemException ;

/**
 *  The corbaloc: URL definitions from the -ORBInitDef and -ORBDefaultInitDef's
 *  will be parsed and converted to  this object. This object is capable of
 *  storing multiple  Host profiles as defined in the CorbaLoc grammer.
 *
 *  @author  Hemanth
 */
public class CorbalocURL extends INSURLBase
{
    static NamingSystemException wrapper = NamingSystemException.get(
        CORBALogDomains.NAMING_READ ) ;

    /**
     * This constructor parses the URL and initializes all the variables. Once
     * the URL Object is constructed it is immutable. URL parameter is a
     * corbaloc: URL string with 'corbaloc:' prefix stripped.
     */
    public CorbalocURL( String aURL ) {
        String url = aURL;

        if( url != null ) {
            try {
                // First Clean the URL Escapes if there are any
                url = Utility.cleanEscapes( url );
            } catch( Exception e ) {
                // There is something wrong with the URL escapes used
                // so throw an exception
                badAddress( e );
            }
            int endIndex = url.indexOf( '/' );
            if( endIndex == -1 ) {
                // If there is no '/' then the endIndex is at the end of the URL
                endIndex = url.length();
            }
            // _REVISIT_: Add a testcase to check 'corbaloc:/'
            if( endIndex == 0 )  {
                // The url starts with a '/', it's an error
                badAddress( null );
            }
            // Anything between corbaloc: and / is the host,port information
            // of the server where the Service Object is located
            StringTokenizer endpoints = new StringTokenizer(
                url.substring( 0, endIndex ), "," );
            // NOTE:
            // There should be atleast one token, because there are checks
            // to make sure that there is host information before the
            // delimiter '/'. So no need to explicitly check for number of
            // tokens != 0
            while( endpoints.hasMoreTokens( ) ) {
                String endpointInfo = endpoints.nextToken();
                IIOPEndpointInfo iiopEndpointInfo = null;
                if( endpointInfo.startsWith( "iiop:" ) ) {
                    iiopEndpointInfo = handleIIOPColon( endpointInfo );
                } else if( endpointInfo.startsWith( "rir:" ) ) {
                    handleRIRColon( endpointInfo );
                    rirFlag = true;
                } else if( endpointInfo.startsWith( ":" ) ) {
                    iiopEndpointInfo = handleColon( endpointInfo );
                } else {
                    // Right now we are not allowing any other protocol
                    // other than iiop:, rir: so raise exception indicating
                    // that the URL is malformed
                    badAddress( null );
                }
                if ( rirFlag == false ) {
                    // Add the Host information if RIR flag is set,
                    // If RIR is set then it means use the internal Boot
                    // Strap protocol for Key String resolution
                    if( theEndpointInfo == null ) {
                        theEndpointInfo = new java.util.ArrayList( );
                    }
                    theEndpointInfo.add( iiopEndpointInfo );
                }
            }
            // If there is something after corbaloc:endpointInfo/
            // then that is the keyString
            if( url.length() > (endIndex + 1) ) {
                theKeyString = url.substring( endIndex + 1 );
            }
        }
    }


    /**
     *  A Utility method to throw BAD_PARAM exception to signal malformed
     *  INS URL.
     */
    private void badAddress( java.lang.Throwable e )
    {
        throw wrapper.insBadAddress( e ) ;
    }

    /**
     *  If there is 'iiop:' token in the URL, this method will parses
     *  and  validates that host and port information.
     */
    private IIOPEndpointInfo handleIIOPColon( String iiopInfo )
    {
         // Check the iiop syntax
         iiopInfo = iiopInfo.substring( NamingConstants.IIOP_LENGTH  );
         return handleColon( iiopInfo );
    }


    /**
     * This is to handle the case of host information with no 'iiop:' prefix.
     * instead if ':' is specified then iiop is assumed.
     */
    private IIOPEndpointInfo handleColon( String iiopInfo ) {
         // String after ":"
         iiopInfo = iiopInfo.substring( 1 );
         String hostandport = iiopInfo;
         // The format can be 1.2@<host>:<port>
         StringTokenizer tokenizer = new StringTokenizer( iiopInfo, "@" );
         IIOPEndpointInfo iiopEndpointInfo = new IIOPEndpointInfo( );
         int tokenCount = tokenizer.countTokens( );
         // There can be 1 or 2 tokens with '@' as the delimiter
         //  - if there is only 1 token then there is no GIOP version
         //    information.  A Default GIOP version of 1.2 is used.
         //  - if there are 2 tokens then there is GIOP version is specified
         //  - if there are no tokens or more than 2 tokens, then that's an
         //    error
         if( ( tokenCount == 0 )
           ||( tokenCount > 2 ))
         {
             badAddress( null );
         }
         if( tokenCount == 2 ) {
            // There is VersionInformation after iiop:
            String version     = tokenizer.nextToken( );
            int dot = version.indexOf('.');
            // There is a version without ., which means
            // Malformed list
            if (dot == -1) {
                badAddress( null );
            }
            try {
                iiopEndpointInfo.setVersion(
                    Integer.parseInt( version.substring( 0, dot )),
                    Integer.parseInt( version.substring(dot+1)) );
                hostandport = tokenizer.nextToken( );
            } catch( Throwable e ) {
                badAddress( e );
            }
         }
         try {
           // A Hack to differentiate IPV6 address
           // from IPV4 address, Current Resolution
           // is to use [ ] to differentiate ipv6 host
           int squareBracketBeginIndex = hostandport.indexOf ( '[' );
           if( squareBracketBeginIndex != -1 ) {
               // ipv6Host should be enclosed in
               // [ ], if not it will result in a
               // BAD_PARAM exception
               String ipv6Port = getIPV6Port( hostandport );
               if( ipv6Port != null ) {
                   iiopEndpointInfo.setPort( Integer.parseInt( ipv6Port ));
               }
               iiopEndpointInfo.setHost( getIPV6Host( hostandport ));
               return iiopEndpointInfo;
           }
           tokenizer = new StringTokenizer( hostandport, ":" );
           // There are three possible cases here
           // 1. Host and Port is explicitly specified by using ":" as a
           //    a separator
           // 2. Only Host is specified without the port
           // 3. HostAndPort info is null
           if( tokenizer.countTokens( ) == 2 ) {
               // Case 1: There is Host and Port Info
               iiopEndpointInfo.setHost( tokenizer.nextToken( ) );
               iiopEndpointInfo.setPort( Integer.parseInt(
                   tokenizer.nextToken( )));
           } else {
               if( ( hostandport != null )
                 &&( hostandport.length() != 0 ) )
               {
                   // Case 2: Only Host is specified. iiopEndpointInfo is
                   // initialized to use the default INS port, if no port is
                   // specified
                   iiopEndpointInfo.setHost( hostandport );
               }
               // Case 3: If no Host and Port info is provided then we use the
               // the default LocalHost and INSPort. iiopEndpointInfo is
               // already initialized with this info.
           }
       } catch( Throwable e ) {
           // Any kind of Exception is bad here.
           // Possible causes: A Number Format exception because port info is
           // malformed
           badAddress( e );
       }
       Utility.validateGIOPVersion( iiopEndpointInfo );
       return iiopEndpointInfo;
    }

    /**
     *  Validate 'rir:' case.
     */
    private void handleRIRColon( String rirInfo )
    {
        if( rirInfo.length() != NamingConstants.RIRCOLON_LENGTH ) {
            badAddress( null );
        }
    }

    /**
      * Returns an IPV6 Port that is after [<ipv6>]:. There is no validation
      * done here, if it is an incorrect port then the request through
      * this URL results in a COMM_FAILURE, otherwise malformed list will
      * result in BAD_PARAM exception thrown in checkcorbalocGrammer.
      */
     private String getIPV6Port( String endpointInfo )
     {
         int squareBracketEndIndex = endpointInfo.indexOf ( ']' );
         // If there is port information, then it has to be after ] bracket
         // indexOf returns the count from the index of zero as the base, so
         // equality check requires squareBracketEndIndex + 1.
         if( (squareBracketEndIndex + 1) != (endpointInfo.length( )) ) {
             if( endpointInfo.charAt( squareBracketEndIndex + 1 ) != ':' ) {
                  throw new RuntimeException(
                      "Host and Port is not separated by ':'" );
             }
             // PortInformation  should be after ']:' delimiter
             // If there is an exception then it will be caught in
             // checkcorbaGrammer method and rethrown as BAD_PARAM
             return endpointInfo.substring( squareBracketEndIndex + 2 );
         }
         return null;
     }


     /**
      * Returns an IPV6 Host that is inside [ ] tokens. There is no validation
      * done here, if it is an incorrect IPV6 address then the request through
      * this URL results in a COMM_FAILURE, otherwise malformed list will
      * result in BAD_PARAM exception thrown in checkcorbalocGrammer.
      */
     private String getIPV6Host( String endpointInfo ) {
          // ipv6Host should be enclosed in
          // [ ], if not it will result in a
          // BAD_PARAM exception
          int squareBracketEndIndex = endpointInfo.indexOf ( ']' );
          // get the host between [ ]
          String ipv6Host = endpointInfo.substring( 1, squareBracketEndIndex  );
          return ipv6Host;
    }

    /**
     * Will be true only in CorbanameURL class.
     */
    public boolean isCorbanameURL( ) {
        return false;
    }


}
