/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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

/*
  File: ConditionVariable.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
  08dec2001  kmc              Added support for Reentrant Mutexes
*/

package com.sun.corba.se.impl.orbutil.concurrent;

import com.sun.corba.se.impl.orbutil.ORBUtility ;

/**
 * This class is designed for fans of POSIX pthreads programming.
 * If you restrict yourself to Mutexes and CondVars, you can
 * use most of your favorite constructions. Don't randomly mix them
 * with synchronized methods or blocks though.
 * <p>
 * Method names and behavior are as close as is reasonable to
 * those in POSIX.
 * <p>
 * <b>Sample Usage.</b> Here is a full version of a bounded buffer
 * that implements the BoundedChannel interface, written in
 * a style reminscent of that in POSIX programming books.
 * <pre>
 * class CVBuffer implements BoundedChannel {
 *   private final Mutex mutex;
 *   private final CondVar notFull;
 *   private final CondVar notEmpty;
 *   private int count = 0;
 *   private int takePtr = 0;
 *   private int putPtr = 0;
 *   private final Object[] array;
 *
 *   public CVBuffer(int capacity) {
 *     array = new Object[capacity];
 *     mutex = new Mutex();
 *     notFull = new CondVar(mutex);
 *     notEmpty = new CondVar(mutex);
 *   }
 *
 *   public int capacity() { return array.length; }
 *
 *   public void put(Object x) throws InterruptedException {
 *     mutex.acquire();
 *     try {
 *       while (count == array.length) {
 *         notFull.await();
 *       }
 *       array[putPtr] = x;
 *       putPtr = (putPtr + 1) % array.length;
 *       ++count;
 *       notEmpty.signal();
 *     }
 *     finally {
 *       mutex.release();
 *     }
 *   }
 *
 *   public Object take() throws InterruptedException {
 *     Object x = null;
 *     mutex.acquire();
 *     try {
 *       while (count == 0) {
 *         notEmpty.await();
 *       }
 *       x = array[takePtr];
 *       array[takePtr] = null;
 *       takePtr = (takePtr + 1) % array.length;
 *       --count;
 *       notFull.signal();
 *     }
 *     finally {
 *       mutex.release();
 *     }
 *     return x;
 *   }
 *
 *   public boolean offer(Object x, long msecs) throws InterruptedException {
 *     mutex.acquire();
 *     try {
 *       if (count == array.length) {
 *         notFull.timedwait(msecs);
 *         if (count == array.length)
 *           return false;
 *       }
 *       array[putPtr] = x;
 *       putPtr = (putPtr + 1) % array.length;
 *       ++count;
 *       notEmpty.signal();
 *       return true;
 *     }
 *     finally {
 *       mutex.release();
 *     }
 *   }
 *
 *   public Object poll(long msecs) throws InterruptedException {
 *     Object x = null;
 *     mutex.acquire();
 *     try {
 *       if (count == 0) {
 *         notEmpty.timedwait(msecs);
 *         if (count == 0)
 *           return null;
 *       }
 *       x = array[takePtr];
 *       array[takePtr] = null;
 *       takePtr = (takePtr + 1) % array.length;
 *       --count;
 *       notFull.signal();
 *     }
 *     finally {
 *       mutex.release();
 *     }
 *     return x;
 *   }
 * }
 *
 * </pre>
 * @see Mutex
 * [<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 **/

public class CondVar {

    protected boolean debug_ ;

    /** The mutex **/
    protected final Sync mutex_;
    protected final ReentrantMutex remutex_;

    private int releaseMutex()
    {
        int count = 1 ;

        if (remutex_!=null)
            count = remutex_.releaseAll() ;
        else
            mutex_.release() ;

        return count ;
    }

    private void acquireMutex( int count ) throws InterruptedException
    {
        if (remutex_!=null)
            remutex_.acquireAll( count ) ;
        else
            mutex_.acquire() ;
    }

