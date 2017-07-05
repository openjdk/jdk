/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.config.management.policy.ManagedClientAssertion;
import com.sun.xml.internal.ws.api.config.management.policy.ManagedServiceAssertion;
import com.sun.xml.internal.ws.api.config.management.policy.ManagementAssertion.Setting;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.client.Stub;
import com.sun.org.glassfish.external.amx.AMXGlassfish;
import com.sun.org.glassfish.gmbal.Description;
import com.sun.org.glassfish.gmbal.InheritedAttribute;
import com.sun.org.glassfish.gmbal.InheritedAttributes;
import com.sun.org.glassfish.gmbal.ManagedData;
import com.sun.org.glassfish.gmbal.ManagedObjectManager;
import com.sun.org.glassfish.gmbal.ManagedObjectManagerFactory;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// BEGIN IMPORTS FOR RewritingMOM
import java.util.ResourceBundle ;
import java.lang.reflect.AnnotatedElement ;
import java.lang.annotation.Annotation ;
import javax.management.ObjectName ;
import javax.management.MBeanServer ;
import com.sun.org.glassfish.gmbal.AMXClient;
import com.sun.org.glassfish.gmbal.GmbalMBean;
// END IMPORTS FOR RewritingMOM

/**
 * @author Harold Carr
 */
