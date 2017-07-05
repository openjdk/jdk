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

import java.util.*;

/**
 * <p>
 *
 * @author Hemanth Puttaswamy
 * </p>
 * <p>
 * A Convenient Abstraction to present String type Monitored Attribute. One
 * of the examples of StringMonitoredAttribute is the State information.
 * </p>
 */
public abstract class StringMonitoredAttributeBase
    extends MonitoredAttributeBase
{

  ///////////////////////////////////////
  // operations


/**
 * <p>
 * Constructs StringMonitoredAttribute with the MonitoredAttributeInfo
 * built with the class type of String.
 * </p>
 * <p>
 *
 * @param name of this attribute
 * </p>
 * <p>
 * @param description of this attribute
 * </p>
 * <p>
 * @return a StringMonitoredAttributeBase
 * </p>
 */
    public  StringMonitoredAttributeBase(String name, String description) {
        super( name );
        MonitoredAttributeInfoFactory f =
            MonitoringFactories.getMonitoredAttributeInfoFactory();
        MonitoredAttributeInfo maInfo = f.createMonitoredAttributeInfo(
            description, String.class, false, false );
       this.setMonitoredAttributeInfo( maInfo );
    } // end StringMonitoredAttributeBase


} // end StringMonitoredAttributeBase
