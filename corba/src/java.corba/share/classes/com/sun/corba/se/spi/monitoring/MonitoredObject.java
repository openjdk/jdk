/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.monitoring;


import com.sun.corba.se.spi.monitoring.MonitoredAttribute;
import java.util.*;
import java.util.Collection;

/**
 * <p>
 *
 * @author Hemanth Puttaswamy
 * </p>
 * <p>
 * Monitored Object provides an Hierarchichal view of the ORB Monitoring
 * System. It can contain multiple children and a single parent. Each
 * Monitored Object may also contain Multiple Monitored Attributes.
 * </p>
 */
public interface MonitoredObject {

  ///////////////////////////////////////
  // operations
/**
 * <p>
 * Gets the name of this MonitoredObject
 * </p><p>
 *
 * @return a String with name of this Monitored Object
 * </p>
 */
    public String getName();
/**
 * <p>
 * Gets the description of MonitoredObject
 * </p><p>
 *
 * @return a String with Monitored Object Description.
 * </p>
 */
    public String getDescription();
/**
 * <p>
 * This method will add a child Monitored Object to this Monitored Object.
 * </p>
 * <p>
 * </p>
 */
    public void addChild( MonitoredObject m );
/**
 * <p>
 * This method will remove child Monitored Object identified by the given name
 * </p>
 * <p>
 * @param name of the ChildMonitored Object
 * </p>
 */
    public void removeChild( String name );

/**
 * <p>
 * Gets the child MonitoredObject associated with this MonitoredObject
 * instance using name as the key. The name should be fully qualified name
 * like orb.connectionmanager
 * </p>
 * <p>
 *
 * @return a MonitoredObject identified by the given name
 * </p>
 * <p>
 * @param name of the ChildMonitored Object
 * </p>
 */
    public MonitoredObject getChild(String name);
/**
 * <p>
 * Gets all the Children registered under this instance of Monitored
 * Object.
 * </p>
 * <p>
 *
 * @return Collection of immediate Children associated with this MonitoredObject.
 * </p>
 */
    public Collection getChildren();
/**
 * <p>
 * Sets the parent for this Monitored Object.
 * </p>
 * <p>
 * </p>
 */
    public void setParent( MonitoredObject m );
/**
 * <p>
 * There will be only one parent for an instance of MontoredObject, this
 * call gets parent and returns null if the Monitored Object is the root.
 * </p>
 * <p>
 *
 * @return a MonitoredObject which is a Parent of this Monitored Object instance
 * </p>
 */
    public MonitoredObject getParent();

/**
 * <p>
 * Adds the attribute with the given name.
 * </p>
 * <p>
 *
 * </p>
 * <p>
 * @param value is the MonitoredAttribute which will be set as one of the
 * attribute of this MonitoredObject.
 * </p>
 */
    public void addAttribute(MonitoredAttribute value);
/**
 * <p>
 * Removes the attribute with the given name.
 * </p>
 * <p>
 *
 * </p>
 * <p>
 * @param name is the MonitoredAttribute name
 * </p>
 */
    public void removeAttribute(String name);

/**
 * <p>
 * Gets the Monitored Object registered by the given name
 * </p>
 *
 * <p>
 * @return a MonitoredAttribute identified by the given name
 * </p>
 * <p>
 * @param name of the attribute
 * </p>
 */
    public MonitoredAttribute getAttribute(String name);
/**
 * <p>
 * Gets all the Monitored Attributes for this Monitored Objects. It doesn't
 * include the Child Monitored Object, that needs to be traversed using
 * getChild() or getChildren() call.
 * </p>
 * <p>
 *
 * @return Collection of all the Attributes for this MonitoredObject
 * </p>
 */
    public Collection getAttributes();
/**
 * <p>
 * Clears the state of all the Monitored Attributes associated with the
 * Monitored Object. It will also clear the state on all it's child
 * Monitored Object. The call to clearState will be initiated from
 * CORBAMBean.startMonitoring() call.
 * </p>
 *
 */
    public void clearState();

} // end MonitoredObject
