/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import java.lang.reflect.*;
import java.io.*;
import java.net.*;

/**
 * This class is provided for access to the underlying poll(2)
 * or /dev/poll kernel interfaces.  This may be needed for
 * multiplexing IO when an application cannot afford to have
 * a thread block on each outstanding IO request.
 *
 * It currently supports the same basic functionality as the
 * C poll(2) API, although for efficiency we needed to avoid
 * passing the entire pollfd array for every call.  See man
 * pages for poll(2) for info on C API and event types.
 *
 *
 * @author  Bruce Chapman
 * @see     java.io.FileDescriptor
 * @see     java.net.Socket
 * @see     attached README.txt
 * @since   JDK1.2
 */

public class Poller {
  /**
   * Solaris POLL event types.
   */
  public final static short POLLERR  = 0x08;
  public final static short POLLHUP  = 0x10;
  public final static short POLLNVAL = 0x20;
  public final static short POLLIN   = 1;
  public final static short POLLPRI  = 2;
  public final static short POLLOUT  = 4;
  public final static short POLLRDNORM = 0x40;
  public final static short POLLWRNORM = POLLOUT ;
  public final static short POLLRDBAND = 0x80;
  public final static short POLLWRBAND = 0x100;
  public final static short POLLNORM   = POLLRDNORM;

  /*
   * This global synchronization object must be used for all
   * creation or destruction of Poller objects.
   */
  private final static Object globalSync = new Object();

  /*
   * The handle for a Poller Object...is used in the JNI C code
   * where all the associated data is kept.
   */
  private int handle;

  /**
   * Constructs an instance of a <code>Poller</code> object.
   * Native code uses sysconf(_SC_OPEN_MAX) to determine how
   * many fd/skt objects this Poller object can contain.
   */
  public Poller() throws Exception {
    synchronized(globalSync) {
      this.handle = nativeCreatePoller(-1);
    }
  }

  /**
   * Constructs an instance of a <code>Poller</code> object.
   * @param  maxFd the maximum number of FileDescriptors/Sockets
   *         this Poller object can contain.
   */
  public Poller(int maxFd) throws Exception {
    synchronized(globalSync) {
      this.handle = nativeCreatePoller(maxFd);
    }
  }

  /**
   * Needed to clean up at the JNI C level when object is GCd.
   */
  protected void finalize() throws Throwable {
    synchronized(globalSync) {
      nativeDestroyPoller(handle);
      super.finalize();
    }
  }

  /**
   * Since we can't guarantee WHEN finalize is called, we may
   * recycle on our own.
   * @param  maxFd the maximum number of FileDescriptors/Sockets
   *         this Poller object can contain.
   */
  public void reset(int maxFd) throws Exception {
    synchronized(globalSync) {
      nativeDestroyPoller(handle);
      this.handle = nativeCreatePoller(maxFd);
    }
  }
  /**
   * Since we can't guarantee WHEN finalize is called, we may
   * recycle on our own.
   */
  public void reset() throws Exception {
    synchronized(globalSync) {
      nativeDestroyPoller(handle);
      this.handle = nativeCreatePoller(-1);
    }
  }

  /**
   * Add FileDescriptor to the set handled by this Poller object.
   *
   * @param  fdObj the FileDescriptor, Socket, or ServerSocket to add.
   * @param  event the bitmask of events we are interested in.
   * @return the OS level fd associated with this IO Object
   *          (which is what waitMultiple() stores in fds[])
   */
  public synchronized int add(Object fdObj, short event) throws Exception {
    return nativeAddFd(handle,findfd(fdObj), event);
  }

  /**
   * Remove FileDescriptor from the set handled by this Poller object.
   *
   * Must be called before the fd/skt is closed.
   * @param fdObj the FileDescriptor, Socket, or ServerSocket to remove.
   * @return true if removal succeeded.
   */
  public synchronized boolean remove(Object fdObj) throws Exception {
    return (nativeRemoveFd(handle,findfd(fdObj)) == 1);
  }
  /**
   * Check if fd or socket is already in the set handled by this Poller object
   *
   * @param fdObj the FileDescriptor or [Server]Socket to check.
   * @return true if fd/skt is in the set for this Poller object.
   */
  public synchronized boolean isMember(Object fdObj) throws Exception {
    return (nativeIsMember(handle,findfd(fdObj)) == 1);
  }
  /**
   * Wait on Multiple IO Objects.
   *
   * @param maxRet    the maximum number of fds[] and revents[] to return.
   * @param fds[]     (return) an array of ints in which to store fds with
   *                  available data upon a successful non-timeout return.
   *                  fds.length must be >= maxRet
   * @param revents[] (return) the actual events available on the
   *                  same-indexed fds[] (i.e. fds[0] has events revents[0])
   *                  revents.length must be >= maxRet
   *
   * Note : both above arrays are "dense," i.e. only fds[] with events
   *        available are returned.
   *
   * @param timeout   the maximum number of milliseconds to wait for
   *                  events before timing out.
   * @return          the number of fds with triggered events.
   *
   * Note : convenience methods exist for skipping the timeout parameter
   *        or the maxRet parameter (in the case of no maxRet, fds.length
   *        must equal revents.length)
   *
   * obj.waitMultiple(null,null,timeout) can be used for pausing the LWP
   * (much more reliable and scalable than Thread.sleep() or Object.wait())
   */
  public synchronized int waitMultiple(int maxRet, int[] fds,short[] revents,
                                       long timeout) throws Exception
    {
      if ((revents == null) || (fds == null)) {
        if (maxRet > 0) {
          throw new NullPointerException("fds or revents is null");
        }
      } else if ( (maxRet < 0) ||
                  (maxRet > revents.length) || (maxRet > fds.length) ) {
        throw new IllegalArgumentException("maxRet out of range");
      }

      int ret = nativeWait(handle, maxRet, fds, revents, timeout);
      if (ret < 0) {
        throw new InterruptedIOException();
      }
      return ret;
    }

