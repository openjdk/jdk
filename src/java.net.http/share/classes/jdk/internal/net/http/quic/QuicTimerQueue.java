/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;

/**
 * A timer queue that can process events which are due, and possibly
 * reschedule them if needed. An instance of a {@link QuicTimerQueue}
 * is usually associated with an instance of {@link QuicSelector} which
 * provides the timer/wakeup facility.
 */
public final class QuicTimerQueue {

    // A queue that contains scheduled events
    private final ConcurrentSkipListSet<QuicTimedEvent> scheduled =
            new ConcurrentSkipListSet<>(QuicTimedEvent.COMPARATOR);

    // A queue that contains events which are due. The queue is
    // filled by processAndReturnNextDeadline()
    private final ConcurrentLinkedQueue<QuicTimedEvent> due =
            new ConcurrentLinkedQueue<>();

    // A queue that contains events that need to be rescheduled.
    // The event may already be in the scheduled queue - in which
    // case it will be removed before being added back.
    private final Set<QuicTimedEvent> rescheduled =
            ConcurrentHashMap.newKeySet();

    // A callback to tell the timer thread to wake up
    private final Runnable notifier;

    // A loop to process events which are due, or which need to
    // be rescheduled.
    private final SequentialScheduler processor =
            SequentialScheduler.lockingScheduler(this::processDue);

    private final Logger debug;
    private volatile boolean closed;
    private volatile Deadline scheduledDeadline = Deadline.MAX;
    private volatile Deadline returnedDeadline = Deadline.MAX;

    /**
     * Creates a new timer queue with the given notifier.
     * A notifier is used to notify the timer thread that
     * new events have been added to the queue of scheduled
     * event. The notifier should wake up the thread and
     * trigger a call to either {@link
     * #processEventsAndReturnNextDeadline(Deadline, Executor)}
     * or {@link #nextDeadline()}.
     *
     * @param notifier A notifier to wake up the timer thread when
     *                 new event have been added and the next
     *                 deadline has changed.
     */
    public QuicTimerQueue(Runnable notifier, Logger debug) {
        this.notifier = notifier;
        this.debug = debug;
    }

    // For debug purposes only
    private String d(Deadline deadline) {
        return Utils.debugDeadline(debugNow(), deadline);
    }

    // For debug purposes only
    private String d(Deadline now, Deadline deadline) {
        return Utils.debugDeadline(now, deadline);
    }

    // For debug purposes only
    private Deadline debugNow() {
        return TimeSource.now();
    }

    /**
     * Schedule the given event by adding it to the timer queue.
     *
     * @param event an event to be scheduled
     */
    public void offer(QuicTimedEvent event) {
        if (event instanceof Marker marker)
            throw new IllegalArgumentException(marker.name());
        assert QuicTimedEvent.COMPARATOR.compare(event, FLOOR) > 0;
        assert QuicTimedEvent.COMPARATOR.compare(event, CEILING) < 0;
        Deadline deadline = event.deadline();
        scheduled.add(event);
        scheduled(deadline);
        if (debug.on()) debug.log("QuicTimerQueue: event %s offered", event);
        if (notify(deadline)) {
            if (debug.on()) debug.log("QuicTimerQueue: event %s will be rescheduled", event);
            if (Log.quicTimer()) {
                var now = debugNow();
                Log.logQuic(String.format("%s: QuicTimerQueue: event %s will be scheduled" +
                                " at %s (returned deadline: %s, nextDeadline: %s)",
                        Thread.currentThread().getName(), event, d(now, deadline),
                        d(now, returnedDeadline), d(now, nextDeadline())));
            }
            notifier.run();
        } else {
            if (Log.quicTimer()) {
                var now = debugNow();
                Log.logQuic(String.format("%s: QuicTimerQueue: event %s will not be scheduled" +
                                " at %s (returned deadline: %s, nextDeadline: %s)",
                        Thread.currentThread().getName(), event, d(now, deadline),
                        d(now, returnedDeadline), d(now, nextDeadline())));
            }
        }
    }

    /**
     * The next deadline for this timer queue. This is only weakly
     * consistent. If the queue is empty, {@link Deadline#MAX} is
     * returned.
     *
     * @return The next deadline, or {@code Deadline.MAX}.
     */
    public Deadline nextDeadline() {
        var event = scheduled.ceiling(FLOOR);
        return event == null ? Deadline.MAX : event.deadline();
    }

