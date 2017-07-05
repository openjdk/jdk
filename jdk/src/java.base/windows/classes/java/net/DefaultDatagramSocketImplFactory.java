/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
package java.net;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class defines a factory for creating DatagramSocketImpls. It defaults
 * to creating plain DatagramSocketImpls, but may create other DatagramSocketImpls
 * by setting the impl.prefix system property.
 *
 * For Windows versions lower than Windows Vista a TwoStacksPlainDatagramSocketImpl
 * is always created. This impl supports IPv6 on these platform where available.
 *
 * On Windows platforms greater than Vista that support a dual layer TCP/IP stack
 * a DualStackPlainDatagramSocketImpl is created for DatagramSockets. For MulticastSockets
 * a TwoStacksPlainDatagramSocketImpl is always created. This is to overcome the lack
 * of behavior defined for multicasting over a dual layer socket by the RFC.
 *
 * @author Chris Hegarty
 */

class DefaultDatagramSocketImplFactory
{
    private static final Class<?> prefixImplClass;

    /* the windows version. */
    private static float version;

    /* java.net.preferIPv4Stack */
    private static boolean preferIPv4Stack = false;

    /* If the version supports a dual stack TCP implementation */
    private static final boolean useDualStackImpl;

    /* sun.net.useExclusiveBind */
    private static String exclBindProp;

    /* True if exclusive binding is on for Windows */
    private static final boolean exclusiveBind;

    static {
        Class<?> prefixImplClassLocal = null;
        boolean useDualStackImplLocal = false;
        boolean exclusiveBindLocal = true;

        // Determine Windows Version.
        java.security.AccessController.doPrivileged(
                new PrivilegedAction<Object>() {
                    public Object run() {
                        version = 0;
                        try {
                            version = Float.parseFloat(System.getProperties()
                                    .getProperty("os.version"));
                            preferIPv4Stack = Boolean.parseBoolean(
                                              System.getProperties()
                                              .getProperty(
                                                   "java.net.preferIPv4Stack"));
                            exclBindProp = System.getProperty(
                                    "sun.net.useExclusiveBind");
                        } catch (NumberFormatException e) {
                            assert false : e;
                        }
                        return null; // nothing to return
                    }
                });

        // (version >= 6.0) implies Vista or greater.
        if (version >= 6.0 && !preferIPv4Stack) {
            useDualStackImplLocal = true;
        }
        if (exclBindProp != null) {
            // sun.net.useExclusiveBind is true
            exclusiveBindLocal = exclBindProp.length() == 0 ? true
                    : Boolean.parseBoolean(exclBindProp);
        } else if (version < 6.0) {
            exclusiveBindLocal = false;
        }

        // impl.prefix
        String prefix = null;
        try {
            prefix = AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("impl.prefix", null));
            if (prefix != null)
                prefixImplClassLocal = Class.forName("java.net."+prefix+"DatagramSocketImpl");
        } catch (Exception e) {
            System.err.println("Can't find class: java.net." +
                                prefix +
                                "DatagramSocketImpl: check impl.prefix property");
        }

        prefixImplClass = prefixImplClassLocal;
        useDualStackImpl = useDualStackImplLocal;
        exclusiveBind = exclusiveBindLocal;
    }

    /**
     * Creates a new <code>DatagramSocketImpl</code> instance.
     *
     * @param   isMulticast true if this impl is to be used for a MutlicastSocket
     * @return  a new instance of <code>PlainDatagramSocketImpl</code>.
     */
    static DatagramSocketImpl createDatagramSocketImpl(boolean isMulticast)
        throws SocketException {
        if (prefixImplClass != null) {
            try {
                return (DatagramSocketImpl) prefixImplClass.newInstance();
            } catch (Exception e) {
                throw new SocketException("can't instantiate DatagramSocketImpl");
            }
        } else {
            if (useDualStackImpl && !isMulticast)
                return new DualStackPlainDatagramSocketImpl(exclusiveBind);
            else
                return new TwoStacksPlainDatagramSocketImpl(exclusiveBind && !isMulticast);
        }
    }
}
