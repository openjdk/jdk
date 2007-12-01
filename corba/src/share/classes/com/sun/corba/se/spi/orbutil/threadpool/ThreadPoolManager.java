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

package com.sun.corba.se.spi.orbutil.threadpool;

public interface ThreadPoolManager
{
    /**
    * This method will return an instance of the threadpool given a threadpoolId,
    * that can be used by any component in the app. server.
    *
    * @throws NoSuchThreadPoolException thrown when invalid threadpoolId is passed
    * as a parameter
    */
    public ThreadPool getThreadPool(String threadpoolId) throws NoSuchThreadPoolException;

    /**
    * This method will return an instance of the threadpool given a numeric threadpoolId.
    * This method will be used by the ORB to support the functionality of
    * dedicated threadpool for EJB beans
    *
    * @throws NoSuchThreadPoolException thrown when invalidnumericIdForThreadpool is passed
    * as a parameter
    */
    public ThreadPool getThreadPool(int numericIdForThreadpool) throws NoSuchThreadPoolException;

    /**
    * This method is used to return the numeric id of the threadpool, given a String
    * threadpoolId. This is used by the POA interceptors to add the numeric threadpool
    * Id, as a tagged component in the IOR. This is used to provide the functionality of
    * dedicated threadpool for EJB beans
    */
    public int  getThreadPoolNumericId(String threadpoolId);

    /**
    * Return a String Id for a numericId of a threadpool managed by the threadpool
    * manager
    */
    public String getThreadPoolStringId(int numericIdForThreadpool);

    /**
    * Returns the first instance of ThreadPool in the ThreadPoolManager
    */
    public ThreadPool getDefaultThreadPool();

    /**
     * Return an instance of ThreadPoolChooser based on the componentId that was
     * passed as argument
     */
    public ThreadPoolChooser getThreadPoolChooser(String componentId);

    /**
     * Return an instance of ThreadPoolChooser based on the componentIndex that was
     * passed as argument. This is added for improved performance so that the caller
     * does not have to pay the cost of computing hashcode for the componentId
     */
    public ThreadPoolChooser getThreadPoolChooser(int componentIndex);

    /**
     * Sets a ThreadPoolChooser for a particular componentId in the ThreadPoolManager. This
     * would enable any component to add a ThreadPoolChooser for their specific use
     */
    public void setThreadPoolChooser(String componentId, ThreadPoolChooser aThreadPoolChooser);

    /**
     * Gets the numeric index associated with the componentId specified for a
     * ThreadPoolChooser. This method would help the component call the more
     * efficient implementation i.e. getThreadPoolChooser(int componentIndex)
     */
    public int getThreadPoolChooserNumericId(String componentId);
}

// End of file.
