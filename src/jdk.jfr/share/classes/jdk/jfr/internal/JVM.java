/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.util.List;

import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.jfr.Event;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;
import jdk.jfr.internal.management.HiddenWait;

/**
 * Interface against the JVM.
 *
 */
public final class JVM {
    private static final JVM jvm = new JVM();

    static final long RESERVED_CLASS_ID_LIMIT = 500;

    /*
     * The JVM uses the chunk rotation monitor to notify Java that a rotation is warranted.
     */
    public static final Object CHUNK_ROTATION_MONITOR = new HiddenWait();

    private static volatile boolean nativeOK;

    private static native void registerNatives();

    static {
        registerNatives();
        for (LogTag tag : LogTag.values()) {
            subscribeLogLevel(tag, tag.id);
        }
        Options.ensureInitialized();
    }

    /**
     * Marks current chunk as final
     * <p>
     * This allows streaming clients to read the chunk header and
     * close the stream when no more data will be written into
     * the current repository.
     */
    public static native void markChunkFinal();

    /**
     * Begin recording events
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     */
    public static native void beginRecording();

    /**
     * Return true if the JVM is recording
     */
    public static native boolean isRecording();

    /**
     * End recording events, which includes flushing data in thread buffers
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     *
     */
    public static native void endRecording();

    /**
     * Return ticks
     *
     * @return the time, in ticks
     *
     */
    @IntrinsicCandidate
    public static native long counterTime();

    /**
     * Emits native periodic event.
     *
     * @param eventTypeId type id
     *
     * @param timestamp commit time for event
     * @param periodicType when it is being done {@link PeriodicType.When}
     *
     * @return true if the event was committed
     */
    public static native boolean emitEvent(long eventTypeId, long timestamp, long periodicType);

    /**
     * Return a list of all classes deriving from {@link jdk.internal.event.Event}
     *
     * @return list of event classes.
     */
    public static native List<Class<? extends jdk.internal.event.Event>> getAllEventClasses();

    /**
     * Return a count of the number of unloaded classes deriving from {@link Event}
     *
     * @return number of unloaded event classes.
     */
    public static native long getUnloadedEventClassCount();

    /**
     * Return a unique identifier for a class. The class is marked as being
     * "in use" in JFR.
     *
     * @param clazz clazz
     *
     * @return a unique class identifier
     */
    @IntrinsicCandidate
    public static native long getClassId(Class<?> clazz);

    /**
     * Return process identifier.
     *
     * @return process identifier
     */
    public static native String getPid();

    /**
     * Return unique identifier for stack trace.
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     *
     * @param skipCount number of frames to skip, or 0 if no frames should be
     *                  skipped
     *
     * @param ID        ID of the filter that should be used, or -1 if no filter should
     *                  be used
     *
     * @return a unique stack trace identifier
     */
    public static native long getStackTraceId(int skipCount, long stackFilerId);

    /**
     * Return identifier for thread
     *
     * @param t thread
     * @return a unique thread identifier
     */
    public static native long getThreadId(Thread t);

    /**
     * Frequency, ticks per second
     *
     * @return frequency
     */
    public static native long getTicksFrequency();

    /**
     * Returns the same clock that sets the start time of a chunk (in nanos).
     */
    public static native long nanosNow();

    /**
     * Write message to log. Should swallow null or empty message, and be able
     * to handle any Java character and not crash with very large message
     *
     * @param tagSetId the tagset id
     * @param level on level
     * @param message log message
     *
     */
    public static native void log(int tagSetId, int level, String message);

    /**
     * Log an event to jfr+event or jfr+event+system.
     * <p>
     * Caller should ensure that message is not null or too large to handle.
     *
     * @param level log level
     * @param lines lines to log
     * @param system if lines should be written to jfr+event+system
     */
    public static native void logEvent(int level, String[] lines, boolean system);

    /**
     * Subscribe to LogLevel updates for LogTag
     *
     * @param lt the log tag to subscribe
     * @param tagSetId the tagset id
     */
    public static native void subscribeLogLevel(LogTag lt, int tagSetId);