public abstract class MonitorBase {

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".monitoring");

    /**
     * Endpoint monitoring is ON by default.
     *
     * prop    |  no assert | assert/no mon | assert/mon off | assert/mon on
     * -------------------------------------------------------------------
     * not set |    on      |      on       |     off        |     on
     * false   |    off     |      off      |     off        |     off
     * true    |    on      |      on       |     off        |     on
     */
    @NotNull public ManagedObjectManager createManagedObjectManager(final WSEndpoint endpoint) {
        // serviceName + portName identifies the managed objects under it.
        // There can be multiple services in the container.
        // The same serviceName+portName can live in different apps at
        // different endpoint addresses.
        //
        // In general, monitoring will add -N, where N is unique integer,
        // in case of collisions.
        //
        // The endpoint address would be unique, but we do not know
        // the endpoint address until the first request comes in,
        // which is after monitoring is setup.

        String rootName =
            endpoint.getServiceName().getLocalPart()
            + "-"
            + endpoint.getPortName().getLocalPart();

        if (rootName.equals("-")) {
            rootName = "provider";
        }

        // contextPath is not always available
        final String contextPath = getContextPath(endpoint);
        if (contextPath != null) {
            rootName = contextPath + "-" + rootName;
        }

        final ManagedServiceAssertion assertion =
            ManagedServiceAssertion.getAssertion(endpoint);
        if (assertion != null) {
            final String id = assertion.getId();
            if (id != null) {
                rootName = id;
            }
            if (assertion.monitoringAttribute() == Setting.OFF) {
                return disabled("This endpoint", rootName);
            }
        }

        if (endpointMonitoring.equals(Setting.OFF)) {
            return disabled("Global endpoint", rootName);
        }
        return createMOMLoop(rootName, 0);
    }

    private String getContextPath(final WSEndpoint endpoint) {
        try {
            Container container = endpoint.getContainer();
            Method getSPI =
                container.getClass().getDeclaredMethod("getSPI", Class.class);
            getSPI.setAccessible(true);
            Class servletContextClass =
                Class.forName("javax.servlet.ServletContext");
            Object servletContext =
                getSPI.invoke(container, servletContextClass);
            if (servletContext != null) {
                Method getContextPath = servletContextClass.getDeclaredMethod("getContextPath");
                getContextPath.setAccessible(true);
                return (String) getContextPath.invoke(servletContext);
            }
            return null;
        } catch (Throwable t) {
            logger.log(Level.FINEST, "getContextPath", t);
        }
        return null;
    }

    /**
     * Client monitoring is OFF by default because there is
     * no standard stub.close() method.  Therefore people do
     * not typically close a stub when they are done with it
     * (even though the RI does provide a .close).
     * <pre>
     * prop    |  no assert | assert/no mon | assert/mon off | assert/mon on
     * -------------------------------------------------------------------
     * not set |    off     |      off      |     off        |     on
     * false   |    off     |      off      |     off        |     off
     * true    |    on      |      on       |     off        |     on
     * </pre>
    */
    @NotNull public ManagedObjectManager createManagedObjectManager(final Stub stub) {
        EndpointAddress ea = stub.requestContext.getEndpointAddress();
        if (ea == null) {
            return ManagedObjectManagerFactory.createNOOP();
        }

        String rootName = ea.toString();

        final ManagedClientAssertion assertion =
            ManagedClientAssertion.getAssertion(stub.getPortInfo());
        if (assertion != null) {
            final String id = assertion.getId();
            if (id != null) {
                rootName = id;
            }
            if (assertion.monitoringAttribute() == Setting.OFF) {
                return disabled("This client", rootName);
            } else if (assertion.monitoringAttribute() == Setting.ON &&
                       clientMonitoring != Setting.OFF) {
                return createMOMLoop(rootName, 0);
            }
        }

        if (clientMonitoring == Setting.NOT_SET ||
            clientMonitoring == Setting.OFF)
        {
            return disabled("Global client", rootName);
        }
        return createMOMLoop(rootName, 0);
    }

    @NotNull private ManagedObjectManager disabled(final String x, final String rootName) {
        final String msg = x + " monitoring disabled. " + rootName + " will not be monitored";
        logger.log(Level.CONFIG, msg);
        return ManagedObjectManagerFactory.createNOOP();
    }

    private @NotNull ManagedObjectManager createMOMLoop(final String rootName, final int unique) {
        final boolean isFederated = AMXGlassfish.getGlassfishVersion() != null;
        ManagedObjectManager mom = createMOM(isFederated);
        mom = initMOM(mom);
        mom = createRoot(mom, rootName, unique);
        return mom;
    }

    private @NotNull ManagedObjectManager createMOM(final boolean isFederated) {
        try {
            return new RewritingMOM(isFederated ?
                ManagedObjectManagerFactory.createFederated(
                    AMXGlassfish.DEFAULT.serverMon(AMXGlassfish.DEFAULT.dasName()))
                :
                ManagedObjectManagerFactory.createStandalone("com.sun.metro"));
        } catch (Throwable t) {
            if (isFederated) {
                logger.log(Level.CONFIG, "Problem while attempting to federate with GlassFish AMX monitoring.  Trying standalone.", t);
                return createMOM(false);
            } else {
                logger.log(Level.WARNING, "Ignoring exception - starting up without monitoring", t);
                return ManagedObjectManagerFactory.createNOOP();
            }
        }
    }

    private @NotNull ManagedObjectManager initMOM(final ManagedObjectManager mom) {
        try {
            if (typelibDebug != -1) {
                mom.setTypelibDebug(typelibDebug);
            }
            if (registrationDebug.equals("FINE")) {
                mom.setRegistrationDebug(ManagedObjectManager.RegistrationDebugLevel.FINE);
            } else if (registrationDebug.equals("NORMAL")) {
                mom.setRegistrationDebug(ManagedObjectManager.RegistrationDebugLevel.NORMAL);
            } else {
                mom.setRegistrationDebug(ManagedObjectManager.RegistrationDebugLevel.NONE);
            }

            mom.setRuntimeDebug(runtimeDebug);

            // Instead of GMBAL throwing an exception and logging
            // duplicate name, just have it return null.
            mom.suppressDuplicateRootReport(true);

            mom.stripPrefix(
                "com.sun.xml.internal.ws.server",
                "com.sun.xml.internal.ws.rx.rm.runtime.sequence");

            // Add annotations to a standard class
            mom.addAnnotation(javax.xml.ws.WebServiceFeature.class, DummyWebServiceFeature.class.getAnnotation(ManagedData.class));
            mom.addAnnotation(javax.xml.ws.WebServiceFeature.class, DummyWebServiceFeature.class.getAnnotation(Description.class));
            mom.addAnnotation(javax.xml.ws.WebServiceFeature.class, DummyWebServiceFeature.class.getAnnotation(InheritedAttributes.class));

            // Defer so we can register "this" as root from
            // within constructor.
            mom.suspendJMXRegistration();

        } catch (Throwable t) {
            try {
                mom.close();
            } catch (IOException e) {
                logger.log(Level.CONFIG, "Ignoring exception caught when closing unused ManagedObjectManager", e);
            }
            logger.log(Level.WARNING, "Ignoring exception - starting up without monitoring", t);
            return ManagedObjectManagerFactory.createNOOP();
        }
        return mom;
    }

    private ManagedObjectManager createRoot(final ManagedObjectManager mom, final String rootName, int unique) {
        final String name = rootName + (unique == 0 ? "" : "-" + String.valueOf(unique));
        try {
            final Object ignored = mom.createRoot(this, name);
            if (ignored != null) {
                ObjectName ignoredName = mom.getObjectName(mom.getRoot());
                // The name is null when the MOM is a NOOP.
                if (ignoredName != null) {
                    logger.log(Level.INFO, "Metro monitoring rootname successfully set to: {0}", ignoredName);
                }
                return mom;
            }
            try {
                mom.close();
            } catch (IOException e) {
                logger.log(Level.CONFIG, "Ignoring exception caught when closing unused ManagedObjectManager", e);
            }
            final String basemsg ="Duplicate Metro monitoring rootname: " + name + " : ";
            if (unique > maxUniqueEndpointRootNameRetries) {
                final String msg = basemsg + "Giving up.";
                logger.log(Level.INFO, msg);
                return ManagedObjectManagerFactory.createNOOP();
            }
            final String msg = basemsg + "Will try to make unique";
            logger.log(Level.CONFIG, msg);
            return createMOMLoop(rootName, ++unique);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error while creating monitoring root with name: " + rootName, t);
            return ManagedObjectManagerFactory.createNOOP();
        }
    }

    private static Setting clientMonitoring          = Setting.NOT_SET;
    private static Setting endpointMonitoring        = Setting.NOT_SET;
    private static int     typelibDebug                     = -1;
    private static String  registrationDebug                = "NONE";
    private static boolean runtimeDebug                     = false;
    private static int     maxUniqueEndpointRootNameRetries = 100;
    private static final String monitorProperty = "com.sun.xml.internal.ws.monitoring.";

    private static Setting propertyToSetting(String propName) {
        String s = System.getProperty(propName);
        if (s == null) {
            return Setting.NOT_SET;
        }
        s = s.toLowerCase();
        if (s.equals("false") || s.equals("off")) {
            return Setting.OFF;
        } else if (s.equals("true") || s.equals("on")) {
            return Setting.ON;
        }
        return Setting.NOT_SET;
    }

    static {
        try {
            endpointMonitoring = propertyToSetting(monitorProperty + "endpoint");

            clientMonitoring = propertyToSetting(monitorProperty + "client");

            Integer i = Integer.getInteger(monitorProperty + "typelibDebug");
            if (i != null) {
                typelibDebug = i;
            }

            String s = System.getProperty(monitorProperty + "registrationDebug");
            if (s != null) {
                registrationDebug = s.toUpperCase();
            }

            s = System.getProperty(monitorProperty + "runtimeDebug");
            if (s != null && s.toLowerCase().equals("true")) {
                runtimeDebug = true;
            }

            i = Integer.getInteger(monitorProperty + "maxUniqueEndpointRootNameRetries");
            if (i != null) {
                maxUniqueEndpointRootNameRetries = i;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error while reading monitoring properties", e);
        }
    }
}


