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
  File: Sync.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
   5Aug1998  dl               Added some convenient time constants
*/

package com.sun.corba.se.impl.orbutil.concurrent;

/**
 * Main interface for locks, gates, and conditions.
 * <p>
 * Sync objects isolate waiting and notification for particular
 * logical states, resource availability, events, and the like that are
 * shared across multiple threads. Use of Syncs sometimes
 * (but by no means always) adds flexibility and efficiency
 * compared to the use of plain java monitor methods
 * and locking, and are sometimes (but by no means always)
 * simpler to program with.
 * <p>
 *
 * Most Syncs are intended to be used primarily (although
 * not exclusively) in  before/after constructions such as:
 * <pre>
 * class X {
 *   Sync gate;
 *   // ...
 *
 *   public void m() {
 *     try {
 *       gate.acquire();  // block until condition holds
 *       try {
 *         // ... method body
 *       }
 *       finally {
 *         gate.release()
 *       }
 *     }
 *     catch (InterruptedException ex) {
 *       // ... evasive action
 *     }
 *   }
 *
 *   public void m2(Sync cond) { // use supplied condition
 *     try {
 *       if (cond.attempt(10)) {         // try the condition for 10 ms
 *         try {
 *           // ... method body
 *         }
 *         finally {
 *           cond.release()
 *         }
 *       }
 *     }
 *     catch (InterruptedException ex) {
 *       // ... evasive action
 *     }
 *   }
 * }
 * </pre>
 * Syncs may be used in somewhat tedious but more flexible replacements
 * for built-in Java synchronized blocks. For example:
 * <pre>
 * class HandSynched {
 *   private double state_ = 0.0;
 *   private final Sync lock;  // use lock type supplied in constructor
 *   public HandSynched(Sync l) { lock = l; }
 *
 *   public void changeState(double d) {
 *     try {
 *       lock.acquire();
 *       try     { state_ = updateFunction(d); }
 *       finally { lock.release(); }
 *     }
 *     catch(InterruptedException ex) { }
 *   }
 *
 *   public double getState() {
 *     double d = 0.0;
 *     try {
 *       lock.acquire();
 *       try     { d = accessFunction(state_); }
 *       finally { lock.release(); }
 *     }
 *     catch(InterruptedException ex){}
 *     return d;
 *   }
 *   private double updateFunction(double d) { ... }
 *   private double accessFunction(double d) { ... }
 * }
 * </pre>
 * If you have a lot of such methods, and they take a common
 * form, you can standardize this using wrappers. Some of these
 * wrappers are standardized in LockedExecutor, but you can make others.
 * For example:
 * <pre>
 * class HandSynchedV2 {
 *   private double state_ = 0.0;
 *   private final Sync lock;  // use lock type supplied in constructor
 *   public HandSynchedV2(Sync l) { lock = l; }
 *
 *   protected void runSafely(Runnable r) {
 *     try {
 *       lock.acquire();
 *       try { r.run(); }
 *       finally { lock.release(); }
 *     }
 *     catch (InterruptedException ex) { // propagate without throwing
 *       Thread.currentThread().interrupt();
 *     }
 *   }
 *
 *   public void changeState(double d) {
 *     runSafely(new Runnable() {
 *       public void run() { state_ = updateFunction(d); }
 *     });
 *   }
 *   // ...
 * }
 * </pre>
 * <p>
 * One reason to bother with such constructions is to use deadlock-
 * avoiding back-offs when dealing with locks involving multiple objects.
 * For example, here is a Cell class that uses attempt to back-off
 * and retry if two Cells are trying to swap values with each other
 * at the same time.
 * <pre>
 * class Cell {
 *   long value;
 *   Sync lock = ... // some sync implementation class
 *   void swapValue(Cell other) {
 *     for (;;) {
 *       try {
 *         lock.acquire();
 *         try {
 *           if (other.lock.attempt(100)) {
 *             try {
 *               long t = value;
 *               value = other.value;
 *               other.value = t;
 *               return;
 *             }
 *             finally { other.lock.release(); }
 *           }
 *         }
 *         finally { lock.release(); }
 *       }
 *       catch (InterruptedException ex) { return; }
 *     }
 *   }
 * }
 *</pre>
 * <p>
 * Here is an even fancier version, that uses lock re-ordering
 * upon conflict:
 * <pre>
 * class Cell {
 *   long value;
 *   Sync lock = ...;
 *   private static boolean trySwap(Cell a, Cell b) {
 *     a.lock.acquire();
 *     try {
 *       if (!b.lock.attempt(0))
 *         return false;
 *       try {
 *         long t = a.value;
 *         a.value = b.value;
 *         b.value = t;
 *         return true;
 *       }
 *       finally { other.lock.release(); }
 *     }
 *     finally { lock.release(); }
 *     return false;
 *   }
 *
 *  void swapValue(Cell other) {
 *    try {
 *      while (!trySwap(this, other) &&
 *            !tryswap(other, this))
 *        Thread.sleep(1);
 *    }
 *    catch (InterruptedException ex) { return; }
 *  }
 *}
 *</pre>
 * <p>
 * Interruptions are in general handled as early as possible.
 * Normally, InterruptionExceptions are thrown
 * in acquire and attempt(msec) if interruption
 * is detected upon entry to the method, as well as in any
 * later context surrounding waits.
 * However, interruption status is ignored in release();
 * <p>
 * Timed versions of attempt report failure via return value.
 * If so desired, you can transform such constructions to use exception
 * throws via
 * <pre>
 *   if (!c.attempt(timeval)) throw new TimeoutException(timeval);
 * </pre>
 * <p>
 * The TimoutSync wrapper class can be used to automate such usages.
 * <p>
 * All time values are expressed in milliseconds as longs, which have a maximum
 * value of Long.MAX_VALUE, or almost 300,000 centuries. It is not
 * known whether JVMs actually deal correctly with such extreme values.
 * For convenience, some useful time values are defined as static constants.
 * <p>
 * All implementations of the three Sync methods guarantee to
 * somehow employ Java <code>synchronized</code> methods or blocks,
 * and so entail the memory operations described in JLS
 * chapter 17 which ensure that variables are loaded and flushed
 * within before/after constructions.
 * <p>
 * Syncs may also be used in spinlock constructions. Although
 * it is normally best to just use acquire(), various forms
 * of busy waits can be implemented. For a simple example
 * (but one that would probably never be preferable to using acquire()):
 * <pre>
 * class X {
 *   Sync lock = ...
 *   void spinUntilAcquired() throws InterruptedException {
 *     // Two phase.
 *     // First spin without pausing.
 *     int purespins = 10;
 *     for (int i = 0; i < purespins; ++i) {
 *       if (lock.attempt(0))
 *         return true;
 *     }
 *     // Second phase - use timed waits
 *     long waitTime = 1; // 1 millisecond
 *     for (;;) {
 *       if (lock.attempt(waitTime))
 *         return true;
 *       else
 *         waitTime = waitTime * 3 / 2 + 1; // increase 50%
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * In addition pure synchronization control, Syncs
 * may be useful in any context requiring before/after methods.
 * For example, you can use an ObservableSync
 * (perhaps as part of a LayeredSync) in order to obtain callbacks
 * before and after each method invocation for a given class.
 * <p>

 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
**/


