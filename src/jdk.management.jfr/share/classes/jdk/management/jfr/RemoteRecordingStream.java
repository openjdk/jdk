/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.management.jfr;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.management.AttributeChangeNotification;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import jdk.jfr.Configuration;
import jdk.jfr.EventSettings;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.management.EventSettingsModifier;
import jdk.jfr.internal.management.ManagementSupport;
import jdk.jfr.internal.management.StreamBarrier;
import jdk.jfr.internal.management.EventByteStream;

/**
 * An implementation of an {@link EventStream} that can serialize events over
 * the network using an {@link MBeanServerConnection}.
 * <p>
 * The following example shows how to record garbage collection pauses and CPU
 * usage on a remote host and print the events to standard out.
 *
 * <pre>
 *     {@literal
 *     String host = "com.example";
 *     int port = 4711;
 *
 *     String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
 *
 *     JMXServiceURL u = new JMXServiceURL(url);
 *     JMXConnector c = JMXConnectorFactory.connect(u);
 *     MBeanServerConnection conn = c.getMBeanServerConnection();
 *
 *     try (var rs = new RemoteRecordingStream(conn)) {
 *         rs.enable("jdk.GCPhasePause").withoutThreshold();
 *         rs.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
 *         rs.onEvent("jdk.CPULoad", System.out::println);
 *         rs.onEvent("jdk.GCPhasePause", System.out::println);
 *         rs.start();
 *     }
 *     }
 * </pre>
 *
 * @since 16
 */
public final class RemoteRecordingStream implements EventStream {
    private static final String ENABLED = "enabled";

    static final class RemoteSettings implements EventSettingsModifier {

        private final FlightRecorderMXBean mbean;
        private final long recordingId;
        private final String identifier;

        RemoteSettings(FlightRecorderMXBean mbean, long recordingId, String identifier) {
            this.mbean = mbean;
            this.recordingId = recordingId;
            this.identifier = identifier;
        }

        @Override
        public void with(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            // FlightRecorderMXBean implementation always returns
            // new instance of Map so no need to create new here.
            Map<String, String> newSettings = getEventSettings();
            newSettings.put(identifier + "#" + name, value);
            mbean.setRecordingSettings(recordingId, newSettings);
        }

        @Override
        public Map<String, String> toMap() {
            return getEventSettings();
        }

        private Map<String, String> getEventSettings() {
            return mbean.getRecordingSettings(recordingId);
        }
    }

    // Reference to stream is released when EventStream::close is called
    static final class ChunkConsumer implements Consumer<Long> {

        private final DiskRepository repository;

        ChunkConsumer(DiskRepository repository) {
            this.repository = repository;
        }

        @Override
        public void accept(Long endNanos) {
            repository.onChunkComplete(endNanos);
        }
    }

    private static final ObjectName OBJECT_NAME = MBeanUtils.createObjectName();

    final Path path;
    final FlightRecorderMXBean mbean;
    final long recordingId;
    final EventStream stream;
    final DiskRepository repository;
    final Instant creationTime;
    private final ReentrantLock lock = new ReentrantLock();
    volatile Instant startTime;
    volatile Instant endTime;
    volatile boolean closed;
    volatile boolean stopped;
    // always guarded by lock
    private boolean started;
    private Duration maxAge;
    private long maxSize;
    private final MBeanServerConnection connection;
    private RemoteStoppedListener remoteStoppedListener;
    private RemoteClosedListener remoteClosedListener;

    /**
     * Creates an event stream that operates against a {@link MBeanServerConnection}
     * that has a registered {@link FlightRecorderMXBean}.
     * <p>
     * To configure event settings, use {@link #setSettings(Map)}.
     *
     * @param connection the {@code MBeanServerConnection} where the
     *                   {@code FlightRecorderMXBean} is registered, not
     *                   {@code null}
     *
     * @throws IOException       if a stream can't be opened, an I/O error occurs
     *                           when trying to access the repository or the
     *                           {@code FlightRecorderMXBean}
     */
    public RemoteRecordingStream(MBeanServerConnection connection) throws IOException {
        this(connection, makeTempDirectory(), true);
    }

