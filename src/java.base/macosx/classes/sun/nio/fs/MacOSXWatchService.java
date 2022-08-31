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

import jdk.internal.misc.Unsafe;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
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
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

class MacOSXWatchService extends AbstractWatchService {
    private final HashMap<Object, MacOSXWatchKey> dirKeyToWatchKey      = new HashMap<>();
    private final HashMap<Long, MacOSXWatchKey>   eventStreamToWatchKey = new HashMap<>();
    private final Object                          watchKeysLock         = new Object();

    private final CFRunLoopThread runLoopThread;

    MacOSXWatchService() throws IOException {
        runLoopThread = new CFRunLoopThread();
        runLoopThread.setDaemon(true);
        runLoopThread.start();

        try {
            // In order to be able to schedule any FSEventStream's, a reference to a run loop is required.
            runLoopThread.waitForRunLoopRef();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @SuppressWarnings("removal")
    @Override
    WatchKey register(Path dir, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        checkIsOpen();

        final UnixPath unixDir = (UnixPath)dir;
        final Object dirKey    = checkPath(unixDir);
        final EnumSet<FSEventKind>   eventSet    = FSEventKind.setOf(events);
        final EnumSet<WatchModifier> modifierSet = WatchModifier.setOf(modifiers);

        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<MacOSXWatchKey>() {
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

    private MacOSXWatchKey doPrivilegedRegister(UnixPath unixDir, Object dirKey, EnumSet<FSEventKind> eventSet, EnumSet<WatchModifier> modifierSet) throws IOException {
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
                watchKeysLock.notify(); // So that run loop gets running again if stopped due to lack of event streams
                return watchKey;
            }
        }
    }

    /**
     * Invoked on the CFRunLoopThread by the native code to report directories that need to be re-scanned.
     */
    private void callback(final long eventStreamRef, final String[] paths, final long eventFlagsPtr) {
        synchronized (watchKeysLock) {
            final MacOSXWatchKey watchKey = eventStreamToWatchKey.get(eventStreamRef);
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

    private class CFRunLoopThread extends Thread {
        // Native reference to the CFRunLoop object of the watch service run loop.
        private long runLoopRef;

        public CFRunLoopThread() {
            super("FileSystemWatcher");
        }

        private synchronized void waitForRunLoopRef() throws InterruptedException {
            if (runLoopRef == 0)
                runLoopThread.wait(); // ...for CFRunLoopRef to become available
        }

        long getRunLoopRef() {
            return runLoopRef;
        }

        synchronized void runLoopStop() {
            if (runLoopRef != 0) {
                // The run loop may have stuck in CFRunLoopRun() even though all of its input sources
                // have been removed. Need to terminate it explicitly so that it can run to completion.
                MacOSXWatchService.CFRunLoopStop(runLoopRef);
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                runLoopRef = CFRunLoopGetCurrent();
                notify(); // ... of CFRunLoopRef availability
            }

            while (isOpen()) {
                CFRunLoopRun(MacOSXWatchService.this);
                waitForEventSource();
            }

            synchronized (this) {
                runLoopRef = 0; // CFRunLoopRef is no longer usable when the loop has been terminated
            }
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
        FILE_TREE, SENSITIVITY_HIGH, SENSITIVITY_MEDIUM, SENSITIVITY_LOW;

        public static WatchModifier of(final WatchEvent.Modifier watchEventModifier) {
            if (ExtendedOptions.FILE_TREE.matches(watchEventModifier)) {
                return FILE_TREE;
            } if (ExtendedOptions.SENSITIVITY_HIGH.matches(watchEventModifier)) {
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
        private static final Unsafe unsafe = Unsafe.getUnsafe();

        private static final long kFSEventStreamEventFlagMustScanSubDirs = 0x00000001;
        private static final long kFSEventStreamEventFlagRootChanged     = 0x00000020;

        private final static Path relativeRootPath = Path.of("");

        // Full path to this key's watch root directory.
        private final Path   realRootPath;
        private final int    realRootPathLength;
        private final Object rootPathKey;

        // Kinds of events to be reported.
        private EnumSet<FSEventKind> eventsToWatch;

        // Should events in directories below realRootPath reported?
        private boolean watchFileTree;

        // Native FSEventStreamRef as returned by FSEventStreamCreate().
        private long         eventStreamRef;
        private final Object eventStreamRefLock = new Object();

        private final DirectoryTreeSnapshot directoryTreeSnapshot = new DirectoryTreeSnapshot();

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
            this.watchFileTree = modifierSet.contains(WatchModifier.FILE_TREE);

            directoryTreeSnapshot.build();

            synchronized (eventStreamRefLock) {
                final int kFSEventStreamCreateFlagWatchRoot  = 0x00000004;
                eventStreamRef = MacOSXWatchService.eventStreamCreate(
                        realRootPath.toString(),
                        WatchModifier.sensitivityOf(modifierSet),
                        kFSEventStreamCreateFlagWatchRoot);

                if (eventStreamRef == 0)
                    throw new IOException("Unable to create FSEventStream");

                MacOSXWatchService.eventStreamSchedule(eventStreamRef, runLoopThread.getRunLoopRef());
            }
        }

        synchronized void disable() {
            invalidate();
            directoryTreeSnapshot.reset();
        }

        synchronized void handleEvents(final String[] paths, long eventFlagsPtr) {
            if (paths == null) {
                reportOverflow(null);
                return;
            }

            final Set<Path> dirsToScan = new LinkedHashSet<>(paths.length);
            final Set<Path> dirsToScanRecursively = new LinkedHashSet<>();
            collectDirsToScan(paths, eventFlagsPtr, dirsToScan, dirsToScanRecursively);

            for (final Path recurseDir : dirsToScanRecursively) {
                dirsToScan.removeIf(dir -> dir.startsWith(recurseDir));
                directoryTreeSnapshot.update(recurseDir, true);
            }

            for (final Path dir : dirsToScan) {
                directoryTreeSnapshot.update(dir, false);
            }
        }

        private Path toRelativePath(final String absPath) {
            return   (absPath.length() > realRootPathLength)
                    ? Path.of(absPath.substring(realRootPathLength))
                    : relativeRootPath;
        }

        private void collectDirsToScan(final String[] paths, long eventFlagsPtr,
                                       final Set<Path> dirsToScan,
                                       final Set<Path> dirsToScanRecursively) {
            for (final String absPath : paths) {
                if (absPath == null) {
                    reportOverflow(null);
                    continue;
                }

                Path path = toRelativePath(absPath);

                if (!watchFileTree && !relativeRootPath.equals(path)) {
                    continue;
                }

                final int flags = unsafe.getInt(eventFlagsPtr);
                if ((flags & kFSEventStreamEventFlagRootChanged) != 0) {
                    cancel();
                    signal();
                    break;
                } else if ((flags & kFSEventStreamEventFlagMustScanSubDirs) != 0 && watchFileTree) {
                    dirsToScanRecursively.add(path);
                } else {
                    dirsToScan.add(path);
                }

                final long SIZEOF_FS_EVENT_STREAM_EVENT_FLAGS = 4L; // FSEventStreamEventFlags is UInt32
                eventFlagsPtr += SIZEOF_FS_EVENT_STREAM_EVENT_FLAGS;
            }
        }

        /**
         * Represents a snapshot of a directory tree.
         * The snapshot includes subdirectories iff <code>watchFileTree</code> is <code>true</code>.
         */
        private class DirectoryTreeSnapshot {
            private final HashMap<Path, DirectorySnapshot> snapshots;

            DirectoryTreeSnapshot() {
                this.snapshots = new HashMap<>(watchFileTree ? 256 : 1);
            }

            void build() throws IOException {
                final Queue<Path> pathToDo = new ArrayDeque<>();
                pathToDo.offer(relativeRootPath);

                while (!pathToDo.isEmpty()) {
                    final Path path = pathToDo.poll();
                    try {
                        createForOneDirectory(path, watchFileTree ? pathToDo : null);
                    } catch (IOException e) {
                        final boolean exceptionForRootPath = relativeRootPath.equals(path);
                        if (exceptionForRootPath)
                            throw e; // report to the user as the watch root may have disappeared

                        // Ignore for sub-directories as some may have been removed during the scan.
                        // That's OK, those kinds of changes in the directory hierarchy is what
                        // WatchService is used for. However, it's impossible to catch all changes
                        // at this point, so we may fail to report some events that had occurred before
                        // FSEventStream has been created to watch for those changes.
                    }
                }
            }

            private DirectorySnapshot createForOneDirectory(
                    final Path directory,
                    final Queue<Path> newDirectoriesFound) throws IOException {
                final DirectorySnapshot snapshot = DirectorySnapshot.create(getRealRootPath(), directory);
                snapshots.put(directory, snapshot);
                if (newDirectoriesFound != null)
                    snapshot.forEachDirectory(newDirectoriesFound::offer);

                return snapshot;
            }

            void reset() {
                snapshots.clear();
            }

            void update(final Path directory, final boolean recurse) {
                if (!recurse) {
                    directoryTreeSnapshot.update(directory, null);
                } else {
                    final Queue<Path> pathToDo = new ArrayDeque<>();
                    pathToDo.offer(directory);
                    while (!pathToDo.isEmpty()) {
                        final Path dir = pathToDo.poll();
                        directoryTreeSnapshot.update(dir, pathToDo);
                    }
                }
            }

            private void update(final Path directory, final Queue<Path> modifiedDirs) {
                final DirectorySnapshot snapshot = snapshots.get(directory);
                if (snapshot == null) {
                    // This means that we missed a notification about an update of our parent.
                    // Report overflow (who knows what else we weren't notified about?) and
                    // do our best to recover from this mess by queueing our parent for an update.
                    reportOverflow(directory);
                    if (modifiedDirs != null)
                        modifiedDirs.offer(getParentOf(directory));

                    return;
                }

                // FSEvents API does not generate events for directories that got moved from/to the directory
                // being watched, so we have to watch for new/deleted directories ourselves. If we still
                // receive an event for, say, one of the new directories, it won't be reported again as this
                // will count as refresh with no modifications detected.
                final Queue<Path> createdDirs = new ArrayDeque<>();
                final Queue<Path> deletedDirs = new ArrayDeque<>();
                snapshot.update(MacOSXWatchKey.this, createdDirs, deletedDirs, modifiedDirs);

                handleNewDirectories(createdDirs);
                handleDeletedDirectories(deletedDirs);
            }

            private Path getParentOf(final Path directory) {
                Path parent = directory.getParent();
                if (parent == null)
                    parent = relativeRootPath;
                return parent;
            }

            private void handleDeletedDirectories(final Queue<Path> deletedDirs) {
                // We don't know the exact sequence in which these were deleted,
                // so at least maintain a sensible order, i.e. children are deleted before the parent.
                final LinkedList<Path> dirsToReportDeleted = new LinkedList<>();
                while (!deletedDirs.isEmpty()) {
                    final Path path = deletedDirs.poll();
                    dirsToReportDeleted.addFirst(path);
                    final DirectorySnapshot directorySnapshot = snapshots.get(path);
                    if (directorySnapshot != null) // May be null if we're not watching the whole file tree.
                        directorySnapshot.forEachDirectory(deletedDirs::offer);
                }

                for(final Path path : dirsToReportDeleted) {
                    final DirectorySnapshot directorySnapshot = snapshots.remove(path);
                    if (directorySnapshot != null) {
                        // This is needed in case a directory tree was moved (mv -f) out of this directory.
                        directorySnapshot.forEachFile(MacOSXWatchKey.this::reportDeleted);
                    }
                    reportDeleted(path);
                }
            }

            private void handleNewDirectories(final Queue<Path> createdDirs) {
                // We don't know the exact sequence in which these were created,
                // so at least maintain a sensible order, i.e. the parent created before its children.
                while (!createdDirs.isEmpty()) {
                    final Path path = createdDirs.poll();
                    reportCreated(path);
                    if (watchFileTree) {
                        if (!snapshots.containsKey(path)) {
                            // Happens when a directory tree gets moved (mv -f) into this directory.
                            DirectorySnapshot newSnapshot = null;
                            try {
                                newSnapshot = createForOneDirectory(path, createdDirs);
                            } catch(IOException ignore) { }

                            if (newSnapshot != null)
                                newSnapshot.forEachFile(MacOSXWatchKey.this::reportCreated);
                        }
                    }
                }
            }
        }

        /**
         * Represents a snapshot of a directory with a millisecond precision timestamp of the last modification.
         */
        private static class DirectorySnapshot {
            // Path to this directory relative to the watch root.
            private final Path directory;

            // Maps file names to their attributes.
            private final Map<Path, Entry> files;

            // A counter to keep track of files that have disappeared since the last run.
            private long currentTick;

            private DirectorySnapshot(final Path directory) {
                this.directory = directory;
                this.files     = new HashMap<>();
            }

            static DirectorySnapshot create(final Path realRootPath, final Path directory) throws IOException {
                final DirectorySnapshot snapshot = new DirectorySnapshot(directory);
                try (final DirectoryStream<Path> directoryStream
                             = Files.newDirectoryStream(realRootPath.resolve(directory))) {
                    for (final Path file : directoryStream) {
                        try {
                            final BasicFileAttributes attrs = Files.readAttributes(
                                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            final Entry entry = new Entry(
                                    attrs.isDirectory(), attrs.lastModifiedTime().toMillis(), 0);
                            snapshot.files.put(file.getFileName(), entry);
                        } catch (IOException ignore) {}
                    }
                } catch (DirectoryIteratorException e) {
                    throw e.getCause();
                }

                return snapshot;
            }

            void forEachDirectory(final Consumer<Path> consumer) {
                files.forEach((path, entry) -> { if (entry.isDirectory) consumer.accept(directory.resolve(path)); } );
            }

            void forEachFile(final Consumer<Path> consumer) {
                files.forEach((path, entry) -> { if (!entry.isDirectory) consumer.accept(directory.resolve(path)); } );
            }

            void update(final MacOSXWatchKey watchKey,
                        final Queue<Path> createdDirs,
                        final Queue<Path> deletedDirs,
                        final Queue<Path> modifiedDirs) {
                currentTick++;

                try (final DirectoryStream<Path> directoryStream
                             = Files.newDirectoryStream(watchKey.getRealRootPath().resolve(directory))) {
                    for (final Path file : directoryStream) {
                        try {
                            final BasicFileAttributes attrs
                                    = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            final Path fileName     = file.getFileName();
                            final Entry entry       = files.get(fileName);
                            final boolean isNew     = (entry == null);
                            final long lastModified = attrs.lastModifiedTime().toMillis();
                            final Path relativePath = directory.resolve(fileName);

                            if (attrs.isDirectory()) {
                                if (isNew) {
                                    files.put(fileName, new Entry(true, lastModified, currentTick));
                                    if (createdDirs != null) createdDirs.offer(relativePath);
                                } else {
                                    if (!entry.isDirectory) { // Used to be a file, now a directory
                                        if (createdDirs != null) createdDirs.offer(relativePath);

                                        files.put(fileName, new Entry(true, lastModified, currentTick));
                                        watchKey.reportDeleted(relativePath);
                                    } else if (entry.isModified(lastModified)) {
                                        if (modifiedDirs != null) modifiedDirs.offer(relativePath);
                                        watchKey.reportModified(relativePath);
                                    }
                                    entry.update(lastModified, currentTick);
                                }
                            } else {
                                if (isNew) {
                                    files.put(fileName, new Entry(false, lastModified, currentTick));
                                    watchKey.reportCreated(relativePath);
                                } else {
                                    if (entry.isDirectory) { // Used to be a directory, now a file.
                                        if (deletedDirs != null) deletedDirs.offer(relativePath);

                                        files.put(fileName, new Entry(false, lastModified, currentTick));
                                        watchKey.reportCreated(directory.resolve(fileName));
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

                checkDeleted(watchKey, deletedDirs);
            }

            private void checkDeleted(final MacOSXWatchKey watchKey, final Queue<Path> deletedDirs) {
                final Iterator<Map.Entry<Path, Entry>> it = files.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<Path, Entry> mapEntry = it.next();
                    final Entry entry = mapEntry.getValue();
                    if (entry.lastTickCount != currentTick) {
                        final Path file = mapEntry.getKey();
                        it.remove();

                        if (entry.isDirectory) {
                            if (deletedDirs != null) deletedDirs.offer(directory.resolve(file));
                        } else {
                            watchKey.reportDeleted(directory.resolve(file));
                        }
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