  /**
   * Create a new CondVar that relies on the given mutual
   * exclusion lock.
   * @param mutex A mutual exclusion lock which must either be non-reentrant,
   * or else be ReentrantMutex.
   * Standard usage is to supply an instance of <code>Mutex</code>,
   * but, for example, a Semaphore initialized to 1 also works.
   * On the other hand, many other Sync implementations would not
   * work here, so some care is required to supply a sensible
   * synchronization object.
   * In normal use, the mutex should be one that is used for <em>all</em>
   * synchronization of the object using the CondVar. Generally,
   * to prevent nested monitor lockouts, this
   * object should not use any native Java synchronized blocks.
   **/

  public CondVar(Sync mutex, boolean debug) {
    debug_ = debug ;
    mutex_ = mutex;
    if (mutex instanceof ReentrantMutex)
        remutex_ = (ReentrantMutex)mutex;
    else
        remutex_ = null;
  }

  public CondVar( Sync mutex ) {
      this( mutex, false ) ;
  }

  /**
   * Wait for notification. This operation at least momentarily
   * releases the mutex. The mutex is always held upon return,
   * even if interrupted.
   * @exception InterruptedException if the thread was interrupted
   * before or during the wait. However, if the thread is interrupted
   * after the wait but during mutex re-acquisition, the interruption
   * is ignored, while still ensuring
   * that the currentThread's interruption state stays true, so can
   * be probed by callers.
   **/
    public void await() throws InterruptedException {
        int count = 0 ;
        if (Thread.interrupted())
            throw new InterruptedException();

        try {
            if (debug_)
                ORBUtility.dprintTrace( this, "await enter" ) ;

            synchronized(this) {
                count = releaseMutex() ;
                try {
                    wait();
                } catch (InterruptedException ex) {
                    notify();
                    throw ex;
                }
            }
        } finally {
            // Must ignore interrupt on re-acquire
            boolean interrupted = false;
            for (;;) {
                try {
                    acquireMutex( count );
                    break;
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            if (debug_)
                ORBUtility.dprintTrace( this, "await exit" ) ;
        }
    }

    /**
    * Wait for at most msecs for notification.
    * This operation at least momentarily
    * releases the mutex. The mutex is always held upon return,
    * even if interrupted.
    * @param msecs The time to wait. A value less than or equal to zero
    * causes a momentarily release
    * and re-acquire of the mutex, and always returns false.
    * @return false if at least msecs have elapsed
    * upon resumption; else true. A
    * false return does NOT necessarily imply that the thread was
    * not notified. For example, it might have been notified
    * after the time elapsed but just before resuming.
    * @exception InterruptedException if the thread was interrupted
    * before or during the wait.
    **/

    public boolean timedwait(long msecs) throws  InterruptedException {

        if (Thread.interrupted())
            throw new InterruptedException();

        boolean success = false;
        int count = 0;

        try {
            if (debug_)
                ORBUtility.dprintTrace( this, "timedwait enter" ) ;

            synchronized(this) {
                count = releaseMutex() ;
                try {
                    if (msecs > 0) {
                        long start = System.currentTimeMillis();
                        wait(msecs);
                        success = System.currentTimeMillis() - start <= msecs;
                    }
                } catch (InterruptedException ex) {
                    notify();
                    throw ex;
                }
            }
        } finally {
            // Must ignore interrupt on re-acquire
            boolean interrupted = false;
            for (;;) {
                try {
                    acquireMutex( count ) ;
                    break;
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            if (debug_)
                ORBUtility.dprintTrace( this, "timedwait exit" ) ;
        }
        return success;
    }

    /**
    * Notify a waiting thread.
    * If one exists, a non-interrupted thread will return
    * normally (i.e., not via InterruptedException) from await or timedwait.
    **/
    public synchronized void signal() {
        notify();
    }

    /** Notify all waiting threads **/
    public synchronized void broadcast() {
        notifyAll();
    }
}