    /**
     * Creates an event stream that operates against a {@link MBeanServerConnection}
     * that has a registered {@link FlightRecorderMXBean}.
     * <p>
     * To configure event settings, use {@link #setSettings(Map)}.
     *
     * @param connection the {@code MBeanServerConnection} where the
     *                   {@code FlightRecorderMXBean} is registered, not
     *                   {@code null}
     *
     * @param directory  the directory to store event data that is downloaded, not
     *                   {@code null}
     *
     * @throws IOException       if a stream can't be opened, an I/O error occurs
     *                           when trying to access the repository or the
     *                           {@code FlightRecorderMXBean}
     */
    public RemoteRecordingStream(MBeanServerConnection connection, Path directory) throws IOException {
        this(connection, directory, false);
    }

    private RemoteRecordingStream(MBeanServerConnection connection, Path directory, boolean delete) throws IOException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(directory, "directory");
        path = directory;
        if (!Files.exists(path)) {
            throw new IOException("Download directory doesn't exist");
        }

        if (!Files.isDirectory(path)) {
            throw new IOException("Download location must be a directory");
        }
        checkFileAccess(path);
        creationTime = Instant.now();
        this.connection = connection;
        mbean = createProxy(connection);
        recordingId = createRecording();
        stream = ManagementSupport.newEventDirectoryStream(path, configurations(mbean));
        stream.setStartTime(Instant.MIN);
        repository = new DiskRepository(path, delete);
        ManagementSupport.setOnChunkCompleteHandler(stream, new ChunkConsumer(repository));
    }

    private List<Configuration> configurations(FlightRecorderMXBean mbean) {
        List<ConfigurationInfo> cis = mbean.getConfigurations();
        List<Configuration> confs = new ArrayList<>(cis.size());
        for (ConfigurationInfo ci : cis) {
            confs.add(ManagementSupport.newConfiguration(ci.getName(), ci.getLabel(), ci.getDescription(),
                    ci.getProvider(), ci.getSettings(), ci.getContents()));
        }
        return Collections.unmodifiableList(confs);
    }

    @Override
    public void onMetadata(Consumer<MetadataEvent> action) {
        stream.onMetadata(action);
    }

    private static void checkFileAccess(Path directory) throws IOException {
        RandomAccessFile f = null;
        try {
            Path testFile = directory.resolve("test-access");
            f = new RandomAccessFile(testFile.toFile(), "rw");
            f.write(0);
            f.seek(0);
            f.read();
            f.close();
            Files.delete(testFile);
        } catch (Exception e) {
            closeSilently(f);
            throw new IOException("Could not read/write/delete in directory" + directory + " :" + e.getMessage());
        }
    }

    private static void closeSilently(RandomAccessFile f) {
        if (f == null) {
            return;
        }
        try {
            f.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    private static FlightRecorderMXBean createProxy(MBeanServerConnection connection) throws IOException {
        try {
            return JMX.newMXBeanProxy(connection, OBJECT_NAME, FlightRecorderMXBean.class);
        } catch (Exception e) {
            throw new IOException("Could not create proxy for FlightRecorderMXBean: " + e.getMessage(), e);
        }
    }

    private long createRecording() throws IOException {
        try {
            long id = mbean.newRecording();
            Map<String, String> options = new HashMap<>();
            options.put("name", EventByteStream.NAME + ": " + creationTime);
            mbean.setRecordingOptions(id, options);
            return id;
        } catch (Exception e) {
            throw new IOException("Could not create new recording: " + e.getMessage(), e);
        }
    }

    /**
     * Replaces all settings for this recording stream.
     * <p>
     * The following example connects to a remote host and stream events using
     * settings from the "default" configuration.
     *
     * <pre>
     * {
     *     {@literal
     *
     *     String host = "com.example";
     *     int port = 4711;
     *
     *     String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
     *
     *     JMXServiceURL u = new JMXServiceURL(url);
     *     JMXConnector c = JMXConnectorFactory.connect(u);
     *     MBeanServerConnection conn = c.getMBeanServerConnection();
     *
     *     try (final var rs = new RemoteRecordingStream(conn)) {
     *         rs.onMetadata(e -> {
     *             for (Configuration c : e.getConfigurations()) {
     *                 if (c.getName().equals("default")) {
     *                     rs.setSettings(c.getSettings());
     *                 }
     *             }
     *         });
     *         rs.onEvent(System.out::println);
     *         rs.start();
     *     }
     *
     * }
     * </pre>
     *
     * @param settings the settings to set, not {@code null}
     *
     * @see Recording#setSettings(Map)
     */
    public void setSettings(Map<String, String> settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            mbean.setRecordingSettings(recordingId, settings);
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
        }
    }

    /**
     * Disables event with the specified name.
     * <p>
     * If multiple events with same name (for example, the same class is loaded in
     * different class loaders), then all events that match the name are disabled.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     */
    public EventSettings disable(String name) {
        Objects.requireNonNull(name, "name");
        EventSettings s = ManagementSupport.newEventSettings(new RemoteSettings(mbean, recordingId, name));
        try {
            return s.with(ENABLED, "false");
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
            return s;
        }
    }

    /**
     * Enables the event with the specified name.
     * <p>
     * If multiple events have the same name (for example, the same class is loaded
     * in different class loaders), then all events that match the name are enabled.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     * @see EventType
     */
    public EventSettings enable(String name) {
        Objects.requireNonNull(name, "name");
        EventSettings s = ManagementSupport.newEventSettings(new RemoteSettings(mbean, recordingId, name));
        try {
            return s.with(ENABLED, "true");
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
            return s;
        }
    }

    /**
     * Determines how far back data is kept for the stream.
     * <p>
     * To control the amount of recording data stored on disk, the maximum length of
     * time to retain the data can be specified. Data stored on disk that is older
     * than the specified length of time is removed by the Java Virtual Machine
     * (JVM).
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the recording
     * may grow indefinitely if events are not consumed.
     *
     * @param maxAge the length of time that data is kept, or {@code null} if
     *               infinite
     *
     * @throws IllegalArgumentException if {@code maxAge} is negative
     *
     * @throws IllegalStateException    if the recording is in the {@code CLOSED}
     *                                  state
     */
    public void setMaxAge(Duration maxAge) {
        try {
            lock.lock();
            repository.setMaxAge(maxAge);
            this.maxAge = maxAge;
            updateOnCompleteHandler();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Determines how much data is kept for the stream.
     * <p>
     * To control the amount of recording data that is stored on disk, the maximum
     * amount of data to retain can be specified. When the maximum limit is
     * exceeded, the Java Virtual Machine (JVM) removes the oldest chunk to make
     * room for a more recent chunk.
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the recording
     * may grow indefinitely if events are not consumed.
     * <p>
     * The size is measured in bytes.
     *
     * @param maxSize the amount of data to retain, {@code 0} if infinite
     *
     * @throws IllegalArgumentException if {@code maxSize} is negative
     *
     * @throws IllegalStateException    if the recording is in {@code CLOSED} state
     */
    public void setMaxSize(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("Max size of recording can't be negative");
        }
        try {
            lock.lock();
            repository.setMaxSize(maxSize);
            this.maxSize = maxSize;
            updateOnCompleteHandler();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        stream.onEvent(action);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        stream.onEvent(eventName, action);
    }

    @Override
    public void onFlush(Runnable action) {
        stream.onFlush(action);
    }

    @Override
    public void onError(Consumer<Throwable> action) {
        stream.onError(action);
    }

    @Override
    public void onClose(Runnable action) {
        stream.onClose(action);
    }

    @Override
    public void close() {
        try {
            lock.lock();
            if (closed) {
                return;
            }
            closeInternal();
            try {
                if (remoteClosedListener != null) {
                    connection.removeNotificationListener(OBJECT_NAME, remoteClosedListener);
                }
                mbean.closeRecording(recordingId);
            } catch (InstanceNotFoundException | ListenerNotFoundException | IOException e) {
                ManagementSupport.logDebug(e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    private void closeInternal() {
        final boolean isHeldByCurrentThread = lock.isHeldByCurrentThread();
        try {
            if (!isHeldByCurrentThread) {
                lock.lock();
            }
            if (closed) {
                return;
            }
            ManagementSupport.setOnChunkCompleteHandler(stream, null);
            stream.close();
            try {
                repository.close();
            } catch (IOException e) {
                ManagementSupport.logDebug(e.getMessage());
            }
            closed = true;
        } finally {
            if (!isHeldByCurrentThread) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean remove(Object action) {
        return stream.remove(action);
    }

    @Override
    public void setReuse(boolean reuse) {
        stream.setReuse(reuse);
    }

    @Override
    public void setOrdered(boolean ordered) {
        stream.setOrdered(ordered);
    }

    @Override
    public void setStartTime(Instant startTime) {
        stream.setStartTime(startTime);
        this.startTime = startTime;
    }

    @Override
    public void setEndTime(Instant endTime) {
        stream.setEndTime(endTime);
        this.endTime = endTime;
    }

    @Override
    public void start() {
        ensureStartable();
        try {
            try {
                this.remoteStoppedListener = new RemoteStoppedListener(recordingId, this);
                this.remoteClosedListener = new RemoteClosedListener(recordingId, this);
                connection.addNotificationListener(OBJECT_NAME, remoteStoppedListener, null, null);
                connection.addNotificationListener(OBJECT_NAME, remoteClosedListener, null, null);
                mbean.startRecording(recordingId);
            } catch (IllegalStateException ise) {
                throw ise;
            }
            startDownload();
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
            return;
        }
        stream.start();
    }

    @Override
    public void startAsync() {
        ensureStartable();
        stream.startAsync();
        try {
            this.remoteStoppedListener = new RemoteStoppedListener(recordingId, this);
            this.remoteClosedListener = new RemoteClosedListener(recordingId, this);
            connection.addNotificationListener(OBJECT_NAME, remoteStoppedListener, null, null);
            connection.addNotificationListener(OBJECT_NAME, remoteClosedListener, null, null);
            mbean.startRecording(recordingId);
            startDownload();
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
        }
    }

    /**
     * Stops the recording stream.
     * <p>
     * Stops a started stream and waits until all events in the recording have
     * been consumed.
     * <p>
     * Invoking this method in an action, for example in the
     * {@link #onEvent(Consumer)} method, could block the stream indefinitely.
     * To stop the stream abruptly, use the {@link #close} method.
     * <p>
     * The following code snippet illustrates how this method can be used in
     * conjunction with the {@link #startAsync()} method to monitor what happens
     * during a test method:
     * {@snippet :
     *   AtomicLong bytesWritten = new AtomicLong();
     *   try (var r = new RemoteRecordingStream(connection)) {
     *     r.setMaxSize(Long.MAX_VALUE);
     *     r.enable("jdk.FileWrite").withoutThreshold();
     *     r.onEvent(event ->
     *       bytesWritten.addAndGet(event.getLong("bytesWritten"))
     *     );
     *     r.startAsync();
     *     testFoo();
     *     r.stop();
     *     if (bytesWritten.get() > 1_000_000L) {
     *       r.dump(Path.of("file-write-events.jfr"));
     *       throw new AssertionError("testFoo() writes too much data to disk");
     *     }
     *   }
     * }
     * @return {@code true} if recording is stopped, {@code false} otherwise
     *
     * @throws IllegalStateException if the recording is not started or is already stopped
     *
     * @since 20
     */
    public boolean stop() {
        try {
            lock.lock();
            if (closed) {
                throw new IllegalStateException("Event stream is closed");
            }
            if (!started) {
                throw new IllegalStateException("Event stream must be started before it can stopped");
            }
            try {
                if (remoteStoppedListener != null) {
                    connection.removeNotificationListener(OBJECT_NAME, remoteStoppedListener);
                }
                stopped = mbean.stopRecording(this.recordingId);
                RecordingInfo recordingInfo = mbean.getRecordings().stream().filter(r -> r.getId() == this.recordingId).findFirst().get();
                long stopTime = recordingInfo.getStopTime();
                stopInternal(stopTime);
                try {
                    stream.awaitTermination();
                } catch (InterruptedException e) {
                    // OK
                }
                return stopped;
            } catch (Exception e) {
                ManagementSupport.logDebug(e.getMessage());
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean stopInternal(long stopTime) {
        final boolean isHeldByCurrentThread = lock.isHeldByCurrentThread();
        try {
            if (!isHeldByCurrentThread) {
                lock.lock();
            }
            boolean stopped = false;
            try (StreamBarrier pb = ManagementSupport.activateStreamBarrier(stream)) {
                try (StreamBarrier rb = repository.activateStreamBarrier()) {
                    ManagementSupport.setCloseOnComplete(stream, false);
                    pb.setStreamEnd(stopTime);
                    rb.setStreamEnd(stopTime);
                }
            }
            return stopped;
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            return false;
        } finally {
            if (!isHeldByCurrentThread) {
                lock.unlock();
            }
        }
    }

    private void ensureStartable() {
        try {
            lock.lock();
            if (closed) {
                throw new IllegalStateException("Event stream is closed");
            }
            if (started) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            started = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes recording data to a file.
     * <p>
     * The recording stream must be started, but not closed.
     * <p>
     * It's highly recommended that a max age or max size is set before
     * starting the stream. Otherwise, the dump may not contain any events.
     *
     * @param destination the location where recording data is written, not
     *        {@code null}
     *
     * @throws IOException if the recording data can't be copied to the specified
     *         location, or if the stream is closed, or not started.
     *
     * @see RemoteRecordingStream#setMaxAge(Duration)
     * @see RemoteRecordingStream#setMaxSize(long)
     *
     * @since 17
     */
    public void dump(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        long id = -1;
        try {
            FileDump fileDump;
            try {
                lock.lock(); // ensure running state while preparing dump
                if (closed) {
                    throw new IOException("Recording stream has been closed, no content to write");
                }
                if (!started) {
                    throw new IOException("Recording stream has not been started, no content to write");
                }
                // Take repository lock to prevent new data to be flushed
                // client-side after clone has been created on the server.
                synchronized (repository) {
                    id = mbean.cloneRecording(recordingId, true);
                    RecordingInfo ri = getRecordingInfo(mbean.getRecordings(), id);
                    fileDump = repository.newDump(ri.getStopTime());
                }
            } finally {
                lock.unlock();
            }
            // Write outside lock
            fileDump.write(destination);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
        } finally {
            if (id != -1) {
                try {
                    mbean.closeRecording(id);
                } catch (Exception e) {
                    ManagementSupport.logDebug(e.getMessage());
                    close();
                }
            }
        }
    }

    private RecordingInfo getRecordingInfo(List<RecordingInfo> infos, long id) throws IOException {
        for (RecordingInfo info : infos) {
            if (info.getId() == id) {
                return info;
            }
        }
        throw new IOException("Unable to find id of dumped recording");
    }

    @Override
    public void awaitTermination(Duration timeout) throws InterruptedException {
        stream.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        stream.awaitTermination();
    }

    private static Path makeTempDirectory() throws IOException {
        return Files.createTempDirectory("jfr-streaming");
    }

    private void updateOnCompleteHandler() {
        if (maxAge != null || maxSize != 0) {
            // User has set a chunk removal policy
            ManagementSupport.setOnChunkCompleteHandler(stream, null);
        } else {
            ManagementSupport.setOnChunkCompleteHandler(stream, new ChunkConsumer(repository));
        }
    }

    private void startDownload() {
        String name = "JFR: Download Thread " + creationTime;
        Thread downLoadThread = new DownLoadThread(this, name);
        downLoadThread.start();
    }

    boolean isClosed() {
        return closed;
    }

    static final class RemoteStoppedListener implements NotificationListener {

        private final long recordingId;
        private final RemoteRecordingStream stream;

        public RemoteStoppedListener(long recordingId, RemoteRecordingStream stream) {
            this.recordingId = recordingId;
            this.stream = stream;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification instanceof AttributeChangeNotification acn) {
                CompositeData[] newVal = (CompositeData[]) acn.getNewValue();
                CompositeData[] oldVal = (CompositeData[]) acn.getOldValue();
                CompositeData newRecording = getRecording(newVal, recordingId);
                CompositeData oldRecording = getRecording(oldVal, recordingId);
                if (oldRecording == null || newRecording == null) {
                    return;
                }
                String newState = (String) newRecording.get("state");
                if (newState.equals(oldRecording.get("state"))) {
                    return;
                }
                if (newState.equals(RecordingState.STOPPED.name())) {
                    stream.stopInternal((long) newRecording.get("stopTime"));
                }
            }
        }
    }

    static class RemoteClosedListener implements NotificationListener {

        private final long recordingId;
        private final RemoteRecordingStream remoteRecordingStream;

        public RemoteClosedListener(long recordingId, RemoteRecordingStream remoteRecordingStream) {
            this.recordingId = recordingId;
            this.remoteRecordingStream = remoteRecordingStream;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification instanceof AttributeChangeNotification acn) {
                CompositeData[] newVal = (CompositeData[]) acn.getNewValue();
                CompositeData[] oldVal = (CompositeData[]) acn.getOldValue();
                CompositeData newRecording = getRecording(newVal, recordingId);
                CompositeData oldRecording = getRecording(oldVal, recordingId);
                if (oldRecording == null) {
                    return;
                }
                if (newRecording == null) {
                    if (!oldRecording.get("state").equals(RecordingState.CLOSED.name())) {
                        remoteRecordingStream.closeInternal();
                    }
                } else {
                    String newState = (String) newRecording.get("state");
                    if (newState.equals(oldRecording.get("state"))) {
                        return;
                    }
                    if (newState.equals(RecordingState.CLOSED.name())) {
                        remoteRecordingStream.closeInternal();
                    }
                }
            }
        }
    }

    private static CompositeData getRecording(CompositeData[] recordings, long id) {
        for (CompositeData r : recordings) {
            if (r.get("id").equals(id)) {
                return r;
            }
        }
        return null;
    }
}
