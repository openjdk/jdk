/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.toolkit.corba;

// Needed for RMI/IIOP
import java.rmi.Remote;

import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Enumeration;
import java.applet.Applet;

import org.omg.CORBA.ORB;

import javax.naming.Context;
import javax.naming.ConfigurationException;
import javax.rmi.CORBA.Stub;
import javax.rmi.PortableRemoteObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;

/**
  * Contains utilities for performing CORBA-related tasks:
  * 1. Get the org.omg.CORBA.Object for a java.rmi.Remote object.
  * 2. Create an ORB to use for a given host/port, and environment properties.
  *    ...
  *
  * @author Simon Nash
  * @author Bryan Atsatt
  */

public class CorbaUtils {
    /**
      * Returns the CORBA object reference associated with a Remote
      * object by using the javax.rmi.CORBA package.
      *<p>
      * This method effective does the following:
      * <blockquote><pre>
      * java.lang.Object stub;
      * try {
      *     stub = PortableRemoteObject.toStub(remoteObj);
      * } catch (Exception e) {
      *     throw new ConfigurationException("Object not exported or not found");
      * }
      * if (!(stub instanceof javax.rmi.CORBA.Stub)) {
      *     return null; // JRMP impl or JRMP stub
      * }
      * try {
      *     ((javax.rmi.CORBA.Stub)stub).connect(orb);  // try to connect IIOP stub
      * } catch (RemoteException e) {
      *     // ignore 'already connected' error
      * }
      * return (javax.rmi.CORBA.Stub)stub;
      * </pre></blockquote>
      *
      * @param remoteObj The non-null remote object for
      * @param orb       The non-null ORB to connect the remote object to
      * @return The CORBA Object for remoteObj; null if {@code remoteObj}
      *                 is a JRMP implementation or JRMP stub.
      * @exception ConfigurationException The CORBA Object cannot be obtained
      *         because of configuration problems.
      */
    public static org.omg.CORBA.Object remoteToCorba(Remote remoteObj, ORB orb)
        throws ConfigurationException {

// First, get remoteObj's stub

            // javax.rmi.CORBA.Stub stub = PortableRemoteObject.toStub(remoteObj);

            Remote stub;

            try {
                stub = PortableRemoteObject.toStub(remoteObj);
            } catch (Throwable t) {
                ConfigurationException ce = new ConfigurationException(
    "Problem with PortableRemoteObject.toStub(); object not exported or stub not found");
                ce.setRootCause(t);
                throw ce;
            }

// Next, make sure that the stub is javax.rmi.CORBA.Stub

            if (!(stub instanceof Stub)) {
                return null;  // JRMP implementation or JRMP stub
            }

// Next, make sure that the stub is connected
            try {
                ((Stub) stub).connect(orb);
            } catch (RemoteException e) {
                // ignore RemoteException because stub might have already
                // been connected
            } catch (Throwable t) {
                ConfigurationException ce = new ConfigurationException(
                        "Problem invoking javax.rmi.CORBA.Stub.connect()");
                ce.setRootCause(t);
                throw ce;
            }
// Finally, return stub
            return (org.omg.CORBA.Object)stub;
    }

    /**
     * Get ORB using given server and port number, and properties from environment.
     *
     * @param server Possibly null server; if null means use default;
     *               For applet, it is the applet host; for app, it is localhost.
     * @param port   Port number, -1 means default port
     * @param env    Possibly null environment. Contains environment properties.
     *               Could contain ORB itself; or applet used for initializing ORB.
     *               Use all String properties from env for initializing ORB
     * @return A non-null ORB.
     */
    public static ORB getOrb(String server, int port, Hashtable<?,?> env) {
        // See if we can get info from environment
        Properties orbProp;

        // Extract any org.omg.CORBA properties from environment
        if (env != null) {
            if (env instanceof Properties) {
                // Already a Properties, just clone
                orbProp = (Properties) env.clone();
            } else {
                // Get all String properties
                Enumeration<?> envProp;
                orbProp = new Properties();
                for (envProp = env.keys(); envProp.hasMoreElements();) {
                    String key = (String)envProp.nextElement();
                    Object val = env.get(key);
                    if (val instanceof String) {
                        orbProp.put(key, val);
                    }
                }
            }
        } else {
            orbProp = new Properties();
        }

        if (server != null) {
            orbProp.put("org.omg.CORBA.ORBInitialHost", server);
        }
        if (port >= 0) {
            orbProp.put("org.omg.CORBA.ORBInitialPort", ""+port);
        }

        // Get Applet from environment
        if (env != null) {
            @SuppressWarnings("deprecation")
            Applet applet = (Applet) env.get(Context.APPLET);
            if (applet != null) {
            // Create ORBs using applet and orbProp
                return ORB.init(applet, orbProp);
            }
        }

        return ORB.init(new String[0], orbProp);
    }

    /**
     * Decode a URI string (according to RFC 2396).
     */
    public static final String decode(String s) throws MalformedURLException {
        try {
            return decode(s, "8859_1");
        } catch (UnsupportedEncodingException e) {
            // ISO-Latin-1 should always be available?
            throw new MalformedURLException("ISO-Latin-1 decoder unavailable");
        }
    }

    /**
     * Decode a URI string (according to RFC 2396).
     *
     * Three-character sequences '%xy', where 'xy' is the two-digit
     * hexadecimal representation of the lower 8-bits of a character,
     * are decoded into the character itself.
     *
     * The string is subsequently converted using the specified encoding
     */
    public static final String decode(String s, String enc)
            throws MalformedURLException, UnsupportedEncodingException {
        try {
            return URLDecoder.decode(s, enc);
        } catch (IllegalArgumentException iae) {
            MalformedURLException mue = new MalformedURLException("Invalid URI encoding: " + s);
            mue.initCause(iae);
            throw mue;
        }
    }

}