    public Deadline pendingScheduledDeadline() {
        return scheduledDeadline;
    }

    /**
     * Process all events that were due before {@code now}, and
     * returns the next deadline. The events are processed within
     * an executor's thread, so this method may return before all
     * events have been processed. The events are processed in
     * order, with respect to their deadline. Processing an event
     * involves invoking its {@link QuicTimedEvent#handle() handle}
     * method. If that method returns a new deadline different from
     * {@link Deadline#MAX} the processed event is rescheduled
     * immediately. Otherwise, it will not be rescheduled.
     *
     * @param now       The point in time before which events are
     *                  considered to be due. Usually, that's now.
     * @param executor  An executor to process events which are due.
     *
     * @return the next unexpired deadline, or {@link Deadline#MAX}
     *         if the queue is empty.
     */
    public Deadline processEventsAndReturnNextDeadline(Deadline now, Executor executor) {
        QuicTimedEvent event;
        int drained = 0;
        int dues;
        synchronized (this) {
            scheduledDeadline = Deadline.MAX;
        }
        // moved scheduled / rescheduled tasks to due, until
        // nothing else is due. Then process dues.
        do {
            dues = processRescheduled(now);
            dues = dues + processScheduled(now);
            drained += dues;
        } while (dues > 0);
        Deadline newDeadline = (event = scheduled.ceiling(FLOOR)) == null ? Deadline.MAX : event.deadline();
        assert event == null || newDeadline.isBefore(Deadline.MAX) : "Invalid deadline for " + event;
        if (debug.on()) {
            debug.log("QuicTimerQueue: newDeadline: " + d(now, newDeadline)
                    + (event == null ? "no event scheduled" : (" for " + event)));
        }
        Deadline next;
        synchronized (this) {
            var scheduled = scheduledDeadline;
            scheduledDeadline = Deadline.MAX;
            // if some task is being rescheduled with a deadline
            // that is before any scheduled deadline, use that deadline.
            next = returnedDeadline = min(newDeadline, scheduled);
        }
        if (next.equals(Deadline.MAX)) {
            if (Log.quicTimer()) {
                Log.logQuic(String.format("%s: TimerQueue: no deadline" +
                        " (scheduled: %s, rescheduled: %s, dues %s)",
                        Thread.currentThread().getName(), this.scheduled.size(),
                        this.rescheduled.size(), this.due.size()));
            }
        }
        if (drained > 0) {
            if (Log.quicTimer()) {
                Log.logQuic(String.format("%s: TimerQueue: %s events to handle (%s in dues)",
                        Thread.currentThread().getName(), drained, this.due.size()));
            }
            processor.runOrSchedule(executor);
        }
        return next;
    }

    // return the deadline which is before the other
    private Deadline min(Deadline one, Deadline two) {
        return one.isBefore(two) ? one : two;
    }

    // walk through the rescheduled tasks and moves any
    // that are due to `due`. Otherwise, move them to
    // `scheduled`
    private int processRescheduled(Deadline now) {
        int drained = 0;
        for (var it = rescheduled.iterator(); it.hasNext(); ) {
            QuicTimedEvent event = it.next();
            it.remove(); // remove before processing to avoid race
            scheduled.remove(event);
            Deadline deadline = event.refreshDeadline();
            if (deadline.equals(Deadline.MAX)) {
                continue;
            }
            if (deadline.isAfter(now)) {
                scheduled.add(event);
            } else {
                due.add(event);
                drained++;
            }
        }
        if (drained > 0) {
            if (debug.on()) {
                debug.log("QuicTimerQueue: %s rescheduled tasks are due", drained);
            }
        }
        return drained;
    }

    // walk through the scheduled tasks and moves any
    // that are due to `due`.
    private int processScheduled(Deadline now) {
        QuicTimedEvent event;
        int drained = 0;
        while ((event = scheduled.ceiling(FLOOR)) != null) {
            Deadline deadline = event.deadline();
            if (!isDue(deadline, now)) {
                break;
            }
            event = scheduled.pollFirst();
            if (event == null) {
                break;
            }
            drained++;
            due.add(event);
        }
        if (drained > 0 && debug.on()) {
            debug.log("QuicTimerQueue: %s scheduled tasks are due", drained);
        }
        return drained;
    }

    private static boolean isDue(final Deadline deadline, final Deadline now) {
        return deadline.compareTo(now) <= 0;
    }

