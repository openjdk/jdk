/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotGraalCompiler.fmt;

import java.util.Arrays;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A watch dog for reporting long running compilations. This is designed to be an always on
 * mechanism for the purpose of getting better reports from customer sites. As such, it only exits
 * the VM when it is very sure about a stuck compilation as opposed to only observing a long running
 * compilation. In both cases, it logs messages to {@link TTY}.
 *
 * A watch dog thread is associated with each compiler thread. It wakes up every
 * {@value #SPIN_TIMEOUT_MS} milliseconds to observe the state of the compiler thread. After the
 * first {@link Options#CompilationWatchDogStartDelay} seconds of a specific compilation, the watch
 * dog reports a long running compilation. Every
 * {@link Options#CompilationWatchDogStackTraceInterval} seconds after that point in time where the
 * same compilation is still executing, the watch dog takes a stack trace of the compiler thread. If
 * more than {@value Options#NonFatalIdenticalCompilationSnapshots} contiguous identical stack
 * traces are seen, the watch dog reports a stuck compilation and exits the VM.
 */
class CompilationWatchDog extends Thread implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Delay in seconds before watch dog monitoring a compilation (0 disables monitoring).", type = OptionType.Debug)
        public static final OptionKey<Double> CompilationWatchDogStartDelay = new OptionKey<>(0.0D);
        @Option(help = "Interval in seconds between a watch dog reporting stack traces for long running compilations.", type = OptionType.Debug)
        public static final OptionKey<Double> CompilationWatchDogStackTraceInterval = new OptionKey<>(60.0D);
        @Option(help = "Number of contiguous identical compiler thread stack traces allowed before the VM exits " +
                       "on the basis of a stuck compilation.", type = OptionType.Debug)
        public static final OptionKey<Integer> NonFatalIdenticalCompilationSnapshots = new OptionKey<>(20);
        // @formatter:on
    }

    private enum WatchDogState {
        /**
         * The watch dog thread sleeps currently, either no method is currently compiled, or no
         * method is compiled long enough to be monitored.
         */
        SLEEPING,
        /**
         * The watch dog thread identified a compilation that already takes long enough to be
         * interesting. It will sleep and wake up periodically and check if the current compilation
         * takes too long. If it takes too long it will start collecting stack traces from the
         * compiler thread.
         */
        WATCHING_WITHOUT_STACK_INSPECTION,
        /**
         * The watch dog thread is fully monitoring the compiler thread. It takes stack traces
         * periodically and sleeps again until the next period. If the number of stack traces
         * reaches a certain upper bound and those stack traces are equal it will shut down the
         * entire VM with an error.
         */
        WATCHING_WITH_STACK_INSPECTION
    }

    /**
     * The number of milliseconds a watch dog thread sleeps between observing the state of the
     * compilation thread it is associated with. Most compilations are expected to complete within
     * this time period and thus not be actively monitored by the watch dog.
     */
    private static final int SPIN_TIMEOUT_MS = 1000;

    private WatchDogState state = WatchDogState.SLEEPING;
    private final Thread compilerThread;
    private final long startDelayMilliseconds;
    private final long stackTraceIntervalMilliseconds;
    private final int nonFatalIdenticalCompilationSnapshots;
    private volatile ResolvedJavaMethod currentMethod;
    private volatile int currentId;
    private ResolvedJavaMethod lastWatched;

    // The 4 fields below are for a single compilation being watched
    private long elapsed;
    private int traceIntervals;
    private int numberOfIdenticalStackTraces;
    private StackTraceElement[] lastStackTrace;

    CompilationWatchDog(Thread compilerThread, long startDelayMilliseconds, long stackTraceIntervalMilliseconds, int nonFatalIdenticalCompilationSnapshots) {
        this.compilerThread = compilerThread;
        this.setName("WatchDog" + getId() + "[" + compilerThread.getName() + "]");
        this.setPriority(Thread.MAX_PRIORITY);
        this.setDaemon(true);
        this.startDelayMilliseconds = startDelayMilliseconds;
        this.stackTraceIntervalMilliseconds = stackTraceIntervalMilliseconds;
        this.nonFatalIdenticalCompilationSnapshots = nonFatalIdenticalCompilationSnapshots;
    }

    public void startCompilation(ResolvedJavaMethod method, int id) {
        trace("start %s", fmt(method));
        this.currentMethod = method;
        this.currentId = id;
    }

    public void stopCompilation() {
        trace(" stop %s", fmt(currentMethod));
        this.currentMethod = null;
    }

    private void reset() {
        elapsed = 0;
        traceIntervals = 0;
        numberOfIdenticalStackTraces = 0;
        lastWatched = null;
        lastStackTrace = null;
        state = WatchDogState.SLEEPING;
    }

    private void tick(WatchDogState newState) {
        state = newState;
    }

    /**
     * Saves the current stack trace {@link StackTraceElement} of the monitored compiler thread
     * {@link CompilationWatchDog#compilerThread}.
     *
     * @param newStackTrace the current stack trace of the monitored compiler thread
     * @return {@code true} if the stack trace is equal to the last stack trace (or if it is the
     *         first one) and {@code false} if it is not equal to the last one.
     */
    private boolean recordStackTrace(StackTraceElement[] newStackTrace) {
        if (lastStackTrace == null) {
            lastStackTrace = newStackTrace;
            return true;
        }
        if (!Arrays.equals(lastStackTrace, newStackTrace)) {
            lastStackTrace = newStackTrace;
            return false;
        }
        return true;
    }

    /**
     * Set to true to debug the watch dog.
     */
    private static final boolean DEBUG = Boolean.getBoolean("debug.graal.CompilationWatchDog");

    private void trace(String format, Object... args) {
        if (DEBUG) {
            TTY.println(this + ": " + String.format(format, args));
        }
    }

    private static long ms(double seconds) {
        return (long) seconds * 1000;
    }

    private static double secs(long ms) {
        return (double) ms / 1000;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public void run() {
        try {
            trace("Started%n", this);
            while (true) {
                // get a copy of the last set method
                final ResolvedJavaMethod currentlyCompiling = currentMethod;
                if (currentlyCompiling == null) {
                    // continue sleeping, compilation is either over before starting
                    // to watch the compiler thread or no compilation at all started
                    reset();
                } else {
                    switch (state) {
                        case SLEEPING:
                            lastWatched = currentlyCompiling;
                            elapsed = 0;
                            tick(WatchDogState.WATCHING_WITHOUT_STACK_INSPECTION);
                            break;
                        case WATCHING_WITHOUT_STACK_INSPECTION:
                            if (currentlyCompiling.equals(lastWatched)) {
                                if (elapsed >= startDelayMilliseconds) {
                                    // we looked at the same compilation for a certain time
                                    // so now we start to collect stack traces
                                    tick(WatchDogState.WATCHING_WITH_STACK_INSPECTION);
                                    trace("changes mode to watching with stack traces");
                                } else {
                                    // we still compile the same method but won't collect traces
                                    // yet
                                    trace("watching without stack traces [%.2f seconds]", secs(elapsed));
                                }
                                elapsed += SPIN_TIMEOUT_MS;
                            } else {
                                // compilation finished before we exceeded initial watching
                                // period
                                reset();
                            }
                            break;
                        case WATCHING_WITH_STACK_INSPECTION:
                            if (currentlyCompiling.equals(lastWatched)) {
                                if (elapsed >= startDelayMilliseconds + (traceIntervals * stackTraceIntervalMilliseconds)) {
                                    trace("took a stack trace");
                                    boolean newStackTrace = recordStackTrace(compilerThread.getStackTrace());
                                    if (!newStackTrace) {
                                        trace("%d identical stack traces in a row", numberOfIdenticalStackTraces);
                                        numberOfIdenticalStackTraces = 0;
                                    }
                                    numberOfIdenticalStackTraces++;
                                    if (numberOfIdenticalStackTraces > nonFatalIdenticalCompilationSnapshots) {
                                        synchronized (CompilationWatchDog.class) {
                                            TTY.printf("======================= WATCH DOG THREAD =======================%n" +
                                                            "%s took %d identical stack traces, which indicates a stuck compilation (id=%d) of %s%n%sExiting VM%n", this,
                                                            numberOfIdenticalStackTraces, currentId, fmt(currentMethod), fmt(lastStackTrace));
                                            System.exit(-1);
                                        }
                                    } else if (newStackTrace) {
                                        synchronized (CompilationWatchDog.class) {
                                            TTY.printf("======================= WATCH DOG THREAD =======================%n" +
                                                            "%s detected long running compilation (id=%d) of %s [%.2f seconds]%n%s", this, currentId, fmt(currentMethod),
                                                            secs(elapsed), fmt(lastStackTrace));
                                        }
                                    }
                                    traceIntervals++;
                                } else {
                                    // we still watch the compilation in the same trace interval
                                    trace("watching with stack traces [%.2f seconds]", secs(elapsed));
                                }
                                elapsed += SPIN_TIMEOUT_MS;
                            } else {
                                // compilation finished before we are able to collect stack
                                // traces
                                reset();
                            }
                            break;
                        default:
                            break;
                    }
                }
                try {
                    Thread.sleep(SPIN_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    // Silently swallow
                }
            }
        } catch (VirtualMachineError vmError) {
            /*
             * We encounter a VM error. This includes for example OutOfMemoryExceptions. In such a
             * case we silently swallow the error. If it happens again the application thread will
             * most likely encounter the same problem. If not the watchdog thread will no longer
             * monitor the compilation and thus the error cannot happen again.
             */
        } catch (Throwable t) {
            /*
             * A real exception happened on the compilation watchdog. This is unintended behavior
             * and must not happen in any case.
             */
            throw new InternalError(String.format("%s encountered an exception%n%s%n", this, fmt(t)), t);
        }
    }

    private static final ThreadLocal<CompilationWatchDog> WATCH_DOGS = new ThreadLocal<>();

    /**
     * Opens a scope for watching the compilation of a given method.
     *
     * @param method a method about to be compiled
     * @param id compilation request identifier
     * @return {@code null} if the compilation watch dog is disabled otherwise this object. The
     *         returned value should be used in a {@code try}-with-resources statement whose scope
     *         is the whole compilation so that leaving the scope will cause {@link #close()} to be
     *         called.
     */
    static CompilationWatchDog watch(ResolvedJavaMethod method, int id, OptionValues options) {
        long startDelayMilliseconds = ms(Options.CompilationWatchDogStartDelay.getValue(options));
        if (startDelayMilliseconds > 0.0D) {
            // Lazily get a watch dog thread for the current compiler thread
            CompilationWatchDog watchDog = WATCH_DOGS.get();
            if (watchDog == null) {
                Thread currentThread = currentThread();
                long stackTraceIntervalMilliseconds = ms(Options.CompilationWatchDogStackTraceInterval.getValue(options));
                int nonFatalIdenticalCompilationSnapshots = Options.NonFatalIdenticalCompilationSnapshots.getValue(options);
                watchDog = new CompilationWatchDog(currentThread, startDelayMilliseconds, stackTraceIntervalMilliseconds, nonFatalIdenticalCompilationSnapshots);
                WATCH_DOGS.set(watchDog);
                watchDog.start();
            }
            watchDog.startCompilation(method, id);
            return watchDog;
        }
        return null;
    }

    @Override
    public void close() {
        stopCompilation();
    }
}
