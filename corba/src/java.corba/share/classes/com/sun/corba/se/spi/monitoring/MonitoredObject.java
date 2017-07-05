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
 * @author Hemanth Puttaswamy
 *
 * Monitored Object provides an Hierarchichal view of the ORB Monitoring
 * System. It can contain multiple children and a single parent. Each
 * Monitored Object may also contain Multiple Monitored Attributes.
 */
public interface MonitoredObject {

  ///////////////////////////////////////
  // operations
/**
 * Gets the name of this MonitoredObject
 *
 * @return a String with name of this Monitored Object
 */
    public String getName();
/**
 * Gets the description of MonitoredObject
 *
 * @return a String with Monitored Object Description.
 */
    public String getDescription();
/**
 * This method will add a child Monitored Object to this Monitored Object.
 */
    public void addChild( MonitoredObject m );
/**
 * This method will remove child Monitored Object identified by the given name
 *
 * @param name of the ChildMonitored Object
 */
    public void removeChild( String name );

/**
 * Gets the child MonitoredObject associated with this MonitoredObject
 * instance using name as the key. The name should be fully qualified name
 * like orb.connectionmanager
 *
 * @return a MonitoredObject identified by the given name
 * @param name of the ChildMonitored Object
 */
    public MonitoredObject getChild(String name);
/**
 * Gets all the Children registered under this instance of Monitored
 * Object.
 *
 * @return Collection of immediate Children associated with this MonitoredObject.
 */
    public Collection getChildren();
/**
 * Sets the parent for this Monitored Object.
 */
    public void setParent( MonitoredObject m );
/**
 * There will be only one parent for an instance of MontoredObject, this
 * call gets parent and returns null if the Monitored Object is the root.
 *
 * @return a MonitoredObject which is a Parent of this Monitored Object instance
 */
    public MonitoredObject getParent();

/**
 * Adds the attribute with the given name.
 *
 * @param value is the MonitoredAttribute which will be set as one of the
 * attribute of this MonitoredObject.
 */
    public void addAttribute(MonitoredAttribute value);
/**
 * Removes the attribute with the given name.
 *
 * @param name is the MonitoredAttribute name
 */
    public void removeAttribute(String name);

/**
 * Gets the Monitored Object registered by the given name
 *
 * @return a MonitoredAttribute identified by the given name
 * @param name of the attribute
 */
    public MonitoredAttribute getAttribute(String name);
/**
 * Gets all the Monitored Attributes for this Monitored Objects. It doesn't
 * include the Child Monitored Object, that needs to be traversed using
 * getChild() or getChildren() call.
 *
 * @return Collection of all the Attributes for this MonitoredObject
 */
    public Collection getAttributes();
/**
 * Clears the state of all the Monitored Attributes associated with the
 * Monitored Object. It will also clear the state on all it's child
 * Monitored Object. The call to clearState will be initiated from
 * CORBAMBean.startMonitoring() call.
 */
    public void clearState();

} // end MonitoredObject
