/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.beans.editors;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * Property editor for java.lang.Enum subclasses.
 *
 * @see PropertyEditor
 *
 * @since 1.7
 *
 * @author Sergey A. Malenkov
 */
public final class EnumEditor implements PropertyEditor {
    private final List<PropertyChangeListener> listeners = new ArrayList<PropertyChangeListener>();

    private final Class type;
    private final String[] tags;

    private Object value;

    public EnumEditor( Class type ) {
        Object[] values = type.getEnumConstants();
        if ( values == null ) {
            throw new IllegalArgumentException( "Unsupported " + type );
        }
        this.type = type;
        this.tags = new String[values.length];
        for ( int i = 0; i < values.length; i++ ) {
            this.tags[i] = ( ( Enum )values[i] ).name();
        }
    }

    public Object getValue() {
        return this.value;
    }

    public void setValue( Object value ) {
        if ( ( value != null ) && !this.type.isInstance( value ) ) {
            throw new IllegalArgumentException( "Unsupported value: " + value );
        }
        Object oldValue;
        PropertyChangeListener[] listeners;
        synchronized ( this.listeners ) {
            oldValue = this.value;
            this.value = value;

            if ( ( value == null ) ? oldValue == null : value.equals( oldValue ) ) {
                return; // do not fire event if value is not changed
            }
            int size = this.listeners.size();
            if ( size == 0 ) {
                return; // do not fire event if there are no any listener
            }
            listeners = this.listeners.toArray( new PropertyChangeListener[size] );
        }
        PropertyChangeEvent event = new PropertyChangeEvent( this, null, oldValue, value );
        for ( PropertyChangeListener listener : listeners ) {
            listener.propertyChange( event );
        }
    }

    public String getAsText() {
        return ( this.value != null )
                ? ( ( Enum )this.value ).name()
                : null;
    }

    public void setAsText( String text ) {
        setValue( ( text != null )
                ? Enum.valueOf( this.type, text )
                : null );
    }

    public String[] getTags() {
        return this.tags.clone();
    }

    public String getJavaInitializationString() {
        String name = getAsText();
        return ( name != null )
                ? this.type.getName() + '.' + name
                : "null";
    }

    public boolean isPaintable() {
        return false;
    }

    public void paintValue( Graphics gfx, Rectangle box ) {
    }

    public boolean supportsCustomEditor() {
        return false;
    }

    public Component getCustomEditor() {
        return null;
    }

    public void addPropertyChangeListener( PropertyChangeListener listener ) {
        synchronized ( this.listeners ) {
            this.listeners.add( listener );
        }
    }

    public void removePropertyChangeListener( PropertyChangeListener listener ) {
        synchronized ( this.listeners ) {
            this.listeners.remove( listener );
        }
    }
}
