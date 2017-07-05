/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.namespace;


import com.sun.jmx.defaults.JmxProperties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;


/**
 * A RoutingConnectionProxy is an MBeanServerConnection proxy that proxies a
 * source name space in a source MBeanServerConnection.
 * It wraps a source MBeanServerConnection, and rewrites routing
 * ObjectNames. It is used to implement
 * {@code JMXNamespaces.narrowToNamespace(MBeanServerConnection)}.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
// See class hierarchy and detailled explanations in RoutingProxy in this
// package.
//
public class RoutingConnectionProxy
        extends RoutingProxy<MBeanServerConnection> {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;


    /**
     * Creates a new instance of RoutingConnectionProxy
     */
    public RoutingConnectionProxy(MBeanServerConnection source,
                               String sourceDir,
                               String targetDir,
                               boolean probe) {
        super(source, sourceDir, targetDir, probe);

        if (LOG.isLoggable(Level.FINER))
            LOG.finer("RoutingConnectionProxy for " + getSourceNamespace() +
                      " created");
    }

    @Override
    public String toString() {
        final String targetNs = getTargetNamespace();
        final String sourceNs = getSourceNamespace();
        String wrapped = String.valueOf(source());
        if ("".equals(targetNs)) {
            return "JMXNamespaces.narrowToNamespace("+
                    wrapped+", \""+
                    sourceNs+"\")";
        }
        return this.getClass().getSimpleName()+"("+wrapped+", \""+
               sourceNs+"\", \""+
               targetNs+"\")";
    }

    static final RoutingProxyFactory
            <MBeanServerConnection,RoutingConnectionProxy>
        FACTORY = new RoutingProxyFactory
        <MBeanServerConnection,RoutingConnectionProxy>() {

        public RoutingConnectionProxy newInstance(MBeanServerConnection source,
                String sourcePath, String targetPath, boolean probe) {
            return new RoutingConnectionProxy(source,sourcePath,
                    targetPath, probe);
        }
    };

    public static MBeanServerConnection cd(
            MBeanServerConnection source, String sourcePath, boolean probe) {
        return RoutingProxy.cd(RoutingConnectionProxy.class, FACTORY,
                source, sourcePath, probe);
    }

}