    /**
     * Call to invoke event tagging and retransformation of the passed classes
     *
     * @param classes
     *
     * @throws IllegalStateException if wrong JVMTI phase.
     */
     public static synchronized native void retransformClasses(Class<?>[] classes);

    /**
     * Enable event
     *
     * @param eventTypeId event type id
     *
     * @param enabled enable event
     */
    public static native void setEnabled(long eventTypeId, boolean enabled);

    /**
     * Interval at which the JVM should notify on {@link #FILE_DELTA_CHANGE}
     *
     * @param delta number of bytes, reset after file rotation
     */
    public static native void setFileNotification(long delta);

    /**
     * Set the number of global buffers to use
     *
     * @param count
     *
     * @throws IllegalArgumentException if count is not within a valid range
     * @throws IllegalStateException if value can't be changed
     */
    public static native void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException;

    /**
     * Set size of a global buffer
     *
     * @param size
     *
     * @throws IllegalArgumentException if buffer size is not within a valid
     *         range
     */
    public static native void setGlobalBufferSize(long size) throws IllegalArgumentException;

    /**
     * Set overall memory size
     *
     * @param size
     *
     * @throws IllegalArgumentException if memory size is not within a valid
     *         range
     */
    public static native void setMemorySize(long size) throws IllegalArgumentException;

    /**
     * Set period for method samples, in milliseconds.
     *
     * Setting period to 0 turns off the method sampler.
     *
     * @param periodMillis the sampling period
     */
    public static native void setMethodSamplingPeriod(long type, long periodMillis);

    /**
     * Sets the file where data should be written.
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     *
     * <pre>
     * Recording  Previous  Current  Action
     * ==============================================
     *    true     null      null     Ignore, keep recording in-memory
     *    true     null      file1    Start disk recording
     *    true     file      null     Copy out metadata to disk and continue in-memory recording
     *    true     file1     file2    Copy out metadata and start with new File (file2)
     *    false     *        null     Ignore, but start recording to memory with {@link #beginRecording()}
     *    false     *        file     Ignore, but start recording to disk with {@link #beginRecording()}
     *
     * </pre>
     *
     * recording can be set to true/false with {@link #beginRecording()}
     * {@link #endRecording()}
     *
     * @param file the file where data should be written, or null if it should
     *        not be copied out (in memory).
     */
    public static native void setOutput(String file);

    /**
     * Controls if a class deriving from jdk.jfr.Event should
     * always be instrumented on class load.
     *
     * @param force, true to force initialization, false otherwise
     */
    public static native void setForceInstrumentation(boolean force);

    /**
     * Turn on/off compressed integers.
     *
     * @param compressed true if compressed integers should be used, false
     *        otherwise.
     *
     * @throws IllegalStateException if state can't be changed.
     */
    public static native void setCompressedIntegers(boolean compressed) throws IllegalStateException;

    /**
     * Set stack depth.
     *
     * @param depth
     *
     * @throws IllegalArgumentException if not within a valid range
     * @throws IllegalStateException if depth can't be changed
     */
    public static native void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException;

    /**
     * Turn on stack trace for an event
     *
     * @param eventTypeId the event id
     *
     * @param enabled if stack traces should be enabled
     */
    public static native void setStackTraceEnabled(long eventTypeId, boolean enabled);

    /**
     * Set thread buffer size.
     *
     * @param size
     *
     * @throws IllegalArgumentException if size is not within a valid range
     * @throws IllegalStateException if size can't be changed
     */
    public static native void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException;

    /**
     * Set threshold for event,
     *
     * Long.MAXIMUM_VALUE = no limit
     *
     * @param eventTypeId the id of the event type
     * @param ticks threshold in ticks,
     * @return true, if it could be set
     */
    public static native boolean setThreshold(long eventTypeId, long ticks);

    /**
     * Store the metadata descriptor that is to be written at the end of a
     * chunk, data should be written after GMT offset and size of metadata event
     * should be adjusted
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     *
     * @param bytes binary representation of metadata descriptor
     */
    public static native void storeMetadataDescriptor(byte[] bytes);

