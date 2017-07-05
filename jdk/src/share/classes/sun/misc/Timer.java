/*
 * Copyright 1995 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc;

/**
    A Timer object is used by algorithms that require timed events.
    For example, in an animation loop, a timer would help in
    determining when to change frames.

    A timer has an interval which determines when it "ticks";
    that is, a timer delays for the specified interval and then
    it calls the owner's tick() method.

    Here's an example of creating a timer with a 5 sec interval:

    <pre>
    class Main implements Timeable {
        public void tick(Timer timer) {
            System.out.println("tick");
        }
        public static void main(String args[]) {
            (new Timer(this, 5000)).cont();
        }
    }
    </pre>

    A timer can be stopped, continued, or reset at any time.
    A timer's state is not stopped while it's calling the
    owner's tick() method.

    A timer can be regular or irregular.  If in regular mode,
    a timer ticks at the specified interval, regardless of
    how long the owner's tick() method takes.  While the timer
    is running, no ticks are ever discarded.  That means that if
    the owner's tick() method takes longer than the interval,
    the ticks that would have occurred are delivered immediately.

    In irregular mode, a timer starts delaying for exactly
    the specified interval only after the tick() method returns.

    Synchronization issues: do not hold the timer's monitor
    while calling any of the Timer operations below otherwise
    the Timer class will deadlock.

    @author     Patrick Chan
*/

/*
    Synchronization issues:  there are two data structures that
    require locking.  A Timer object and the Timer queue
    (described in the TimerThread class).  To avoid deadlock,
    the timer queue monitor is always acquired before the timer
    object's monitor.  However, the timer queue monitor is acquired
    only if the timer operation will make use of the timer
    queue, e.g. stop().

    The class monitor on the class TimerThread severs as the monitor
    to the timer queue.

    Possible feature: perhaps a timer should have an associated
    thread priority.  The thread that makes the callback temporarily
    takes on that priority before calling the owner's tick() method.
*/

public class Timer {
    /**
     * This is the owner of the timer.  Its tick method is
     * called when the timer ticks.
     */
    public Timeable owner;

    /*
     * This is the interval of time in ms.
     */
    long interval;

    /*
     * This variable is used for two different purposes.
     * This is done in order to save space.
     * If 'stopped' is true, this variable holds the time
     * that the timer was stopped; otherwise, this variable
     * is used by the TimerThread to determine when the timer
     * should tick.
     */
    long sleepUntil;

    /*
     * This is the time remaining before the timer ticks.  It
     * is only valid if 'stopped' is true.  If the timer is
     * continued, the next tick will happen remaingTime
     * milliseconds later.
     */
    long remainingTime;

    /*
     * True iff the timer is in regular mode.
     */
    boolean regular;

    /*
     * True iff the timer has been stopped.
     */
    boolean stopped;

    /* **************************************************************
     * Timer queue-related variables
     * ************************************************************** */

    /*
     * A link to another timer object.  This is used while the
     * timer object is enqueued in the timer queue.
     */
    Timer next;

    /* **************************************************************
     * Timer methods
     * ************************************************************** */

    /*
     * This variable holds a handle to the TimerThread class for
     * the purpose of getting at the class monitor.  The reason
     * why Class.forName("TimerThread") is not used is because it
     * doesn't appear to work when loaded via a net class loader.
     */
    static TimerThread timerThread = null;

    /**
     * Creates a timer object that is owned by 'owner' and
     * with the interval 'interval' milliseconds.  The new timer
     * object is stopped and is regular.  getRemainingTime()
     * return 'interval' at this point.  getStopTime() returns
     * the time this object was created.
     * @param owner    owner of the timer object
     * @param interval interval of the timer in milliseconds
     */
    public Timer(Timeable owner, long interval) {
        this.owner = owner;
        this.interval = interval;
        remainingTime = interval;
        regular = true;
        sleepUntil = System.currentTimeMillis();
        stopped = true;
        synchronized (getClass()) {
            if (timerThread == null) {
                timerThread = new TimerThread();
            }
        }
    }

