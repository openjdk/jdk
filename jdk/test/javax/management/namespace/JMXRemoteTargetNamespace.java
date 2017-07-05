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


import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.event.EventClient;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.namespace.JMXRemoteNamespaceMBean;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

// These options originally in the draft of javax/management/namespaces
// but we decided to retire them - since they could be implemented
// by subclasses. The JMXRemoteTargetNamespace is such a subclass.
//
public class JMXRemoteTargetNamespace extends JMXRemoteNamespace {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(JMXRemoteTargetNamespace.class.getName());
    public static final String CREATE_EVENT_CLIENT =
           "jmx.test.create.event.client";

    private final String sourceNamespace;
    private final boolean createEventClient;

    public JMXRemoteTargetNamespace(JMXServiceURL sourceURL,
            Map<String,?> optionsMap) {
        this(sourceURL,optionsMap,null);
    }

    public JMXRemoteTargetNamespace(JMXServiceURL sourceURL,
            Map<String,?> optionsMap, String sourceNamespace) {
        this(sourceURL,optionsMap,sourceNamespace,false);
    }

    public JMXRemoteTargetNamespace(JMXServiceURL sourceURL,
            Map<String,?> optionsMap, String sourceNamespace,
            boolean createEventClient) {
        super(sourceURL,optionsMap);
        this.sourceNamespace = sourceNamespace;
        this.createEventClient = createEventClient(optionsMap);
    }

    private boolean createEventClient(Map<String,?> options) {
        if (options == null) return false;
        final Object createValue = options.get(CREATE_EVENT_CLIENT);
        if (createValue == null) return false;
        if (createValue instanceof Boolean)
            return ((Boolean)createValue).booleanValue();
        if (createValue instanceof String)
            return Boolean.valueOf((String)createValue);
        throw new IllegalArgumentException("Bad type for value of property " +
                CREATE_EVENT_CLIENT+": "+createValue.getClass().getName());
    }

    @Override
    protected JMXConnector newJMXConnector(JMXServiceURL url,
            Map<String, ?> env) throws IOException {
        JMXConnector sup = super.newJMXConnector(url, env);
        if (sourceNamespace == null || "".equals(sourceNamespace))
            return sup;
        if (createEventClient)
            sup = EventClient.withEventClient(sup);
        return JMXNamespaces.narrowToNamespace(sup, sourceNamespace);
    }


    /**
     * Creates a target name space to mirror a remote source name space in
     * the target server.
     * @param targetServer A connection to the target MBean server in which
     *        the new name space should be created.
     * @param targetPath the name space to create in the target server. Note
     *        that if the target name space is a path - that is if
     *        {@code targetPath} contains '//', then the parent name space
     *        must be pre-existing in the target server. Attempting to create
     *        {code targetPath="a//b//c"} in {@code targetServer}
     *        will fail if name space {@code "a//b"} doesn't already exists
     *        in {@code targetServer}.
     * @param sourceURL a JMX service URL that can be used to connect to the
     *        source MBean server.
     * @param options the set of options to use when creating the
     *        {@link #JMXRemoteNamespace JMXRemoteNamespace} that will
     *        handle the new name space.
     * @return An {@code ObjectInstance} representing the
     *         {@link JMXRemoteNamespaceMBean} which handles the
     *         new name space.
     *
     **/
    public static ObjectInstance createNamespace(
            MBeanServerConnection targetServer,
            String targetPath,
            JMXServiceURL sourceURL,
            Map<String,?> options)
        throws IOException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException {
        final ObjectName name =
                JMXNamespaces.getNamespaceObjectName(targetPath);
        return createInstance(targetServer, name, sourceURL, options, null);
    }

    /**
     * Creates a target name space to mirror a remote source name space in
     * the target server.
     * @param targetServer A connection to the target MBean server in which
     *        the new name space should be created.
     * @param targetPath the name space to create in the target server. Note
     *        that if the target name space is a path - that is if
     *        {@code targetPath} contains '//', then the parent name space
     *        must be pre-existing in the target server. Attempting to create
     *        {code targetPath="a//b//c"} in {@code targetServer}
     *        will fail if name space {@code "a//b"} doesn't already exists
     *        in {@code targetServer}.
     * @param sourceURL a JMX service URL that can be used to connect to the
     *        source MBean server.
     * @param sourcePath the source namespace path insode the source server.
     * @param options the set of options to use when creating the
     *        {@link #JMXRemoteNamespace JMXRemoteNamespace} that will
     *        handle the new name space.
     * @return An {@code ObjectInstance} representing the
     *         {@link JMXRemoteNamespaceMBean} which handles the
     *         new name space.
     *
     **/
    public static ObjectInstance createNamespace(
            MBeanServerConnection targetServer,
            String targetPath,
            JMXServiceURL sourceURL,
            Map<String,?> options,
            String sourcePath)
        throws IOException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException {
        final ObjectName name =
                JMXNamespaces.getNamespaceObjectName(targetPath);
        return createInstance(targetServer, name, sourceURL, options, sourcePath);
    }

    /**
     * Creates and registers a {@link JMXRemoteNamespaceMBean} in a target
     * server, to mirror a remote source name space.
     *
     * @param server A connection to the target MBean server in which
     *        the new name space should be created.
     * @param handlerName the name of the JMXRemoteNamespace to create.
     *        This must be a compliant name space handler name as returned
     *        by {@link
     *        JMXNamespaces#getNamespaceObjectName JMXNamespaces.getNamespaceObjectName}.
     * @param sourceURL a JMX service URL that can be used to connect to the
     *        source MBean server.
     * @param sourcePath the path inside the source server
     * @param options the set of options to use when creating the
     *        {@link #JMXRemoteNamespace JMXRemoteNamespace} that will
     *        handle the new name space.
     * @return An {@code ObjectInstance} representing the new
     *         {@link JMXRemoteNamespaceMBean} created.
     * @see #createNamespace createNamespace
     */
     static ObjectInstance createInstance(MBeanServerConnection server,
                  ObjectName handlerName,
                  JMXServiceURL sourceURL, Map<String,?> options,
                  String sourcePath)
        throws IOException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException {
        try {
            final String[] signature = {
                JMXServiceURL.class.getName(),
                Map.class.getName(),
                String.class.getName()
            };
            final Object[] params = {
                sourceURL,options,sourcePath
            };
            final ObjectInstance instance =
                server.createMBean(JMXRemoteTargetNamespace.class.getName(),
                handlerName,params,signature);
            return instance;
        } catch (NotCompliantMBeanException ex) {
            throw new RuntimeException("unexpected exception: " + ex, ex);
        } catch (ReflectionException ex) {
            throw new RuntimeException("unexpected exception: " + ex, ex);
        }
    }

}
