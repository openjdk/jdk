/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.generatezip;

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

/**
 * Generate a zip file in a "reproducible" manner from the input files or
 * directory.
 * Standard zip tools rely on OS file list querying whose ordering varies
 * by platform architecture, this class ensures the zip entries are ordered
 * and also supports SOURCE_DATE_EPOCH timestamps.
 */
public class GenerateZip {
    String fname = null;
    String zname = "";
    long   timestamp = -1L;
    List<String> files = new ArrayList<>();;
    boolean verbose = false;

    Set<File> entries = new LinkedHashSet<>();

    private boolean ok;

    public GenerateZip() {
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

            if (verbose) System.out.println("Files or directories to zip: "+files);

            File zipFile = new File(fname);
            // Check archive to create does not exist
            if (!zipFile.exists()) {
                // Process Files
                for(String file : files) {
                    Path filepath = Paths.get(file);
                    processFiles(filepath);
                }

                try (FileOutputStream out = new FileOutputStream(fname)) {
                    boolean createOk = create(new BufferedOutputStream(out, 4096));
                    if (ok) {
                        ok = createOk;
                    }
                }
            } else {
                error("Target zip file "+fname+" already exists.");
                ok = false;
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
                        timestamp = Long.parseLong(args[++count]) * 1000;
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
                    // file or dir to zip
                    files.add(args[count]);
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
            error(String.format("-f <archiveName> must be specified"));
            usageError();
            return false;
        }
        // If no files specified then default to current directory
        if (files.size() == 0) {
            error("No input directory or files were specified");
            usageError();
            return false;
        }

        return true;
    }

    // Walk tree matching files and adding to entries list
    void processFiles(Path path) throws IOException {
        File fpath = path.toFile();
        boolean pathIsDir = fpath.isDirectory();

        // Keep a sorted Set of files to be processed, so that the Jmod is reproducible
        // as Files.walkFileTree order is not defined
        SortedMap<String, Path> filesToProcess  = new TreeMap<String, Path>();

        Files.walkFileTree(path, Set.of(FileVisitOption.FOLLOW_LINKS),
            Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException
                {
                    Path relPath;
                    String name;
                    if (pathIsDir) {
                        relPath = path.relativize(file);
                        name = relPath.toString();
                    } else {
                        relPath = file;
                        name = file.toString();
                    }
                    filesToProcess.put(name, file);
                    return FileVisitResult.CONTINUE;
                }
        });

        // Process files in sorted order
        for (Map.Entry<String, Path> entry : filesToProcess.entrySet()) {
            String name = entry.getKey();
            Path   filepath = entry.getValue();

            File f = filepath.toFile();
            entries.add(f);
        }
    }

    // Create new zip from entries
    boolean create(OutputStream out) throws IOException
    {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (File file: entries) {
                addFile(zos, file);
            }
        }
        return true;
    }

    // Ensure a consistent entry name format
    String entryName(String name) {
        name = name.replace(File.separatorChar, '/');

        if (name.startsWith("/")) {
            name = name.substring(1);
        } else if (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name;
    }

    // Add File to Zip
    void addFile(ZipOutputStream zos, File file) throws IOException {
        String name = file.getPath();
        boolean isDir = file.isDirectory();
        if (isDir) {
            name = name.endsWith(File.separator) ? name : (name + File.separator);
        }
        name = entryName(name);

        if (name.equals("") || name.equals(".") || name.equals(zname)) {
            return;
        }

        long size = isDir ? 0 : file.length();

        if (verbose) {
            System.out.println("Adding: "+name);
        }

        ZipEntry e = new ZipEntry(name);
        // Set to specified timestamp if set otherwise use file lastModified time
        if (timestamp != -1L) {
            e.setTime(timestamp);
        } else {
            e.setTime(file.lastModified());
        }
        if (size == 0) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(0);
            e.setCrc(0);
        }
        zos.putNextEntry(e);
        if (!isDir) {
            byte[] buf = new byte[8192];
            int len;
            try (FileInputStream fis = new FileInputStream(file);
                 FileChannel fic = fis.getChannel()) {
                fic.transferTo(0, fic.size(), Channels.newChannel(zos));
            }
        }
        zos.closeEntry();
    }

    void usageError() {
        error(
        "Usage: GenerateZip [-v] -f <zip_file> <files_or_directories>\n" +
        "Options:\n" +
        "   -v  verbose output\n" +
        "   -f  specify archive file name to create\n" +
        "   -t  specific SOURCE_DATE_EPOCH value to use for timestamps\n" +
        "If any file is a directory then it is processed recursively.\n");
    }

    void fatalError(Exception e) {
        e.printStackTrace();
    }

    protected void error(String s) {
        System.err.println(s);
    }

    public static void main(String args[]) {
        GenerateZip z = new GenerateZip();
        System.exit(z.run(args) ? 0 : 1);
    }
}

