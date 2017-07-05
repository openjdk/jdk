/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.corba.se.spi.monitoring;

import com.sun.corba.se.spi.monitoring.MonitoredAttributeInfo;
import java.util.*;

/**
 * <p>
 *
 * @author Hemanth Puttaswamy
 * </p>
 * <p>
 * Monitored Attribute is the interface to represent a Monitorable
 * Attribute. Using this interface, one can get the value of the attribute
 * and set the value if it is a writeable attribute.
 * </p>
 */
public interface MonitoredAttribute {

  ///////////////////////////////////////
  // operations

/**
 * <p>
 * Gets the Monitored Attribute Info for the attribute.
 * </p>
 * <p>
 *
 * @param monitoredAttributeInfo for this Monitored Attribute.
 * </p>
 */
    public MonitoredAttributeInfo getAttributeInfo();
/**
 * <p>
 * Sets the value for the Monitored Attribute if isWritable() is false, the
 * method will throw ILLEGAL Operation exception.
 *
 * Also, the type of 'value' should be same as specified in the
 * MonitoredAttributeInfo for a particular instance.
 * </p>
 * <p>
 *
 * @param value should be any one of the Basic Java Type Objects which are
 * Long, Double, Float, String, Integer, Short, Character, Byte.
 * </p>
 */
    public void setValue(Object value);


/**
 * <p>
 * Gets the value of the Monitored Attribute. The value can be obtained
 * from different parts of the module. User may choose to delegate the call
 * to getValue() to other variables.
 *
 * NOTE: It is important to make sure that the type of Object returned in
 * getvalue is same as the one specified in MonitoredAttributeInfo for this
 * attribute.
 * </p>
 * <p>
 *
 * </p>
 * <p>
 *
 * @param value is the current value for this MonitoredAttribute
 * </p>
 */
    public Object getValue();
/**
 * <p>
 * Gets the name of the Monitored Attribute.
 * </p>
 * <p>
 *
 * @param name of this Attribute
 * </p>
 */
    public String getName();
/**
 * <p>
 * If this attribute needs to be cleared, the user needs to implement this
 * method to reset the state to initial state. If the Monitored Attribute
 * doesn't change like for example (ConnectionManager High Water Mark),
 * then clearState() is a No Op.
 * </p>
 *
 */
    public void clearState();

} // end MonitoredAttribute
