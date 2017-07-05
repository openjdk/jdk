/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.org.glassfish.gmbal;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ResourceBundle;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/** NOP impl of ManagedObjectManager used when annotations and ManagedObjectManager
 * are needed, but MBeans are not.  This allows using gmbal to optionally support
 * MBeans.  This is the implementation of the ManagedObjectManager that is used when
 * the full implementation is not available.
 *
 * @author ken_admin
 */
class ManagedObjectManagerNOPImpl implements ManagedObjectManager {
    static final ManagedObjectManager self =
        new ManagedObjectManagerNOPImpl() ;
    private static final GmbalMBean gmb =
        new GmbalMBeanNOPImpl() ;

    private ManagedObjectManagerNOPImpl() {}

    public void suspendJMXRegistration() {
        // NOP
    }

    public void resumeJMXRegistration() {
        // NOP
    }

    public boolean isManagedObject( Object obj ) {
        return false ;
    }

    public GmbalMBean createRoot() {
        return gmb ;
    }

    public GmbalMBean createRoot(Object root) {
        return gmb ;
    }

    public GmbalMBean createRoot(Object root, String name) {
        return gmb ;
    }

    public Object getRoot() {
        return null ;
    }

    public GmbalMBean register(Object parent, Object obj, String name) {
        return gmb ;
    }

    public GmbalMBean register(Object parent, Object obj) {
        return gmb ;
    }

    public GmbalMBean registerAtRoot(Object obj, String name) {
        return gmb ;
    }

    public GmbalMBean registerAtRoot(Object obj) {
        return gmb ;
    }

    public void unregister(Object obj) {
        // NOP
    }

    public ObjectName getObjectName(Object obj) {
        return null ;
    }

    public Object getObject(ObjectName oname) {
        return null ;
    }

    public void stripPrefix(String... str) {
        // NOP
    }

    public String getDomain() {
        return null ;
    }

    public void setMBeanServer(MBeanServer server) {
        // NOP
    }

    public MBeanServer getMBeanServer() {
        return null ;
    }

    public void setResourceBundle(ResourceBundle rb) {
        // NOP
    }

    public ResourceBundle getResourceBundle() {
        return null ;
    }

    public void addAnnotation(AnnotatedElement element, Annotation annotation) {
        // NOP
    }

    public void setRegistrationDebug(RegistrationDebugLevel level) {
        // NOP
    }

    public void setRuntimeDebug(boolean flag) {
        // NOP
    }

    public String dumpSkeleton(Object obj) {
        return "" ;
    }

    public void close() throws IOException {
        // NOP
    }

    public void setTypelibDebug(int level) {
        // NOP
    }

    public void stripPackagePrefix() {
        // NOP
    }

    public void suppressDuplicateRootReport(boolean suppressReport) {
        // NOP
    }

    public AMXClient getAMXClient(Object obj) {
        return null ;
    }

    public void setJMXRegistrationDebug(boolean flag) {
        // NOP
    }
}
