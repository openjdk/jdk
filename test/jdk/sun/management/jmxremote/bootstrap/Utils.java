/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class.
 */
public class Utils {
    static private String fileSeparator = System.getProperty("file.separator");

    /**
     * Converts slashes in a pathname to backslashes
     * if slashes is not the file separator.
     */
    static String convertPath(String path) {
        // No need to do the conversion if file separator is '/'
        if (fileSeparator.length() == 1 && fileSeparator.charAt(0) == '/') {
            return path;
        }

        char[] cs = path.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] == '/') {
                cs[i] = '\\';
            }
        }
        String newPath = new String(cs);
        return newPath;
    }

    /**
     * Return file directories that satisfy the specified filter.
     *
     * @param searchDirectory the base directory to search
     * @param filter          a filename filter
     * @return                file directories
     */
    public static List<Path> findFiles(Path searchDirectory,
            FilenameFilter filter) {
        return Arrays.stream(searchDirectory.toFile().listFiles(filter))
                .map(f -> f.toPath())
                .collect(Collectors.toList());
    }

    /**
     * Copy files to the target path.
     *
     * @param source         the paths to the files to copy
     * @param target         the path to the target files
     * @param filenameMapper mapper function applied to filenames
     * @param options        options specifying how the copy should be done
     * @return               the paths to the target files
     * @throws IOException   if error occurs
     */
    public static List<Path> copyFiles(List<Path> source, Path target,
            Function<String, String> filenameMapper,
            CopyOption... options) throws IOException {
        List<Path> result = new ArrayList<>();

        if (!target.toFile().exists()) {
            Files.createDirectory(target);
        }

        for (Path file : source) {
            if (!file.toFile().exists()) {
                continue;
            }

            String baseName = file.getFileName().toString();

            Path targetFile = target.resolve(filenameMapper.apply(baseName));
            Files.copy(file, targetFile, options);
            result.add(targetFile);
        }
        return result;
    }

    /**
     * Copy files to the target path.
     *
     * @param source         the paths to the files to copy
     * @param target         the path to the target files
     * @param options        options specifying how the copy should be done
     * @return               the paths to the target files
     * @throws IOException   if error occurs
     */
    public static List<Path> copyFiles(List<Path> source, Path target,
            CopyOption... options) throws IOException {
        return copyFiles(source, target, (s) -> s, options);
    }

    /**
     * Return an ACL entry that revokes owner access.
     *
     * @param acl   original ACL entry to build from
     * @return      an ACL entry that revokes all access
     */
    public static AclEntry revokeAccess(AclEntry acl) {
        return buildAclEntry(acl, AclEntryType.DENY);
    }

    /**
     * Return an ACL entry that allow owner access.
     * @param acl   original ACL entry to build from
     * @return      an ACL entry that allows all access
     */
    public static AclEntry allowAccess(AclEntry acl) {
        return buildAclEntry(acl, AclEntryType.ALLOW);
    }

    /**
     * Build an ACL entry with a given ACL entry type.
     *
     * @param acl   original ACL entry to build from
     * @return      an ACL entry with a given ACL entry type
     */
    public static AclEntry buildAclEntry(AclEntry acl, AclEntryType type) {
        return AclEntry.newBuilder()
                .setType(type)
                .setPrincipal(acl.principal())
                .setPermissions(acl.permissions())
                .build();
    }

    /**
     * Replace file string by applying the given mapper function.
     *
     * @param source        the file to read
     * @param contentMapper the mapper function applied to file's content
     * @throws IOException  if an I/O error occurs
     */
    public static void replaceFileString(Path source,
            Function<String, String> contentMapper) throws IOException {
        StringBuilder sb = new StringBuilder();
        String lineSep = System.getProperty("line.separator");

        try (BufferedReader reader =
                new BufferedReader(new FileReader(source.toFile()))) {

            String line;

            // read all and replace all at once??
            while ((line = reader.readLine()) != null) {
                sb.append(contentMapper.apply(line))
                        .append(lineSep);
            }
        }

        try (FileWriter writer = new FileWriter(source.toFile())) {
            writer.write(sb.toString());
        }
    }

    /**
     * Replace files' string by applying the given mapper function.
     *
     * @param source        the file to read
     * @param contentMapper the mapper function applied to files' content
     * @throws IOException  if an I/O error occurs
     */
    public static void replaceFilesString(List<Path> source,
            Function<String, String> contentMapper) throws IOException {
        for (Path file : source) {
            replaceFileString(file, contentMapper);
        }
    }
}