    // process all due events in order
    private void processDue() {
        try {
            QuicTimedEvent event;
            if (closed) return;
            if (debug.on()) debug.log("QuicTimerQueue: processDue");
            if (Log.quicTimer()) {
                Log.logQuic(String.format("%s: TimerQueue: process %s events",
                        Thread.currentThread().getName(), due.size()));
            }
            Deadline minDeadLine = Deadline.MAX;
            while ((event = due.poll()) != null) {
                if (closed) return;
                Deadline nextDeadline = event.handle();
                if (Deadline.MAX.equals(nextDeadline)) continue;
                rescheduled.add(event);
                if (nextDeadline.isBefore(minDeadLine)) minDeadLine = nextDeadline;
            }

            // record the minimal deadline that was rescheduled
            scheduled(minDeadLine);

            // wake up the selector thread if necessary
            if (notify(minDeadLine)) {
                if (Log.quicTimer()) {
                    Log.logQuic(String.format("%s: TimerQueue: notify: minDeadline: %s",
                            Thread.currentThread().getName(), d(minDeadLine)));
                }
                notifier.run();
            } else if (!minDeadLine.equals(Deadline.MAX)) {
                if (Log.quicTimer()) {
                    Log.logQuic(String.format("%s: TimerQueue: no need to notify: minDeadline: %s",
                            Thread.currentThread().getName(), d(minDeadLine)));
                }
            }

        } catch (Throwable t) {
            if (!closed) {
                if (Log.errors()) {
                    Log.logError(Thread.currentThread().getName() +
                            ": Unexpected exception while processing due events: " + t);
                    Log.logError(t);
                } else if (debug.on()) {
                    debug.log("Unexpected exception while processing due events", t);
                }
                throw t;
            } else {
                if (Log.errors()) {
                    Log.logError(Thread.currentThread().getName() +
                            ": Ignoring exception while closing: " + t);
                    Log.logError(t);
                } else if (debug.on()) {
                    debug.log("Ignoring exception while closing: " + t);
                }
            }
        }
    }

    // We do not need to notify the selector thread if the next scheduled
    // deadline is before the given deadline, or if it is after
    // the last returned deadline.
    private boolean notify(Deadline deadline) {
        synchronized (this) {
            if (deadline.isBefore(nextDeadline())
                || deadline.isBefore(returnedDeadline)) {
                return true;
            }
        }
        return false;
    }

    // Record a prospective attempt to reschedule an event at
    // the given deadline
    private Deadline scheduled(Deadline deadline) {
        synchronized (this) {
            var scheduled = scheduledDeadline;
            if (deadline.isBefore(scheduled)) {
                scheduledDeadline = deadline;
                return deadline;
            }
            return scheduled;
        }
    }

    /**
     * Reschedule the given {@code QuicTimedEvent}.
     *
     * @apiNote
     * This method is used if the prospective future deadline at which the event
     * should be scheduled is not known by the caller.
     * This may cause an idle wakeup in the selector thread owning this
     * {@code QuicTimerQueue}. Use {@link #reschedule(QuicTimedEvent, Deadline)}
     * to minimize idle wakeup.
     *
     * @param event an event to reschedule
     */
    public void reschedule(QuicTimedEvent event) {
        if (event instanceof Marker marker)
            throw new IllegalArgumentException(marker.name());
        assert QuicTimedEvent.COMPARATOR.compare(event, FLOOR) > 0;
        assert QuicTimedEvent.COMPARATOR.compare(event, CEILING) < 0;
        rescheduled.add(event);
        if (debug.on()) debug.log("QuicTimerQueue: event %s will be rescheduled", event);
        if (Log.quicTimer()) {
            var now = debugNow();
            Log.logQuic(String.format("%s: QuicTimerQueue: event %s will be rescheduled" +
                    " (returned deadline: %s, nextDeadline: %s)",
                    Thread.currentThread().getName(), event, d(now, returnedDeadline),
                    d(now, nextDeadline())));
        }
        notifier.run();
    }