    /**
     * Returns true if this timer is stopped.
     */
    public synchronized boolean isStopped() {
        return stopped;
    }

    /**
     * Stops the timer.  The amount of time the timer has already
     * delayed is saved so if the timer is continued, it will only
     * delay for the amount of time remaining.
     * Note that even after stopping a timer, one more tick may
     * still occur.
     * This method is MT-safe; i.e. it is synchronized but for
     * implementation reasons, the synchronized modifier cannot
     * be included in the method declaration.
     */
    public void stop() {
        long now = System.currentTimeMillis();

        synchronized (timerThread) {
            synchronized (this) {
                if (!stopped) {
                    TimerThread.dequeue(this);
                    remainingTime = Math.max(0, sleepUntil - now);
                    sleepUntil = now;        // stop time
                    stopped = true;
                }
            }
        }
    }

    /**
     * Continue the timer.  The next tick will come at getRemainingTime()
     * milliseconds later.  If the timer is not stopped, this
     * call will be a no-op.
     * This method is MT-safe; i.e. it is synchronized but for
     * implementation reasons, the synchronized modifier cannot
     * be included in the method declaration.
     */
    public void cont() {
        synchronized (timerThread) {
            synchronized (this) {
                if (stopped) {
                    // The TimerTickThread avoids requeuing the
                    // timer only if the sleepUntil value has changed.
                    // The following guarantees that the sleepUntil
                    // value will be different; without this guarantee,
                    // it's theoretically possible for the timer to be
                    // inserted twice.
                    sleepUntil = Math.max(sleepUntil + 1,
                        System.currentTimeMillis() + remainingTime);
                    TimerThread.enqueue(this);
                    stopped = false;
                }
            }
        }
    }

    /**
     * Resets the timer's remaining time to the timer's interval.
     * If the timer's running state is not altered.
     */
    public void reset() {
        synchronized (timerThread) {
            synchronized (this) {
                setRemainingTime(interval);
            }
        }
    }

    /**
     * Returns the time at which the timer was last stopped.  The
     * return value is valid only if the timer is stopped.
     */
    public synchronized long getStopTime() {
        return sleepUntil;
    }

    /**
     * Returns the timer's interval.
     */
    public synchronized long getInterval() {
        return interval;
    }

    /**
     * Changes the timer's interval.  The new interval setting
     * does not take effect until after the next tick.
     * This method does not alter the remaining time or the
     * running state of the timer.
     * @param interval new interval of the timer in milliseconds
     */
    public synchronized void setInterval(long interval) {
        this.interval = interval;
    }

    /**
     * Returns the remaining time before the timer's next tick.
     * The return value is valid only if timer is stopped.
     */
    public synchronized long getRemainingTime() {
        return remainingTime;
    }

    /**
     * Sets the remaining time before the timer's next tick.
     * This method does not alter the timer's running state.
     * This method is MT-safe; i.e. it is synchronized but for
     * implementation reasons, the synchronized modifier cannot
     * be included in the method declaration.
     * @param time new remaining time in milliseconds.
     */
    public void setRemainingTime(long time) {
        synchronized (timerThread) {
            synchronized (this) {
                if (stopped) {
                    remainingTime = time;
                } else {
                    stop();
                    remainingTime = time;
                    cont();
                }
            }
        }
    }

    /**
     * In regular mode, a timer ticks at the specified interval,
     * regardless of how long the owner's tick() method takes.
     * While the timer is running, no ticks are ever discarded.
     * That means that if the owner's tick() method takes longer
     * than the interval, the ticks that would have occurred are
     * delivered immediately.
     *
     * In irregular mode, a timer starts delaying for exactly
     * the specified interval only after the tick() method returns.
     */
    public synchronized void setRegular(boolean regular) {
        this.regular = regular;
    }

    /*
     * This method is used only for testing purposes.
     */
    protected Thread getTimerThread() {
        return TimerThread.timerThread;
    }
}


