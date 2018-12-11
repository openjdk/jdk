/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

/**
 * Miscellaneous methods for modifying and generating file system paths.
 */
public class PathUtilities {

    /**
     * Gets a value based on {@code name} that can be passed to {@link Paths#get(String, String...)}
     * without causing an {@link InvalidPathException}.
     *
     * @return {@code name} with all characters invalid for the current file system replaced by
     *         {@code '_'}
     */
    public static String sanitizeFileName(String name) {
        try {
            Path path = Paths.get(name);
            if (path.getNameCount() == 0) {
                return name;
            }
        } catch (InvalidPathException e) {
            // fall through
        }
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != File.separatorChar && c != ' ' && !Character.isISOControl(c)) {
                try {
                    Paths.get(String.valueOf(c));
                    buf.append(c);
                    continue;
                } catch (InvalidPathException e) {
                }
            }
            buf.append('_');
        }
        return buf.toString();
    }

    /**
     * A maximum file name length supported by most file systems. There is no platform independent
     * way to get this in Java. Normally it is 255. But for AUFS it is 242. Refer AUFS_MAX_NAMELEN
     * in http://aufs.sourceforge.net/aufs3/man.html.
     */
    private static final int MAX_FILE_NAME_LENGTH = 242;

    private static final String ELLIPSIS = "...";

    static Path createUnique(OptionValues options, OptionKey<String> baseNameOption, String id, String label, String ext, boolean createDirectory) throws IOException {
        String uniqueTag = "";
        int dumpCounter = 1;
        String prefix;
        if (id == null) {
            prefix = baseNameOption.getValue(options);
            int slash = prefix.lastIndexOf(File.separatorChar);
            prefix = prefix.substring(slash + 1);
        } else {
            prefix = id;
        }
        for (;;) {
            int fileNameLengthWithoutLabel = uniqueTag.length() + ext.length() + prefix.length() + "[]".length();
            int labelLengthLimit = MAX_FILE_NAME_LENGTH - fileNameLengthWithoutLabel;
            String fileName;
            if (labelLengthLimit < ELLIPSIS.length()) {
                // This means `id` is very long
                String suffix = uniqueTag + ext;
                int idLengthLimit = Math.min(MAX_FILE_NAME_LENGTH - suffix.length(), prefix.length());
                fileName = sanitizeFileName(prefix.substring(0, idLengthLimit) + suffix);
            } else {
                if (label == null) {
                    fileName = sanitizeFileName(prefix + uniqueTag + ext);
                } else {
                    String adjustedLabel = label;
                    if (label.length() > labelLengthLimit) {
                        adjustedLabel = label.substring(0, labelLengthLimit - ELLIPSIS.length()) + ELLIPSIS;
                    }
                    fileName = sanitizeFileName(prefix + '[' + adjustedLabel + ']' + uniqueTag + ext);
                }
            }
            Path dumpDir = DebugOptions.getDumpDirectory(options);
            Path result = Paths.get(dumpDir.toString(), fileName);
            try {
                if (createDirectory) {
                    return Files.createDirectory(result);
                } else {
                    try {
                        return Files.createFile(result);
                    } catch (AccessDeniedException e) {
                        /*
                         * Thrown on Windows if a directory with the same name already exists, so
                         * convert it to FileAlreadyExistsException if that's the case.
                         */
                        throw Files.isDirectory(result, NOFOLLOW_LINKS) ? new FileAlreadyExistsException(e.getFile()) : e;
                    }
                }
            } catch (FileAlreadyExistsException e) {
                uniqueTag = "_" + dumpCounter++;
            }
        }
    }

}
