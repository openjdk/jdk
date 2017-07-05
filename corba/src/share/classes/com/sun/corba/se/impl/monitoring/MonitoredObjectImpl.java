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

package com.sun.corba.se.impl.monitoring;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;

import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.MonitoredAttribute;

public class MonitoredObjectImpl implements MonitoredObject {
    private final String name;
    private final String description;

    // List of all child Monitored Objects
    private Map children = new HashMap();

    // All the Attributes of this Monitored Object instance
    private Map monitoredAttributes = new HashMap();

    private MonitoredObject parent = null;


    // Constructor
    MonitoredObjectImpl( String name, String description ) {
        this.name = name;
        this.description = description;
    }

    public MonitoredObject getChild( String name ) {
        synchronized( this ) {
            return (MonitoredObject) children.get( name );
        }
    }

    public Collection getChildren( ) {
        synchronized( this ) {
            return children.values();
        }
    }

    public void addChild( MonitoredObject m ) {
        if (m != null){
            synchronized( this ) {
                children.put( m.getName(), m);
                m.setParent( this );
            }
        }
    }

    public void removeChild( String name ) {
        if (name != null){
            synchronized( this ) {
                children.remove( name );
            }
        }
    }

    public synchronized MonitoredObject getParent( ) {
       return parent;
    }

    public synchronized void setParent( MonitoredObject p ) {
        parent = p;
    }

    public MonitoredAttribute getAttribute( String name ) {
        synchronized( this ) {
            return (MonitoredAttribute) monitoredAttributes.get( name );
        }
    }

    public Collection getAttributes( ) {
        synchronized( this ) {
            return monitoredAttributes.values();
        }
    }

    public void addAttribute( MonitoredAttribute value ) {
        if (value != null) {
            synchronized( this ) {
                monitoredAttributes.put( value.getName(), value );
            }
        }
    }

    public void removeAttribute( String name ) {
        if (name != null) {
            synchronized( this ) {
                monitoredAttributes.remove( name );
            }
        }
    }

    /**
     * calls clearState() on all the registered children MonitoredObjects and
     * MonitoredAttributes.
     */
    public void clearState( ) {
        synchronized( this ) {
            Iterator i = monitoredAttributes.values().iterator();
            // First call clearState on all the local attributes
            while( i.hasNext( ) ) {
                ((MonitoredAttribute)i.next()).clearState();
            }
            i = children.values().iterator();
            // next call clearState on all the children MonitoredObjects
            while( i.hasNext() ) {
                ((MonitoredObject)i.next()).clearState();
           }
        }
    }

    public String getName( ) {
        return name;
    }

    public String getDescription( ) {
        return description;
    }
}
