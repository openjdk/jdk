/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.encoding;

import java.nio.ByteBuffer;


import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.spi.orb.ORB;


// Notes about the class.
// Assumptions, the ByteBuffer's position is set by the constructor's
// index variable and the ByteBuffer's limit points to the end of the
// data. Also, since the index variable tracks the current empty
// position in the buffer, the ByteBuffer's position is updated
// any time there's a call to this class's position().
// Although, a ByteBuffer's length is it's capacity(), the context in
// which length is used in this object, this.buflen is actually the
// ByteBuffer limit().

public class ByteBufferWithInfo
{
    private ORB orb;
    private boolean debug;
    // REVISIT - index should eventually be replaced with byteBuffer.position()
    private int     index;     // Current empty position in buffer.
    // REVISIT - CHANGE THESE TO PRIVATE
    public ByteBuffer byteBuffer;// Marshal buffer.
    public int     buflen;     // Total length of buffer. // Unnecessary...
    public int     needed;     // How many more bytes are needed on overflow.
    public boolean fragmented; // Did the overflow operation fragment?

    public ByteBufferWithInfo(org.omg.CORBA.ORB orb,
                              ByteBuffer byteBuffer,
                              int index)
    {
        this.orb = (com.sun.corba.se.spi.orb.ORB)orb;
        debug = this.orb.transportDebugFlag;
        this.byteBuffer = byteBuffer;
        if (byteBuffer != null)
        {
            this.buflen = byteBuffer.limit();
        }
        position(index);
        this.needed = 0;
        this.fragmented = false;
    }

    public ByteBufferWithInfo(org.omg.CORBA.ORB orb, ByteBuffer byteBuffer)
    {
        this(orb, byteBuffer, 0);
    }

    public ByteBufferWithInfo(org.omg.CORBA.ORB orb,
                              BufferManagerWrite bufferManager)
    {
        this(orb, bufferManager, true);
    }

    // Right now, EncapsOutputStream's do not use pooled byte buffers.
    // EncapsOutputStream's is the only one that does not use pooled
    // byte buffers. Hence, the reason for the boolean 'usePooledByteBuffers'.
    // See EncapsOutputStream for additional information.

    public ByteBufferWithInfo(org.omg.CORBA.ORB orb,
                              BufferManagerWrite bufferManager,
                              boolean usePooledByteBuffers)
    {
        this.orb = (com.sun.corba.se.spi.orb.ORB)orb;
        debug = this.orb.transportDebugFlag;

        int bufferSize = bufferManager.getBufferSize();

        if (usePooledByteBuffers)
        {
            ByteBufferPool byteBufferPool = this.orb.getByteBufferPool();
            this.byteBuffer = byteBufferPool.getByteBuffer(bufferSize);

            if (debug)
            {
                // print address of ByteBuffer gotten from pool
                int bbAddress = System.identityHashCode(byteBuffer);
                StringBuffer sb = new StringBuffer(80);
                sb.append("constructor (ORB, BufferManagerWrite) - got ")
                  .append("ByteBuffer id (").append(bbAddress)
                  .append(") from ByteBufferPool.");
                String msgStr = sb.toString();
                dprint(msgStr);
            }
        }
        else
        {
             // don't allocate from pool, allocate non-direct ByteBuffer
             this.byteBuffer = ByteBuffer.allocate(bufferSize);
        }

        position(0);
        this.buflen = bufferSize;
        this.byteBuffer.limit(this.buflen);
        this.needed = 0;
        this.fragmented = false;
    }

    // Shallow copy constructor
    public ByteBufferWithInfo (ByteBufferWithInfo bbwi)
    {
        this.orb = bbwi.orb;
        this.debug = bbwi.debug;
        this.byteBuffer = bbwi.byteBuffer;
        this.buflen = bbwi.buflen;
        this.byteBuffer.limit(this.buflen);
        position(bbwi.position());
        this.needed = bbwi.needed;
        this.fragmented = bbwi.fragmented;
    }

    // So IIOPOutputStream seems more intuitive
    public int getSize()
    {
        return position();
    }

    // accessor to buflen
    public int getLength()
    {
         return buflen;
    }

    // get position in this buffer
    public int position()
    {
        // REVISIT - This should be changed to return the
        //           value of byteBuffer.position() rather
        //           than this.index. But, byteBuffer.position
        //           is manipulated via ByteBuffer writes, reads,
        //           gets and puts. These locations need to be
        //           investigated and updated before
        //           byteBuffer.position() can be returned here.
        // return byteBuffer.position();
        return index;
    }

    // set position in this buffer
    public void position(int newPosition)
    {
        // REVISIT - This should be changed to set only the
        //           value of byteBuffer.position rather
        //           than this.index. This change should be made
        //           in conjunction with the change to this.position().
        byteBuffer.position(newPosition);
        index = newPosition;
    }

    // mutator to buflen
    public void setLength(int theLength)
    {
        buflen = theLength;
        byteBuffer.limit(buflen);
    }

    // Grow byteBuffer to a size larger than position() + needed
    public void growBuffer(com.sun.corba.se.spi.orb.ORB orb)
    {
        // This code used to live directly in CDROutputStream.grow.

        // Recall that the byteBuffer size is 'really' the limit or
        // buflen.

        int newLength = byteBuffer.limit() * 2;

        while (position() + needed >= newLength)
            newLength = newLength * 2;

        ByteBufferPool byteBufferPool = orb.getByteBufferPool();
        ByteBuffer newBB = byteBufferPool.getByteBuffer(newLength);

        if (debug)
        {
            // print address of ByteBuffer just gotten
            int newbbAddress = System.identityHashCode(newBB);
            StringBuffer sb = new StringBuffer(80);
            sb.append("growBuffer() - got ByteBuffer id (");
            sb.append(newbbAddress).append(") from ByteBufferPool.");
            String msgStr = sb.toString();
            dprint(msgStr);
        }

        byteBuffer.position(0);
        newBB.put(byteBuffer);

        // return 'old' byteBuffer reference to the ByteBuffer pool
        if (debug)
        {
            // print address of ByteBuffer being released
            int bbAddress = System.identityHashCode(byteBuffer);
            StringBuffer sb = new StringBuffer(80);
            sb.append("growBuffer() - releasing ByteBuffer id (");
            sb.append(bbAddress).append(") to ByteBufferPool.");
            String msgStr2 = sb.toString();
            dprint(msgStr2);
        }
        byteBufferPool.releaseByteBuffer(byteBuffer);

        // update the byteBuffer with a larger ByteBuffer
        byteBuffer = newBB;

        // limit and buflen must be set to newLength.
        buflen = newLength;
        byteBuffer.limit(buflen);
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer("ByteBufferWithInfo:");

        str.append(" buflen = " + buflen);
        str.append(" byteBuffer.limit = " + byteBuffer.limit());
        str.append(" index = " + index);
        str.append(" position = " + position());
        str.append(" needed = " + needed);
        str.append(" byteBuffer = " + (byteBuffer == null ? "null" : "not null"));
        str.append(" fragmented = " + fragmented);

        return str.toString();
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("ByteBufferWithInfo", msg);
    }
}
