/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.beans.beancontext;

import java.util.EventObject;

import java.beans.beancontext.BeanContext;

/**
 * <p>
 * <code>BeanContextEvent</code> is the abstract root event class
 * for all events emitted
 * from, and pertaining to the semantics of, a <code>BeanContext</code>.
 * This class introduces a mechanism to allow the propagation of
 * <code>BeanContextEvent</code> subclasses through a hierarchy of
 * <code>BeanContext</code>s. The <code>setPropagatedFrom()</code>
 * and <code>getPropagatedFrom()</code> methods allow a
 * <code>BeanContext</code> to identify itself as the source
 * of a propagated event.
 * </p>
 *
 * @author      Laurence P. G. Cable
 * @since       1.2
 * @see         java.beans.beancontext.BeanContext
 */

public abstract class BeanContextEvent extends EventObject {
    private static final long serialVersionUID = 7267998073569045052L;

    /**
     * Contruct a BeanContextEvent
     *
     * @param bc        The BeanContext source
     */
    protected BeanContextEvent(BeanContext bc) {
        super(bc);
    }

    /**
     * Gets the <code>BeanContext</code> associated with this event.
     * @return the <code>BeanContext</code> associated with this event.
     */
    public BeanContext getBeanContext() { return (BeanContext)getSource(); }

    /**
     * Sets the <code>BeanContext</code> from which this event was propagated.
     * @param bc the <code>BeanContext</code> from which this event
     * was propagated
     */
    public synchronized void setPropagatedFrom(BeanContext bc) {
        propagatedFrom = bc;
    }

    /**
     * Gets the <code>BeanContext</code> from which this event was propagated.
     * @return the <code>BeanContext</code> from which this
     * event was propagated
     */
    public synchronized BeanContext getPropagatedFrom() {
        return propagatedFrom;
    }

    /**
     * Reports whether or not this event is
     * propagated from some other <code>BeanContext</code>.
     * @return <code>true</code> if propagated, <code>false</code>
     * if not
     */
    public synchronized boolean isPropagated() {
        return propagatedFrom != null;
    }

    /*
     * fields
     */

    /**
     * The <code>BeanContext</code> from which this event was propagated
     */
    protected BeanContext propagatedFrom;
}