public interface Sync {

  /**
   *  Wait (possibly forever) until successful passage.
   *  Fail only upon interuption. Interruptions always result in
   *  `clean' failures. On failure,  you can be sure that it has not
   *  been acquired, and that no
   *  corresponding release should be performed. Conversely,
   *  a normal return guarantees that the acquire was successful.
  **/

  public void acquire() throws InterruptedException;

  /**
   * Wait at most msecs to pass; report whether passed.
   * <p>
   * The method has best-effort semantics:
   * The msecs bound cannot
   * be guaranteed to be a precise upper bound on wait time in Java.
   * Implementations generally can only attempt to return as soon as possible
   * after the specified bound. Also, timers in Java do not stop during garbage
   * collection, so timeouts can occur just because a GC intervened.
   * So, msecs arguments should be used in
   * a coarse-grained manner. Further,
   * implementations cannot always guarantee that this method
   * will return at all without blocking indefinitely when used in
   * unintended ways. For example, deadlocks may be encountered
   * when called in an unintended context.
   * <p>
   * @param msecs the number of milleseconds to wait.
   * An argument less than or equal to zero means not to wait at all.
   * However, this may still require
   * access to a synchronization lock, which can impose unbounded
   * delay if there is a lot of contention among threads.
   * @return true if acquired
  **/

  public boolean attempt(long msecs) throws InterruptedException;

  /**
   * Potentially enable others to pass.
   * <p>
   * Because release does not raise exceptions,
   * it can be used in `finally' clauses without requiring extra
   * embedded try/catch blocks. But keep in mind that
   * as with any java method, implementations may
   * still throw unchecked exceptions such as Error or NullPointerException
   * when faced with uncontinuable errors. However, these should normally
   * only be caught by higher-level error handlers.
  **/

  public void release();

  /**  One second, in milliseconds; convenient as a time-out value **/
  public static final long ONE_SECOND = 1000;

  /**  One minute, in milliseconds; convenient as a time-out value **/
  public static final long ONE_MINUTE = 60 * ONE_SECOND;

  /**  One hour, in milliseconds; convenient as a time-out value **/
  public static final long ONE_HOUR = 60 * ONE_MINUTE;

  /**  One day, in milliseconds; convenient as a time-out value **/
  public static final long ONE_DAY = 24 * ONE_HOUR;

  /**  One week, in milliseconds; convenient as a time-out value **/
  public static final long ONE_WEEK = 7 * ONE_DAY;

  /**  One year in milliseconds; convenient as a time-out value  **/
  // Not that it matters, but there is some variation across
  // standard sources about value at msec precision.
  // The value used is the same as in java.util.GregorianCalendar
  public static final long ONE_YEAR = (long)(365.2425 * ONE_DAY);

  /**  One century in milliseconds; convenient as a time-out value **/
  public static final long ONE_CENTURY = 100 * ONE_YEAR;


}
