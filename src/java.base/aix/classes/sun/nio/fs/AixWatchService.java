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
import jdk.internal.misc.Unsafe;

import static sun.nio.fs.UnixNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * AIX WatchService Implementation
 */
public class AixWatchService extends AbstractWatchService
{
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    Map<Path,AixWatchKey> registeredKeys;
    AhafsPoller poller;

    public static record PollEvent(AixWatchKey key, WatchEvent.Kind<?> kind, Optional<String> mContext) {}
    public static final WatchEvent.Kind<Path> POLL_ERROR = new WatchEvent.Kind<>() {
        @Override public String name() { return "POLL_ERROR"; }
        @Override public Class<Path> type() { return Path.class; }
        @Override public String toString() { return this.name(); }
    };

    public static class FatalException extends Exception
    {
        private static final long serialVersionUID = 1L;
        FatalException(String msg) { super(msg); }
        FatalException(String msg, Throwable e) { super(msg, e); }
    }

    public AixWatchService()
        throws IOException
    {
        super();
        this.registeredKeys = new HashMap<>();
        try {
            this.poller = new AhafsPoller(this);
            this.poller.start();
        } catch (FatalException e) {
            // Re-Throw as IOE so the exception conforms to the expected type
            throw new IOException(e);
        }
    }

    @Override
    WatchKey register(Path dir,
                      WatchEvent.Kind<?>[] events,
                      WatchEvent.Modifier... modifiers)
         throws IOException
    {
        // If path is already registered with this WatchService, re-register existing key.
        if (registeredKeys.containsKey(dir)) {
            AixWatchKey existingKey = registeredKeys.get(dir);
            if (existingKey.isValid()) {
                try {
                    return poller.reRegister(existingKey,
                                             new HashSet<>(Arrays.asList(events)),
                                             modifiers);
                } catch (AixWatchService.FatalException e) {
                    throw new IOException(e);
                }
            } else {
                registeredKeys.remove(existingKey);
            }
        }

        // O.W. create and register new key
        AixWatchKey key = (AixWatchKey)poller.register(dir, events, modifiers);
        registeredKeys.put(dir, key);
        return key;
    }

    @Override
    void implClose() throws IOException
    { poller.close(); }

 }
