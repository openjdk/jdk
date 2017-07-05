/*
 * Copyright 1997-2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.beans;

import java.applet.Applet;

import java.beans.beancontext.BeanContext;

/**
 * <p>
 * This interface is designed to work in collusion with java.beans.Beans.instantiate.
 * The interafce is intended to provide mechanism to allow the proper
 * initialization of JavaBeans that are also Applets, during their
 * instantiation by java.beans.Beans.instantiate().
 * </p>
 *
 * @see java.beans.Beans#instantiate
 *
 * @since 1.2
 *
 */


public interface AppletInitializer {

    /**
     * <p>
     * If passed to the appropriate variant of java.beans.Beans.instantiate
     * this method will be called in order to associate the newly instantiated
     * Applet (JavaBean) with its AppletContext, AppletStub, and Container.
     * </p>
     * <p>
     * Conformant implementations shall:
     * <ol>
     * <li> Associate the newly instantiated Applet with the appropriate
     * AppletContext.
     *
     * <li> Instantiate an AppletStub() and associate that AppletStub with
     * the Applet via an invocation of setStub().
     *
     * <li> If BeanContext parameter is null, then it shall associate the
     * Applet with its appropriate Container by adding that Applet to its
     * Container via an invocation of add(). If the BeanContext parameter is
     * non-null, then it is the responsibility of the BeanContext to associate
     * the Applet with its Container during the subsequent invocation of its
     * addChildren() method.
     * </ol>
     * </p>
     *
     * @param newAppletBean  The newly instantiated JavaBean
     * @param bCtxt          The BeanContext intended for this Applet, or
     *                       null.
     */

    void initialize(Applet newAppletBean, BeanContext bCtxt);

    /**
     * <p>
     * Activate, and/or mark Applet active. Implementors of this interface
     * shall mark this Applet as active, and optionally invoke its start()
     * method.
     * </p>
     *
     * @param newApplet  The newly instantiated JavaBean
     */

    void activate(Applet newApplet);
}
