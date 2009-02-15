/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.DirectoryStreamFilters
 * @library ..
 */

import java.nio.file.*;
import static java.nio.file.DirectoryStreamFilters.*;
import java.nio.file.attribute.Attributes;
import java.io.*;
import java.util.*;

public class Filters {
    static final Random rand = new Random();

    // returns a filter that only accepts files that are larger than a given size
    static DirectoryStream.Filter<FileRef> newMinimumSizeFilter(final long min) {
        return new DirectoryStream.Filter<FileRef>() {
            public boolean accept(FileRef file) {
                try {
                    long size = Attributes.readBasicFileAttributes(file).size();
                    return size >= min;
                } catch (IOException e) {
                    throw new IOError(e);
                }
            }
        };
    }

    // returns a filter that only accepts files that are matched by a given glob
    static DirectoryStream.Filter<Path> newGlobFilter(final String glob) {
        return new DirectoryStream.Filter<Path>() {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:"+ glob);
            public boolean accept(Path file) {
                return matcher.matches(file.getName());
            }
        };
    }

    static final int BIG_FILE_THRESHOLD = 8192;

    static int totalCount;
    static int htmlCount;
    static int bigAndHtmlCount;
    static int bigOrHtmlCount;

    // generates random files in the test directory and initializes the counts
    static void setup(Path dir) throws IOException {
        // create 10-26 files.
        totalCount = 10 + rand.nextInt(17);
        char firstChar = 'A';
        for (int i=0; i<totalCount; i++) {
            boolean isHtml = rand.nextBoolean();
            boolean isBig = rand.nextBoolean();
            if (isHtml) {
                htmlCount++;
                if (isBig) bigAndHtmlCount++;
            }
            if (isHtml || isBig)
                bigOrHtmlCount++;
            String name;
            if (isHtml) {
                name = firstChar + ".html";
            } else {
                name = firstChar + ".tmp";
            }
            firstChar++;
            int size = rand.nextInt(BIG_FILE_THRESHOLD);
            if (isBig)
                size += BIG_FILE_THRESHOLD;
            Path file = dir.resolve(name);
            OutputStream out = file.newOutputStream();
            try {
                if (size > 0)
                    out.write(new byte[size]);
            } finally {
                out.close();
            }
            System.out.format("Created %s, size %d byte(s)\n", name, size);
        }
    }

    static boolean isHtml(Path file) {
        return file.toString().endsWith(".html");
    }

    static boolean isBig(Path file) throws IOException {
        long size = Attributes.readBasicFileAttributes(file).size();
        return size >= BIG_FILE_THRESHOLD;
    }

    static void checkCount(int expected, int actual) {
        if (actual != expected)
            throw new RuntimeException("'" + expected +
                "' entries expected, actual: " + actual);
    }

    static void doTests(Path dir) throws IOException {
        final List<DirectoryStream.Filter<Path>> emptyList = Collections.emptyList();

        // list containing two filters
        List<DirectoryStream.Filter<? super Path>> filters =
            new ArrayList<DirectoryStream.Filter<? super Path>>();
        filters.add(newMinimumSizeFilter(BIG_FILE_THRESHOLD));
        filters.add(newGlobFilter("*.html"));

        int accepted;
        DirectoryStream<Path> stream;

        System.out.println("Test: newContentTypeFilter");
        accepted = 0;
        stream = dir.newDirectoryStream(newContentTypeFilter("text/html"));
        try {
            for (Path entry: stream) {
                if (!isHtml(entry))
                    throw new RuntimeException("html file expected");
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(htmlCount, accepted);

        System.out.println("Test: allOf with list of filters");
        accepted = 0;
        stream = dir.newDirectoryStream(allOf(filters));
        try {
            for (Path entry: stream) {
                if (!isHtml(entry))
                    throw new RuntimeException("html file expected");
                if (!isBig(entry))
                    throw new RuntimeException("big file expected");
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(bigAndHtmlCount, accepted);

        System.out.println("Test: allOf with empty list");
        accepted = 0;
        stream = dir.newDirectoryStream(allOf(emptyList));
        try {
            for (Path entry: stream) {
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(totalCount, accepted);

        System.out.println("Test: anyOf with list of filters");
        accepted = 0;
        stream = dir.newDirectoryStream(anyOf(filters));
        try {
            for (Path entry: stream) {
                if (!isHtml(entry) && !isBig(entry))
                    throw new RuntimeException("html or big file expected");
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(bigOrHtmlCount, accepted);

        System.out.println("Test: anyOf with empty list");
        accepted = 0;
        stream = dir.newDirectoryStream(anyOf(emptyList));
        try {
            for (Path entry: stream) {
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(0, accepted);

        System.out.println("Test: complementOf");
        accepted = 0;
        stream = dir.newDirectoryStream(complementOf(newGlobFilter("*.html")));
        try {
            for (Path entry: stream) {
                accepted++;
            }
        } finally {
            stream.close();
        }
        checkCount(totalCount-htmlCount, accepted);

        System.out.println("Test: nulls");
        try {
            newContentTypeFilter(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        try {
            allOf(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        try {
            anyOf(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        try {
            complementOf(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
    }

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            setup(dir);
            doTests(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