    /**
     * Reschedule the given {@code QuicTimedEvent}.
     *
     * @apiNote
     * This method should be used in preference of {@link #reschedule(QuicTimedEvent)}
     * if the prospective future deadline at which the event should be scheduled is
     * already known by the caller. Using this method will minimize idle wakeup
     * of the selector thread, in comparison of {@link #reschedule(QuicTimedEvent)}.
     *
     * @param event an event to reschedule
     * @param deadline the prospective future deadline at which the event should
     *                 be rescheduled
     */
    public void reschedule(QuicTimedEvent event, Deadline deadline) {
        if (event instanceof Marker marker)
            throw new IllegalArgumentException(marker.name());
        assert QuicTimedEvent.COMPARATOR.compare(event, FLOOR) > 0;
        assert QuicTimedEvent.COMPARATOR.compare(event, CEILING) < 0;
        rescheduled.add(event);
        scheduled(deadline);
        // no need to wake up the selector thread if the next deadline
        // is already before the new deadline

        if (notify(deadline)) {
            if (Log.quicTimer()) {
                var now = debugNow();
                Log.logQuic(String.format("%s: QuicTimerQueue: event %s will be rescheduled" +
                        " at %s (returned deadline: %s, nextDeadline: %s)",
                        Thread.currentThread().getName(), event, d(now, deadline),
                        d(now, returnedDeadline), d(now, nextDeadline())));
            } else if (debug.on()) {
                debug.log("QuicTimerQueue: event %s will be rescheduled", event);
            }
            notifier.run();
        } else {
            if (Log.quicTimer()) {
                var now = debugNow();
                Log.logQuic(String.format("%s: QuicTimerQueue: event %s will not be rescheduled" +
                        " at %s (returned deadline: %s, nextDeadline: %s)",
                        Thread.currentThread().getName(), event, d(now, deadline),
                        d(now, returnedDeadline), d(now, nextDeadline())));
            }
        }
    }

    private static final AtomicLong EVENTIDS = new AtomicLong();

    /**
     * {@return a unique id for a new {@link QuicTimedEvent}}
     * Each new instance of {@link QuicTimedEvent} is created with a long
     * ID returned by this method to ensure a total ordering of
     * {@code QuicTimedEvent} instances, even when their deadlines
     * are equal.
     */
    public static long newEventId() {
        return EVENTIDS.getAndIncrement();
    }

    // aliases
    private static final Marker FLOOR = Marker.FLOOR;
    private static final Marker CEILING = Marker.CEILING;

    /**
     * Called to clean up the timer queue when it is no longer needed.
     * Makes sure that all pending tasks are cleared from the various lists.
     */
    public void stop() {
        closed = true;
        do {
            processor.stop();
            due.clear();
            rescheduled.clear();
            scheduled.clear();
        } while (!due.isEmpty() || !rescheduled.isEmpty() || !scheduled.isEmpty());
    }

    // This class is used to work around the lack of a peek() method
    // in ConcurrentSkipListSet. ConcurrentSkipListSet has a method
    // called first(), but it throws NoSuchElementException if the
    // set isEmpty() - whereas peek() would return {@code null}.
    // The next best thing is to use ConcurrentSkipListSet::ceiling,
    // but for that we need to define a minimum event which is lower
    // than any other event: we do this by defining Marker.FLOOR
    // which has deadline=Deadline.MIN and eventId=Long.MIN_VALUE;
    // Note: it would be easier to use a record, but an enum ensures that we
    // can only have the two instances FLOOR and CEILING.
    enum Marker implements QuicTimedEvent {
        /**
         * A {@code Marker} event to pass to {@link ConcurrentSkipListSet#ceiling(Object)
         * ConcurrentSkipListSet::ceiling} in order to get the first event in the list,
         * or {@code null}.
         *
         * @apiNote
         * The intended usage is: <pre>{@code
         *       var head = scheduled.ceiling(FLOOR);
         * }</pre>
         *
         */
        FLOOR(Deadline.MIN, Long.MIN_VALUE),
        /**
         * A {@code Marker} event to pass to {@link ConcurrentSkipListSet#floor(Object)
         * ConcurrentSkipListSet::floor} in order to get the last event in the list,
         * or {@code null}.
         *
         * @apiNote
         * The intended usage is: <pre>{@code
         *       var head = scheduled.floor(CEILING);
         * }</pre>
         *
         */
        CEILING(Deadline.MAX, Long.MAX_VALUE);
        private final Deadline deadline;
        private final long eventId;
        private Marker(Deadline deadline, long eventId)  {
            this.deadline = deadline;
            this.eventId = eventId;
        }

        @Override public Deadline deadline() { return deadline; }
        @Override public Deadline refreshDeadline() {return Deadline.MAX;}
        @Override public Deadline handle() { return Deadline.MAX; }
        @Override public long eventId() { return eventId; }
    }

}
