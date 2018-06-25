/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.LogLevel.INFO;
import static jdk.jfr.internal.LogLevel.TRACE;
import static jdk.jfr.internal.LogLevel.WARN;
import static jdk.jfr.internal.LogTag.JFR;
import static jdk.jfr.internal.LogTag.JFR_SYSTEM;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
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
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.events.ActiveRecordingEvent;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.internal.SecuritySupport.SecureRecorderListener;
import jdk.jfr.internal.instrument.JDKEvents;

public final class PlatformRecorder {

    private final List<PlatformRecording> recordings = new ArrayList<>();
    private final static List<SecureRecorderListener> changeListeners = new ArrayList<>();
    private final Repository repository;
    private final Timer timer;
    private final static JVM jvm = JVM.getJVM();
    private final EventType activeRecordingEvent;
    private final EventType activeSettingEvent;
    private final Thread shutdownHook;

    private long recordingCounter = 0;
    private RepositoryChunk currentChunk;

    public PlatformRecorder() throws Exception {
        repository = Repository.getRepository();
        Logger.log(JFR_SYSTEM, INFO, "Initialized disk repository");
        repository.ensureRepository();
        jvm.createNativeJFR();
        Logger.log(JFR_SYSTEM, INFO, "Created native");
        JDKEvents.initialize();
        Logger.log(JFR_SYSTEM, INFO, "Registered JDK events");
        JDKEvents.addInstrumentation();
        startDiskMonitor();
        SecuritySupport.registerEvent(ActiveRecordingEvent.class);
        activeRecordingEvent = EventType.getEventType(ActiveRecordingEvent.class);
        SecuritySupport.registerEvent(ActiveSettingEvent.class);
        activeSettingEvent = EventType.getEventType(ActiveSettingEvent.class);
        shutdownHook = SecuritySupport.createThreadWitNoPermissions("JFR: Shutdown Hook", new ShutdownHook(this));
        SecuritySupport.setUncaughtExceptionHandler(shutdownHook, new ShutdownHook.ExceptionHandler());
        SecuritySupport.registerShutdownHook(shutdownHook);
        timer = createTimer();
    }

