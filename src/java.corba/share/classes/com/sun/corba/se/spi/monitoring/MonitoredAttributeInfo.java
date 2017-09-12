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
 * @author Hemanth Puttaswamy
 *
 * Monitored AttributeInfo contains the meta information of the Monitored
 * Attribute.
 */
public interface MonitoredAttributeInfo {

  ///////////////////////////////////////
  // operations

/**
 * If the Attribute is writable from ASAdmin then isWritable() will return
 * true.
 *
 * @return a boolean with true or false
 */
    public boolean isWritable();
/**
 * isStatistic() is true if the attribute is presented as a Statistic.
 *
 * @return a boolean with true or false
 */
    public boolean isStatistic();
/**
 * Class Type: We will allow only basic class types:
 * <ol>
 * <li>Boolean</li>
 * <li>Integer</li>
 * <li>Byte</li>
 * <li>Long</li>
 * <li>Float</li>
 * <li>Double</li>
 * <li>String</li>
 * <li>Character</li>
 * </ol>
 *
 * @return a Class Type
 */
    public Class type();
/**
 * Get's the description for the Monitored Attribute.
 *
 * @return a String with description
 */
    public String getDescription();

} // end MonitoredAttributeInfo