    /**
     * If the JVM supports JVM TI and retransformation has not been disabled this
     * method will return true. This flag can not change during the lifetime of
     * the JVM.
     *
     * @return if transform is allowed
     */
    public static native boolean getAllowedToDoEventRetransforms();

    /**
     * Set up native resources, data structures, threads etc. for JFR
     *
     * @param simulateFailure simulate a initialization failure and rollback in
     *        native, used for testing purposes
     *
     * @throws IllegalStateException if native part of JFR could not be created.
     *
     */
    static native boolean createJFR(boolean simulateFailure) throws IllegalStateException;

    /**
     * Destroys native part of JFR. If already destroy, call is ignored.
     *
     * Requires that JFR has been started with {@link #createNativeJFR()}
     *
     * @return if an instance was actually destroyed.
     *
     */
    static native boolean destroyJFR();

    /**
     * Cheap test to check if JFR functionality is available.
     *
     * @return
     */
    public static native boolean isAvailable();

    /**
     * To convert ticks to wall clock time.
     */
    public static native double getTimeConversionFactor();

    /**
     * Return a unique identifier for a class. Compared to {@link #getClassId(Class)},
     * this method does not tag the class as being "in-use".
     *
     * @param clazz class
     *
     * @return a unique class identifier
     */
    public static native long getTypeId(Class<?> clazz);

    /**
     * Fast path fetching the EventWriter using VM intrinsics
     *
     * @return thread local EventWriter
     */
    @IntrinsicCandidate
    public static native EventWriter getEventWriter();

    /**
     * Create a new EventWriter
     *
     * @return thread local EventWriter
     */
    public static native EventWriter newEventWriter();

    /**
     * Flushes the EventWriter for this thread.
     */
    public static native void flush(EventWriter writer, int uncommittedSize, int requestedSize);

    /**
     * Commits an event to the underlying buffer by setting the nextPosition.
     *
     * @param nextPosition
     *
     * @return the next startPosition
     */
    @IntrinsicCandidate
    public static native long commit(long nextPosition);

    /**
     * Flushes all thread buffers to disk and the constant pool data needed to read
     * them.
     * <p>
     * When the method returns, the chunk header should be updated with valid
     * pointers to the metadata event, last check point event, correct file size and
     * the generation id.
     *
     */
    public static native void flush();

    /**
     * Sets the location of the disk repository.
     *
     * @param dirText
     */
    public static native void setRepositoryLocation(String dirText);

    /**
     * Sets the path to emergency dump.
     *
     * @param dumpPathText
     */
    public static native void setDumpPath(String dumpPathText);

    /**
     * Gets the path to emergency dump.
     *
     * @return The path to emergency dump.
     */
    public static native String getDumpPath();

   /**
    * Access to VM termination support.
    *
    * @param errorMsg descriptive message to be include in VM termination sequence
    */
    public static native void abort(String errorMsg);

    /**
     * Adds a string to the string constant pool.
     *
     * If the same string is added twice, two entries will be created.
     *
     * @param id identifier associated with the string, not negative
     *
     * @param s string constant to be added, not null
     *
     * @return true, if the string was successfully added.
     */
    public static native boolean addStringConstant(long id, String s);

    public static native void uncaughtException(Thread thread, Throwable t);

    /**
     * Sets cutoff for event.
     *
     * Determines how long the event should be allowed to run.
     *
     * Long.MAXIMUM_VALUE = no limit
     *
     * @param eventTypeId the id of the event type
     * @param cutoffTicks cutoff in ticks,
     * @return true, if it could be set
     */
    public static native boolean setCutoff(long eventTypeId, long cutoffTicks);

    /**
     * Sets the event emission rate in event sample size per time unit.
     *
     * Determines how events are throttled.
     *
     * @param eventTypeId the id of the event type
     * @param eventSampleSize event sample size
     * @param period_ms time period in milliseconds
     * @return true, if it could be set
     */
    public static native boolean setThrottle(long eventTypeId, long eventSampleSize, long period_ms);

