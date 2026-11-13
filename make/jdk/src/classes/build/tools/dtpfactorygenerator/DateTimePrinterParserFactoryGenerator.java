/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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

package build.tools.dtpfactorygenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A standalone generator to create DateTimePrinterParserFactory.java from template.
 * This separates the datetime printer/parser factory generation from the CLDR converter.
 */
public class DateTimePrinterParserFactoryGenerator {

    private static String templateFile;
    private static String destinationDir = "build/gensrc";

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            String currentArg = null;
            try {
                for (int i = 0; i < args.length; i++) {
                    currentArg = args[i];
                    switch (currentArg) {
                    case "-dtpftempfile":
                        templateFile = args[++i];
                        break;
                    case "-o":
                        destinationDir = args[++i];
                        break;
                    case "-help":
                        usage();
                        System.exit(0);
                        break;
                    default:
                        throw new RuntimeException();
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("Error: unknown or incomplete arg(s): " + currentArg);
                usage();
                System.exit(1);
            }
        }

        if (templateFile == null) {
            System.err.println("Error: -dtpftempfile is required");
            usage();
            System.exit(1);
        }

        // Generate java.time.format.DateTimePrinterParserFactory.java
        generateDateTimePrinterParserFactory();
    }

    private static void usage() {
        System.err.println("Usage: java DateTimePrinterParserFactoryGenerator [options]");
        System.err.println("\t-help          output this usage message and exit");
        System.err.println("\t-dtpftempfile  template file for java.time.format.DateTimePrinterParserFactory.java");
        System.err.println("\t-o dir         output directory (default: ./build/gensrc)");
    }

    private static void generateDateTimePrinterParserFactory() throws Exception {
        Files.createDirectories(Paths.get(destinationDir, "java", "time", "format"));
        Files.write(Paths.get(destinationDir, "java", "time", "format", "DateTimePrinterParserFactory.java"),
                Files.lines(Paths.get(templateFile))
                        .flatMap(l -> {
                            if (l.startsWith("%%%%CASES-FORMAT:")) {
                                return generateDateTimePrinterCases(l, "%%%%CASES-FORMAT:", false);  // formatter cases
                            } else if (l.startsWith("%%%%CASES-PARSE:")) {
                                return generateDateTimePrinterCases(l, "%%%%CASES-PARSE:", true);   // parser cases
                            } else {
                                return Stream.of(l);
                            }
                        })
                        .collect(Collectors.toList()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static Stream<String> generateDateTimePrinterCases(String line, String prefix, boolean isParse) {
        // Parse the range, defaulting to 1-16 if no range is specified
        int start = 1;
        int end = 16;

        String rangePart = line.substring(prefix.length(), line.length() - 4); // Remove trailing%%%%
        String[] parts = rangePart.split("-");
        if (parts.length == 2) {
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Use defaults if parsing fails
            }
        }

        return IntStream.rangeClosed(start, end)
            .mapToObj(i -> {
                if (isParse) {
                    // Generate parser cases
                    var sb = new StringBuilder("            case ").append(i).append(" -> (context, text, position)\n");
                    if (i == 1) {
                        // Special case for 1 - direct method reference used instead
                        // This shouldn't happen since case 1 is handled separately in the template
                        sb.append("                    -> printerParsers[0].parse(context, text, position);");
                    } else {
                        // For i >= 2, build the sequence
                        sb.append("                    -> (position = printerParsers[0].parse(context, text, position)) < 0\n");
                        for (int j = 1; j < i - 1; j++) {
                            sb.append("                    ? position : (position = printerParsers[").append(j).append("].parse(context, text, position)) < 0\n");
                        }
                        // The last parser in the chain doesn't check for failure, just returns its result
                        if (i > 1) {
                            sb.append("                    ? position : printerParsers[").append(i - 1).append("].parse(context, text, position);");
                        }
                    }
                    return sb.toString();
                } else {
                    // Generate formatter cases (original behavior)
                    var sb = new StringBuilder("            case ").append(i).append(" -> (context, buf, optional)\n")
                            .append("                    -> ");
                    for (int j = 0; j < i; j++) {
                        sb.append("printerParsers[").append(j).append("].format(context, buf, optional)");
                        if (j < i - 1) {
                            sb.append("\n                    && ");
                        }
                    }
                    return sb.append(";").toString();
                }
            });
    }
}