/*

This class implements the timer queue and is exclusively used by the
Timer class.  There are only two methods exported to the Timer class -
enqueue, for inserting a timer into queue and dequeue, for removing
a timer from the queue.

A timer in the timer queue is awaiting a tick.  When a timer is to be
ticked, it is removed from the timer queue before the owner's tick()
method is called.

A single timer thread manages the timer queue.  This timer thread
looks at the head of the timer queue and delays until it's time for
the timer to tick.  When the time comes, the timer thread creates a
callback thread to call the timer owner's tick() method.  The timer
thread then processes the next timer in the queue.

When a timer is inserted at the head of the queue, the timer thread is
notified.  This causes the timer thread to prematurely wake up and
process the new head of the queue.

*/

class TimerThread extends Thread {
    /*
     * Set to true to get debugging output.
     */
    public static boolean debug = false;

    /*
     * This is a handle to the thread managing the thread queue.
     */
    static TimerThread timerThread;

    /*
     * This flag is set if the timer thread has been notified
     * while it was in the timed wait.  This flag allows the
     * timer thread to tell whether or not the wait completed.
     */
    static boolean notified = false;

    protected TimerThread() {
        super("TimerThread");
        timerThread = this;
        start();
    }

    public synchronized void run() {
        while (true) {
            long delay;

            while (timerQueue == null) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                   // Just drop through and check timerQueue.
                }
            }
            notified = false;
            delay = timerQueue.sleepUntil - System.currentTimeMillis();
            if (delay > 0) {
                try {
                    wait(delay);
                } catch (InterruptedException ex) {
                    // Just drop through.
                }
            }
            // remove from timer queue.
            if (!notified) {
                Timer timer = timerQueue;
                timerQueue = timerQueue.next;
                TimerTickThread thr = TimerTickThread.call(
                    timer, timer.sleepUntil);
                if (debug) {
                    long delta = (System.currentTimeMillis() - timer.sleepUntil);
                    System.out.println("tick(" + thr.getName() + ","
                        + timer.interval + ","+delta+ ")");
                    if (delta > 250) {
                        System.out.println("*** BIG DELAY ***");
                    }
                }
            }
        }
    }

    /* *******************************************************
       Timer Queue
       ******************************************************* */

    /*
     * The timer queue is a queue of timers waiting to tick.
     */
    static Timer timerQueue = null;

    /*
     * Uses timer.sleepUntil to determine where in the queue
     * to insert the timer object.
     * A new ticker thread is created only if the timer
     * is inserted at the beginning of the queue.
     * The timer must not already be in the queue.
     * Assumes the caller has the TimerThread monitor.
     */
    static protected void enqueue(Timer timer) {
        Timer prev = null;
        Timer cur = timerQueue;

        if (cur == null || timer.sleepUntil <= cur.sleepUntil) {
            // insert at front of queue
            timer.next = timerQueue;
            timerQueue = timer;
            notified = true;
            timerThread.notify();
        } else {
            do {
                prev = cur;
                cur = cur.next;
            } while (cur != null && timer.sleepUntil > cur.sleepUntil);
            // insert or append to the timer queue
            timer.next = cur;
            prev.next = timer;
        }
        if (debug) {
            long now = System.currentTimeMillis();

            System.out.print(Thread.currentThread().getName()
                + ": enqueue " + timer.interval + ": ");
            cur = timerQueue;
            while(cur != null) {
                long delta = cur.sleepUntil - now;
                System.out.print(cur.interval + "(" + delta + ") ");
                cur = cur.next;
            }
            System.out.println();
        }
    }

    /*
     * If the timer is not in the queue, returns false;
     * otherwise removes the timer from the timer queue and returns true.
     * Assumes the caller has the TimerThread monitor.
     */
    static protected boolean dequeue(Timer timer) {
        Timer prev = null;
        Timer cur = timerQueue;

        while (cur != null && cur != timer) {
            prev = cur;
            cur = cur.next;
        }
        if (cur == null) {
            if (debug) {
                System.out.println(Thread.currentThread().getName()
                    + ": dequeue " + timer.interval + ": no-op");
            }
            return false;
        }       if (prev == null) {
            timerQueue = timer.next;
            notified = true;
            timerThread.notify();
        } else {
            prev.next = timer.next;
        }
        timer.next = null;
        if (debug) {
            long now = System.currentTimeMillis();

            System.out.print(Thread.currentThread().getName()
                + ": dequeue " + timer.interval + ": ");
            cur = timerQueue;
            while(cur != null) {
                long delta = cur.sleepUntil - now;
                System.out.print(cur.interval + "(" + delta + ") ");
                cur = cur.next;
            }
            System.out.println();
        }
        return true;
    }

    /*
     * Inserts the timer back into the queue.  This method
     * is used by a callback thread after it has called the
     * timer owner's tick() method.  This method recomputes
     * the sleepUntil field.
     * Assumes the caller has the TimerThread and Timer monitor.
     */
    protected static void requeue(Timer timer) {
        if (!timer.stopped) {
            long now = System.currentTimeMillis();
            if (timer.regular) {
                timer.sleepUntil += timer.interval;
            } else {
                timer.sleepUntil = now + timer.interval;
            }
            enqueue(timer);
        } else if (debug) {
            System.out.println(Thread.currentThread().getName()
                + ": requeue " + timer.interval + ": no-op");
        }
    }
}

