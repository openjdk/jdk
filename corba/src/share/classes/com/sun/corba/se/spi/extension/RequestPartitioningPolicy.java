/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.extension ;

import org.omg.CORBA.Policy ;
import org.omg.CORBA.LocalObject ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

/** Policy used to support the request partitioning feature and to
 *  specify the partition to use.
*/
public class RequestPartitioningPolicy extends LocalObject implements Policy
{
    private static ORBUtilSystemException wrapper =
        ORBUtilSystemException.get( CORBALogDomains.OA_IOR ) ;
    public final static int DEFAULT_VALUE = 0;
    private final int value;

    public RequestPartitioningPolicy( int value )
    {
        if (value < ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID ||
            value > ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID) {
            throw wrapper.invalidRequestPartitioningPolicyValue(
                  new Integer(value),
                  new Integer(
                      ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID),
                  new Integer(
                      ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID));
        }
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    public int policy_type()
    {
        return ORBConstants.REQUEST_PARTITIONING_POLICY;
    }

    public org.omg.CORBA.Policy copy()
    {
        return this;
    }

    public void destroy()
    {
        // NO-OP
    }

    public String toString()
    {
        return "RequestPartitioningPolicy[" + value + "]" ;
    }
}
