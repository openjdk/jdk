/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.org.glassfish.gmbal.AMXClient;
import com.sun.org.glassfish.gmbal.GmbalMBean;
import com.sun.org.glassfish.gmbal.ManagedObjectManager;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ResourceBundle;

/**
 * {@link ManagedObjectManager} proxy class for {@link WSEndpointImpl} instances that could be used when Gmbal API calls
 * need to be deferred. The proxy tries to defer a need of a real ManagedObjectManager instance to the time when any
 * method from {@link ManagedObjectManager} is invoked on it. In this case a real instance of ManagedObjectManager is
 * obtained from WSEndpointImpl and the method is rather invoked on this object.
 */
public class WSEndpointMOMProxy implements ManagedObjectManager {

    private final @NotNull
    WSEndpointImpl wsEndpoint;
    private ManagedObjectManager managedObjectManager;

    WSEndpointMOMProxy(@NotNull WSEndpointImpl wsEndpoint) {
        this.wsEndpoint = wsEndpoint;
    }

    /**
     * Returns a real instance of {@link ManagedObjectManager}
     *
     * @return an ManagedObjectManager instance
     */
    private ManagedObjectManager getManagedObjectManager() {
        if (managedObjectManager == null) {
            managedObjectManager = wsEndpoint.obtainManagedObjectManager();
        }
        return managedObjectManager;
    }

    void setManagedObjectManager(ManagedObjectManager managedObjectManager) {
        this.managedObjectManager = managedObjectManager;
    }

    /**
     * Returns {@code true} if this proxy contains a reference to real ManagedObjectManager instance, {@code false}
     * otherwise.
     *
     * @return {@code true} if ManagedObjectManager has been created, {@code false} otherwise.
     */
    public boolean isInitialized() {
        return this.managedObjectManager != null;
    }

    public WSEndpointImpl getWsEndpoint() {
        return wsEndpoint;
    }

    public void suspendJMXRegistration() {
        getManagedObjectManager().suspendJMXRegistration();
    }

    public void resumeJMXRegistration() {
        getManagedObjectManager().resumeJMXRegistration();
    }

    public boolean isManagedObject(Object obj) {
        return getManagedObjectManager().isManagedObject(obj);
    }

    public GmbalMBean createRoot() {
        return getManagedObjectManager().createRoot();
    }

    public GmbalMBean createRoot(Object root) {
        return getManagedObjectManager().createRoot(root);
    }

    public GmbalMBean createRoot(Object root, String name) {
        return getManagedObjectManager().createRoot(root, name);
    }

    public Object getRoot() {
        return getManagedObjectManager().getRoot();
    }

    public GmbalMBean register(Object parent, Object obj, String name) {
        return getManagedObjectManager().register(parent, obj, name);
    }

    public GmbalMBean register(Object parent, Object obj) {
        return getManagedObjectManager().register(parent, obj);
    }

    public GmbalMBean registerAtRoot(Object obj, String name) {
        return getManagedObjectManager().registerAtRoot(obj, name);
    }

    public GmbalMBean registerAtRoot(Object obj) {
        return getManagedObjectManager().registerAtRoot(obj);
    }

    public void unregister(Object obj) {
        getManagedObjectManager().unregister(obj);
    }

    public ObjectName getObjectName(Object obj) {
        return getManagedObjectManager().getObjectName(obj);
    }

    public AMXClient getAMXClient(Object obj) {
        return getManagedObjectManager().getAMXClient(obj);
    }

    public Object getObject(ObjectName oname) {
        return getManagedObjectManager().getObject(oname);
    }

    public void stripPrefix(String... str) {
        getManagedObjectManager().stripPrefix(str);
    }

    public void stripPackagePrefix() {
        getManagedObjectManager().stripPackagePrefix();
    }

    public String getDomain() {
        return getManagedObjectManager().getDomain();
    }

    public void setMBeanServer(MBeanServer server) {
        getManagedObjectManager().setMBeanServer(server);
    }

    public MBeanServer getMBeanServer() {
        return getManagedObjectManager().getMBeanServer();
    }

    public void setResourceBundle(ResourceBundle rb) {
        getManagedObjectManager().setResourceBundle(rb);
    }

    public ResourceBundle getResourceBundle() {
        return getManagedObjectManager().getResourceBundle();
    }

    public void addAnnotation(AnnotatedElement element, Annotation annotation) {
        getManagedObjectManager().addAnnotation(element, annotation);
    }

    public void setRegistrationDebug(RegistrationDebugLevel level) {
        getManagedObjectManager().setRegistrationDebug(level);
    }

    public void setRuntimeDebug(boolean flag) {
        getManagedObjectManager().setRuntimeDebug(flag);
    }

    public void setTypelibDebug(int level) {
        getManagedObjectManager().setTypelibDebug(level);
    }

    public void setJMXRegistrationDebug(boolean flag) {
        getManagedObjectManager().setJMXRegistrationDebug(flag);
    }

    public String dumpSkeleton(Object obj) {
        return getManagedObjectManager().dumpSkeleton(obj);
    }

    public void suppressDuplicateRootReport(boolean suppressReport) {
        getManagedObjectManager().suppressDuplicateRootReport(suppressReport);
    }

    public void close() throws IOException {
        getManagedObjectManager().close();
    }

}
