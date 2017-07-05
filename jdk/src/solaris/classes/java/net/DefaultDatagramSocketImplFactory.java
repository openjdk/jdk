/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.net;

import java.security.AccessController;

/**
 * This class defines a factory for creating DatagramSocketImpls. It defaults
 * to creating plain DatagramSocketImpls, but may create other DatagramSocketImpls
 * by setting the impl.prefix system property.
 *
 * @author Chris Hegarty
 */

class DefaultDatagramSocketImplFactory {
    static Class prefixImplClass = null;

    static {
        String prefix = null;
        try {
            prefix = AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("impl.prefix", null));
            if (prefix != null)
                prefixImplClass = Class.forName("java.net."+prefix+"DatagramSocketImpl");
        } catch (Exception e) {
            System.err.println("Can't find class: java.net." +
                                prefix +
                                "DatagramSocketImpl: check impl.prefix property");
            //prefixImplClass = null;
        }
    }

    /**
     * Creates a new <code>DatagramSocketImpl</code> instance.
     *
     * @param   isMulticast     true if this impl if for a MutlicastSocket
     * @return  a new instance of a <code>DatagramSocketImpl</code>.
     */
    static DatagramSocketImpl createDatagramSocketImpl(boolean isMulticast /*unused on unix*/)
        throws SocketException {
        if (prefixImplClass != null) {
            try {
                return (DatagramSocketImpl)prefixImplClass.newInstance();
            } catch (Exception e) {
                throw new SocketException("can't instantiate DatagramSocketImpl");
            }
        } else {
            return new java.net.PlainDatagramSocketImpl();
        }
    }
}
