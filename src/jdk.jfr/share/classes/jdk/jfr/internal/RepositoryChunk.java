/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Objects;

import jdk.jfr.internal.SecuritySupport.SafePath;

final class RepositoryChunk {
    private static final int MAX_CHUNK_NAMES = 100;

    static final Comparator<RepositoryChunk> END_TIME_COMPARATOR = new Comparator<RepositoryChunk>() {
        @Override
        public int compare(RepositoryChunk c1, RepositoryChunk c2) {
            return c1.endTime.compareTo(c2.endTime);
        }
    };

    private final SafePath repositoryPath;
    private final SafePath unFinishedFile;
    private final SafePath file;
    private final Instant startTime;
    private final RandomAccessFile unFinishedRAF;

    private Instant endTime = null; // unfinished
    private int refCount = 0;
    private long size;

    RepositoryChunk(SafePath path, Instant startTime) throws Exception {
        ZonedDateTime z = ZonedDateTime.now();
        String fileName = Repository.REPO_DATE_FORMAT.format(
                LocalDateTime.ofInstant(startTime, z.getZone()));
        this.startTime = startTime;
        this.repositoryPath = path;
        this.unFinishedFile = findFileName(repositoryPath, fileName, ".jfr");
        this.file = findFileName(repositoryPath, fileName, ".jfr");
        this.unFinishedRAF = SecuritySupport.createRandomAccessFile(unFinishedFile);
 //       SecuritySupport.touch(file);
    }

    private static SafePath findFileName(SafePath directory, String name, String extension) throws Exception {
        Path p = directory.toPath().resolve(name + extension);
        for (int i = 1; i < MAX_CHUNK_NAMES; i++) {
            SafePath s = new SafePath(p);
            if (!SecuritySupport.exists(s)) {
                return s;
            }
            String extendedName = String.format("%s_%02d%s", name, i, extension);
            p = directory.toPath().resolve(extendedName);
        }
        p = directory.toPath().resolve(name + "_" + System.currentTimeMillis() + extension);
        return SecuritySupport.toRealPath(new SafePath(p));
    }

    public SafePath getUnfinishedFile() {
        return unFinishedFile;
    }

    void finish(Instant endTime) {
        try {
            finishWithException(endTime);
        } catch (IOException e) {
            Logger.log(LogTag.JFR, LogLevel.ERROR, "Could not finish chunk. " + e.getClass() + " "+ e.getMessage());
        }
    }

    private void finishWithException(Instant endTime) throws IOException {
        unFinishedRAF.close();
        this.size = finish(unFinishedFile, file);
        this.endTime = endTime;
        Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, () -> "Chunk finished: " + file);
    }

    private static long finish(SafePath unFinishedFile, SafePath file) throws IOException {
        Objects.requireNonNull(unFinishedFile);
        Objects.requireNonNull(file);
        return SecuritySupport.getFileSize(file);
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    private void delete(SafePath f) {
        try {
            SecuritySupport.delete(f);
            Logger.log(LogTag.JFR, LogLevel.DEBUG, () -> "Repository chunk " + f + " deleted");
        } catch (IOException e) {
            // Probably happens because file is being streamed
            // on Windows where files in use can't be removed.
            Logger.log(LogTag.JFR, LogLevel.DEBUG, ()  -> "Repository chunk " + f + " could not be deleted: " + e.getMessage());
            if (f != null) {
                FilePurger.add(f);
            }
        }
    }

    private void destroy() {
        if (!isFinished()) {
            finish(Instant.MIN);
        }
        if (file != null) {
            delete(file);
        }
        try {
            unFinishedRAF.close();
        } catch (IOException e) {
            Logger.log(LogTag.JFR, LogLevel.ERROR, () -> "Could not close random access file: " + unFinishedFile.toString() + ". File will not be deleted due to: " + e.getMessage());
        }
    }

    public synchronized void use() {
        ++refCount;
        Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, () -> "Use chunk " + toString() + " ref count now " + refCount);
    }

    public synchronized void release() {
        --refCount;
        Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, () -> "Release chunk " + toString() + " ref count now " + refCount);
        if (refCount == 0) {
            destroy();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() {
        boolean destroy = false;
        synchronized (this) {
            if (refCount > 0) {
                destroy = true;
            }
        }
        if (destroy) {
            destroy();
        }
    }

    public long getSize() {
        return size;
    }

    public boolean isFinished() {
        return endTime != null;
    }

    @Override
    public String toString() {
        if (isFinished()) {
            return file.toString();
        }
        return unFinishedFile.toString();
    }

    ReadableByteChannel newChannel() throws IOException {
        if (!isFinished()) {
            throw new IOException("Chunk not finished");
        }
        return ((SecuritySupport.newFileChannelToRead(file)));
    }

    public boolean inInterval(Instant startTime, Instant endTime) {
        if (startTime != null && getEndTime().isBefore(startTime)) {
            return false;
        }
        if (endTime != null && getStartTime().isAfter(endTime)) {
            return false;
        }
        return true;
    }

    public SafePath getFile() {
        return file;
    }
}
