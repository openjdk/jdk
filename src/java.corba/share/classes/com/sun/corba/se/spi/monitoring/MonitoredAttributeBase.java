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
 *  A Convenient class provided to help users extend and implement only
 *  getValue(), if there is no need to clear the state and the attribute is not
 *  writable.
 */
public abstract class MonitoredAttributeBase implements MonitoredAttribute {
    String name;
    MonitoredAttributeInfo attributeInfo;
    /**
     * Constructor.
     */
    public MonitoredAttributeBase( String name, MonitoredAttributeInfo info ) {
        this.name = name;
        this.attributeInfo = info;
    }


    /**
     * A Package Private Constructor for internal use only.
     */
    MonitoredAttributeBase( String name ) {
        this.name = name;
    }


    /**
     * A Package Private convenience method for setting MonitoredAttributeInfo
     * for this Monitored Attribute.
     */
    void setMonitoredAttributeInfo( MonitoredAttributeInfo info ) {
        this.attributeInfo = info;
    }

    /**
     *  If the concrete class decides not to provide the implementation of this
     *  method, then it's OK. Some of the  examples where we may decide to not
     *  provide the implementation is the connection state. Irrespective of
     *  the call to clearState, the connection state will be showing the
     *  currect state of the connection.
     *  NOTE: This method is only used to clear the Monitored Attribute state,
     *  not the real state of the system itself.
     */
    public void clearState( ) {
    }

    /**
     *  This method should be implemented by the concrete class.
     */
    public abstract Object getValue( );

    /**
     *  This method should be implemented by the concrete class only if the
     *  attribute is writable. If the attribute is not writable and if this
     *  method called, it will result in an IllegalStateException.
     */
    public void setValue( Object value ) {
        if( !attributeInfo.isWritable() ) {
            throw new IllegalStateException(
                "The Attribute " + name + " is not Writable..." );
        }
        throw new IllegalStateException(
            "The method implementation is not provided for the attribute " +
            name );
    }


    /**
     *  Gets the MonitoredAttributeInfo for the attribute.
     */
    public MonitoredAttributeInfo getAttributeInfo( ) {
        return attributeInfo;
    }

    /**
     * Gets the name of the attribute.
     */
    public String getName( ) {
        return name;
    }
} // end MonitoredAttributeBase
