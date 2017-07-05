/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.io.IOException;
import jdk.internal.misc.Unsafe;

import static sun.nio.fs.UnixConstants.*;

/**
 * Solaris implementation of WatchService based on file events notification
 * facility.
 */

class SolarisWatchService
    extends AbstractWatchService
{
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static int addressSize = unsafe.addressSize();

    private static int dependsArch(int value32, int value64) {
        return (addressSize == 4) ? value32 : value64;
    }

    /*
     * typedef struct port_event {
     *     int             portev_events;
     *     ushort_t        portev_source;
     *     ushort_t        portev_pad;
     *     uintptr_t       portev_object;
     *     void            *portev_user;
     * } port_event_t;
     */
    private static final int SIZEOF_PORT_EVENT  = dependsArch(16, 24);
    private static final int OFFSETOF_EVENTS    = 0;
    private static final int OFFSETOF_SOURCE    = 4;
    private static final int OFFSETOF_OBJECT    = 8;

    /*
     * typedef struct file_obj {
     *     timestruc_t     fo_atime;
     *     timestruc_t     fo_mtime;
     *     timestruc_t     fo_ctime;
     *     uintptr_t       fo_pad[3];
     *     char            *fo_name;
     * } file_obj_t;
     */
    private static final int SIZEOF_FILEOBJ    = dependsArch(40, 80);
    private static final int OFFSET_FO_NAME    = dependsArch(36, 72);

    // port sources
    private static final short PORT_SOURCE_USER     = 3;
    private static final short PORT_SOURCE_FILE     = 7;

    // user-watchable events
    private static final int FILE_MODIFIED      = 0x00000002;
    private static final int FILE_ATTRIB        = 0x00000004;
    private static final int FILE_NOFOLLOW      = 0x10000000;

    // exception events
    private static final int FILE_DELETE        = 0x00000010;
    private static final int FILE_RENAME_TO     = 0x00000020;
    private static final int FILE_RENAME_FROM   = 0x00000040;
    private static final int UNMOUNTED          = 0x20000000;
    private static final int MOUNTEDOVER        = 0x40000000;

    // background thread to read change events
    private final Poller poller;

    SolarisWatchService(UnixFileSystem fs) throws IOException {
        int port = -1;
        try {
            port = portCreate();
        } catch (UnixException x) {
            throw new IOException(x.errorString());
        }

        this.poller = new Poller(fs, this, port);
        this.poller.start();
    }

    @Override
    WatchKey register(Path dir,
                      WatchEvent.Kind<?>[] events,
                      WatchEvent.Modifier... modifiers)
         throws IOException
    {
        // delegate to poller
        return poller.register(dir, events, modifiers);
    }

    @Override
    void implClose() throws IOException {
        // delegate to poller
        poller.close();
    }

    /**
     * WatchKey implementation
     */
    private class SolarisWatchKey extends AbstractWatchKey
        implements DirectoryNode
    {
        private final UnixFileKey fileKey;

        // pointer to native file_obj object
        private final long object;

        // events (may be changed). set to null when watch key is invalid
        private volatile Set<? extends WatchEvent.Kind<?>> events;

        // map of entries in directory; created lazily; accessed only by
        // poller thread.
        private Map<Path,EntryNode> children = new HashMap<>();

        SolarisWatchKey(SolarisWatchService watcher,
                        UnixPath dir,
                        UnixFileKey fileKey,
                        long object,
                        Set<? extends WatchEvent.Kind<?>> events)
        {
            super(dir, watcher);
            this.fileKey = fileKey;
            this.object = object;
            this.events = events;
        }

        UnixPath getDirectory() {
            return (UnixPath)watchable();
        }

        UnixFileKey getFileKey() {
            return fileKey;
        }

        @Override
        public long object() {
            return object;
        }

        void invalidate() {
            events = null;
        }

        Set<? extends WatchEvent.Kind<?>> events() {
            return events;
        }

        void setEvents(Set<? extends WatchEvent.Kind<?>> events) {
            this.events = events;
        }

        Map<Path,EntryNode> children() {
            return children;
        }

        @Override
        public boolean isValid() {
            return events != null;
        }

        @Override
        public void cancel() {
            if (isValid()) {
                // delegate to poller
                poller.cancel(this);
            }
        }

        @Override
        public void addChild(Path name, EntryNode node) {
            children.put(name, node);
        }

        @Override
        public void removeChild(Path name) {
            children.remove(name);
        }

        @Override
        public EntryNode getChild(Path name) {
            return children.get(name);
        }
    }

    /**
     * Background thread to read from port
     */
    private class Poller extends AbstractPoller {

        // maximum number of events to read per call to port_getn
        private static final int MAX_EVENT_COUNT            = 128;

        // events that map to ENTRY_DELETE
        private static final int FILE_REMOVED =
            (FILE_DELETE|FILE_RENAME_TO|FILE_RENAME_FROM);

        // events that tell us not to re-associate the object
        private static final int FILE_EXCEPTION =
            (FILE_REMOVED|UNMOUNTED|MOUNTEDOVER);

        // address of event buffers (used to receive events with port_getn)
        private final long bufferAddress;

        private final SolarisWatchService watcher;

        // the I/O port
        private final int port;

        // maps file key (dev/inode) to WatchKey
        private final Map<UnixFileKey,SolarisWatchKey> fileKey2WatchKey;

        // maps file_obj object to Node
        private final Map<Long,Node> object2Node;

        /**
         * Create a new instance
         */
        Poller(UnixFileSystem fs, SolarisWatchService watcher, int port) {
            this.watcher = watcher;
            this.port = port;
            this.bufferAddress =
                unsafe.allocateMemory(SIZEOF_PORT_EVENT * MAX_EVENT_COUNT);
            this.fileKey2WatchKey = new HashMap<UnixFileKey,SolarisWatchKey>();
            this.object2Node = new HashMap<Long,Node>();
        }

        @Override
        void wakeup() throws IOException {
            // write to port to wakeup polling thread
            try {
                portSend(port, 0);
            } catch (UnixException x) {
                throw new IOException(x.errorString());
            }
        }

        @Override
        Object implRegister(Path obj,
                            Set<? extends WatchEvent.Kind<?>> events,
                            WatchEvent.Modifier... modifiers)
        {
            // no modifiers supported at this time
            if (modifiers.length > 0) {
                for (WatchEvent.Modifier modifier: modifiers) {
                    if (modifier == null)
                        return new NullPointerException();
                    if (!ExtendedOptions.SENSITIVITY_HIGH.matches(modifier) &&
                            !ExtendedOptions.SENSITIVITY_MEDIUM.matches(modifier) &&
                            !ExtendedOptions.SENSITIVITY_LOW.matches(modifier)) {
                        return new UnsupportedOperationException("Modifier not supported");
                    }
                }
            }

            UnixPath dir = (UnixPath)obj;

            // check file is directory
            UnixFileAttributes attrs = null;
            try {
                attrs = UnixFileAttributes.get(dir, true);
            } catch (UnixException x) {
                return x.asIOException(dir);
            }
            if (!attrs.isDirectory()) {
                return new NotDirectoryException(dir.getPathForExceptionMessage());
            }

            // if already registered then update the events and return existing key
            UnixFileKey fileKey = attrs.fileKey();
            SolarisWatchKey watchKey = fileKey2WatchKey.get(fileKey);
            if (watchKey != null) {
                try {
                    updateEvents(watchKey, events);
                } catch (UnixException x) {
                    return x.asIOException(dir);
                }
                return watchKey;
            }

            // register directory
            long object = 0L;
            try {
                object = registerImpl(dir, (FILE_MODIFIED | FILE_ATTRIB));
            } catch (UnixException x) {
                return x.asIOException(dir);
            }

            // create watch key and insert it into maps
            watchKey = new SolarisWatchKey(watcher, dir, fileKey, object, events);
            object2Node.put(object, watchKey);
            fileKey2WatchKey.put(fileKey, watchKey);

            // register all entries in directory
            registerChildren(dir, watchKey, false, false);

            return watchKey;
        }

        // release resources for single entry
        void releaseChild(EntryNode node) {
            long object = node.object();
            if (object != 0L) {
               object2Node.remove(object);
               releaseObject(object, true);
               node.setObject(0L);
           }
        }

        // release resources for entries in directory
        void releaseChildren(SolarisWatchKey key) {
           for (EntryNode node: key.children().values()) {
               releaseChild(node);
           }
        }

        // cancel single key
        @Override
        void implCancelKey(WatchKey obj) {
           SolarisWatchKey key = (SolarisWatchKey)obj;
           if (key.isValid()) {
               fileKey2WatchKey.remove(key.getFileKey());

               // release resources for entries
               releaseChildren(key);

               // release resources for directory
               long object = key.object();
               object2Node.remove(object);
               releaseObject(object, true);

               // and finally invalidate the key
               key.invalidate();
           }
        }

        // close watch service
        @Override
        void implCloseAll() {
            // release all native resources
            for (Long object: object2Node.keySet()) {
                releaseObject(object, true);
            }

            // invalidate all keys
            for (Map.Entry<UnixFileKey,SolarisWatchKey> entry: fileKey2WatchKey.entrySet()) {
                entry.getValue().invalidate();
            }

            // clean-up
            object2Node.clear();
            fileKey2WatchKey.clear();

            // free global resources
            unsafe.freeMemory(bufferAddress);
            UnixNativeDispatcher.close(port);
        }

        /**
         * Poller main loop. Blocks on port_getn waiting for events and then
         * processes them.
         */
        @Override
        public void run() {
            try {
                for (;;) {
                    int n = portGetn(port, bufferAddress, MAX_EVENT_COUNT);
                    assert n > 0;

                    long address = bufferAddress;
                    for (int i=0; i<n; i++) {
                        boolean shutdown = processEvent(address);
                        if (shutdown)
                            return;
                        address += SIZEOF_PORT_EVENT;
                    }
                }
            } catch (UnixException x) {
                x.printStackTrace();
            }
        }

        /**
         * Process a single port_event
         *
         * Returns true if poller thread is requested to shutdown.
         */
        boolean processEvent(long address) {
            // pe->portev_source
            short source = unsafe.getShort(address + OFFSETOF_SOURCE);
            // pe->portev_object
            long object = unsafe.getAddress(address + OFFSETOF_OBJECT);
            // pe->portev_events
            int events = unsafe.getInt(address + OFFSETOF_EVENTS);

            // user event is trigger to process pending requests
            if (source != PORT_SOURCE_FILE) {
                if (source == PORT_SOURCE_USER) {
                    // process any pending requests
                    boolean shutdown = processRequests();
                    if (shutdown)
                        return true;
                }
                return false;
            }

            // lookup object to get Node
            Node node = object2Node.get(object);
            if (node == null) {
                // should not happen
                return false;
            }

            // As a workaround for 6642290 and 6636438/6636412 we don't use
            // FILE_EXCEPTION events to tell use not to register the file.
            // boolean reregister = (events & FILE_EXCEPTION) == 0;
            boolean reregister = true;

            // If node is EntryNode then event relates to entry in directory
            // If node is a SolarisWatchKey (DirectoryNode) then event relates
            // to a watched directory.
            boolean isDirectory = (node instanceof SolarisWatchKey);
            if (isDirectory) {
                processDirectoryEvents((SolarisWatchKey)node, events);
            } else {
                boolean ignore = processEntryEvents((EntryNode)node, events);
                if (ignore)
                    reregister = false;
            }

            // need to re-associate to get further events
            if (reregister) {
                try {
                    events = FILE_MODIFIED | FILE_ATTRIB;
                    if (!isDirectory) events |= FILE_NOFOLLOW;
                    portAssociate(port,
                                  PORT_SOURCE_FILE,
                                  object,
                                  events);
                } catch (UnixException x) {
                    // unable to re-register
                    reregister = false;
                }
            }

            // object is not re-registered so release resources. If
            // object is a watched directory then signal key
            if (!reregister) {
                // release resources
                object2Node.remove(object);
                releaseObject(object, false);

                // if watch key then signal it
                if (isDirectory) {
                    SolarisWatchKey key = (SolarisWatchKey)node;
                    fileKey2WatchKey.remove( key.getFileKey() );
                    key.invalidate();
                    key.signal();
                } else {
                    // if entry then remove it from parent
                    EntryNode entry = (EntryNode)node;
                    SolarisWatchKey key = (SolarisWatchKey)entry.parent();
                    key.removeChild(entry.name());
                }
            }

            return false;
        }

        /**
         * Process directory events. If directory is modified then re-scan
         * directory to register any new entries
         */
        void processDirectoryEvents(SolarisWatchKey key, int mask) {
            if ((mask & (FILE_MODIFIED | FILE_ATTRIB)) != 0) {
                registerChildren(key.getDirectory(), key,
                    key.events().contains(StandardWatchEventKinds.ENTRY_CREATE),
                    key.events().contains(StandardWatchEventKinds.ENTRY_DELETE));
            }
        }

        /**
         * Process events for entries in registered directories. Returns {@code
         * true} if events are ignored because the watch key has been cancelled.
         */
        boolean processEntryEvents(EntryNode node, int mask) {
            SolarisWatchKey key = (SolarisWatchKey)node.parent();
            Set<? extends WatchEvent.Kind<?>> events = key.events();
            if (events == null) {
                // key has been cancelled so ignore event
                return true;
            }

            // entry modified
            if (((mask & (FILE_MODIFIED | FILE_ATTRIB)) != 0) &&
                events.contains(StandardWatchEventKinds.ENTRY_MODIFY))
            {
                key.signalEvent(StandardWatchEventKinds.ENTRY_MODIFY, node.name());
            }


            return false;
        }

        /**
         * Registers all entries in the given directory
         *
         * The {@code sendCreateEvents} and {@code sendDeleteEvents} parameters
         * indicates if ENTRY_CREATE and ENTRY_DELETE events should be queued
         * when new entries are found. When initially registering a directory
         * they will always be false. When re-scanning a directory then it
         * depends on if the events are enabled or not.
         */
        void registerChildren(UnixPath dir,
                              SolarisWatchKey parent,
                              boolean sendCreateEvents,
                              boolean sendDeleteEvents)
        {
            boolean isModifyEnabled =
                parent.events().contains(StandardWatchEventKinds.ENTRY_MODIFY) ;

            // reset visited flag on entries so that we can detect file deletes
            for (EntryNode node: parent.children().values()) {
                node.setVisited(false);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry: stream) {
                    Path name = entry.getFileName();

                    // skip entry if already registered
                    EntryNode node = parent.getChild(name);
                    if (node != null) {
                        node.setVisited(true);
                        continue;
                    }

                    // new entry found

                    long object = 0L;
                    int errno = 0;
                    boolean addNode = false;

                    // if ENTRY_MODIFY enabled then we register the entry for events
                    if (isModifyEnabled) {
                        try {
                            UnixPath path = (UnixPath)entry;
                            int events = (FILE_NOFOLLOW | FILE_MODIFIED | FILE_ATTRIB);
                            object = registerImpl(path, events);
                            addNode = true;
                        } catch (UnixException x) {
                            errno = x.errno();
                        }
                    } else {
                        addNode = true;
                    }

                    if (addNode) {
                        // create node
                        node = new EntryNode(object, (UnixPath)entry.getFileName(), parent);
                        node.setVisited(true);
                        // tell the parent about it
                        parent.addChild(entry.getFileName(), node);
                        if (object != 0L)
                            object2Node.put(object, node);
                    }

                    // send ENTRY_CREATE event for the new file
                    // send ENTRY_DELETE event for files that were deleted immediately
                    boolean deleted = (errno == ENOENT);
                    if (sendCreateEvents && (addNode || deleted))
                        parent.signalEvent(StandardWatchEventKinds.ENTRY_CREATE, name);
                    if (sendDeleteEvents && deleted)
                        parent.signalEvent(StandardWatchEventKinds.ENTRY_DELETE, name);

                }
            } catch (DirectoryIteratorException | IOException x) {
                // queue OVERFLOW event so that user knows to re-scan directory
                parent.signalEvent(StandardWatchEventKinds.OVERFLOW, null);
                return;
            }

            // clean-up and send ENTRY_DELETE events for any entries that were
            // not found
            Iterator<Map.Entry<Path,EntryNode>> iterator =
                parent.children().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Path,EntryNode> entry = iterator.next();
                EntryNode node = entry.getValue();
                if (!node.isVisited()) {
                    long object = node.object();
                    if (object != 0L) {
                        object2Node.remove(object);
                        releaseObject(object, true);
                    }
                    if (sendDeleteEvents)
                        parent.signalEvent(StandardWatchEventKinds.ENTRY_DELETE, node.name());
                    iterator.remove();
                }
            }
        }

        /**
         * Update watch key's events. If ENTRY_MODIFY changes to be enabled
         * then register each file in the directory; If ENTRY_MODIFY changed to
         * be disabled then unregister each file.
         */
        void updateEvents(SolarisWatchKey key, Set<? extends WatchEvent.Kind<?>> events)
            throws UnixException
        {

            // update events, remembering if ENTRY_MODIFY was previously
            // enabled or disabled.
            boolean oldModifyEnabled = key.events()
                .contains(StandardWatchEventKinds.ENTRY_MODIFY);
            key.setEvents(events);

            // check if ENTRY_MODIFY has changed
            boolean newModifyEnabled = events
                .contains(StandardWatchEventKinds.ENTRY_MODIFY);
            if (newModifyEnabled != oldModifyEnabled) {
                UnixException ex = null;
                for (EntryNode node: key.children().values()) {
                    if (newModifyEnabled) {
                        // register
                        UnixPath path = key.getDirectory().resolve(node.name());
                        int ev = (FILE_NOFOLLOW | FILE_MODIFIED | FILE_ATTRIB);
                        try {
                            long object = registerImpl(path, ev);
                            object2Node.put(object, node);
                            node.setObject(object);
                        } catch (UnixException x) {
                            // if file has been deleted then it will be detected
                            // as a FILE_MODIFIED event on the directory
                            if (x.errno() != ENOENT) {
                                ex = x;
                                break;
                            }
                        }
                    } else {
                        // unregister
                        releaseChild(node);
                    }
                }

                // an error occurred
                if (ex != null) {
                    releaseChildren(key);
                    throw ex;
                }
            }
        }

        /**
         * Calls port_associate to register the given path.
         * Returns pointer to fileobj structure that is allocated for
         * the registration.
         */
        long registerImpl(UnixPath dir, int events)
            throws UnixException
        {
            // allocate memory for the path (file_obj->fo_name field)
            byte[] path = dir.getByteArrayForSysCalls();
            int len = path.length;
            long name = unsafe.allocateMemory(len+1);
            unsafe.copyMemory(path, Unsafe.ARRAY_BYTE_BASE_OFFSET, null,
                name, (long)len);
            unsafe.putByte(name + len, (byte)0);

            // allocate memory for filedatanode structure - this is the object
            // to port_associate
            long object = unsafe.allocateMemory(SIZEOF_FILEOBJ);
            unsafe.setMemory(null, object, SIZEOF_FILEOBJ, (byte)0);
            unsafe.putAddress(object + OFFSET_FO_NAME, name);

            // associate the object with the port
            try {
                portAssociate(port,
                              PORT_SOURCE_FILE,
                              object,
                              events);
            } catch (UnixException x) {
                // debugging
                if (x.errno() == EAGAIN) {
                    System.err.println("The maximum number of objects associated "+
                        "with the port has been reached");
                }

                unsafe.freeMemory(name);
                unsafe.freeMemory(object);
                throw x;
            }
            return object;
        }

        /**
         * Frees all resources for an file_obj object; optionally remove
         * association from port
         */
        void releaseObject(long object, boolean dissociate) {
            // remove association
            if (dissociate) {
                try {
                    portDissociate(port, PORT_SOURCE_FILE, object);
                } catch (UnixException x) {
                    // ignore
                }
            }

            // free native memory
            long name = unsafe.getAddress(object + OFFSET_FO_NAME);
            unsafe.freeMemory(name);
            unsafe.freeMemory(object);
        }
    }

    /**
     * A node with native (file_obj) resources
     */
    private static interface Node {
        long object();
    }

    /**
     * A directory node with a map of the entries in the directory
     */
    private static interface DirectoryNode extends Node {
        void addChild(Path name, EntryNode node);
        void removeChild(Path name);
        EntryNode getChild(Path name);
    }

    /**
     * An implementation of a node that is an entry in a directory.
     */
    private static class EntryNode implements Node {
        private long object;
        private final UnixPath name;
        private final DirectoryNode parent;
        private boolean visited;

        EntryNode(long object, UnixPath name, DirectoryNode parent) {
            this.object = object;
            this.name = name;
            this.parent = parent;
        }

        @Override
        public long object() {
            return object;
        }

        void setObject(long ptr) {
            this.object = ptr;
        }

        UnixPath name() {
            return name;
        }

        DirectoryNode parent() {
            return parent;
        }

        boolean isVisited() {
            return visited;
        }

        void setVisited(boolean v) {
            this.visited = v;
        }
    }

    // -- native methods --

    private static native void init();

    private static native int portCreate() throws UnixException;

    private static native void portAssociate(int port, int source, long object, int events)
        throws UnixException;

    private static native void portDissociate(int port, int source, long object)
        throws UnixException;

    private static native void portSend(int port, int events)
        throws UnixException;

    private static native int portGetn(int port, long address, int max)
        throws UnixException;

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("nio");
                return null;
        }});
        init();
    }
}
