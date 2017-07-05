/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation, and proper error handling, might not be present in
 * this sample code.
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Grep prints lines matching a regex. See {@link #printUsageAndExit(String...)}
 * method for instructions and command line parameters. This sample shows
 * examples of using next features:
 * <ul>
 * <li>Lambda and bulk operations. Working with streams:
 * map(...),filter(...),flatMap(...),limit(...) methods.</li>
 * <li>Static method reference for printing values.</li>
 * <li>New Collections API forEach(...) method.</li>
 * <li>Try-with-resources feature.</li>
 * <li>new Files.walk(...), Files.lines(...) API.</li>
 * <li>Streams that need to be closed.</li>
 * </ul>
 *
 */
public class Grep {

    private static void printUsageAndExit(String... str) {
        System.out.println("Usage: " + Grep.class.getSimpleName()
                + " [OPTION]... PATTERN FILE...");
        System.out.println("Search for PATTERN in each FILE. "
                + "If FILE is a directory then whole file tree of the directory"
                + " will be processed.");
        System.out.println("Example: grep -m 100 'hello world' menu.h main.c");
        System.out.println("Options:");
        System.out.println("    -m NUM: stop analysis after NUM matches");
        Arrays.asList(str).forEach(System.err::println);
        System.exit(1);
    }

    /**
     * The main method for the Grep program. Run program with empty argument
     * list to see possible arguments.
     *
     * @param args the argument list for Grep.
     * @throws java.io.IOException If an I/O error occurs.
     */
    public static void main(String[] args) throws IOException {
        long maxCount = Long.MAX_VALUE;
        if (args.length < 2) {
            printUsageAndExit();
        }
        int i = 0;
        //parse OPTIONS
        while (args[i].startsWith("-")) {
            switch (args[i]) {
                case "-m":
                    try {
                        maxCount = Long.parseLong(args[++i]);
                    } catch (NumberFormatException ex) {
                        printUsageAndExit(ex.toString());
                    }
                    break;
                default:
                    printUsageAndExit("Unexpected option " + args[i]);
            }
            i++;
        }
        //parse PATTERN
        Pattern pattern = Pattern.compile(args[i++]);
        if (i == args.length) {
            printUsageAndExit("There are no files for input");
        }

        try {
            /*
            * First obtain the list of all paths.
            * For a small number of arguments there is little to be gained
            * by producing this list in parallel. For one argument
            * there will be no parallelism.
            *
            * File names are converted to paths. If a path is a directory then
            * Stream is populated with whole file tree of the directory by
            * flatMap() method. Files are filtered from directories.
            */
            List<Path> files = Arrays.stream(args, i, args.length)
                    .map(Paths::get)
                    // flatMap will ensure each I/O-based stream will be closed
                    .flatMap(Grep::getPathStream)
                    .filter(Files::isRegularFile)
                    .collect(toList());
            /*
            * Then operate on that list in parallel.
            * This is likely to give a more even distribution of work for
            * parallel execution.
            *
            * Lines are extracted from files. Lines are filtered by pattern.
            * Stream is limited by number of matches. Each remaining string is
            * displayed in std output by method reference System.out::println.
            */
            files.parallelStream()
                    // flatMap will ensure each I/O-based stream will be closed
                    .flatMap(Grep::path2Lines)
                    .filter(pattern.asPredicate())
                    .limit(maxCount)
                    .forEachOrdered(System.out::println);
        } catch (UncheckedIOException ioe) {
            printUsageAndExit(ioe.toString());
        }
    }

    /**
     * Flattens file system hierarchy into a stream. This code is not inlined
     * for the reason of Files.walk() throwing a checked IOException that must
     * be caught.
     *
     * @param path - the file or directory
     * @return Whole file tree starting from path, a stream with one element -
     * the path itself - if it is a file.
     */
    private static Stream<Path> getPathStream(Path path) {
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Produces a stream of lines from a file. The result is a stream in order
     * to close it later. This code is not inlined for the reason of
     * Files.lines() throwing a checked IOException that must be caught.
     *
     * @param path - the file to read
     * @return stream of lines from the file
     */
    private static Stream<String> path2Lines(Path path) {
        try {
            return Files.lines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
