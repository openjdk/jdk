/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orb ;

import com.sun.corba.se.impl.orbutil.GetPropertyAction ;

import java.security.PrivilegedAction ;
import java.security.AccessController ;

import java.applet.Applet ;

import java.util.Properties ;
import java.util.Vector ;
import java.util.Set ;
import java.util.HashSet ;
import java.util.Enumeration ;
import java.util.Iterator ;
import java.util.StringTokenizer ;

import java.net.URL ;

import java.security.AccessController ;

import java.io.File ;
import java.io.FileInputStream ;

import com.sun.corba.se.spi.orb.DataCollector ;
import com.sun.corba.se.spi.orb.PropertyParser ;

import com.sun.corba.se.impl.orbutil.ORBConstants ;
import com.sun.corba.se.impl.orbutil.ORBUtility;

public abstract class DataCollectorBase implements DataCollector {
    private PropertyParser parser ;
    private Set propertyNames ;
    private Set propertyPrefixes ;
    private Set URLPropertyNames ;
    protected String localHostName ;
    protected String configurationHostName ;
    private boolean setParserCalled ;
    private Properties originalProps ;
    private Properties resultProps ;

    public DataCollectorBase( Properties props, String localHostName,
        String configurationHostName )
    {
        // XXX This is fully initialized here.  So do we ever want to
        // generalize this (or perhaps this is the wrong place for this?)
        URLPropertyNames = new HashSet() ;
        URLPropertyNames.add( ORBConstants.INITIAL_SERVICES_PROPERTY ) ;

        propertyNames = new HashSet() ;

        // Make sure that we are ready to handle -ORBInitRef.  This is special
        // due to the need to handle multiple -ORBInitRef args as prefix
        // parsing.
        propertyNames.add( ORBConstants.ORB_INIT_REF_PROPERTY ) ;

        propertyPrefixes = new HashSet() ;

        this.originalProps = props ;
        this.localHostName = localHostName ;
        this.configurationHostName = configurationHostName ;
        setParserCalled = false ;
        resultProps = new Properties() ;
    }

//////////////////////////////////////////////////////////
// Public interface defined in DataCollector
//////////////////////////////////////////////////////////

    public boolean initialHostIsLocal()
    {
        checkSetParserCalled() ;
        return localHostName.equals( resultProps.getProperty(
            ORBConstants.INITIAL_HOST_PROPERTY ) ) ;
    }

    public void setParser( PropertyParser parser )
    {
        Iterator iter = parser.iterator() ;
        while (iter.hasNext()) {
            ParserAction pa = (ParserAction)(iter.next()) ;
            if (pa.isPrefix())
                propertyPrefixes.add( pa.getPropertyName() ) ;
            else
                propertyNames.add( pa.getPropertyName() ) ;
        }

        collect() ;
        setParserCalled = true ;
    }

    public Properties getProperties()
    {
        checkSetParserCalled() ;
        return resultProps ;
    }

//////////////////////////////////////////////////////////
// public interface from DataCollector that must be defined
// in subclasses
//////////////////////////////////////////////////////////

    public abstract boolean isApplet() ;

//////////////////////////////////////////////////////////
// Implementation methods needed in subclasses
//////////////////////////////////////////////////////////

    protected abstract void collect() ;

//////////////////////////////////////////////////////////
// methods for use by subclasses
//////////////////////////////////////////////////////////

    protected void checkPropertyDefaults()
    {
        String host =
            resultProps.getProperty( ORBConstants.INITIAL_HOST_PROPERTY ) ;

        if ((host == null) || (host.equals("")))
            setProperty( ORBConstants.INITIAL_HOST_PROPERTY,
                configurationHostName );

        String serverHost =
            resultProps.getProperty( ORBConstants.SERVER_HOST_PROPERTY ) ;

        if (serverHost == null ||
            serverHost.equals("") ||
            serverHost.equals("0.0.0.0") ||
            serverHost.equals("::") ||
            serverHost.toLowerCase().equals("::ffff:0.0.0.0"))
        {
            setProperty(ORBConstants.SERVER_HOST_PROPERTY,
                        localHostName);
            setProperty(ORBConstants.LISTEN_ON_ALL_INTERFACES,
                        ORBConstants.LISTEN_ON_ALL_INTERFACES);
        }
    }