/*

This class implements a simple thread whose only purpose is to call a
timer owner's tick() method.  A small fixed-sized pool of threads is
maintained and is protected by the class monitor.  If the pool is
exhausted, a new thread is temporarily created and destroyed when
done.

A thread that's in the pool waits on it's own monitor.  When the
thread is retrieved from the pool, the retriever notifies the thread's
monitor.

*/

class TimerTickThread extends Thread {
    /*
     * Maximum size of the thread pool.
     */
    static final int MAX_POOL_SIZE = 3;

    /*
     * Number of threads in the pool.
     */
    static int curPoolSize = 0;

    /*
     * The pool of timer threads.
     */
    static TimerTickThread pool = null;

    /*
     * Is used when linked into the thread pool.
     */
    TimerTickThread next = null;

    /*
     * This is the handle to the timer whose owner's
     * tick() method will be called.
     */
    Timer timer;

    /*
     * The value of a timer's sleepUntil value is captured here.
     * This is used to determine whether or not the timer should
     * be reinserted into the queue.  If the timer's sleepUntil
     * value has changed, the timer is not reinserted.
     */
    long lastSleepUntil;

    /*
     * Creates a new callback thread to call the timer owner's
     * tick() method.  A thread is taken from the pool if one
     * is available, otherwise, a new thread is created.
     * The thread handle is returned.
     */
    protected static synchronized TimerTickThread call(
            Timer timer, long sleepUntil) {
        TimerTickThread thread = pool;

        if (thread == null) {
            // create one.
            thread = new TimerTickThread();
            thread.timer = timer;
            thread.lastSleepUntil = sleepUntil;
            thread.start();
        } else {
            pool = pool.next;
            thread.timer = timer;
            thread.lastSleepUntil = sleepUntil;
            synchronized (thread) {
                thread.notify();
            }
        }
        return thread;
    }

    /*
     * Returns false if the thread should simply exit;
     * otherwise the thread is returned the pool, where
     * it waits to be notified.  (I did try to use the
     * class monitor but the time between the notify
     * and breaking out of the wait seemed to take
     * significantly longer; need to look into this later.)
     */
    private boolean returnToPool() {
        synchronized (getClass()) {
            if (curPoolSize >= MAX_POOL_SIZE) {
                return false;
            }
            next = pool;
            pool = this;
            curPoolSize++;
            timer = null;
        }
        while (timer == null) {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                   // Just drop through and retest timer.
                }
            }
        }
        synchronized (getClass()) {
            curPoolSize--;
        }
        return true;
    }

    public void run() {
        do {
            timer.owner.tick(timer);
            synchronized (TimerThread.timerThread) {
                synchronized (timer) {
                    if (lastSleepUntil == timer.sleepUntil) {
                        TimerThread.requeue(timer);
                    }
                }
            }
        } while (returnToPool());
    }
}
