/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.encoding;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.spi.orb.ORB;

public class BufferManagerWriteGrow extends BufferManagerWrite
{
    BufferManagerWriteGrow( ORB orb )
    {
        super(orb) ;
    }

    public boolean sentFragment() {
        return false;
    }

    /**
     * Returns the correct buffer size for this type of
     * buffer manager as set in the ORB.
     */
    public int getBufferSize() {
        return orb.getORBData().getGIOPBufferSize();
    }

    public void overflow (ByteBufferWithInfo bbwi)
    {
        // The code that once lived directly in CDROutputStream.grow()
        // has been moved ByteBufferWithInfo.growBuffer().

        // Grow ByteBufferWithInfo to a larger size.
        bbwi.growBuffer(orb);

        // Must be false for the grow case
        bbwi.fragmented = false;
    }

    public void sendMessage ()
    {
        Connection conn =
              ((OutputObject)outputObject).getMessageMediator().getConnection();

        conn.writeLock();

        try {

            conn.sendWithoutLock((OutputObject)outputObject);

            sentFullMessage = true;

        } finally {

            conn.writeUnlock();
        }
    }

    /**
     * Close the BufferManagerWrite and do any outstanding cleanup.
     *
     * No work to do for a BufferManagerWriteGrow.
     */
    public void close() {}

}
