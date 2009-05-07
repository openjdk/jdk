/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.nio.ByteBuffer;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.RequestCanceledException;
import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import java.util.*;

public class BufferManagerReadStream
    implements BufferManagerRead, MarkAndResetHandler
{
    private boolean receivedCancel = false;
    private int cancelReqId = 0;

    // We should convert endOfStream to a final static dummy end node
    private boolean endOfStream = true;
    private BufferQueue fragmentQueue = new BufferQueue();
    private long FRAGMENT_TIMEOUT = 60000;

    // REVISIT - This should go in BufferManagerRead. But, since
    //           BufferManagerRead is an interface. BufferManagerRead
    //           might ought to be an abstract class instead of an
    //           interface.
    private ORB orb ;
    private ORBUtilSystemException wrapper ;
    private boolean debug = false;

    BufferManagerReadStream( ORB orb )
    {
        this.orb = orb ;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_ENCODING ) ;
        debug = orb.transportDebugFlag;
    }

    public void cancelProcessing(int requestId) {
        synchronized(fragmentQueue) {
            receivedCancel = true;
            cancelReqId = requestId;
            fragmentQueue.notify();
        }
    }

    public void processFragment(ByteBuffer byteBuffer, FragmentMessage msg)
    {
        ByteBufferWithInfo bbwi =
            new ByteBufferWithInfo(orb, byteBuffer, msg.getHeaderLength());

        synchronized (fragmentQueue) {
            if (debug)
            {
                // print address of ByteBuffer being queued
                int bbAddress = System.identityHashCode(byteBuffer);
                StringBuffer sb = new StringBuffer(80);
                sb.append("processFragment() - queueing ByteBuffer id (");
                sb.append(bbAddress).append(") to fragment queue.");
                String strMsg = sb.toString();
                dprint(strMsg);
            }
            fragmentQueue.enqueue(bbwi);
            endOfStream = !msg.moreFragmentsToFollow();
            fragmentQueue.notify();
        }
    }

    public ByteBufferWithInfo underflow (ByteBufferWithInfo bbwi)
    {

      ByteBufferWithInfo result = null;

      try {
          //System.out.println("ENTER underflow");

        synchronized (fragmentQueue) {

            if (receivedCancel) {
                throw new RequestCanceledException(cancelReqId);
            }

            while (fragmentQueue.size() == 0) {

                if (endOfStream) {
                    throw wrapper.endOfStream() ;
                }

                boolean interrupted = false;
                try {
                    fragmentQueue.wait(FRAGMENT_TIMEOUT);
                } catch (InterruptedException e) {
                    interrupted = true;
                }

                if (!interrupted && fragmentQueue.size() == 0) {
                    throw wrapper.bufferReadManagerTimeout();
                }

                if (receivedCancel) {
                    throw new RequestCanceledException(cancelReqId);
                }
            }

            result = fragmentQueue.dequeue();
            result.fragmented = true;

            if (debug)
            {
                // print address of ByteBuffer being dequeued
                int bbAddr = System.identityHashCode(result.byteBuffer);
                StringBuffer sb1 = new StringBuffer(80);
                sb1.append("underflow() - dequeued ByteBuffer id (");
                sb1.append(bbAddr).append(") from fragment queue.");
                String msg1 = sb1.toString();
                dprint(msg1);
            }

            // VERY IMPORTANT
            // Release bbwi.byteBuffer to the ByteBufferPool only if
            // this BufferManagerStream is not marked for potential restore.
            if (markEngaged == false && bbwi != null && bbwi.byteBuffer != null)
            {
                ByteBufferPool byteBufferPool = getByteBufferPool();

                if (debug)
                {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(bbwi.byteBuffer);
                    StringBuffer sb = new StringBuffer(80);
                    sb.append("underflow() - releasing ByteBuffer id (");
                    sb.append(bbAddress).append(") to ByteBufferPool.");
                    String msg = sb.toString();
                    dprint(msg);
                }

                byteBufferPool.releaseByteBuffer(bbwi.byteBuffer);
                bbwi.byteBuffer = null;
                bbwi = null;
            }
        }
        return result;
      } finally {
          //System.out.println("EXIT underflow");
      }
    }

    public void init(Message msg) {
        if (msg != null)
            endOfStream = !msg.moreFragmentsToFollow();
    }

    // Release any queued ByteBufferWithInfo's byteBuffers to the
    // ByteBufferPoool
    public void close(ByteBufferWithInfo bbwi)
    {
        int inputBbAddress = 0;

        // release ByteBuffers on fragmentQueue
        if (fragmentQueue != null)
        {
            synchronized (fragmentQueue)
            {
                // IMPORTANT: The fragment queue may have one ByteBuffer
                //            on it that's also on the CDRInputStream if
                //            this method is called when the stream is 'marked'.
                //            Thus, we'll compare the ByteBuffer passed
                //            in (from a CDRInputStream) with all ByteBuffers
                //            on the stack. If one is found to equal, it will
                //            not be released to the ByteBufferPool.
                if (bbwi != null)
                {
                    inputBbAddress = System.identityHashCode(bbwi.byteBuffer);
                }

                ByteBufferWithInfo abbwi = null;
                ByteBufferPool byteBufferPool = getByteBufferPool();
                while (fragmentQueue.size() != 0)
                {
                    abbwi = fragmentQueue.dequeue();
                    if (abbwi != null && abbwi.byteBuffer != null)
                    {
                        int bbAddress = System.identityHashCode(abbwi.byteBuffer);
                        if (inputBbAddress != bbAddress)
                        {
                            if (debug)
                            {
                                 // print address of ByteBuffer released
                                 StringBuffer sb = new StringBuffer(80);
                                 sb.append("close() - fragmentQueue is ")
                                   .append("releasing ByteBuffer id (")
                                   .append(bbAddress).append(") to ")
                                   .append("ByteBufferPool.");
                                 String msg = sb.toString();
                                 dprint(msg);
                            }
                        }
                        byteBufferPool.releaseByteBuffer(abbwi.byteBuffer);
                    }
                }
            }
            fragmentQueue = null;
        }

        // release ByteBuffers on fragmentStack
        if (fragmentStack != null && fragmentStack.size() != 0)
        {
            // IMPORTANT: The fragment stack may have one ByteBuffer
            //            on it that's also on the CDRInputStream if
            //            this method is called when the stream is 'marked'.
            //            Thus, we'll compare the ByteBuffer passed
            //            in (from a CDRInputStream) with all ByteBuffers
            //            on the stack. If one is found to equal, it will
            //            not be released to the ByteBufferPool.
            if (bbwi != null)
            {
                inputBbAddress = System.identityHashCode(bbwi.byteBuffer);
            }

            ByteBufferWithInfo abbwi = null;
            ByteBufferPool byteBufferPool = getByteBufferPool();
            ListIterator itr = fragmentStack.listIterator();
            while (itr.hasNext())
            {
                abbwi = (ByteBufferWithInfo)itr.next();

                if (abbwi != null && abbwi.byteBuffer != null)
                {
                   int bbAddress = System.identityHashCode(abbwi.byteBuffer);
                   if (inputBbAddress != bbAddress)
                   {
                       if (debug)
                       {
                            // print address of ByteBuffer being released
                            StringBuffer sb = new StringBuffer(80);
                            sb.append("close() - fragmentStack - releasing ")
                              .append("ByteBuffer id (" + bbAddress + ") to ")
                              .append("ByteBufferPool.");
                            String msg = sb.toString();
                            dprint(msg);
                       }
                       byteBufferPool.releaseByteBuffer(abbwi.byteBuffer);
                   }
                }
            }
            fragmentStack = null;
        }

    }

    protected ByteBufferPool getByteBufferPool()
    {
        return orb.getByteBufferPool();
    }

    private void dprint(String msg)
    {
        ORBUtility.dprint("BufferManagerReadStream", msg);
    }

    // Mark and reset handler ----------------------------------------

    private boolean markEngaged = false;

    // List of fragment ByteBufferWithInfos received since
    // the mark was engaged.
    private LinkedList fragmentStack = null;
    private RestorableInputStream inputStream = null;

    // Original state of the stream
    private Object streamMemento = null;

    public void mark(RestorableInputStream inputStream)
    {
        this.inputStream = inputStream;
        markEngaged = true;

        // Get the magic Object that the stream will use to
        // reconstruct it's state when reset is called
        streamMemento = inputStream.createStreamMemento();

        if (fragmentStack != null) {
            fragmentStack.clear();
        }
    }

    // Collects fragments received since the mark was engaged.
    public void fragmentationOccured(ByteBufferWithInfo newFragment)
    {
        if (!markEngaged)
            return;

        if (fragmentStack == null)
            fragmentStack = new LinkedList();

        fragmentStack.addFirst(new ByteBufferWithInfo(newFragment));
    }

    public void reset()
    {
        if (!markEngaged) {
            // REVISIT - call to reset without call to mark
            return;
        }

        markEngaged = false;

        // If we actually did peek across fragments, we need
        // to push those fragments onto the front of the
        // buffer queue.
        if (fragmentStack != null && fragmentStack.size() != 0) {
            ListIterator iter = fragmentStack.listIterator();

            synchronized(fragmentQueue) {
                while (iter.hasNext()) {
                    fragmentQueue.push((ByteBufferWithInfo)iter.next());
                }
            }

            fragmentStack.clear();
        }

        // Give the stream the magic Object to restore
        // it's state.
        inputStream.restoreInternalState(streamMemento);
    }

    public MarkAndResetHandler getMarkAndResetHandler() {
        return this;
    }
}