// This enables us to annotate the WebServiceFeature class even thought
// we can't explicitly put the annotations in the class itself.
@ManagedData
@Description("WebServiceFeature")
@InheritedAttributes({
        @InheritedAttribute(methodName="getID", description="unique id for this feature"),
        @InheritedAttribute(methodName="isEnabled", description="true if this feature is enabled")
})
interface DummyWebServiceFeature {}

class RewritingMOM implements ManagedObjectManager
{
    private final ManagedObjectManager mom;

    private final static String gmbalQuotingCharsRegex = "\n|\\|\"|\\*|\\?|:|=|,";
    private final static String replacementChar        = "-";

    RewritingMOM(final ManagedObjectManager mom) { this.mom = mom; }

    private String rewrite(final String x) {
        return x.replaceAll(gmbalQuotingCharsRegex, replacementChar);
    }

    // The interface

    @Override public void suspendJMXRegistration() { mom.suspendJMXRegistration(); }
    @Override public void resumeJMXRegistration()  { mom.resumeJMXRegistration(); }
    @Override public GmbalMBean createRoot()       { return mom.createRoot(); }
    @Override public GmbalMBean createRoot(Object root) { return mom.createRoot(root); }
    @Override public GmbalMBean createRoot(Object root, String name) {
        return mom.createRoot(root, rewrite(name));
    }
    @Override public Object getRoot() { return mom.getRoot(); }
    @Override public GmbalMBean register(Object parent, Object obj, String name) {
        return mom.register(parent, obj, rewrite(name));
    }
    @Override public GmbalMBean register(Object parent, Object obj) { return mom.register(parent, obj);}
    @Override public GmbalMBean registerAtRoot(Object obj, String name) {
        return mom.registerAtRoot(obj, rewrite(name));
    }
    @Override public GmbalMBean registerAtRoot(Object obj) { return mom.registerAtRoot(obj); }
    @Override public void unregister(Object obj)           { mom.unregister(obj); }
    @Override public ObjectName getObjectName(Object obj)  { return mom.getObjectName(obj); }
    @Override public AMXClient getAMXClient(Object obj)    { return mom.getAMXClient(obj); }
    @Override public Object getObject(ObjectName oname)    { return mom.getObject(oname); }
    @Override public void stripPrefix(String... str)       { mom.stripPrefix(str); }
    @Override public void stripPackagePrefix()             { mom.stripPackagePrefix(); }
    @Override public String getDomain()                    { return mom.getDomain(); }
    @Override public void setMBeanServer(MBeanServer server){mom.setMBeanServer(server); }
    @Override public MBeanServer getMBeanServer()          { return mom.getMBeanServer(); }
    @Override public void setResourceBundle(ResourceBundle rb) { mom.setResourceBundle(rb); }
    @Override public ResourceBundle getResourceBundle()    { return mom.getResourceBundle(); }
    @Override public void addAnnotation(AnnotatedElement element, Annotation annotation) { mom.addAnnotation(element, annotation); }
    @Override public void setRegistrationDebug(RegistrationDebugLevel level) { mom.setRegistrationDebug(level); }
    @Override public void setRuntimeDebug(boolean flag) { mom.setRuntimeDebug(flag); }
    @Override public void setTypelibDebug(int level)    { mom.setTypelibDebug(level); }
    @Override public String dumpSkeleton(Object obj)    { return mom.dumpSkeleton(obj); }
    @Override public void suppressDuplicateRootReport(boolean suppressReport) { mom.suppressDuplicateRootReport(suppressReport); }
    @Override public void close() throws IOException    { mom.close(); }
    @Override public void setJMXRegistrationDebug(boolean x) { mom.setJMXRegistrationDebug(x); }
    @Override public boolean isManagedObject(Object x)  { return mom.isManagedObject(x); }
}

// End of file.
