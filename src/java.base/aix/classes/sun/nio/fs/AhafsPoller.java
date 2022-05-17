/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, IBM Corp.
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

package sun.nio.fs;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import jdk.internal.misc.Unsafe;

import static sun.nio.fs.UnixNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * AIX Poller Implementation
 */
public class AhafsPoller extends AbstractPoller
{
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    // The mount point for ahafs. Can be mounted at any point, but setup instructions recommend /aha.
    private static final String AHA_MOUNT_POINT = "/aha";
    // The following timeout controls the maximum time a worker thread will remain blocked before
    // picking up newly registered keys.
    private static final int POLL_TIMEOUT = 100; // ms
    // This affects the OPAQUE_BUFFER_SIZE.
    private static final int MAX_FDS = 2048;
    // See description of opaque buffer below.
    private static final int OPAQUE_BUFFER_SIZE = MAX_FDS*nPollfdSize();
    // Careful when changing the following buffer size. Keep in sync with the one in AixWatchService.c
    private static final int EVENT_BUFFER_SIZE = 2048;

    private static final int SP_LISTEN = 0;
    private static final int SP_NOTIFY = 1;

    private AixWatchService watcher;
    private HashMap<Integer, AixWatchKey> wdToKey;
    // A socket pair created by the native socket pair routine.
    private int[] sp_signal;
    // Native-memory buffer used by AIX Event Infrastructure (AHAFS)
    // to store file descriptors and other info.
    // Treated as an 'opaque memory store' on the JVM heap. Keeping it here allows the
    // native procedures to be functional (as in the paradigm).
    private NativeBuffer opaqueBuffer;
    // Number of open fds by the system. Managed by the native methods,
    // but stored here for the same reasons as above.
    private int[] nfds;

    public AhafsPoller (AixWatchService watchService)
        throws AixWatchService.FatalException
    {
        this.watcher = watchService;
        this.wdToKey = new HashMap<Integer, AixWatchKey>();
        this.nfds = new int[1];
        this.opaqueBuffer = new NativeBuffer(OPAQUE_BUFFER_SIZE);

        this. sp_signal = new int[2];
        try {
            nSocketpair(sp_signal);
        } catch (UnixException e) {
            throw new AixWatchService.FatalException("Could not create socketpair for Poller", e);
        }

        nInit(opaqueBuffer.address(), OPAQUE_BUFFER_SIZE, nfds, sp_signal[SP_LISTEN]);
    }

    /**
     * Wake up the Poller to process changes.
     */
    @Override
    void wakeup()
        throws IOException
    {
        // write to socketpair to wakeup polling thread
        try (NativeBuffer buffer = new NativeBuffer(1)) {
            write(sp_signal[SP_NOTIFY], buffer.address(), 1);
        } catch (UnixException x) {
            throw new IOException("Exception ocurred during poller wakeup " + x.errorString());
        }
    }

    private Path buildAhafsDirMonitorPath(Path path) { return buildAhafsMonitorPath(path, "modDir.monFactory"); }

    private Path buildAhafsFileMonitorPath(Path path) { return buildAhafsMonitorPath(path, "modFile.monFactory"); }

    /**
     *  AHAFS works by creating a specific path in the AHA File System to monitor a target file elsewhere in the system.
     *  This method and it's two helpers, create the path to be used depending on which event producer is to be used.
     *  - modDir watches the contents of a directory for file or sub-directory create or delete events.
     *  - modFile watches the file for modify events.
     *  */
    private Path buildAhafsMonitorPath(Path path, String eventProducer)
    {
        // Create path AHA_MOUNT_POINT/fs/<event-producer>/<parent-dir-path>/<fname>.mon
        return Path.of(AHA_MOUNT_POINT, "fs", eventProducer,
                       path.getParent().toString(), path.getFileName().toString() + ".mon");
    }

