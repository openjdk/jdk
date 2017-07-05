/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

/**
 */
package com.sun.corba.se.impl.ior.iiop;

import org.omg.CORBA_2_3.portable.OutputStream;

import com.sun.corba.se.spi.ior.TaggedComponentBase;
import com.sun.corba.se.spi.ior.iiop.RequestPartitioningComponent;
import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.orbutil.ORBConstants;


public class RequestPartitioningComponentImpl extends TaggedComponentBase
    implements RequestPartitioningComponent
{
    private static ORBUtilSystemException wrapper =
        ORBUtilSystemException.get( CORBALogDomains.OA_IOR ) ;

    private int partitionToUse;

    public boolean equals(Object obj)
    {
        if (!(obj instanceof RequestPartitioningComponentImpl))
            return false ;

        RequestPartitioningComponentImpl other =
            (RequestPartitioningComponentImpl)obj ;

        return partitionToUse == other.partitionToUse ;
    }

    public int hashCode()
    {
        return partitionToUse;
    }

    public String toString()
    {
        return "RequestPartitioningComponentImpl[partitionToUse=" + partitionToUse + "]" ;
    }

    public RequestPartitioningComponentImpl()
    {
        partitionToUse = 0;
    }

    public RequestPartitioningComponentImpl(int thePartitionToUse) {
        if (thePartitionToUse < ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID ||
            thePartitionToUse > ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID) {
            throw wrapper.invalidRequestPartitioningComponentValue(
                  new Integer(thePartitionToUse),
                  new Integer(ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID),
                  new Integer(ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID));
        }
        partitionToUse = thePartitionToUse;
    }

    public int getRequestPartitioningId()
    {
        return partitionToUse;
    }

    public void writeContents(OutputStream os)
    {
        os.write_ulong(partitionToUse);
    }

    public int getId()
    {
        return ORBConstants.TAG_REQUEST_PARTITIONING_ID;
    }
}
