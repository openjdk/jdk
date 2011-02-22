/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;

import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.util.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/** A cache for ZipFileIndex objects. */
public class ZipFileIndexCache {

    private final Map<File, ZipFileIndex> map =
            new HashMap<File, ZipFileIndex>();

    /** Get a shared instance of the cache. */
    private static ZipFileIndexCache sharedInstance;
    public synchronized static ZipFileIndexCache getSharedInstance() {
        if (sharedInstance == null)
            sharedInstance = new ZipFileIndexCache();
        return sharedInstance;
    }

    /** Get a context-specific instance of a cache. */
    public static ZipFileIndexCache instance(Context context) {
        ZipFileIndexCache instance = context.get(ZipFileIndexCache.class);
        if (instance == null)
            context.put(ZipFileIndexCache.class, instance = new ZipFileIndexCache());
        return instance;
    }

    /**
     * Returns a list of all ZipFileIndex entries
     *
     * @return A list of ZipFileIndex entries, or an empty list
     */
    public List<ZipFileIndex> getZipFileIndexes() {
        return getZipFileIndexes(false);
    }

    /**
     * Returns a list of all ZipFileIndex entries
     *
     * @param openedOnly If true it returns a list of only opened ZipFileIndex entries, otherwise
     *                   all ZipFileEntry(s) are included into the list.
     * @return A list of ZipFileIndex entries, or an empty list
     */
    public synchronized List<ZipFileIndex> getZipFileIndexes(boolean openedOnly) {
        List<ZipFileIndex> zipFileIndexes = new ArrayList<ZipFileIndex>();

        zipFileIndexes.addAll(map.values());

        if (openedOnly) {
            for(ZipFileIndex elem : zipFileIndexes) {
                if (!elem.isOpen()) {
                    zipFileIndexes.remove(elem);
                }
            }
        }

        return zipFileIndexes;
    }

    public synchronized ZipFileIndex getZipFileIndex(File zipFile,
            RelativeDirectory symbolFilePrefix,
            boolean useCache, String cacheLocation,
            boolean writeIndex) throws IOException {
        ZipFileIndex zi = getExistingZipIndex(zipFile);

        if (zi == null || (zi != null && zipFile.lastModified() != zi.zipFileLastModified)) {
            zi = new ZipFileIndex(zipFile, symbolFilePrefix, writeIndex,
                    useCache, cacheLocation);
            map.put(zipFile, zi);
        }
        return zi;
    }

    public synchronized ZipFileIndex getExistingZipIndex(File zipFile) {
        return map.get(zipFile);
    }

    public synchronized void clearCache() {
        map.clear();
    }

    public synchronized void clearCache(long timeNotUsed) {
        Iterator<File> cachedFileIterator = map.keySet().iterator();
        while (cachedFileIterator.hasNext()) {
            File cachedFile = cachedFileIterator.next();
            ZipFileIndex cachedZipIndex = map.get(cachedFile);
            if (cachedZipIndex != null) {
                long timeToTest = cachedZipIndex.lastReferenceTimeStamp + timeNotUsed;
                if (timeToTest < cachedZipIndex.lastReferenceTimeStamp || // Overflow...
                        System.currentTimeMillis() > timeToTest) {
                    map.remove(cachedFile);
                }
            }
        }
    }

    public synchronized void removeFromCache(File file) {
        map.remove(file);
    }

    /** Sets already opened list of ZipFileIndexes from an outside client
      * of the compiler. This functionality should be used in a non-batch clients of the compiler.
      */
    public synchronized void setOpenedIndexes(List<ZipFileIndex>indexes) throws IllegalStateException {
        if (map.isEmpty()) {
            String msg =
                    "Setting opened indexes should be called only when the ZipFileCache is empty. "
                    + "Call JavacFileManager.flush() before calling this method.";
            throw new IllegalStateException(msg);
        }

        for (ZipFileIndex zfi : indexes) {
            map.put(zfi.zipFile, zfi);
        }
    }
}