    /**
     * Create the resources required to monitor a new file, and add the system created
     * file descriptor to the opaque buffer to be watched with poll.
     *
     * The file descriptor returned by the system is used as a unique identifier for this key.
     */
    private int createNewWatchDescriptor(UnixPath ahafsMonitorPath)
        throws AixWatchService.FatalException
    {
        int wd = AixWatchKey.INVALID_WATCH_DESCRIPTOR;

        // Create resources in AHAFS
        try {
            Files.createDirectories(ahafsMonitorPath.getParent());
        } catch (FileAlreadyExistsException e) {
            // Ignore. It's OK if the parent directory is already present in AHAFS.
        } catch (IOException e) {
            throw new AixWatchService.FatalException("Unable to create parent directory in AHAFS for " + ahafsMonitorPath, e);
        }

        try (NativeBuffer strBuff =
             NativeBuffers.asNativeBuffer(ahafsMonitorPath.getByteArrayForSysCalls())) {
            wd = nRegisterMonitorPath(opaqueBuffer.address(), nfds[0], strBuff.address());
        } catch (UnixException e) {
            throw new AixWatchService.FatalException("Invalid WatchDescriptor (" + wd + ") returned by native procedure while attempting to register " + ahafsMonitorPath);
        }

        assert(wd != AixWatchKey.INVALID_WATCH_DESCRIPTOR);

        nfds[0] += 1;
        return wd;
    }

    /**
     * Create a new SubKey.
     *
     * SubKeys are used to monitor a top level directory for modify events. This is required because
     * AHAFS only supports monitoring a directory for create and delete events directly.
     */
    private AixWatchKey.SubKey createSubKey(Path filePath, AixWatchKey.TopLevelKey topLevelKey)
        throws AixWatchService.FatalException
    {
        UnixPath monitorPath = (UnixPath) buildAhafsFileMonitorPath(filePath.normalize().toAbsolutePath());

        int wd = createNewWatchDescriptor(monitorPath);

        AixWatchKey.SubKey k = new AixWatchKey.SubKey(filePath, wd, topLevelKey);
        wdToKey.put(wd, k);
        return k;
    }

    /** Create SubKeys from a given root and add them to a given TopLevelKey
     */
    private void createSubKeys(Path root, AixWatchKey.TopLevelKey topLevelKey)
        throws AixWatchService.FatalException, IOException
    {
        HashSet<AixWatchKey.SubKey> subKeys = new HashSet<>();

        for (Path filePath: Files.walk(root, 1)
                                 .filter((Path p) -> Files.isRegularFile(p))
                                 .collect(Collectors.toList())) {
            subKeys.add(createSubKey(filePath, topLevelKey));
        }

        topLevelKey.addSubKeys(subKeys);
    }