    protected void findPropertiesFromArgs( String[] params )
    {
        if (params == null)
            return;

        // All command-line args are of the form "-ORBkey value".
        // The key is mapped to <prefix>.ORBkey.

        String name ;
        String value ;

        for ( int i=0; i<params.length; i++ ) {
            value = null ;
            name = null ;

            if ( params[i] != null && params[i].startsWith("-ORB") ) {
                String argName = params[i].substring( 1 ) ;
                name = findMatchingPropertyName( propertyNames, argName ) ;

                if (name != null)
                    if ( i+1 < params.length && params[i+1] != null ) {
                        value = params[++i];
                    }
            }

            if (value != null) {
                setProperty( name, value ) ;
            }
        }
    }

    protected void findPropertiesFromApplet( final Applet app )
    {
        // Cannot use propertyPrefixes here, since there is no
        // way to fetch properties by prefix from an Applet.
        if (app == null)
            return;

        PropertyCallback callback = new PropertyCallback() {
            public String get(String name) {
                return app.getParameter(name);
            }
        } ;

        findPropertiesByName( propertyNames.iterator(), callback ) ;

        // Special Case:
        //
        // Convert any applet parameter relative URLs to an
        // absolute URL based on the Document Root. This is so HTML
        // URLs can be kept relative which is sometimes useful for
        // managing the Document Root layout.
        PropertyCallback URLCallback = new PropertyCallback() {
            public String get( String name ) {
                String value = resultProps.getProperty(name);
                if (value == null)
                    return null ;

                try {
                    URL url = new URL( app.getDocumentBase(), value ) ;
                    return url.toExternalForm() ;
                } catch (java.net.MalformedURLException exc) {
                    // Just preserve the original (malformed) value:
                    // the error will be handled later.
                    return value ;
                }
            }
        } ;

        findPropertiesByName( URLPropertyNames.iterator(),
            URLCallback ) ;
    }

    private void doProperties( final Properties props )
    {
        PropertyCallback callback =  new PropertyCallback() {
            public String get(String name) {
                return props.getProperty(name);
            }
        } ;

        findPropertiesByName( propertyNames.iterator(), callback ) ;

        findPropertiesByPrefix( propertyPrefixes,
            makeIterator( props.propertyNames()), callback );
    }

    protected void findPropertiesFromFile()
    {
        final Properties fileProps = getFileProperties() ;
        if (fileProps==null)
            return ;

        doProperties( fileProps ) ;
    }

    protected void findPropertiesFromProperties()
    {
        if (originalProps == null)
            return;

        doProperties( originalProps ) ;
    }

    //
    // Map System properties to ORB properties.
    // Security bug fix 4278205:
    // Only allow reading of system properties with ORB prefixes.
    // Previously a malicious subclass was able to read ANY system property.
    // Note that other prefixes are fine in other contexts; it is only
    // system properties that should impose a restriction.
    protected void findPropertiesFromSystem()
    {
        Set normalNames = getCORBAPrefixes( propertyNames ) ;
        Set prefixNames = getCORBAPrefixes( propertyPrefixes ) ;

        PropertyCallback callback = new PropertyCallback() {
            public String get(String name) {
                return getSystemProperty(name);
            }
        } ;

        findPropertiesByName( normalNames.iterator(), callback ) ;

        findPropertiesByPrefix( prefixNames,
            getSystemPropertyNames(), callback ) ;
    }

//////////////////////////////////////////////////////////
// internal implementation
//////////////////////////////////////////////////////////

    // Store name, value in resultProps, with special
    // treatment of ORBInitRef.  All updates to resultProps
    // must happen through this method.
    private void setProperty( String name, String value )
    {
        if( name.equals( ORBConstants.ORB_INIT_REF_PROPERTY ) ) {
            // Value is <name>=<URL>
            StringTokenizer st = new StringTokenizer( value, "=" ) ;
            if (st.countTokens() != 2)
                throw new IllegalArgumentException() ;

            String refName = st.nextToken() ;
            String refValue = st.nextToken() ;

            resultProps.setProperty( name + "." + refName, refValue ) ;
        } else {
            resultProps.setProperty( name, value ) ;
        }
    }

    private void checkSetParserCalled()
    {
        if (!setParserCalled)
            throw new IllegalStateException( "setParser not called." ) ;
    }