    /**
     * Emit old object sample events.
     *
     * @param cutoff the cutoff in ticks
     * @param emitAll emit all samples in old object queue
     * @param skipBFS don't use BFS when searching for path to GC root
     */
    public static native void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS);

    /**
     * Test if a chunk rotation is warranted.
     *
     * @return if it is time to perform a chunk rotation
     */
    public static native boolean shouldRotateDisk();

    /**
     * Exclude a thread from the jfr system
     *
     */
    public static native void exclude(Thread thread);

    /**
     * Include a thread back into the jfr system
     *
     */
    public static native void include(Thread thread);

    /**
     * Test if a thread is currently excluded from the jfr system.
     *
     * @return is thread currently excluded
     */
    public static native boolean isExcluded(Thread thread);

    /**
     * Test if a class is excluded from the jfr system.
     *
     * @param eventClass the class, not {@code null}
     *
     * @return is class excluded
     */
    public static native boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass);

    /**
     * Test if a class is instrumented.
     *
     * @param eventClass the class, not {@code null}
     *
     * @return is class instrumented
     */
    public static native boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass);

    /**
     * Get the start time in nanos from the header of the current chunk
     *
     * @return start time of the recording in nanos, -1 in case of in-memory
     */
    public static native long getChunkStartNanos();

    /**
     * Stores an EventConfiguration to the configuration field of an event class.
     *
     * @param eventClass the class, not {@code null}
     *
     * @param configuration the configuration, may be {@code null}
     *
     * @return if the field could be set
     */
    public static native boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, EventConfiguration configuration);

    /**
     * Retrieves the EventConfiguration for an event class.
     *
     * @param eventClass the class, not {@code null}
     *
     * @return the configuration, may be {@code null}
     */
    public static native Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass);

    /**
     * Returns the id for the Java types defined in metadata.xml.
     *
     * @param name the name of the type
     *
     * @return the id, or a negative value if it does not exists.
     */
    public static native long getTypeId(String name);

    /**
     * Returns {@code true}, if the JVM is running in a container, {@code false} otherwise.
     * <p>
     * If -XX:-UseContainerSupport has been specified, this method returns {@code false},
     * which is questionable, but Container.metrics() returns {@code null}, so events
     * can't be emitted anyway.
     */
    public static native boolean isContainerized();

    /**
     * Returns the total amount of memory of the host system whether or not this
     * JVM runs in a container.
     */
    public static native long hostTotalMemory();

    /**
     * Returns the total amount of swap memory of the host system whether or not this
     * JVM runs in a container.
     */
    public static native long hostTotalSwapMemory();

    /**
     * Emit a jdk.DataLoss event for the specified amount of bytes.
     *
     * @param bytes number of bytes that were lost
     */
    public static native void emitDataLoss(long bytes);

    /**
     * Registers stack filters that should be used with getStackTrace(int, long)
     * <p>
     * Method name at an array index is for class at the same array index.
     * <p>
     * This method should be called holding the MetadataRepository lock and before
     * bytecode for the associated event class has been added.
     *
     * @param classes, name of classes, for example {"java/lang/String"}, not
     *                 {@code null}
     * @param methods, name of method, for example {"toString"}, not {@code null}
     *
     * @return an ID that can be used to unregister the start frames, or -1 if it could not be registered
     */
    public static native long registerStackFilter(String[] classes, String[] methods);

    /**
     * Unregisters a set of stack filters.
     * <p>
     * This method should be called holding the MetadataRepository lock and after
     * the associated event class has been unloaded.
     *
     * @param stackFilterId the stack filter ID to unregister
     */
    public static native void unregisterStackFilter(long stackFilterId);

    /**
     * Sets bits used for event settings, like cutoff(ticks) and level
     *
     * @param eventTypeId the id of the event type
     * @param value
     */
    public static native void setMiscellaneous(long eventTypeId, long value);
}