    Optional<? extends Exception> checkRegisterArgs(Path watchPath,
                                                    Set<? extends WatchEvent.Kind<?>> events,
                                                    WatchEvent.Modifier... modifiers)
    {
        UnixPath watchUnixPath = (UnixPath) watchPath;
        UnixFileAttributes attrs = null;

        // Validate path
        if (watchPath == null) {
            return Optional.of(new NullPointerException("AixWatchSerice: Path was null"));
        }
        try {
            attrs = UnixFileAttributes.get(watchUnixPath , true);
        } catch (UnixException x) {
            return Optional.of(x.asIOException(watchUnixPath));
        }
        if (!attrs.isDirectory()) {
            return Optional.of(new NotDirectoryException(watchUnixPath.getPathForExceptionMessage()));
        }

        // Validate events
        if (events == null) {
            return Optional.of(new NullPointerException("AixWatchSerice: Watch events list was null"));
        } else if (events.contains(null)) {
            return Optional.of(new NullPointerException("AixWatchSerice: Watch events list contains a null element"));
        } else if (!(events.contains(StandardWatchEventKinds.ENTRY_CREATE) ||
                     events.contains(StandardWatchEventKinds.ENTRY_MODIFY) ||
                     events.contains(StandardWatchEventKinds.ENTRY_DELETE))) {
            return Optional.of(new UnsupportedOperationException("AixWatchService: No supported events in registration of " + watchPath));
        }

        // Validate modifiers
        if (modifiers.length > 0) {
            for (WatchEvent.Modifier modifier: modifiers) {
                if (modifier == null)
                    return Optional.of(new NullPointerException("AixWatchService: Modifier was null"));
                if (!ExtendedOptions.SENSITIVITY_HIGH.matches(modifier) &&
                    !ExtendedOptions.SENSITIVITY_MEDIUM.matches(modifier) &&
                    !ExtendedOptions.SENSITIVITY_LOW.matches(modifier)) {
                    return Optional.of(new UnsupportedOperationException("AixWatchService: Modifiers not supported"));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Register a path with the Poller.
     *
     * @return [WatchKey | RuntimeException | IOException] the caller is expected to check
     * if the retuned object is an instance of either exception and act accordingly.
     */
    @Override
    Object implRegister(Path watchPath,
                        Set<? extends WatchEvent.Kind<?>> events,
                        WatchEvent.Modifier... modifiers)
    {
        Optional<? extends Exception> mError = checkRegisterArgs(watchPath, events, modifiers);
        if (mError.isPresent()) {
            return mError.get();
        }

        // Create resources
        UnixPath ahafsMonitorPath = (UnixPath)buildAhafsDirMonitorPath(watchPath.toAbsolutePath());
        int wd = AixWatchKey.INVALID_WATCH_DESCRIPTOR;
        try {
            wd = createNewWatchDescriptor(ahafsMonitorPath);
        } catch (AixWatchService.FatalException e) {
            return new IOException("Invalid watch descriptor returned for " + ahafsMonitorPath + " during registration of " + watchPath, e);
        }

        AixWatchKey.TopLevelKey wk = new AixWatchKey.TopLevelKey(watchPath, wd, events, this.watcher);
        wdToKey.put(wd, wk);

        // Directory modifications are not supported directly in AIX Event
        // Infrastructure. So the modify event is detected by monitoring
        // for changes to the individual files in the directory.
        if (wk.isWatching(StandardWatchEventKinds.ENTRY_MODIFY)) {
            try {
                createSubKeys(watchPath, wk);
            } catch (IOException e) {
                // See note about returning exceptions below.
                return new IOException("[AixWatchService] IO error reported during file registration", e);
            } catch (AixWatchService.FatalException e) {
                // AbstractPolller checks if return type is insanceof IOE or RuntimeException
                // and then throws. To ensure client is informed of the error, we
                // map our internal exception to one checked by the caller and return.
                return new IOException("[AixWatchService] Error reported during file registration", e);
            }
        }

        return wk;
    }

    /**
     * Update an existing AixWatchKey registration.
     * Replace the currently watched events with the new event set,
     * and perform any necessary housekeeping.
     */
    WatchKey reRegister(AixWatchKey wk,
                        Set<? extends WatchEvent.Kind<?>> newEvents,
                        WatchEvent.Modifier... modifiers)
        throws AixWatchService.FatalException
    {
        AixWatchKey.TopLevelKey tlk = wk.resolve();
        if (newEvents.contains(StandardWatchEventKinds.ENTRY_MODIFY) &&
            !wk.isWatching(StandardWatchEventKinds.ENTRY_MODIFY)) {
            try {
                createSubKeys(wk.watchable(), tlk);
            } catch (IOException e) {
                throw new AixWatchService.FatalException("[AixWatchService] Error reported during file registration", e);
            }
        }
        if (wk.isWatching(StandardWatchEventKinds.ENTRY_MODIFY) &&
            !newEvents.contains(StandardWatchEventKinds.ENTRY_MODIFY)) {
            for (AixWatchKey.SubKey sk : tlk.subKeys()) {
                tlk.removeSubKey(sk);
                cleanKeyResources(sk);
            }
        }

        tlk.replaceEvents(newEvents);
        return tlk;
    }

    /**
     * Cancel an AixWatchKey.
     * This method performs two (slightly different) tasks:
     *
     * 1. Cancel the given primary key (provided by the client) and
     *      any subkeys.
     * 2. Cancel the given subkey (provided as an interal call). Do not
     *      resolve to TopLevelKey or cancel SubKeys.
     *
     */
    @Override
    void implCancelKey(WatchKey wk)
    {
        // Only cancel AixWatchKeys given out by this WatchService
        if (!(wk instanceof AixWatchKey))
            return;

        AixWatchKey awk = (AixWatchKey) wk;
        awk.invalidate();

        // If cancelling a TopLevelKey, also cancel any SubKeys
        if(wk instanceof AixWatchKey.TopLevelKey) {
            AixWatchKey.TopLevelKey tlk = awk.resolve();
            for (AixWatchKey.SubKey sk: tlk.subKeys()) {
                tlk.removeSubKey(sk);
                cleanKeyResources(sk);
            }
        }

        // Cancel _this_ key regardless of whether it is a TopLevelKey or SubKey.
        cleanKeyResources(awk);
    }

    /**
     * Clean up Resources accociated with a given AixWatchKey
     */
    void cleanKeyResources(AixWatchKey awk)
    {
        nCancelWatchDescriptor(opaqueBuffer.address(), nfds[0], awk.watchDescriptor());
        try {
            Path monitorPath;
            if (awk instanceof AixWatchKey.TopLevelKey) {
                monitorPath = buildAhafsDirMonitorPath(awk.watchable());
            } else {
                monitorPath = buildAhafsFileMonitorPath(awk.watchable());
            }
            Files.deleteIfExists(monitorPath);
        } catch (IOException e) {
            System.err.println("Warn: Unable to remove monitor path in AixWatchService for key with (actual) path "
                               + awk.watchable());
        }
        wdToKey.remove(awk.watchDescriptor());
    }

    // Cancel all keys. Close poller
    @Override
    void implCloseAll()
    {
        List<AixWatchKey> topLevelKeys = wdToKey.values()
            .stream()
            .filter((AixWatchKey k) -> k instanceof AixWatchKey.TopLevelKey)
            .collect(Collectors.toList());

        topLevelKeys.forEach(k -> implCancelKey(k));

        UnixNativeDispatcher.close(sp_signal[SP_LISTEN], e -> null);
        UnixNativeDispatcher.close(sp_signal[SP_NOTIFY], e -> null);

        nCloseAll(opaqueBuffer.address(), nfds[0]);

        nfds[0] = 0;
        opaqueBuffer.close();
    }

    private int parseWd(String wdLine)
    {
        // Expect: BEGIN_WD=<wd>
        return Integer.parseInt(wdLine.substring(9));
    }

    private Optional<WatchEvent.Kind<?>> parseEventKind(String line, AixWatchKey key)
    {
        if (key instanceof AixWatchKey.TopLevelKey) {
            return parseTopLevelEventKind(line);
        } else {
            return parseSubKeyEventKind(line);
        }
    }

    private Optional<WatchEvent.Kind<?>> parseTopLevelEventKind(String line)
    {
        if (line.equals("RC_FROM_EVPROD=1000")) {
            return Optional.of(StandardWatchEventKinds.ENTRY_CREATE);
        } else if (line.equals("RC_FROM_EVPROD=1002")) {
            return Optional.of(StandardWatchEventKinds.ENTRY_DELETE);
        } else if (line.equals("RC_FROM_EVPROD=1003")) {
            // Listed an 'unavailable' in the documentation, but seen in
            // the wild. Indicates the monitored directory has been deleted.
            return Optional.of(StandardWatchEventKinds.ENTRY_DELETE);
        } else {
            return Optional.of(AixWatchService.POLL_ERROR);
        }
    }

    static final int RC_LOWER = 1000;
    static final int RC_UPPER = 1007;
    static final int RC_PRE_LENGTH = "RC_FROM_EVPROD=".length();

    private Optional<WatchEvent.Kind<?>> parseSubKeyEventKind(String line)
    {
        if (line.startsWith("RC_FROM_EVPROD=")) {
            // Files watched by the modFile event producer may generate
            // an event when they are:
            // - Written to
            // - Mapped for writing by a process
            // - Cleared (by fclear)
            // - Truncated (by ftrunc)
            //
            // The actual event code is umimportant as long as the value is
            // in the expected range since the above events become ENTRY_MODIFY
            // in the context of the WatchService API.
            int code = Integer.parseInt(line.substring(RC_PRE_LENGTH));
            if (code >= RC_LOWER && code <= RC_UPPER) {
                return Optional.of(StandardWatchEventKinds.ENTRY_MODIFY);
            }
        }
        return Optional.of(AixWatchService.POLL_ERROR);
    }

    private Iterable<String> getLines(long cBuffer)
    {
        ArrayList<String> lines = new ArrayList<String>();

        int start = 0;
        int pos = start;
        boolean atEnd = false;
        while(!atEnd) {
            byte b = UNSAFE.getByte(cBuffer + pos);

            // Detect end of c-string
            atEnd |= (b == 0);

            // Process new line
            if (b == '\n' || b == 0) {
                byte[] bytes = new byte[pos - start];

                for (int c = 0; c < pos - start; c++) {
                    bytes[c] = UNSAFE.getByte(cBuffer + start + c);
                }

                String line = new String(bytes);
                lines.add(line);

                start = pos + 1;
                pos = start;
            }
            // O.W. Advance cursor until line is complete
            else {
                pos++;
                atEnd |= (pos >= EVENT_BUFFER_SIZE);
            }
        }
        return lines;
    }

    private Iterable<AixWatchService.PollEvent> parsePollEvents(long eventBuffer, int expCount)
    {
        ArrayList<AixWatchService.PollEvent> events = new ArrayList<>();

        if (expCount <= 0)
            return events;

        Optional<AixWatchKey> mKey = Optional.empty();
        Optional<WatchEvent.Kind<?>> mKind = Optional.empty();
        Optional<String> mFilename = Optional.empty();

        Iterator<String> lines = getLines(eventBuffer).iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            // Start parsing event.
            if (line.startsWith("BEGIN_WD")) {
                int wd = parseWd(line);
                mKey = Optional.ofNullable(wdToKey.get(wd));
            }
            // Detect buffer overflow
            else if (line.equals("BUF_WRAP")) {
                mKind = Optional.of(StandardWatchEventKinds.OVERFLOW);
            }
            // Parse return code
            else if (mKey.isPresent() && line.startsWith("RC_FROM_EVPROD")) {
                mKind = parseEventKind(line, mKey.get());
            }
            // Parse additional info from event producer
            else if (line.startsWith("BEGIN_EVPROD_INFO") && lines.hasNext()) {
                // Expect exactly:                     // BEGIN_EVPROD_INFO
                mFilename = Optional.of(lines.next()); // <filename>
                if (lines.hasNext()) lines.next();     // END_EVPROD_INFO
            }
            // Finish parsing event.
            else if (line.startsWith("END_WD")) {
                if (mKey.isPresent() && mKind.isPresent()) {
                    events.add(new AixWatchService.PollEvent(mKey.get(), mKind.get(), mFilename));
                }
                mKey      = Optional.empty();
                mKind     = Optional.empty();
                mFilename = Optional.empty();
            }
        }

        return events;
    }

    private void processPollEvent(AixWatchService.PollEvent e)
        throws AixWatchService.FatalException
    {
        // Process all events via the parent event since this is the only
        // event expected by the client.
        AixWatchKey.TopLevelKey receiver = e.key().resolve();

        // Notify client of changes.
        if (e.key().isWatching(e.kind())) {
            receiver.signalEvent(e.kind(),
                                 e.mContext().isPresent() ?
                                 Path.of(e.mContext().get()) : e.key().watchable().getFileName());
        } else if (e.kind().equals(AixWatchService.POLL_ERROR)) {
            implCancelKey(e.key());
            e.key().signal();
        }

        // Clean up or create resources as required by key & event type.
        if (e.kind() == StandardWatchEventKinds.ENTRY_CREATE &&
            receiver.isWatching(StandardWatchEventKinds.ENTRY_MODIFY)) {
            String context = e.mContext().get();
            createSubKey(Path.of(receiver.watchable().toString(), context), receiver);
        } else if (e.kind() == StandardWatchEventKinds.ENTRY_DELETE &&
                   (e.key() instanceof AixWatchKey.SubKey)) {
            receiver.removeSubKey(e.key());
            implCancelKey(e.key());
        } else if (e.kind() == StandardWatchEventKinds.ENTRY_DELETE &&
                   (e.key() instanceof AixWatchKey.TopLevelKey)) {
            // 'Auto-Cancel' key and SubKeys if the directory that the TopLevelKey
            // is watching has been deleted. Detect this by checking whether the
            // event context (filename) matches the directory of the TopLevelKey
            if (Optional.of(e.key().watchable().getFileName().toString()).equals(e.mContext())) {
                implCancelKey(e.key());
            }
        }
    }

    // Main poller loop
    @Override
    public void run()
    {
        while (!processRequests()) {
            int evcnt = 0;

            try (NativeBuffer evbuf = new NativeBuffer(EVENT_BUFFER_SIZE)) {
                evcnt = nPoll(opaqueBuffer.address(), nfds[0], POLL_TIMEOUT, evbuf.address(), EVENT_BUFFER_SIZE);
                for (AixWatchService.PollEvent e: parsePollEvents(evbuf.address(), evcnt)) {
                    processPollEvent(e);
                }
            } catch (Throwable e) {
                // Warn, but don't stop processing events on error. Throwing here will stop all processing for
                // the poller.
                System.err.println("[AixWatchService] Caught exception in poll loop: " + e);
                e.printStackTrace();
            }
        }
    }

    private static native int nPollfdSize();

    private static native void nInit(long buffer, int buff_size, int[] nv, int socketfd);

    private static native void nCloseAll(long buffer, int nfds);

    private static native void nSocketpair(int[] sv) throws UnixException;

    private static native int nRegisterMonitorPath(long buffer, int nxt_fd, long pathv) throws UnixException;

    private static native int nCancelWatchDescriptor(long buffer, int nfds, int wd);

    private static native int nPoll(long buffer, int nfds, int timeout, long evbuf, int evbuf_size) throws UnixException;

    static {
        jdk.internal.loader.BootLoader.loadLibrary("nio");
    }
}
