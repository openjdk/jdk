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


/*
  File: SLQ.java
  Originally: LinkedQueue.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
  25aug1998  dl               added peek
  10dec1998  dl               added isEmpty
  10jun1999  bc               modified for isolated use
*/

// Original was in package EDU.oswego.cs.dl.util.concurrent;

/**
 * A linked list based channel implementation,
 * adapted from the TwoLockQueue class from CPJ.
 * The algorithm avoids contention between puts
 * and takes when the queue is not empty.
 * Normally a put and a take can proceed simultaneously.
 * (Although it does not allow multiple concurrent puts or takes.)
 * This class tends to perform more efficently than
 * other Channel implementations in producer/consumer
 * applications.
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 **/

public class LinkedQueue {


  /**
   * Dummy header node of list. The first actual node, if it exists, is always
   * at head_.next. After each take, the old first node becomes the head.
   **/
  protected LinkedNode head_;
  protected int count_;
  /**
   * Helper monitor for managing access to last node, in case it is also first.
   * last_ and waitingForTake_ ONLY used with synch on appendMonitor_
   **/
  protected final Object lastMonitor_ = new Object();

  /**
   * The last node of list. Put() appends to list, so modifies last_
   **/
  protected LinkedNode last_;

  /**
   * The number of threads waiting for a take.
   * Notifications are provided in put only if greater than zero.
   * The bookkeeping is worth it here since in reasonably balanced
   * usages, the notifications will hardly ever be necessary, so
   * the call overhead to notify can be eliminated.
   **/
  protected int waitingForTake_ = 0;

  public LinkedQueue() {
    head_ = new LinkedNode(null);
    last_ = head_;
    count_ = 0;
  }

  /** Main mechanics for put/offer **/
  protected void insert(Object x) {
    synchronized(lastMonitor_) {
      LinkedNode p = new LinkedNode(x);
      last_.next = p;
      last_ = p;
      count_++;
      if (count_ > 1000 && (count_ % 1000 == 0))
        System.out.println("In Queue : " + count_);
      if (waitingForTake_ > 0)
        lastMonitor_.notify();
    }
  }

  /** Main mechanics for take/poll **/
  protected synchronized Object extract() {
    Object x = null;
    LinkedNode first = head_.next;
    if (first != null) {
      x = first.value;
      first.value = null;
      head_ = first;
      count_ --;
    }
    return x;
  }


  public void put(Object x) throws InterruptedException {
    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    insert(x);
  }

  public boolean offer(Object x, long msecs) throws InterruptedException {
    if (x == null) throw new IllegalArgumentException();
    if (Thread.interrupted()) throw new InterruptedException();
    insert(x);
    return true;
  }

  public Object take() throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    // try to extract. If fail, then enter wait-based retry loop
    Object x = extract();
    if (x != null)
      return x;
    else {
      synchronized(lastMonitor_) {
        try {
          ++waitingForTake_;
          for (;;) {
            x = extract();
            if (x != null) {
              --waitingForTake_;
              return x;
            }
            else {
              lastMonitor_.wait();
            }
          }
        }
        catch(InterruptedException ex) {
          --waitingForTake_;
          lastMonitor_.notify();
          throw ex;
        }
      }
    }
  }

  public synchronized Object peek() {
    LinkedNode first = head_.next;
    if (first != null)
      return first.value;
    else
      return null;
  }


  public synchronized boolean isEmpty() {
    return head_.next == null;
  }

  public Object poll(long msecs) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    Object x = extract();
    if (x != null)
      return x;
    else {
      synchronized(lastMonitor_) {
        try {
          long waitTime = msecs;
          long start = (msecs <= 0)? 0 : System.currentTimeMillis();
          ++waitingForTake_;
          for (;;) {
            x = extract();
            if (x != null || waitTime <= 0) {
              --waitingForTake_;
              return x;
            }
            else {
              lastMonitor_.wait(waitTime);
              waitTime = msecs - (System.currentTimeMillis() - start);
            }
          }
        }
        catch(InterruptedException ex) {
          --waitingForTake_;
          lastMonitor_.notify();
          throw ex;
        }
      }
    }
  }

  class LinkedNode {
    Object value;
    LinkedNode next = null;
    LinkedNode(Object x) { value = x; }
    LinkedNode(Object x, LinkedNode n) { value = x; next = n; }
  }
}
