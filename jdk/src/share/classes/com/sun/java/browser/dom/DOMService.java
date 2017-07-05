/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.browser.dom;

import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class DOMService
{
    /**
     * Returns new instance of a DOMService. The implementation
     * of the DOMService returns depends on the setting of the
     * com.sun.java.browser.dom.DOMServiceProvider property or,
     * if the property is not set, a platform specific default.
     *
     * Throws DOMUnsupportedException if the DOMService is not
     * available to the obj.
     *
     * @param obj Object to leverage the DOMService
     */
    public static DOMService getService(Object obj)
                  throws DOMUnsupportedException
    {
        try
        {
            String provider = AccessController.doPrivileged(
                (PrivilegedAction<String>) () ->
                    System.getProperty("com.sun.java.browser.dom.DOMServiceProvider"));

            Class clazz = Class.forName("sun.plugin.dom.DOMService");

            return (DOMService) clazz.newInstance();
        }
        catch (Throwable e)
        {
            throw new DOMUnsupportedException(e.toString());
        }
    }

    /**
     * An empty constructor is provided. Implementations of this
     * abstract class must provide a public no-argument constructor
     * in order for the static getService() method to work correctly.
     * Application programmers should not be able to directly
     * construct implementation subclasses of this abstract subclass.
     */
    public DOMService()
    {
    }

    /**
     * Causes action.run() to be executed synchronously on the
     * DOM action dispatching thread. This call will block until all
     * pending DOM actions have been processed and (then)
     * action.run() returns. This method should be used when an
     * application thread needs to access the browser's DOM.
     * It should not be called from the DOMActionDispatchThread.
     *
     * Note that if the DOMAction.run() method throws an uncaught
     * exception (on the DOM action dispatching thread),  it's caught
     * and re-thrown, as an DOMAccessException, on the caller's thread.
     *
     * If the DOMAction.run() method throws any DOM security related
     * exception (on the DOM action dispatching thread), it's caught
     * and re-thrown, as an DOMSecurityException, on the caller's thread.
     *
     * @param action DOMAction.
     */
    public abstract Object invokeAndWait(DOMAction action) throws DOMAccessException;

    /**
     * Causes action.run() to be executed asynchronously on the
     * DOM action dispatching thread. This method should be used
     * when an application thread needs to access the browser's
     * DOM. It should not be called from the DOMActionDispatchThread.
     *
     * Note that if the DOMAction.run() method throws an uncaught
     * exception (on the DOM action dispatching thread),  it will not be
     * caught and re-thrown on the caller's thread.
     *
     * @param action DOMAction.
     */
    public abstract void invokeLater(DOMAction action);
}
