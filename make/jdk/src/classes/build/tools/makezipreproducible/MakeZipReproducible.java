/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.makezipreproducible;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;

/**
 * Generate a zip file in a "reproducible" manner from the input zip file.
 * Standard zip tools rely on OS file list querying whose ordering can vary
 * by platform architecture, this class ensures the zip entries are ordered
 * and also supports SOURCE_DATE_EPOCH timestamps which will set the ZipEntry
 * local time in UTC.
 */
public class MakeZipReproducible {
    String input_file = null;
    String fname = null;
    String zname = "";
    LocalDateTime timestamp = null;
    boolean verbose = false;

    // Keep a sorted Set of ZipEntrys to be processed, so that the zip is reproducible
    SortedMap<String, ZipEntry> entries  = new TreeMap<String, ZipEntry>();

    private boolean ok;

    public MakeZipReproducible() {
    }

    public synchronized boolean run(String args[]) {
        ok = true;
        if (!parseArgs(args)) {
            return false;
        }
        try {
            zname = fname.replace(File.separatorChar, '/');
            if (zname.startsWith("./")) {
                zname = zname.substring(2);
            }

            if (verbose) System.out.println("Input zip file: " + input_file);

            File inFile  = new File(input_file);
            if (!inFile.exists()) {
                error("Input zip file does not exist");
                ok = false;
            } else {
                File zipFile = new File(fname);
                // Check archive to create does not exist
                if (!zipFile.exists()) {
                    // Process input ZipEntries
                    ok = processInputEntries(inFile);
                    if (ok) {
                        try (FileOutputStream out = new FileOutputStream(fname)) {
                            ok = create(inFile, new BufferedOutputStream(out, 4096));
                        }
                    } else {
                    }
                } else {
                    error("Target zip file "+fname+" already exists.");
                    ok = false;
                }
            }
        } catch (IOException e) {
            fatalError(e);
            ok = false;
        } catch (Error ee) {
            ee.printStackTrace();
            ok = false;
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        return ok;
    }

    boolean parseArgs(String args[]) {
        try {
            boolean parsingIncludes = false;
            boolean parsingExcludes = false;
            int count = 0;
            while(count < args.length) {
                if (args[count].startsWith("-")) {
                    String flag = args[count].substring(1);
                    switch (flag.charAt(0)) {
                    case 'f':
                        fname = args[++count];
                        break;
                    case 't':
                        // SOURCE_DATE_EPOCH timestamp specified
                        long epochSeconds = Long.parseLong(args[++count]);
                        Instant instant = Instant.ofEpochSecond(epochSeconds);
                        timestamp = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                        break;
                    case 'v':
                        verbose = true;
                        break;
                    default:
                        error(String.format("Illegal option -%s", String.valueOf(flag.charAt(0))));
                        usageError();
                        return false;
                    }
                } else {
                    // input zip file
                    if (input_file != null) {
                        error("Input zip file already specified");
                        usageError();
                        return false;
                    }
                    input_file = args[count];
                }
                count++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usageError();
            return false;
        } catch (NumberFormatException e) {
            usageError();
            return false;
        }
        if (fname == null) {
            error("-f <outputArchiveName> must be specified");
            usageError();
            return false;
        }
        // If no files specified then default to current directory
        if (input_file == null) {
            error("No input zip file specified");
            usageError();
            return false;
        }

        return true;
    }

    // Process input zip file and add to sorted entries set
    boolean processInputEntries(File inFile) throws IOException {
        ZipFile zipFile = new ZipFile(inFile);
        zipFile.stream().forEach(entry -> entries.put(entry.getName(), entry));

        return true;
    }

    // Create new zip from entries
    boolean create(File inFile, OutputStream out) throws IOException
    {
        try (ZipFile zipFile = new ZipFile(inFile);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, ZipEntry> entry : entries.entrySet()) {
                ZipEntry zipEntry = entry.getValue();
                if (zipEntry.getSize() > 0) {
                    try (InputStream eis = zipFile.getInputStream(zipEntry)) {
                        addEntry(zos, zipEntry, eis);
                    }
                } else {
                    addEntry(zos, zipEntry, null);
                }
            }
        }
        return true;
    }

    // Add Entry and data to Zip
    void addEntry(ZipOutputStream zos, ZipEntry entry, InputStream entryInputStream) throws IOException {
        if (verbose) {
            System.out.println("Adding: "+entry.getName());
        }

        // Set to specified timestamp if set otherwise leave as original lastModified time
        if (timestamp != null) {
            entry.setTimeLocal(timestamp);
        }

        // Ensure "extra" field is not set from original ZipEntry info that may be not deterministic
        // eg.may contain specific UID/GID
        entry.setExtra(null);

        zos.putNextEntry(entry);
        if (entry.getSize() > 0 && entryInputStream != null) {
            entryInputStream.transferTo(zos);
        }
        zos.closeEntry();
    }

    void usageError() {
        error(
        "Usage: MakeZipReproducible [-v] [-t <SOURCE_DATE_EPOCH>] -f <output_zip_file> <input_zip_file>\n" +
        "Options:\n" +
        "   -v  verbose output\n" +
        "   -f  specify archive file name to create\n" +
        "   -t  specific SOURCE_DATE_EPOCH value to use for timestamps\n" +
        "   input_zip_file re-written as a reproducible zip output_zip_file.\n");
    }

    void fatalError(Exception e) {
        e.printStackTrace();
    }

    protected void error(String s) {
        System.err.println(s);
    }

    public static void main(String args[]) {
        MakeZipReproducible z = new MakeZipReproducible();
        System.exit(z.run(args) ? 0 : 1);
    }
}

