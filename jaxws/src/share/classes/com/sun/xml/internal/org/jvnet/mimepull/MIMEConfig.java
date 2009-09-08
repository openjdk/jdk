/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.File;
import java.io.IOException;

/**
 * Configuration for MIME message parsing and storing.
 *
 * @author Jitendra Kotamraju
 */
public class MIMEConfig {

    private static final int DEFAULT_CHUNK_SIZE = 8192;
    private static final long DEFAULT_MEMORY_THRESHOLD = 1048576L;
    private static final String DEFAULT_FILE_PREFIX = "MIME";

    // Parses the entire message eagerly
    boolean parseEagerly;

    // Approximate Chunk size
    int chunkSize;

    // Maximum in-memory data per attachment
    long memoryThreshold;

    // Do not store to disk
    boolean onlyMemory;

    // temp Dir to store large files
    File tempDir;
    String prefix;
    String suffix;


    private MIMEConfig(boolean parseEagerly, int chunkSize,
                       long inMemoryThreshold, String dir, String prefix, String suffix) {
        this.parseEagerly = parseEagerly;
        this.chunkSize = chunkSize;
        this.memoryThreshold = inMemoryThreshold;
        this.prefix = prefix;
        this.suffix = suffix;
        setDir(dir);
    }

    public MIMEConfig() {
        this(false, DEFAULT_CHUNK_SIZE, DEFAULT_MEMORY_THRESHOLD, null,
                DEFAULT_FILE_PREFIX, null);
    }

    boolean isParseEagerly() {
        return parseEagerly;
    }

    public void setParseEagerly(boolean parseEagerly) {
        this.parseEagerly = parseEagerly;
    }

    int getChunkSize() {
        return chunkSize;
    }

    void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    long getMemoryThreshold() {
        return memoryThreshold;
    }

    /**
     * If the attachment is greater than the threshold, it is
     * written to the disk.
     *
     * @param memoryThreshold no of bytes per attachment
     *        if -1, then the whole attachment is kept in memory
     */
    public void setMemoryThreshold(long memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    boolean isOnlyMemory() {
        return memoryThreshold == -1L;
    }

    File getTempDir() {
        return tempDir;
    }

    String getTempFilePrefix() {
        return prefix;
    }

    String getTempFileSuffix() {
        return suffix;
    }

    /**
     * @param dir
     */
    public void setDir(String dir) {
        if (tempDir == null && dir != null && !dir.equals("")) {
            tempDir = new File(dir);
        }
    }

    /**
     * Validates if it can create temporary files. Otherwise, it stores
     * attachment contents in memory.
     */
    public void validate() {
        if (!isOnlyMemory()) {
            try {
                File tempFile = (tempDir == null)
                        ? File.createTempFile(prefix, suffix)
                        : File.createTempFile(prefix, suffix, tempDir);
                tempFile.delete();
            } catch(Exception ioe) {
                memoryThreshold = -1L;      // whole attachment will be in-memory
            }
        }
    }

}
