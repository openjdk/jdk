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
import java.io.IOException;

import static sun.nio.fs.UnixConstants.*;

/**
 * AIX WatchKey Implementation
 */
public abstract class AixWatchKey extends AbstractWatchKey
{
    public static final int INVALID_WATCH_DESCRIPTOR = -1;

    protected int watchDescriptor;

    AixWatchKey(Path p, AbstractWatchService ws) { super(p, ws); }

    public static class TopLevelKey extends AixWatchKey
    {
        private Set<AixWatchKey.SubKey> subKeys;
        private Set<WatchEvent.Kind<?>> watchEventKinds;

        /**
         * TopLevelKeys may contain SubKeys
         */
        public TopLevelKey(Path topLevelPath, int watchDescriptor, Set<? extends WatchEvent.Kind<?>> events, AixWatchService ws)
        {
            super(topLevelPath, ws);
            this.watchDescriptor = watchDescriptor;
            this.watchEventKinds = new HashSet<>(events);
            this.subKeys = new HashSet<>();
        }

        public void replaceEvents(Set<? extends WatchEvent.Kind<?>> events)
        {
            this.watchEventKinds = new HashSet<>(events);
        }

        public Iterable<AixWatchKey.SubKey> subKeys()
        {
            return Set.copyOf(subKeys);
        }

        protected void addSubKeys(HashSet<AixWatchKey.SubKey> subKeys)
        {
            this.subKeys.addAll(subKeys);
        }

        protected void removeSubKey(AixWatchKey key)
        {
            subKeys.remove(key);
        }

        @Override
        protected TopLevelKey resolve()
        {
            return this;
        }

        @Override
        public boolean isWatching(WatchEvent.Kind<?> kind)
        {
            return this.watchEventKinds.contains(kind) ||
                StandardWatchEventKinds.OVERFLOW.equals(kind);
        }
    }

    public static class SubKey extends AixWatchKey
    {
        private TopLevelKey topLevelKey;

        public SubKey(Path subKeyPath, int watchDescriptor, TopLevelKey topLevelKey)
        {
            super(subKeyPath, topLevelKey.watcher());
            this.watchDescriptor = watchDescriptor;
            this.topLevelKey = topLevelKey;
        }

        @Override
        public TopLevelKey resolve()
        {
            return this.topLevelKey;
        }

        @Override
        public boolean isWatching(WatchEvent.Kind<?> kind)
        {
            return topLevelKey.isWatching(kind);
        }
    }

    protected abstract TopLevelKey resolve();

    public abstract boolean isWatching(WatchEvent.Kind<?> kind);

    public int watchDescriptor() { return Math.abs(this.watchDescriptor); }

    @Override
    public boolean isValid()
    {
        return this.watchDescriptor > 0;
    }

    public void invalidate()
    {
        this.watchDescriptor = -this.watchDescriptor;
    }

    @Override
    public void cancel()
    {
        if (isValid()) {
            ((AixWatchService)watcher()).poller.cancel(this);
            invalidate();
        }
    }


}