  /**
   * Wait on Multiple IO Objects (no timeout).
   * A convenience method for waiting indefinitely on IO events
   *
   * @see Poller#waitMultiple
   *
   */
  public int waitMultiple(int maxRet, int[] fds, short[] revents)
    throws Exception
    {
      return waitMultiple(maxRet, fds, revents,-1L); // already synchronized
    }

  /**
   * Wait on Multiple IO Objects (no maxRet).
   * A convenience method for waiting on IO events when the fds
   * and revents arrays are the same length and that specifies the
   * maximum number of return events.
   *
   * @see Poller#waitMultiple
   *
   */
  public synchronized int waitMultiple(int[] fds, short[] revents,
                                       long timeout) throws Exception
    {
      if ((revents == null) && (fds == null)) {
        return nativeWait(handle,0,null,null,timeout);
      } else if ((revents == null) || (fds == null)) {
        throw new NullPointerException("revents or fds is null");
      } else if (fds.length == revents.length) {
        return nativeWait(handle, fds.length, fds, revents, timeout);
      }
      throw new IllegalArgumentException("fds.length != revents.length");
    }


  /**
   * Wait on Multiple IO Objects (no maxRet/timeout).
   * A convenience method for waiting on IO events when the fds
   * and revents arrays are the same length and that specifies the
   * maximum number of return events, and when waiting indefinitely
   * for IO events to occur.
   *
   * @see Poller#waitMultiple
   *
   */
  public int waitMultiple(int[] fds, short[] revents)
    throws Exception
    {
      if ((revents == null) || (fds == null)) {
        throw new NullPointerException("fds or revents is null");
      } else if (fds.length == revents.length) {
        return waitMultiple(revents.length,fds,revents,-1L); // already sync
      }
      throw new IllegalArgumentException("fds.length != revents.length");
    }

  // Utility - get (int) fd from FileDescriptor or [Server]Socket objects.

  private int findfd(Object fdObj) throws Exception {
    Class cl;
    Field f;
    Object val, implVal;

    if ((fdObj instanceof java.net.Socket) ||
        (fdObj instanceof java.net.ServerSocket)) {
      cl = fdObj.getClass();
      f = cl.getDeclaredField("impl");
      f.setAccessible(true);
      val = f.get(fdObj);
      cl = f.getType();
      f = cl.getDeclaredField("fd");
      f.setAccessible(true);
      implVal = f.get(val);
      cl = f.getType();
      f = cl.getDeclaredField("fd");
      f.setAccessible(true);
      return  ((Integer) f.get(implVal)).intValue();
    } else if ( fdObj instanceof java.io.FileDescriptor ) {
      cl = fdObj.getClass();
      f = cl.getDeclaredField("fd");
      f.setAccessible(true);
      return  ((Integer) f.get(fdObj)).intValue();
    }
    else {
      throw new IllegalArgumentException("Illegal Object type.");
    }
  }

  // Actual NATIVE calls

  private static native int  nativeInit();
  private native int  nativeCreatePoller(int maxFd) throws Exception;
  private native void nativeDestroyPoller(int handle) throws Exception;
  private native int  nativeAddFd(int handle, int fd, short events)
    throws Exception;
  private native int  nativeRemoveFd(int handle, int fd) throws Exception;
  private native int  nativeRemoveIndex(int handle, int index)
    throws Exception;
  private native int  nativeIsMember(int handle, int fd) throws Exception;
  private native int  nativeWait(int handle, int maxRet, int[] fds,
                                        short[] events, long timeout)
    throws Exception;
  /**
   * Get number of active CPUs in this machine
   * to determine proper level of concurrency.
   */
  public static native int  getNumCPUs();

  static {
      System.loadLibrary("poller");
      nativeInit();
  }
}
