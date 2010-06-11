/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.pept.transport.ByteBufferPool;

/**
 * @author Charlie Hunt
 */

public class ByteBufferPoolImpl implements ByteBufferPool
{
    private ORB itsOrb;
    private int itsByteBufferSize;
    private ArrayList itsPool;
    private int itsObjectCounter = 0;
    private boolean debug;

    // Construct a ByteBufferPool for a pool of NIO ByteBuffers
    // of ORB fragment size.
    public ByteBufferPoolImpl(ORB theORB)
    {
        itsByteBufferSize = theORB.getORBData().getGIOPFragmentSize();
        itsPool = new ArrayList();
        itsOrb = theORB;
        debug = theORB.transportDebugFlag;
    }

    /*
     * Locations where ByteBuffers are gotten from the pool:
     * 1. ContactInfoBase.createMessageMediator()
     * 2. ByteBufferWithInfo.growBuffer()
     * 3. ByteBufferWithInfo(ORB, BufferManagerWrite) - constructor
    */

    // If the requested ByteBuffer size is less than or equal to
    // the ORB fragment size, and we have not disabled use of
    // direct byte buffers (normally for debugging purposes)
    // then get a DirectByteBuffer from the
    // pool if there is one, if there is not one in the pool,
    // then allocate a a DirectByteBuffer of ORB fragment size.
    //
    // If the request ByteBuffer size is greater than the ORB fragment
    // size, allocate a new non-direct ByteBuffer.
    public ByteBuffer getByteBuffer(int theAskSize)
    {
        ByteBuffer abb = null;

        if ((theAskSize <= itsByteBufferSize) &&
            !itsOrb.getORBData().disableDirectByteBufferUse())
        {
            // check if there's one in the pool, if not allocate one.
            int poolSize;
            synchronized (itsPool)
            {
                poolSize = itsPool.size();
                if (poolSize > 0)
                {
                    abb = (ByteBuffer)itsPool.remove(poolSize - 1);

                    // clear ByteBuffer before returning it
                    abb.clear();
                }
            }

            // NOTE: Moved the 'else' part of the above if statement
            //       outside the synchronized block since it is likely
            //       less expensive to check poolSize than to allocate a
            //       DirectByteBuffer in the synchronized block.
            if (poolSize <= 0)
            {
                abb = ByteBuffer.allocateDirect(itsByteBufferSize);
            }

            // increment the number of ByteBuffers gotten from pool
            // IMPORTANT: Since this counter is used only for information
            //            purposes, it does not use synchronized access.
            itsObjectCounter++;
        }
        else
        {
            // Requested ByteBuffer size larger than the pool manages.
            // Just allocate a non-direct ByteBuffer
            abb = ByteBuffer.allocate(theAskSize);
        }

        return abb;
    }


    /*
     * Locations where ByteBuffers are released to the pool:
     * 1. ByteBufferWithInfo.growBuffer()
     * 2. BufferManagerWriteCollect.sendMessage()
     * 3. CDROutputStream_1_0.close()
     * 4. CDRInputStream_1_0.close()
     * 5. BufferManagerReadStream.underflow()
     * 6. BufferManagerWrite.close()
     * 7. BufferManagerRead.close()
     * 8. CorbaMessageMediatorImpl.releaseByteBufferToPool()
    */

    // If the ByteBuffer is a DirectByteBuffer, add it to the pool.
    // Otherwise, set its reference to null since it's not kept in
    // the pool and caller is saying he/she is done with it.
    // NOTE: The size of the ByteBuffer is not checked with the
    //       this pool's ByteBuffer size since only DirectByteBuffers
    //       ever allocated. Hence, only DirectByteBuffer are checked
    //       here. An additional check could be added here for that though.
    public void releaseByteBuffer(ByteBuffer thebb)
    {
        if (thebb.isDirect())
        {
            synchronized (itsPool)
            {
                // use with debug to determine if byteBuffer is already
                // in the pool.
                boolean refInPool = false;
                int bbAddr = 0;

                if (debug)
                {
                    // Check to make sure we don't have 'thebb' reference
                    // already in the pool before adding it.

                    for (int i = 0; i < itsPool.size() && refInPool == false; i++)
                    {
                         ByteBuffer tmpbb = (ByteBuffer)itsPool.get(i);
                         if (thebb == tmpbb)
                         {
                             refInPool = true;
                             bbAddr = System.identityHashCode(thebb);
                         }
                    }

                }

                // NOTE: The else part of this if will only get called
                //       if debug = true and refInPool = true, see logic above.
                if (refInPool == false || debug == false)
                {
                    // add ByteBuffer back to the pool
                    itsPool.add(thebb);
                }
                else // otherwise, log a stack trace with duplicate message
                {
                    String threadName = Thread.currentThread().getName();
                    Throwable t =
                            new Throwable(threadName +
                                         ": Duplicate ByteBuffer reference (" +
                                         bbAddr + ")");
                    t.printStackTrace(System.out);
                }
            }

            // decrement the count of ByteBuffers released
            // IMPORTANT: Since this counter is used only for information
            //            purposes, it does not use synchronized access.
            itsObjectCounter--;
        }
        else
        {
            // ByteBuffer not pooled nor needed
            thebb = null;
        }
    }


    // Get a count of the outstanding allocated DirectByteBuffers.
    // (Those allocated and have not been returned to the pool).
    // IMPORTANT: Since this counter is used only for information
    //            purposes, it does not use synchronized access.
    public int activeCount()
    {
         return itsObjectCounter;
    }
}

// End of file.
