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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.LinkedList;

import com.sun.corba.se.impl.encoding.BufferQueue;
import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.pept.encoding.OutputObject;

/**
 * Collect buffer manager.
 */
public class BufferManagerWriteCollect extends BufferManagerWrite
{
    private BufferQueue queue = new BufferQueue();

    private boolean sentFragment = false;
    private boolean debug = false;


    BufferManagerWriteCollect(ORB orb)
    {
        super(orb);
         if (orb != null)
            debug = orb.transportDebugFlag;
    }

    public boolean sentFragment() {
        return sentFragment;
    }

    /**
     * Returns the correct buffer size for this type of
     * buffer manager as set in the ORB.
     */
    public int getBufferSize() {
        return orb.getORBData().getGIOPFragmentSize();
    }

    // Set the fragment's "more fragments" bit to true, put it in the
    // queue, and allocate a new bbwi.
    public void overflow (ByteBufferWithInfo bbwi)
    {
        // Set the fragment's moreFragments field to true
        MessageBase.setFlag(bbwi.byteBuffer, Message.MORE_FRAGMENTS_BIT);

        // Enqueue the previous fragment
        queue.enqueue(bbwi);

        // Create a new bbwi
        ByteBufferWithInfo newBbwi = new ByteBufferWithInfo(orb, this);
        newBbwi.fragmented = true;

        // XREVISIT - Downcast
        ((CDROutputObject)outputObject).setByteBufferWithInfo(newBbwi);

        // Now we must marshal in the fragment header/GIOP header

        // REVISIT - we can optimize this by not creating the fragment message
        // each time.

        // XREVISIT - Downcast
        FragmentMessage header =
              ((CDROutputObject)outputObject).getMessageHeader()
                                             .createFragmentMessage();

        header.write((CDROutputObject)outputObject);
    }

    // Send all fragments
    public void sendMessage ()
    {
        // Enqueue the last fragment
        queue.enqueue(((CDROutputObject)outputObject).getByteBufferWithInfo());

        Iterator bufs = iterator();

        Connection conn =
                          ((OutputObject)outputObject).getMessageMediator().
                                                       getConnection();

        // With the collect strategy, we must lock the connection
        // while fragments are being sent.  This is so that there are
        // no interleved fragments in GIOP 1.1.
        //
        // Note that this thread must not call writeLock again in any
        // of its send methods!
        conn.writeLock();

        try {

            // Get a reference to ByteBufferPool so that the ByteBufferWithInfo
            // ByteBuffer can be released to the ByteBufferPool
            ByteBufferPool byteBufferPool = orb.getByteBufferPool();

            while (bufs.hasNext()) {

                ByteBufferWithInfo bbwi = (ByteBufferWithInfo)bufs.next();
                ((CDROutputObject)outputObject).setByteBufferWithInfo(bbwi);

                conn.sendWithoutLock(((CDROutputObject)outputObject));

                sentFragment = true;

                // Release ByteBufferWithInfo's ByteBuffer back to the pool
                // of ByteBuffers.
                if (debug)
                {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(bbwi.byteBuffer);
                    StringBuffer sb = new StringBuffer(80);
                    sb.append("sendMessage() - releasing ByteBuffer id (");
                    sb.append(bbAddress).append(") to ByteBufferPool.");
                    String msg = sb.toString();
                    dprint(msg);
                }
                byteBufferPool.releaseByteBuffer(bbwi.byteBuffer);
                bbwi.byteBuffer = null;
                bbwi = null;
            }

            sentFullMessage = true;

        } finally {

            conn.writeUnlock();
        }
    }

    /**
     * Close the BufferManagerWrite - do any outstanding cleanup.
     *
     * For a BufferManagerWriteGrow any queued ByteBufferWithInfo must
     * have its ByteBuffer released to the ByteBufferPool.
     */
    public void close()
    {
        // iterate thru queue and release any ByteBufferWithInfo's
        // ByteBuffer that may be remaining on the queue to the
        // ByteBufferPool.

        Iterator bufs = iterator();

        ByteBufferPool byteBufferPool = orb.getByteBufferPool();

        while (bufs.hasNext())
        {
            ByteBufferWithInfo bbwi = (ByteBufferWithInfo)bufs.next();
            if (bbwi != null && bbwi.byteBuffer != null)
            {
                if (debug)
                {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(bbwi.byteBuffer);
                    StringBuffer sb = new StringBuffer(80);
                    sb.append("close() - releasing ByteBuffer id (");
                    sb.append(bbAddress).append(") to ByteBufferPool.");
                    String msg = sb.toString();
                    dprint(msg);
                }
                 byteBufferPool.releaseByteBuffer(bbwi.byteBuffer);
                 bbwi.byteBuffer = null;
                 bbwi = null;
            }
        }
    }

    private void dprint(String msg)
    {
        ORBUtility.dprint("BufferManagerWriteCollect", msg);
    }

    private Iterator iterator ()
    {
        return new BufferManagerWriteCollectIterator();
    }

    private class BufferManagerWriteCollectIterator implements Iterator
    {
        public boolean hasNext ()
        {
            return queue.size() != 0;
        }

        public Object next ()
        {
            return queue.dequeue();
        }

        public void remove ()
        {
            throw new UnsupportedOperationException();
        }
    }
}
