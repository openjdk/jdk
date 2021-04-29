/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.security.AccessControlException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import jdk.jfr.Configuration;
import jdk.jfr.EventSettings;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.management.EventSettingsModifier;
import jdk.jfr.internal.management.ManagementSupport;
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

        RemoteSettings(FlightRecorderMXBean mbean, long recordingId) {
            this.mbean = mbean;
            this.recordingId = recordingId;
        }

        @Override
        public void with(String name, String value) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            // FlightRecorderMXBean implementation always returns
            // new instance of Map so no need to create new here.
            Map<String, String> newSettings = getEventSettings();
            newSettings.put(name, value);
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
    final AccessControlContext accessControllerContext;
    final DiskRepository repository;
    final Instant creationTime;
    volatile Instant startTime;
    volatile Instant endTime;
    volatile boolean closed;

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
     *
     * @throws SecurityException if a security manager exists and its
     *                           {@code checkRead} method denies read access to the
     *                           directory, or files in the directory.
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
     *
     * @throws SecurityException if a security manager exists and its
     *                           {@code checkRead} method denies read access to the
     *                           directory, or files in the directory.
     */
    public RemoteRecordingStream(MBeanServerConnection connection, Path directory) throws IOException {
        this(connection, directory, false);
    }

    private RemoteRecordingStream(MBeanServerConnection connection, Path dir, boolean delete) throws IOException {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(dir);
        accessControllerContext = AccessController.getContext();
        // Make sure users can't implement malicious version of a Path object.
        path = Paths.get(dir.toString());
        if (!Files.exists(path)) {
            throw new IOException("Download directory doesn't exist");
        }

        if (!Files.isDirectory(path)) {
            throw new IOException("Download location must be a directory");
        }
        checkFileAccess(path);
        creationTime = Instant.now();
        mbean = createProxy(connection);
        recordingId = createRecording();
        stream = ManagementSupport.newEventDirectoryStream(accessControllerContext, path, configurations(mbean));
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

    private Map<String, String> getRecordingOptions() throws IOException {
        try {
            return mbean.getRecordingOptions(recordingId);
        } catch (Exception e) {
            throw new IOException("Could not get recording options: " + e.getMessage(), e);
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
        Objects.requireNonNull(settings);
        try {
            mbean.setRecordingSettings(recordingId, settings);
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
        }
    };

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
        Objects.requireNonNull(name);
        EventSettings s = ManagementSupport.newEventSettings(new RemoteSettings(mbean, recordingId));
        try {
            return s.with(name + "#" + ENABLED, "false");
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
        Objects.requireNonNull(name);
        EventSettings s = ManagementSupport.newEventSettings(new RemoteSettings(mbean, recordingId));
        try {
            return s.with(name + "#" + ENABLED, "true");
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
        Objects.requireNonNull(maxAge);
        repository.setMaxAge(maxAge);
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
        repository.setMaxSize(maxSize);
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
        if (closed) {
            return;
        }
        closed = true;
        ManagementSupport.setOnChunkCompleteHandler(stream, null);
        stream.close();
        try {
            mbean.closeRecording(recordingId);
        } catch (IOException e) {
            ManagementSupport.logDebug(e.getMessage());
        }
        try {
            repository.close();
        } catch (IOException e) {
            ManagementSupport.logDebug(e.getMessage());
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
        try {
            try {
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
        stream.startAsync();
        try {
            mbean.startRecording(recordingId);
            startDownload();
        } catch (Exception e) {
            ManagementSupport.logDebug(e.getMessage());
            close();
        }
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

    private void startDownload() {
        String name = "JFR: Download Thread " + creationTime;
        Thread downLoadThread = new DownLoadThread(this, name);
        downLoadThread.start();
    }

    boolean isClosed() {
        return closed;
    }
}
