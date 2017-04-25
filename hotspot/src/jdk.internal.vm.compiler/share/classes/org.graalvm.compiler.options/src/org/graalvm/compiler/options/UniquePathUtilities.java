/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class UniquePathUtilities {

    private static final AtomicLong globalTimeStamp = new AtomicLong();
    /**
     * This generates a per thread persistent id to aid mapping related dump files with each other.
     */
    private static final ThreadLocal<PerThreadSequence> threadDumpId = new ThreadLocal<>();
    private static final AtomicInteger dumpId = new AtomicInteger();

    static class PerThreadSequence {
        final int threadID;
        HashMap<String, Integer> sequences = new HashMap<>(2);

        PerThreadSequence(int threadID) {
            this.threadID = threadID;
        }

        String generateID(String extension) {
            Integer box = sequences.get(extension);
            if (box == null) {
                sequences.put(extension, 1);
                return Integer.toString(threadID);
            } else {
                sequences.put(extension, box + 1);
                return Integer.toString(threadID) + '-' + box;
            }
        }
    }

    private static String getThreadDumpId(String extension) {
        PerThreadSequence id = threadDumpId.get();
        if (id == null) {
            id = new PerThreadSequence(dumpId.incrementAndGet());
            threadDumpId.set(id);
        }
        return id.generateID(extension);
    }

    private static String formatExtension(String ext) {
        if (ext == null || ext.length() == 0) {
            return "";
        }
        return "." + ext;
    }

    public static long getGlobalTimeStamp() {
        if (globalTimeStamp.get() == 0) {
            globalTimeStamp.compareAndSet(0, System.currentTimeMillis());
        }
        return globalTimeStamp.get();
    }

    /**
     * Generates a {@link Path} using the format "%s-%d_%d%s" with the {@link OptionKey#getValue
     * base filename}, a {@link #globalTimeStamp global timestamp} , {@link #getThreadDumpId a per
     * thread unique id} and an optional {@code extension}.
     *
     * @return the output file path or null if the flag is null
     */
    public static Path getPath(OptionValues options, OptionKey<String> option, OptionKey<String> defaultDirectory, String extension) {
        return getPath(options, option, defaultDirectory, extension, true);
    }

    /**
     * Generate a {@link Path} using the format "%s-%d_%s" with the {@link OptionKey#getValue base
     * filename}, a {@link #globalTimeStamp global timestamp} and an optional {@code extension} .
     *
     * @return the output file path or null if the flag is null
     */
    public static Path getPathGlobal(OptionValues options, OptionKey<String> option, OptionKey<String> defaultDirectory, String extension) {
        return getPath(options, option, defaultDirectory, extension, false);
    }

    private static Path getPath(OptionValues options, OptionKey<String> option, OptionKey<String> defaultDirectory, String extension, boolean includeThreadId) {
        if (option.getValue(options) == null) {
            return null;
        }
        String ext = formatExtension(extension);
        final String name = includeThreadId
                        ? String.format("%s-%d_%s%s", option.getValue(options), getGlobalTimeStamp(), getThreadDumpId(ext), ext)
                        : String.format("%s-%d%s", option.getValue(options), getGlobalTimeStamp(), ext);
        Path result = Paths.get(name);
        if (result.isAbsolute() || defaultDirectory == null) {
            return result;
        }
        return Paths.get(defaultDirectory.getValue(options), name).normalize();
    }
}