    // For each prefix in prefixes, For each name in propertyNames,
    // if (prefix is a prefix of name) get value from getProperties and
    // setProperty (name, value).
    private void findPropertiesByPrefix( Set prefixes,
        Iterator propertyNames, PropertyCallback getProperty )
    {
        while (propertyNames.hasNext()) {
            String name = (String)(propertyNames.next()) ;
            Iterator iter = prefixes.iterator() ;
            while (iter.hasNext()) {
                String prefix = (String)(iter.next()) ;
                if (name.startsWith( prefix )) {
                    String value = getProperty.get( name ) ;

                    // Note: do a put even if value is null since just
                    // the presence of the property may be significant.
                    setProperty( name, value ) ;
                }
            }
        }
    }

    // For each prefix in names, get the corresponding property
    // value from the callback, and store the name/value pair in
    // the result.
    private void findPropertiesByName( Iterator names,
        PropertyCallback getProperty )
    {
        while (names.hasNext()) {
            String name = (String)(names.next()) ;
            String value = getProperty.get( name ) ;
            if (value != null)
                setProperty( name, value ) ;
        }
    }

    private static String getSystemProperty(final String name)
    {
        return (String)AccessController.doPrivileged(
            new GetPropertyAction(name));
    }

    // Map command-line arguments to ORB properties.
    //
    private String findMatchingPropertyName( Set names,
        String suffix )
    {
        Iterator iter = names.iterator() ;
        while (iter.hasNext()) {
            String name = (String)(iter.next()) ;
            if (name.endsWith( suffix ))
                return name ;
        }

        return null ;
    }

    private static Iterator makeIterator( final Enumeration enumeration )
    {
        return new Iterator() {
            public boolean hasNext() { return enumeration.hasMoreElements() ; }
            public Object next() { return enumeration.nextElement() ; }
            public void remove() { throw new UnsupportedOperationException() ; }
        } ;
    }

    private static Iterator getSystemPropertyNames()
    {
        // This will not throw a SecurityException because this
        // class was loaded from rt.jar using the bootstrap classloader.
        Enumeration enumeration = (Enumeration)
            AccessController.doPrivileged(
                new PrivilegedAction() {
                      public java.lang.Object run() {
                          return System.getProperties().propertyNames();
                      }
                }
            );

        return makeIterator( enumeration ) ;
    }

    private void getPropertiesFromFile( Properties props, String fileName )
    {
        try {
            File file = new File( fileName ) ;
            if (!file.exists())
                return ;

            FileInputStream in = new FileInputStream( file ) ;

            try {
                props.load( in ) ;
            } finally {
                in.close() ;
            }
        } catch (Exception exc) {
            // if (ORBInitDebug)
                // dprint( "ORB properties file " + fileName + " not found: " +
                    // exc) ;
        }
    }

    private Properties getFileProperties()
    {
        Properties defaults = new Properties() ;

        String javaHome = getSystemProperty( "java.home" ) ;
        String fileName = javaHome + File.separator + "lib" + File.separator +
            "orb.properties" ;

        getPropertiesFromFile( defaults, fileName ) ;

        Properties results = new Properties( defaults ) ;

        String userHome = getSystemProperty( "user.home" ) ;
        fileName = userHome + File.separator + "orb.properties" ;

        getPropertiesFromFile( results, fileName ) ;
        return results ;
    }

    private boolean hasCORBAPrefix( String prefix )
    {
        return prefix.startsWith( ORBConstants.ORG_OMG_PREFIX ) ||
            prefix.startsWith( ORBConstants.SUN_PREFIX ) ||
            prefix.startsWith( ORBConstants.SUN_LC_PREFIX ) ||
            prefix.startsWith( ORBConstants.SUN_LC_VERSION_PREFIX ) ;
    }

    // Return only those element of prefixes for which hasCORBAPrefix
    // is true.
    private Set getCORBAPrefixes( final Set prefixes )
    {
        Set result = new HashSet() ;
        Iterator iter = prefixes.iterator() ;
        while (iter.hasNext()) {
            String element = (String)(iter.next()) ;
            if (hasCORBAPrefix( element ))
                result.add( element ) ;
        }

        return result ;
    }
}

// Used to collect properties from various sources.
abstract class PropertyCallback
{
    abstract public String get(String name);
}
