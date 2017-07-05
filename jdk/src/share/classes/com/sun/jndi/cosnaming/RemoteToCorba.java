/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.cosnaming;

import javax.naming.*;
import javax.naming.spi.StateFactory;
import java.util.Hashtable;

import org.omg.CORBA.ORB;

import java.rmi.Remote;
import java.rmi.server.ExportException;

import com.sun.jndi.toolkit.corba.CorbaUtils;  // for RMI-IIOP

/**
  * StateFactory that turns java.rmi.Remote objects to org.omg.CORBA.Object.
  *
  * @author Rosanna Lee
  */

public class RemoteToCorba implements StateFactory {
    public RemoteToCorba() {
    }

    /**
     * Returns the CORBA object for a Remote object.
     * If input is not a Remote object, or if Remote object uses JRMP, return null.
     * If the RMI-IIOP library is not available, throw ConfigurationException.
     *
     * @param orig The object to turn into a CORBA object. If not Remote,
     *             or if is a JRMP stub or impl, return null.
     * @param name Ignored
     * @param ctx The non-null CNCtx whose ORB to use.
     * @param env Ignored
     * @return The CORBA object for <tt>orig</tt> or null.
     * @exception ConfigurationException If the CORBA object cannot be obtained
     *    due to configuration problems, for instance, if RMI-IIOP not available.
     * @exception NamingException If some other problem prevented a CORBA
     *    object from being obtained from the Remote object.
     */
    public Object getStateToBind(Object orig, Name name, Context ctx,
        Hashtable<?,?> env) throws NamingException {
        if (orig instanceof org.omg.CORBA.Object) {
            // Already a CORBA object, just use it
            return null;
        }

        if (orig instanceof Remote) {
            // Turn remote object into org.omg.CORBA.Object
            try {
                // Returns null if JRMP; let next factory try
                // CNCtx will eventually throw IllegalArgumentException if
                // no CORBA object gotten
                return
                    CorbaUtils.remoteToCorba((Remote)orig, ((CNCtx)ctx)._orb);
            } catch (ClassNotFoundException e) {
                // RMI-IIOP library not available
                throw new ConfigurationException(
                    "javax.rmi packages not available");
            }
        }
        return null; // pass and let next state factory try
    }
}