    private static Timer createTimer() {
        try {
            List<Timer> result = new CopyOnWriteArrayList<>();
            Thread t = SecuritySupport.createThreadWitNoPermissions("Permissionless thread", ()-> {
                result.add(new Timer("JFR Recording Scheduler", true));
            });
            t.start();
            t.join();
            return result.get(0);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Not able to create timer task. " + e.getMessage(), e);
        }
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

    public synchronized static void addListener(FlightRecorderListener changeListener) {
        AccessControlContext context = AccessController.getContext();
        SecureRecorderListener sl = new SecureRecorderListener(context, changeListener);
        boolean runInitialized;
        synchronized (PlatformRecorder.class) {
            runInitialized = FlightRecorder.isInitialized();
            changeListeners.add(sl);
        }
        if (runInitialized) {
            sl.recorderInitialized(FlightRecorder.getFlightRecorder());
        }
    }

    public synchronized static boolean removeListener(FlightRecorderListener changeListener) {
        for (SecureRecorderListener s : new ArrayList<>(changeListeners)) {
            if (s.getChangeListener() == changeListener) {
                changeListeners.remove(s);
                return true;
            }
        }
        return false;
    }

    static synchronized List<FlightRecorderListener> getListeners() {
        return new ArrayList<>(changeListeners);
    }

    Timer getTimer() {
        return timer;
    }

    public static void notifyRecorderInitialized(FlightRecorder recorder) {
        Logger.log(JFR_SYSTEM, TRACE, "Notifying listeners that Flight Recorder is initialized");
        for (FlightRecorderListener r : getListeners()) {
            r.recorderInitialized(recorder);
        }
    }

    // called by shutdown hook
    synchronized void destroy() {
        try {
            timer.cancel();
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

        if (jvm.hasNativeJFR()) {
            if (jvm.isRecording()) {
                jvm.endRecording_();
            }
            jvm.destroyNativeJFR();
        }
        repository.clear();
    }

    synchronized void start(PlatformRecording recording) {
        // State can only be NEW or DELAYED because of previous checks
        Instant now = Instant.now();
        recording.setStartTime(now);
        recording.updateTimer();
        Duration duration = recording.getDuration();
        if (duration != null) {
            recording.setStopTime(now.plus(duration));
        }
        boolean toDisk = recording.isToDisk();
        boolean beginPhysical = true;
        for (PlatformRecording s : getRecordings()) {
            if (s.getState() == RecordingState.RUNNING) {
                beginPhysical = false;
                if (s.isToDisk()) {
                    toDisk = true;
                }
            }
        }
        if (beginPhysical) {
            RepositoryChunk newChunk = null;
            if (toDisk) {
                newChunk = repository.newChunk(now);
                MetadataRepository.getInstance().setOutput(newChunk.getUnfishedFile().toString());
            } else {
                MetadataRepository.getInstance().setOutput(null);
            }
            currentChunk = newChunk;
            jvm.beginRecording_();
            recording.setState(RecordingState.RUNNING);
            updateSettings();
            writeMetaEvents();
        } else {
            RepositoryChunk newChunk = null;
            if (toDisk) {
                newChunk = repository.newChunk(now);
                RequestEngine.doChunkEnd();
                MetadataRepository.getInstance().setOutput(newChunk.getUnfishedFile().toString());
            }
            recording.setState(RecordingState.RUNNING);
            updateSettings();
            writeMetaEvents();
            if (currentChunk != null) {
                finishChunk(currentChunk, now, recording);
            }
            currentChunk = newChunk;
        }

        RequestEngine.doChunkBegin();
    }

    synchronized void stop(PlatformRecording recording) {
        RecordingState state = recording.getState();

        if (Utils.isAfter(state, RecordingState.RUNNING)) {
            throw new IllegalStateException("Can't stop an already stopped recording.");
        }
        if (Utils.isBefore(state, RecordingState.RUNNING)) {
            throw new IllegalStateException("Recording must be started before it can be stopped.");
        }
        Instant now = Instant.now();
        boolean toDisk = false;
        boolean endPhysical = true;
        for (PlatformRecording s : getRecordings()) {
            RecordingState rs = s.getState();
            if (s != recording && RecordingState.RUNNING == rs) {
                endPhysical = false;
                if (s.isToDisk()) {
                    toDisk = true;
                }
            }
        }
        OldObjectSample.emit(recording);

        if (endPhysical) {
            RequestEngine.doChunkEnd();
            if (recording.isToDisk()) {
                if (currentChunk != null) {
                    MetadataRepository.getInstance().setOutput(null);
                    finishChunk(currentChunk, now, null);
                    currentChunk = null;
                }
            } else {
                // last memory
                dumpMemoryToDestination(recording);
            }
            jvm.endRecording_();
            disableEvents();
        } else {
            RepositoryChunk newChunk = null;
            RequestEngine.doChunkEnd();
            updateSettingsButIgnoreRecording(recording);
            if (toDisk) {
                newChunk = repository.newChunk(now);
                MetadataRepository.getInstance().setOutput(newChunk.getUnfishedFile().toString());
            } else {
                MetadataRepository.getInstance().setOutput(null);
            }
            writeMetaEvents();
            if (currentChunk != null) {
                finishChunk(currentChunk, now, null);
            }
            currentChunk = newChunk;
            RequestEngine.doChunkBegin();
        }
        recording.setState(RecordingState.STOPPED);
    }

    private void dumpMemoryToDestination(PlatformRecording recording)  {
        WriteableUserPath dest = recording.getDestination();
        if (dest != null) {
            MetadataRepository.getInstance().setOutput(dest.getText());
            recording.clearDestination();
        }
    }
    private void disableEvents() {
        MetadataRepository.getInstance().disableEvents();
    }

    void updateSettings() {
        updateSettingsButIgnoreRecording(null);
    }

    void updateSettingsButIgnoreRecording(PlatformRecording ignoreMe) {
        List<PlatformRecording> recordings = getRunningRecordings();
        List<Map<String, String>> list = new ArrayList<>(recordings.size());
        for (PlatformRecording r : recordings) {
            if (r != ignoreMe) {
                list.add(r.getSettings());
            }
        }
        MetadataRepository.getInstance().setSettings(list);
    }

    synchronized void rotateDisk() {
        Instant now = Instant.now();
        RepositoryChunk newChunk = repository.newChunk(now);
        RequestEngine.doChunkEnd();
        MetadataRepository.getInstance().setOutput(newChunk.getUnfishedFile().toString());
        writeMetaEvents();
        if (currentChunk != null) {
            finishChunk(currentChunk, now, null);
        }
        currentChunk = newChunk;
        RequestEngine.doChunkBegin();
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

    private List<RepositoryChunk> makeChunkList(Instant startTime, Instant endTime) {
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
            Collections.sort(chunks, RepositoryChunk.END_TIME_COMPARATOR);
            return chunks;
        }

        return Collections.emptyList();
    }

    private void startDiskMonitor() {
        Thread t = SecuritySupport.createThreadWitNoPermissions("JFR Periodic Tasks", () -> periodicTask());
        SecuritySupport.setDaemonThread(t, true);
        t.start();
    }

    private void finishChunk(RepositoryChunk chunk, Instant time, PlatformRecording ignoreMe) {
        chunk.finish(time);
        for (PlatformRecording r : getRecordings()) {
            if (r != ignoreMe && r.getState() == RecordingState.RUNNING) {
                r.appendChunk(chunk);
            }
        }
    }

    private void writeMetaEvents() {

        if (activeRecordingEvent.isEnabled()) {
            for (PlatformRecording r : getRecordings()) {
                if (r.getState() == RecordingState.RUNNING && r.shouldWriteMetadataEvent()) {
                    ActiveRecordingEvent event = new ActiveRecordingEvent();
                    event.id = r.getId();
                    event.name = r.getName();
                    WriteableUserPath p = r.getDestination();
                    event.destination = p == null ? null : p.getText();
                    Duration d = r.getDuration();
                    event.recordingDuration = d == null ? Long.MAX_VALUE : d.toMillis();
                    Duration age = r.getMaxAge();
                    event.maxAge = age == null ? Long.MAX_VALUE : age.toMillis();
                    Long size = r.getMaxSize();
                    event.maxSize = size == null ? Long.MAX_VALUE : size;
                    Instant start = r.getStartTime();
                    event.recordingStart = start == null ? Long.MAX_VALUE : start.toEpochMilli();
                    event.commit();
                }
            }
        }
        if (activeSettingEvent.isEnabled()) {
            for (EventControl ec : MetadataRepository.getInstance().getEventControls()) {
                ec.writeActiveSettingEvent();
            }
        }
    }

    private void periodicTask() {
        while (true) {
            synchronized (this) {
                if (!jvm.hasNativeJFR()) {
                    return;
                }
                if (currentChunk != null) {
                    try {
                        if (SecuritySupport.getFileSize(currentChunk.getUnfishedFile()) > Options.getMaxChunkSize()) {
                            rotateDisk();
                        }
                    } catch (IOException e) {
                        Logger.log(JFR_SYSTEM, WARN, "Could not check file size to determine chunk rotation");
                    }
                }
            }
            long minDelta = RequestEngine.doPeriodic();
            long wait = Math.min(minDelta, Options.getWaitInterval());
            takeNap(wait);
        }
    }

    private void takeNap(long duration) {
        try {
            synchronized (JVM.FILE_DELTA_CHANGE) {
                JVM.FILE_DELTA_CHANGE.wait(duration < 10 ? 10 : duration);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
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
        target.setInternalDuration(Duration.between(startTime, endTime));
    }
}
