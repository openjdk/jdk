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
 * Monitored AttributeInfo contains the meta information of the Monitored
 * Attribute.
 * </p>
 */
public interface MonitoredAttributeInfo {

  ///////////////////////////////////////
  // operations

/**
 * <p>
 * If the Attribute is writable from ASAdmin then isWritable() will return
 * true.
 * </p>
 * <p>
 *
 * @return a boolean with true or false
 * </p>
 */
    public boolean isWritable();
/**
 * <p>
 * isStatistic() is true if the attribute is presented as a Statistic.
 * </p>
 * <p>
 *
 * @return a boolean with true or false
 * </p>
 */
    public boolean isStatistic();
/**
 * <p>
 * Class Type: We will allow only basic class types: 1)Boolean 2)Integer
 * 3)Byte 4)Long 5)Float 6)Double 7)String 8)Character
 * </p>
 * <p>
 *
 * @return a Class Type
 * </p>
 */
    public Class type();
/**
 * <p>
 * Get's the description for the Monitored Attribute.
 * </p>
 * <p>
 *
 * @return a String with description
 * </p>
 */
    public String getDescription();

} // end MonitoredAttributeInfo
