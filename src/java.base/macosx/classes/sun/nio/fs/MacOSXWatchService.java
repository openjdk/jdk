/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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

package sun.nio.fs;

import jdk.internal.misc.InnocuousThread;
import jdk.internal.misc.Unsafe;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

class MacOSXWatchService extends AbstractWatchService {
    private static final MacOSXFileSystemProvider THE_FS_PROVIDER = DefaultFileSystemProvider.instance();
    private static final MacOSXFileSystem THE_FS = (MacOSXFileSystem) THE_FS_PROVIDER.theFileSystem();

    private final HashMap<Object, MacOSXWatchKey> dirKeyToWatchKey      = new HashMap<>();
    private final HashMap<Long, MacOSXWatchKey>   eventStreamToWatchKey = new HashMap<>();
    private final Object                          watchKeysLock         = new Object();

    private final CFRunLoopThread runLoopThread;

    MacOSXWatchService() throws IOException {
        runLoopThread = new CFRunLoopThread();
        Thread t = InnocuousThread.newThread("FileSystemWatcher", runLoopThread);
        t.setDaemon(true);
        t.start();

        // In order to be able to schedule any FSEventStreams,
        // a reference to a run loop is required.
        runLoopThread.waitForRunLoopRef();
    }

    @SuppressWarnings("removal")
    @Override
    WatchKey register(Path dir,
                      WatchEvent.Kind<?>[] events,
                      WatchEvent.Modifier... modifiers) throws IOException {
        checkIsOpen();

        final UnixPath unixDir = (UnixPath)dir;
        final Object dirKey    = checkPath(unixDir);
        final EnumSet<FSEventKind>   eventSet    = FSEventKind.setOf(events);
        final EnumSet<WatchModifier> modifierSet = WatchModifier.setOf(modifiers);

        try {
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<MacOSXWatchKey>() {
                @Override
                public MacOSXWatchKey run() throws IOException {
                    return doPrivilegedRegister(unixDir, dirKey, eventSet, modifierSet);
                }
            });
        } catch (PrivilegedActionException pae) {
            final Throwable cause = pae.getCause();
            if (cause instanceof IOException ioe)
                throw ioe;
            throw new AssertionError(pae);
        }
    }

    private MacOSXWatchKey doPrivilegedRegister(UnixPath unixDir,
                                                Object dirKey,
                                                EnumSet<FSEventKind> eventSet,
                                                EnumSet<WatchModifier> modifierSet) throws IOException {
        synchronized (closeLock()) {
            checkIsOpen();

            synchronized (watchKeysLock) {
                MacOSXWatchKey watchKey = dirKeyToWatchKey.get(dirKey);
                final boolean keyForDirAlreadyExists = (watchKey != null);
                if (keyForDirAlreadyExists) {
                    eventStreamToWatchKey.remove(watchKey.getEventStreamRef());
                    watchKey.disable();
                } else {
                    watchKey = new MacOSXWatchKey(this, unixDir, dirKey);
                    dirKeyToWatchKey.put(dirKey, watchKey);
                }

                watchKey.enable(runLoopThread, eventSet, modifierSet);
                eventStreamToWatchKey.put(watchKey.getEventStreamRef(), watchKey);
                watchKeysLock.notify(); // So that run loop gets running again
                                        // if stopped due to lack of event streams
                return watchKey;
            }
        }
    }

    /**
     * Invoked on the CFRunLoopThread by the native code to report directories
     * that need to be re-scanned.
     */
    private void handleEvents(final long eventStreamRef,
                              final String[] paths,
                              final long eventFlagsPtr) {
        synchronized (watchKeysLock) {
            final MacOSXWatchKey watchKey = eventStreamToWatchKey.get(eventStreamRef);
            System.out.println("WatchService: handleEvents for event stream " + Long.toHexString(eventStreamRef)
                                + " watchKey=" + watchKey);
            if (watchKey != null) {
                watchKey.handleEvents(paths, eventFlagsPtr);
            }
        }
    }

    void cancel(final MacOSXWatchKey watchKey) {
        synchronized (watchKeysLock) {
            dirKeyToWatchKey.remove(watchKey.getRootPathKey());
            eventStreamToWatchKey.remove(watchKey.getEventStreamRef());
        }
    }

    void waitForEventSource() {
        synchronized (watchKeysLock) {
            if (isOpen() && eventStreamToWatchKey.isEmpty()) {
                try {
                    watchKeysLock.wait();
                } catch (InterruptedException ignore) {}
            }
        }
    }

    @Override
    void implClose() {
        synchronized (watchKeysLock) {
            eventStreamToWatchKey.clear();
            dirKeyToWatchKey.forEach((key, watchKey) -> watchKey.invalidate());
            dirKeyToWatchKey.clear();
            watchKeysLock.notify(); // Let waitForEventSource() go if it was waiting
            runLoopThread.runLoopStop(); // Force exit from CFRunLoopRun()
        }
    }

    private class CFRunLoopThread implements Runnable {
        // Native reference to the CFRunLoop object of the watch service run loop.
        private volatile long runLoopRef;
        private final CountDownLatch runLoopRefAvailabilitySignal = new CountDownLatch(1);

        private void waitForRunLoopRef() throws IOException {
            try {
                runLoopRefAvailabilitySignal.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        long getRunLoopRef() {
            return runLoopRef;
        }

        void runLoopStop() {
            if (runLoopRef != 0) {
                // The run loop may have stuck in CFRunLoopRun() even though
                // all of its input sources have been removed. Need to terminate
                // it explicitly so that it can run to completion.
                MacOSXWatchService.CFRunLoopStop(runLoopRef);
            }
        }

        @Override
        public void run() {
            runLoopRef = CFRunLoopGetCurrent();
            runLoopRefAvailabilitySignal.countDown();

            System.out.println("WatchService: Run loop " + Long.toHexString(runLoopRef) + " - waiting...");
            while (isOpen()) {
                System.out.println("WatchService: Run loop " + Long.toHexString(runLoopRef) + " - starting...");
                CFRunLoopRun(MacOSXWatchService.this);
                System.out.println("WatchService: Run loop "  + Long.toHexString(runLoopRef) +  " - waiting for event source...");
                waitForEventSource();
            }

            runLoopRef = 0; // CFRunLoopRef is no longer usable when the loop has been terminated
        }
    }

    private void checkIsOpen() {
        if (!isOpen())
            throw new ClosedWatchServiceException();
    }

    private Object checkPath(UnixPath dir) throws IOException {
        if (dir == null)
            throw new NullPointerException("No path to watch");

        UnixFileAttributes attrs;
        try {
            attrs = UnixFileAttributes.get(dir, true);
        } catch (UnixException x) {
            throw x.asIOException(dir);
        }

        if (!attrs.isDirectory())
            throw new NotDirectoryException(dir.getPathForExceptionMessage());

        final Object fileKey = attrs.fileKey();
        if (fileKey == null)
            throw new AssertionError("File keys must be supported");

        return fileKey;
    }

    private enum FSEventKind {
        CREATE, MODIFY, DELETE, OVERFLOW;

        public static FSEventKind of(final WatchEvent.Kind<?> watchEventKind) {
            if (StandardWatchEventKinds.ENTRY_CREATE == watchEventKind) {
                return CREATE;
            } else if (StandardWatchEventKinds.ENTRY_MODIFY == watchEventKind) {
                return MODIFY;
            } else if (StandardWatchEventKinds.ENTRY_DELETE == watchEventKind) {
                return DELETE;
            } else if (StandardWatchEventKinds.OVERFLOW == watchEventKind) {
                return OVERFLOW;
            } else {
                throw new UnsupportedOperationException(watchEventKind.name());
            }
        }

        public static EnumSet<FSEventKind> setOf(final WatchEvent.Kind<?>[] events) {
            final EnumSet<FSEventKind> eventSet = EnumSet.noneOf(FSEventKind.class);
            for (final WatchEvent.Kind<?> event: events) {
                if (event == null) {
                    throw new NullPointerException("An element in event set is 'null'");
                } else if (event == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                eventSet.add(FSEventKind.of(event));
            }

            if (eventSet.isEmpty())
                throw new IllegalArgumentException("No events to register");

            return eventSet;
        }
    }

    private enum WatchModifier {
        SENSITIVITY_HIGH, SENSITIVITY_MEDIUM, SENSITIVITY_LOW;

        public static WatchModifier of(final WatchEvent.Modifier watchEventModifier) {
            if (ExtendedOptions.SENSITIVITY_HIGH.matches(watchEventModifier)) {
                return SENSITIVITY_HIGH;
            } if (ExtendedOptions.SENSITIVITY_MEDIUM.matches(watchEventModifier)) {
                return SENSITIVITY_MEDIUM;
            } if (ExtendedOptions.SENSITIVITY_LOW.matches(watchEventModifier)) {
                return SENSITIVITY_LOW;
            } else {
                throw new UnsupportedOperationException(watchEventModifier.name());
            }
        }

        public static EnumSet<WatchModifier> setOf(final WatchEvent.Modifier[] modifiers) {
            final EnumSet<WatchModifier> modifierSet = EnumSet.noneOf(WatchModifier.class);
            for (final WatchEvent.Modifier modifier : modifiers) {
                if (modifier == null)
                    throw new NullPointerException("An element in modifier set is 'null'");

                modifierSet.add(WatchModifier.of(modifier));
            }

            return modifierSet;
        }

        public static double sensitivityOf(final EnumSet<WatchModifier> modifiers) {
            if (modifiers.contains(SENSITIVITY_HIGH)) {
                return 0.1;
            } else if (modifiers.contains(SENSITIVITY_LOW)) {
                return 1;
            } else {
                return 0.5; // aka SENSITIVITY_MEDIUM
            }
        }
    }

    private static class MacOSXWatchKey extends AbstractWatchKey {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private static final Path RELATIVE_ROOT_PATH = THE_FS.getPath("");

        // Full path to this key's watch root directory.
        private final Path   realRootPath;
        private final int    realRootPathLength;
        private final Object rootPathKey;

        // Kinds of events to be reported.
        private EnumSet<FSEventKind> eventsToWatch;

        // Native FSEventStreamRef as returned by FSEventStreamCreate().
        private long         eventStreamRef;
        private final Object eventStreamRefLock = new Object();

        private DirectorySnapshot directorySnapshot;

        MacOSXWatchKey(final MacOSXWatchService watchService,
                       final UnixPath dir,
                       final Object rootPathKey) throws IOException {
            super(dir, watchService);
            this.realRootPath       = dir.toRealPath().normalize();
            this.realRootPathLength = realRootPath.toString().length() + 1;
            this.rootPathKey        = rootPathKey;
        }

        synchronized void enable(final CFRunLoopThread runLoopThread,
                                 final EnumSet<FSEventKind> eventsToWatch,
                                 final EnumSet<WatchModifier> modifierSet) throws IOException {
            assert(!isValid());

            this.eventsToWatch = eventsToWatch;

            directorySnapshot = DirectorySnapshot.create(getRealRootPath());

            synchronized (eventStreamRefLock) {
                final int kFSEventStreamCreateFlagWatchRoot = 0x00000004;
                eventStreamRef = MacOSXWatchService.eventStreamCreate(
                        realRootPath.toString(),
                        WatchModifier.sensitivityOf(modifierSet),
                        kFSEventStreamCreateFlagWatchRoot);

                if (eventStreamRef == 0)
                    throw new IOException("Unable to create FSEventStream");

                MacOSXWatchService.eventStreamSchedule(eventStreamRef,
                        runLoopThread.getRunLoopRef());
            }
        }

        synchronized void disable() {
            invalidate();
            directorySnapshot = null;
        }

        synchronized void handleEvents(final String[] paths, long eventFlagsPtr) {
            System.out.println("WatchService: handleEvents " + paths);
            if (paths == null) {
                reportOverflow(null);
                return;
            }

            if (updateNeeded(paths, eventFlagsPtr)) {
                directorySnapshot.update(MacOSXWatchKey.this);
            }
        }

        private Path toRelativePath(final String absPath) {
            return   (absPath.length() > realRootPathLength)
                    ? THE_FS.getPath(absPath.substring(realRootPathLength))
                    : RELATIVE_ROOT_PATH;
        }

        private boolean updateNeeded(String[] paths, long eventFlagsPtr) {
            // FSEventStreamEventFlags is UInt32.
            final long SIZEOF_FS_EVENT_STREAM_EVENT_FLAGS = 4L;

            System.out.println("WatchService: updateNeeded for " + paths.length + " path names");
            boolean rootChanged = false;
            for (final String absPath : paths) {
                if (absPath == null) {
                    reportOverflow(null);
                    continue;
                }

                Path path = toRelativePath(absPath);

                if (!RELATIVE_ROOT_PATH.equals(path)) {
                    // Ignore events from subdirectories for now.
                    eventFlagsPtr += SIZEOF_FS_EVENT_STREAM_EVENT_FLAGS;
                    continue;
                }

                final int kFSEventStreamEventFlagRootChanged = 0x00000020;
                final int flags = UNSAFE.getInt(eventFlagsPtr);
                if ((flags & kFSEventStreamEventFlagRootChanged) != 0) {
                    cancel();
                    signal();
                    rootChanged = false;
                    break;
                } else {
                    rootChanged = true;
                }

                eventFlagsPtr += SIZEOF_FS_EVENT_STREAM_EVENT_FLAGS;
            }

            return rootChanged;
        }

        /**
         * Represents a snapshot of the watched directory with a millisecond
         * precision timestamp of the last modification.
         */
        private static class DirectorySnapshot {
            // Maps file names to their attributes.
            private final Map<Path, Entry> files = new HashMap<>();

            // A counter to keep track of files that have disappeared since the last run.
            private long currentTick;

            static DirectorySnapshot create(final Path realRootPath) throws IOException {
                final DirectorySnapshot snapshot = new DirectorySnapshot();
                try (final DirectoryStream<Path> directoryStream
                             = THE_FS_PROVIDER.newDirectoryStream(
                                     realRootPath, p -> true)) {
                    for (final Path file : directoryStream) {
                        try {
                            final BasicFileAttributes attrs
                                    = THE_FS_PROVIDER.readAttributes(
                                            file,
                                            BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS);
                            final Entry entry = new Entry(
                                    attrs.isDirectory(),
                                    attrs.lastModifiedTime().toMillis(),
                                    0);
                            snapshot.files.put(file.getFileName(), entry);
                        } catch (IOException ignore) {}
                    }
                } catch (DirectoryIteratorException e) {
                    throw e.getCause();
                }

                return snapshot;
            }

            void update(final MacOSXWatchKey watchKey) {
                currentTick++;

                try (final DirectoryStream<Path> directoryStream
                             = THE_FS_PROVIDER.newDirectoryStream(
                                     watchKey.getRealRootPath().resolve(RELATIVE_ROOT_PATH),
                                     p -> true)) {
                    for (final Path file : directoryStream) {
                        try {
                            final BasicFileAttributes attrs
                                    = THE_FS_PROVIDER.readAttributes(
                                            file,
                                            BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS);
                            final Path fileName     = file.getFileName();
                            final Entry entry       = files.get(fileName);
                            final boolean isNew     = (entry == null);
                            final long lastModified = attrs.lastModifiedTime().toMillis();
                            final Path relativePath = RELATIVE_ROOT_PATH.resolve(fileName);

                            if (attrs.isDirectory()) {
                                if (isNew) {
                                    files.put(fileName,
                                            new Entry(true, lastModified, currentTick));
                                    watchKey.reportCreated(relativePath);
                                } else {
                                    if (!entry.isDirectory) { // Used to be a file, now a directory
                                        watchKey.reportCreated(relativePath);
                                        files.put(fileName,
                                                new Entry(true, lastModified, currentTick));
                                        watchKey.reportDeleted(relativePath);
                                    } else if (entry.isModified(lastModified)) {
                                        watchKey.reportModified(relativePath);
                                    }
                                    entry.update(lastModified, currentTick);
                                }
                            } else {
                                if (isNew) {
                                    files.put(fileName,
                                            new Entry(false, lastModified, currentTick));
                                    watchKey.reportCreated(relativePath);
                                } else {
                                    if (entry.isDirectory) { // Used to be a directory, now a file.
                                        watchKey.reportDeleted(relativePath);
                                        files.put(fileName,
                                                new Entry(false, lastModified, currentTick));
                                        watchKey.reportCreated(RELATIVE_ROOT_PATH.resolve(fileName));
                                    } else if (entry.isModified(lastModified)) {
                                        watchKey.reportModified(relativePath);
                                    }
                                    entry.update(lastModified, currentTick);
                                }
                            }
                        } catch (IOException ignore) {
                            // Simply skip the file we couldn't read; it'll get marked as deleted later.
                        }
                    }
                } catch (IOException | DirectoryIteratorException ignore) {
                    // Most probably this directory has just been deleted; its parent will notice that.
                }

                checkDeleted(watchKey);
            }

            private void checkDeleted(final MacOSXWatchKey watchKey) {
                final Iterator<Map.Entry<Path, Entry>> it = files.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<Path, Entry> mapEntry = it.next();
                    final Entry entry = mapEntry.getValue();
                    if (entry.lastTickCount != currentTick) {
                        final Path file = mapEntry.getKey();
                        it.remove();
                        watchKey.reportDeleted(RELATIVE_ROOT_PATH.resolve(file));
                    }
                }
            }

            /**
             * Information about an entry in a directory.
             */
            private static class Entry {
                private long lastModified;
                private long lastTickCount;
                private final boolean isDirectory;

                Entry(final boolean isDirectory, final long lastModified, final long lastTickCount) {
                    this.lastModified  = lastModified;
                    this.lastTickCount = lastTickCount;
                    this.isDirectory   = isDirectory;
                }

                boolean isModified(final long lastModified) {
                    return (this.lastModified != lastModified);
                }

                void update(final long lastModified, final long lastTickCount) {
                    this.lastModified = lastModified;
                    this.lastTickCount = lastTickCount;
                }
            }
        }

        private void reportCreated(final Path path) {
            if (eventsToWatch.contains(FSEventKind.CREATE))
                signalEvent(StandardWatchEventKinds.ENTRY_CREATE, path);
        }

        private void reportDeleted(final Path path) {
            if (eventsToWatch.contains(FSEventKind.DELETE))
                signalEvent(StandardWatchEventKinds.ENTRY_DELETE, path);
        }

        private void reportModified(final Path path) {
           if (eventsToWatch.contains(FSEventKind.MODIFY))
                signalEvent(StandardWatchEventKinds.ENTRY_MODIFY, path);
        }

        private void reportOverflow(final Path path) {
            if (eventsToWatch.contains(FSEventKind.OVERFLOW))
                signalEvent(StandardWatchEventKinds.OVERFLOW, path);
        }

        public Object getRootPathKey() {
            return rootPathKey;
        }

        public Path getRealRootPath() {
            return realRootPath;
        }

        @Override
        public boolean isValid() {
            synchronized (eventStreamRefLock) {
                return eventStreamRef != 0;
            }
        }

        @Override
        public void cancel() {
            if (!isValid()) return;

            // First, must stop the corresponding run loop:
            ((MacOSXWatchService) watcher()).cancel(this);

            // Next, invalidate the corresponding native FSEventStream.
            invalidate();
        }

        void invalidate() {
            synchronized (eventStreamRefLock) {
                if (isValid()) {
                    eventStreamStop(eventStreamRef);
                    eventStreamRef = 0;
                }
            }
        }

        long getEventStreamRef() {
            synchronized (eventStreamRefLock) {
                assert (isValid());
                return eventStreamRef;
            }
        }
    }

    /* native methods */

    private static native long eventStreamCreate(String dir, double latencyInSeconds, int flags);
    private static native void eventStreamSchedule(long eventStreamRef, long runLoopRef);
    private static native void eventStreamStop(long eventStreamRef);
    private static native long CFRunLoopGetCurrent();
    private static native void CFRunLoopRun(final MacOSXWatchService watchService);
    private static native void CFRunLoopStop(long runLoopRef);

    private static native void initIDs();

    static {
        jdk.internal.loader.BootLoader.loadLibrary("nio");
        initIDs();
    }
}
