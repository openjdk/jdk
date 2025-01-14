/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.LogLevel.ERROR;
import static jdk.jfr.internal.LogLevel.INFO;
import static jdk.jfr.internal.LogLevel.TRACE;
import static jdk.jfr.internal.LogLevel.WARN;
import static jdk.jfr.internal.LogTag.JFR;
import static jdk.jfr.internal.LogTag.JFR_SYSTEM;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.events.ActiveRecordingEvent;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.internal.consumer.EventLog;
import jdk.jfr.internal.periodic.PeriodicEvents;
import jdk.jfr.internal.util.Utils;

public final class PlatformRecorder {


    private final ArrayList<PlatformRecording> recordings = new ArrayList<>();
    private static final List<FlightRecorderListener> changeListeners = new ArrayList<>();
    private final Repository repository;
    private final Thread shutdownHook;

    private Timer timer;
    private long recordingCounter = 0;
    private RepositoryChunk currentChunk;
    private boolean inShutdown;
    private boolean runPeriodicTask;

    public PlatformRecorder() throws Exception {
        repository = Repository.getRepository();
        Logger.log(JFR_SYSTEM, INFO, "Initialized disk repository");
        repository.ensureRepository();
        JVMSupport.createJFR();
        Logger.log(JFR_SYSTEM, INFO, "Created native");
        JDKEvents.initialize();
        Logger.log(JFR_SYSTEM, INFO, "Registered JDK events");
        startDiskMonitor();
        shutdownHook = new ShutdownHook(this);
        shutdownHook.setUncaughtExceptionHandler(new ShutdownHook.ExceptionHandler());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized PlatformRecording newRecording(Map<String, String> settings) {
        return newRecording(settings, ++recordingCounter);
    }

    // To be used internally when doing dumps.
    // Caller must have recorder lock and close recording before releasing lock
    public PlatformRecording newTemporaryRecording() {
        if(!Thread.holdsLock(this)) {
            throw new InternalError("Caller must have recorder lock");
        }
        return newRecording(new HashMap<>(), 0);
    }

    private synchronized PlatformRecording newRecording(Map<String, String> settings, long id) {
        PlatformRecording recording = new PlatformRecording(this, id);
        if (!settings.isEmpty()) {
            recording.setSettings(settings);
        }
        recordings.add(recording);
        return recording;
    }

    synchronized void finish(PlatformRecording recording) {
        if (recording.getState() == RecordingState.RUNNING) {
            recording.stop("Recording closed");
        }
        recordings.remove(recording);
    }

    public synchronized List<PlatformRecording> getRecordings() {
        return Collections.unmodifiableList(new ArrayList<PlatformRecording>(recordings));
    }

    public static synchronized void addListener(FlightRecorderListener changeListener) {
        boolean runInitialized;
        synchronized (PlatformRecorder.class) {
            runInitialized = FlightRecorder.isInitialized();
            changeListeners.add(changeListener);
        }
        if (runInitialized) {
            changeListener.recorderInitialized(FlightRecorder.getFlightRecorder());
        }
    }

    public static synchronized boolean removeListener(FlightRecorderListener changeListener) {
        return changeListeners.remove(changeListener);
    }

    static synchronized List<FlightRecorderListener> getListeners() {
        return new ArrayList<>(changeListeners);
    }

    synchronized Timer getTimer() {
        if (timer == null) {
            timer = new Timer("JFR Recording Scheduler", true);
        }
        return timer;
    }

    public static void notifyRecorderInitialized(FlightRecorder recorder) {
        Logger.log(JFR_SYSTEM, TRACE, "Notifying listeners that Flight Recorder is initialized");
        for (FlightRecorderListener r : getListeners()) {
            r.recorderInitialized(recorder);
        }
    }

    synchronized void setInShutDown() {
        this.inShutdown = true;
    }

    // called by shutdown hook
    synchronized void destroy() {
        try {
            if (timer != null) {
                timer.cancel();
            }
        } catch (Exception ex) {
            Logger.log(JFR_SYSTEM, WARN, "Shutdown hook could not cancel timer");
        }

        for (PlatformRecording p : getRecordings()) {
            if (p.getState() == RecordingState.RUNNING) {
                try {
                    p.stop("Shutdown");
                } catch (Exception ex) {
                    Logger.log(JFR, WARN, "Recording " + p.getName() + ":" + p.getId() + " could not be stopped");
                }
            }
        }

        JDKEvents.remove();

        if (JVMSupport.hasJFR()) {
            if (JVM.isRecording()) {
                JVM.endRecording();
            }
            JVMSupport.destroyJFR();
        }
        repository.clear();
    }

    synchronized long start(PlatformRecording recording) {
        // State can only be NEW or DELAYED because of previous checks
        Instant startTime = null;
        boolean toDisk = recording.isToDisk();
        boolean beginPhysical = true;
        long streamInterval = recording.getStreamIntervalMillis();
        for (PlatformRecording s : getRecordings()) {
            if (s.getState() == RecordingState.RUNNING) {
                beginPhysical = false;
                if (s.isToDisk()) {
                    toDisk = true;
                }
                streamInterval = Math.min(streamInterval, s.getStreamIntervalMillis());
            }
        }
        long startNanos = -1;
        if (beginPhysical) {
            RepositoryChunk newChunk = null;
            if (toDisk) {
                newChunk = repository.newChunk();
                if (EventLog.shouldLog()) {
                    EventLog.start();
                }
                MetadataRepository.getInstance().setOutput(newChunk.getFile().toString());
            } else {
                MetadataRepository.getInstance().setOutput(null);
            }
            currentChunk = newChunk;
            JVM.beginRecording();
            startNanos = JVMSupport.getChunkStartNanos();
            startTime = Utils.epochNanosToInstant(startNanos);
            if (currentChunk != null) {
                currentChunk.setStartTime(startTime);
            }
            recording.setState(RecordingState.RUNNING);
            updateSettings(false);
            recording.setStartTime(startTime);
            writeMetaEvents();
            setRunPeriodicTask(true);
        } else {
            RepositoryChunk newChunk = null;
            if (toDisk) {
                newChunk = repository.newChunk();
                if (EventLog.shouldLog()) {
                    EventLog.start();
                }
                PeriodicEvents.doChunkEnd();
                String p = newChunk.getFile().toString();
                startTime = MetadataRepository.getInstance().setOutput(p);
                newChunk.setStartTime(startTime);
            }
            startNanos = JVMSupport.getChunkStartNanos();
            startTime = Utils.epochNanosToInstant(startNanos);
            recording.setStartTime(startTime);
            recording.setState(RecordingState.RUNNING);
            updateSettings(false);
            writeMetaEvents();
            if (currentChunk != null) {
                finishChunk(currentChunk, startTime, recording);
            }
            currentChunk = newChunk;
        }
        if (toDisk) {
            PeriodicEvents.setFlushInterval(streamInterval);
        }
        PeriodicEvents.doChunkBegin();
        Duration duration = recording.getDuration();
        if (duration != null) {
            recording.setStopTime(startTime.plus(duration));
        }
        recording.updateTimer();
        return startNanos;
    }

    synchronized void stop(PlatformRecording recording) {
        RecordingState state = recording.getState();
        Instant stopTime;

        if (Utils.isAfter(state, RecordingState.RUNNING)) {
            throw new IllegalStateException("Can't stop an already stopped recording.");
        }
        if (Utils.isBefore(state, RecordingState.RUNNING)) {
            throw new IllegalStateException("Recording must be started before it can be stopped.");
        }
        boolean toDisk = false;
        boolean endPhysical = true;
        long streamInterval = Long.MAX_VALUE;
        for (PlatformRecording s : getRecordings()) {
            RecordingState rs = s.getState();
            if (s != recording && RecordingState.RUNNING == rs) {
                endPhysical = false;
                if (s.isToDisk()) {
                    toDisk = true;
                }
                streamInterval = Math.min(streamInterval, s.getStreamIntervalMillis());
            }
        }
        OldObjectSample.emit(recording);
        recording.setFinalStartnanos(JVMSupport.getChunkStartNanos());

        if (endPhysical) {
            PeriodicEvents.doChunkEnd();
            if (recording.isToDisk()) {
                if (inShutdown) {
                    JVM.markChunkFinal();
                }
                stopTime = MetadataRepository.getInstance().setOutput(null);
                finishChunk(currentChunk, stopTime, null);
                currentChunk = null;
            } else {
                // last memory
                stopTime = dumpMemoryToDestination(recording);
            }
            JVM.endRecording();
            recording.setStopTime(stopTime);
            disableEvents();
            setRunPeriodicTask(false);
        } else {
            RepositoryChunk newChunk = null;
            PeriodicEvents.doChunkEnd();
            updateSettingsButIgnoreRecording(recording, false);

            String path = null;
            if (toDisk) {
                newChunk = repository.newChunk();
                path = newChunk.getFile().toString();
            }
            stopTime = MetadataRepository.getInstance().setOutput(path);
            if (toDisk) {
                newChunk.setStartTime(stopTime);
            }
            recording.setStopTime(stopTime);
            writeMetaEvents();
            if (currentChunk != null) {
                finishChunk(currentChunk, stopTime, null);
            }
            currentChunk = newChunk;
            PeriodicEvents.doChunkBegin();
        }

        if (toDisk) {
            PeriodicEvents.setFlushInterval(streamInterval);
        } else {
            PeriodicEvents.setFlushInterval(Long.MAX_VALUE);
        }
        recording.setState(RecordingState.STOPPED);
        if (!isToDisk()) {
            EventLog.stop();
        }
    }

    private Instant dumpMemoryToDestination(PlatformRecording recording)  {
        WriteablePath dest = recording.getDestination();
        if (dest != null) {
            Instant t = MetadataRepository.getInstance().setOutput(dest.getRealPathText());
            recording.clearDestination();
            return t;
        }
        return Instant.now();
    }
    private void disableEvents() {
        MetadataRepository.getInstance().disableEvents();
    }

    void updateSettings(boolean writeSettingEvents) {
        updateSettingsButIgnoreRecording(null, writeSettingEvents);
    }

    void updateSettingsButIgnoreRecording(PlatformRecording ignoreMe, boolean writeSettingEvents) {
        List<PlatformRecording> recordings = getRunningRecordings();
        List<Map<String, String>> list = new ArrayList<>(recordings.size());
        for (PlatformRecording r : recordings) {
            if (r != ignoreMe) {
                list.add(r.getSettings());
            }
        }
        MetadataRepository.getInstance().setSettings(list, writeSettingEvents);
    }



    synchronized void rotateDisk() {
        RepositoryChunk newChunk = repository.newChunk();
        PeriodicEvents.doChunkEnd();
        String path = newChunk.getFile().toString();
        Instant timestamp = MetadataRepository.getInstance().setOutput(path);
        newChunk.setStartTime(timestamp);
        writeMetaEvents();
        if (currentChunk != null) {
            finishChunk(currentChunk, timestamp, null);
        }
        currentChunk = newChunk;
        PeriodicEvents.doChunkBegin();
    }

    private List<PlatformRecording> getRunningRecordings() {
        List<PlatformRecording> runningRecordings = new ArrayList<>();
        for (PlatformRecording recording : getRecordings()) {
            if (recording.getState() == RecordingState.RUNNING) {
                runningRecordings.add(recording);
            }
        }
        return runningRecordings;
    }

    public List<RepositoryChunk> makeChunkList(Instant startTime, Instant endTime) {
        Set<RepositoryChunk> chunkSet = new HashSet<>();
        for (PlatformRecording r : getRecordings()) {
            chunkSet.addAll(r.getChunks());
        }
        if (chunkSet.size() > 0) {
            List<RepositoryChunk> chunks = new ArrayList<>(chunkSet.size());
            for (RepositoryChunk rc : chunkSet) {
                if (rc.inInterval(startTime, endTime)) {
                    chunks.add(rc);
                }
            }
            // n*log(n), should be able to do n*log(k) with a priority queue,
            // where k = number of recordings, n = number of chunks
            chunks.sort(RepositoryChunk.END_TIME_COMPARATOR);
            return chunks;
        }

        return new ArrayList<>();
    }

    private void startDiskMonitor() {
        Thread t = new Thread(() -> periodicTask(), "JFR Periodic Tasks");
        t.setDaemon(true);
        t.start();
    }

    private void finishChunk(RepositoryChunk chunk, Instant time, PlatformRecording ignoreMe) {
        if (chunk.finish(time)) {
            for (PlatformRecording r : getRecordings()) {
                if (r != ignoreMe && r.getState() == RecordingState.RUNNING) {
                    r.appendChunk(chunk);
                }
            }
        } else {
            if (chunk.isMissingFile()) {
                // With one chunkfile found missing, its likely more could've been removed too. Iterate through all recordings,
                // and check for missing files. This will emit more error logs that can be seen in subsequent recordings.
                for (PlatformRecording r : getRecordings()) {
                    r.removeNonExistantPaths();
                }
            }
        }
        // Decrease initial reference count
        chunk.release();
        FilePurger.purge();
    }

    private void writeMetaEvents() {
        long timestamp = JVM.counterTime();
        if (ActiveRecordingEvent.enabled()) {
            for (PlatformRecording r : getRecordings()) {
                if (r.getState() == RecordingState.RUNNING && r.shouldWriteMetadataEvent()) {
                    WriteablePath path = r.getDestination();
                    Duration age = r.getMaxAge();
                    Duration flush = r.getFlushInterval();
                    Long size = r.getMaxSize();
                    Instant rStart = r.getStartTime();
                    Duration rDuration = r.getDuration();
                    ActiveRecordingEvent.commit(
                        timestamp,
                        r.getId(),
                        r.getName(),
                        path == null ? null : path.getRealPathText(),
                        r.isToDisk(),
                        age == null ? Long.MAX_VALUE : age.toMillis(),
                        flush == null ? Long.MAX_VALUE : flush.toMillis(),
                        size == null ? Long.MAX_VALUE : size,
                        rStart == null ? Long.MAX_VALUE : rStart.toEpochMilli(),
                        rDuration == null ? Long.MAX_VALUE : rDuration.toMillis()
                    );
                }
            }
        }
        if (ActiveSettingEvent.enabled()) {
            for (EventControl ec : MetadataRepository.getInstance().getEventControls()) {
                ec.writeActiveSettingEvent(timestamp);
            }
        }
    }

    private void periodicTask() {
        if (!JVMSupport.hasJFR()) {
            return;
        }
        while (true) {
            long wait = Options.getWaitInterval();
            try {
                synchronized (this) {
                    if (JVM.shouldRotateDisk()) {
                        rotateDisk();
                    }
                    if (isToDisk()) {
                        EventLog.update();
                    }
                }
                long minDelta = PeriodicEvents.doPeriodic();
                wait = Math.min(minDelta, Options.getWaitInterval());
            } catch (Throwable t) {
                // Catch everything and log, but don't allow it to end the periodic task
                Logger.log(JFR_SYSTEM, WARN, "Error in Periodic task: " + t.getMessage());
            } finally {
                takeNap(wait);
            }
        }
    }

    private boolean isToDisk() {
        // Use indexing to avoid Iterator allocation if nothing happens
        int count = recordings.size();
        for (int i = 0; i < count; i++) {
            PlatformRecording r = recordings.get(i);
            if (r.isToDisk() && r.getState() == RecordingState.RUNNING) {
                return true;
            }
        }
        return false;
    }

    private void setRunPeriodicTask(boolean runPeriodicTask) {
        synchronized (JVM.CHUNK_ROTATION_MONITOR) {
            this.runPeriodicTask = runPeriodicTask;
            if (runPeriodicTask) {
                JVM.CHUNK_ROTATION_MONITOR.notifyAll();
            }
        }
    }

    private void takeNap(long duration) {
        try {
            synchronized (JVM.CHUNK_ROTATION_MONITOR) {
                if (!runPeriodicTask) {
                    duration = Long.MAX_VALUE;
                }
                JVM.CHUNK_ROTATION_MONITOR.wait(duration < 10 ? 10 : duration);
            }
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    synchronized Recording newCopy(PlatformRecording r, boolean stop) {
        Recording newRec = new Recording();
        PlatformRecording copy = PrivateAccess.getInstance().getPlatformRecording(newRec);
        copy.setSettings(r.getSettings());
        copy.setMaxAge(r.getMaxAge());
        copy.setMaxSize(r.getMaxSize());
        copy.setDumpOnExit(r.getDumpOnExit());
        copy.setName("Clone of " + r.getName());
        copy.setToDisk(r.isToDisk());
        copy.setInternalDuration(r.getDuration());
        copy.setStartTime(r.getStartTime());
        copy.setStopTime(r.getStopTime());
        copy.setFlushInterval(r.getFlushInterval());

        if (r.getState() == RecordingState.NEW) {
            return newRec;
        }
        if (r.getState() == RecordingState.DELAYED) {
            copy.scheduleStart(r.getStartTime());
            return newRec;
        }
        copy.setState(r.getState());
        // recording has started, copy chunks
        for (RepositoryChunk c : r.getChunks()) {
            copy.add(c);
        }
        if (r.getState() == RecordingState.RUNNING) {
            if (stop) {
                copy.stop("Stopped when cloning recording '" + r.getName() + "'");
            } else {
                if (r.getStopTime() != null) {
                    TimerTask stopTask = copy.createStopTask();
                    copy.setStopTask(copy.createStopTask());
                    getTimer().schedule(stopTask, r.getStopTime().toEpochMilli());
                }
            }
        }
        return newRec;
    }

    public synchronized void fillWithRecordedData(PlatformRecording target, Boolean pathToGcRoots) {
        boolean running = false;
        boolean toDisk = false;

        for (PlatformRecording r : recordings) {
            if (r.getState() == RecordingState.RUNNING) {
                running = true;
                if (r.isToDisk()) {
                    toDisk = true;
                }
            }
        }
        // If needed, flush data from memory
        if (running) {
            if (toDisk) {
                OldObjectSample.emit(recordings, pathToGcRoots);
                rotateDisk();
            } else {
                try (PlatformRecording snapshot = newTemporaryRecording()) {
                    snapshot.setToDisk(true);
                    snapshot.setShouldWriteActiveRecordingEvent(false);
                    snapshot.start();
                    OldObjectSample.emit(recordings, pathToGcRoots);
                    snapshot.stop("Snapshot dump");
                    fillWithDiskChunks(target);
                }
                return;
            }
        }
        fillWithDiskChunks(target);
    }

    private void fillWithDiskChunks(PlatformRecording target) {
        for (RepositoryChunk c : makeChunkList(null, null)) {
            target.add(c);
        }
        target.setState(RecordingState.STOPPED);
        Instant startTime = null;
        Instant endTime = null;

        for (RepositoryChunk c : target.getChunks()) {
            if (startTime == null || c.getStartTime().isBefore(startTime)) {
                startTime = c.getStartTime();
            }
            if (endTime == null || c.getEndTime().isAfter(endTime)) {
                endTime = c.getEndTime();
            }
        }
        Instant now = Instant.now();
        if (startTime == null) {
            startTime = now;
        }
        if (endTime == null) {
            endTime = now;
        }
        target.setStartTime(startTime);
        target.setStopTime(endTime);
        target.setInternalDuration(startTime.until(endTime));
    }

    public synchronized void migrate(Path repo) throws IOException {
        // Must set repository while holding recorder lock so
        // the final chunk in repository gets marked correctly
        Repository.getRepository().setBasePath(repo);
        boolean disk = false;
        for (PlatformRecording s : getRecordings()) {
            if (RecordingState.RUNNING == s.getState() && s.isToDisk()) {
                disk = true;
            }
        }
        if (disk) {
            JVM.markChunkFinal();
            rotateDisk();
        }
    }

    public RepositoryChunk getCurrentChunk() {
        return currentChunk;
    }
}
