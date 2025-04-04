/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.jfc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.jfr.Configuration;
import jdk.jfr.internal.jfc.model.JFCModelException;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.util.Utils;

/**
 * {@link Configuration} factory for JFC files. *
 */
public final class JFC {
    private static final Path JFC_DIRECTORY = Utils.getPathInProperty("java.home", "lib/jfr");
    private static final int BUFFER_SIZE = 8192;
    private static final int MAXIMUM_FILE_SIZE = 1024 * 1024;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    private static volatile List<KnownConfiguration> knownConfigurations;

    public static List<Path> getPredefined() {
        List<Path> list = new ArrayList<>();
        try (var ds = Files.newDirectoryStream(JFC_DIRECTORY)) {
            for (Path path : ds) {
                String text = path.toString();
                if (text.endsWith(".jfc") && !Files.isDirectory(path)) {
                    list.add(path);
                }
            }
        } catch (IOException ioe) {
            Logger.log(LogTag.JFR, LogLevel.WARN, "Could not access .jfc-files in " + JFC_DIRECTORY + ", " + ioe.getMessage());
        }
        return list;
    }

    /**
     * Reads a known configuration file (located into a string, but doesn't
     * parse it until it's being used.
     */
    private static final class KnownConfiguration {
        private final String content;
        private final String filename;
        private final String name;
        private final Path path;
        private Configuration configuration;

        public KnownConfiguration(Path knownPath) throws IOException {
            this.path = knownPath;
            this.content = readContent(knownPath);
            this.name = nameFromPath(knownPath);
            this.filename = nullSafeFileName(knownPath);
        }

        public boolean isNamed(String name) {
            return filename.equals(name) || this.name.equals(name);
        }

        public Configuration getConfigurationFile() throws IOException, ParseException {
            if (configuration == null) {
                configuration = JFCParser.createConfiguration(name, content);
            }
            return configuration;
        }

        public String getName() {
            return name;
        }

        private static String readContent(Path knownPath) throws IOException {
            if (Files.size(knownPath) > MAXIMUM_FILE_SIZE) {
                throw new IOException("Configuration with more than "
                        + MAXIMUM_FILE_SIZE + " characters can't be read.");
            }
            try (InputStream r = Files.newInputStream(knownPath)) {
                return JFC.readContent(r);
            }
        }
    }

    private JFC() {
        // private utility class
    }

    /**
     * Reads a configuration from a file.
     *
     * @param path the file containing the configuration, not {@code null}
     * @return {@link Configuration}, not {@code null}
     * @throws ParseException if the file can't be parsed
     * @throws IOException if the file can't be read
     *
     * @see java.io.File#getPath()
     */
    public static Configuration create(String name, Reader reader) throws IOException, ParseException {
        try {
            return JFCParser.createConfiguration(name, reader);
        } catch (ParseException pe) {
            throw new ParseException("Error reading JFC file. " + pe.getMessage(), -1);
        }
    }

    /**
     * Create a path to a .jfc file.
     * <p>
     * If the name is predefined name,
     * i.e. "default" or "profile.jfc", it will return the path for
     * the predefined path in the JDK.
     *
     * @param path textual representation of the path
     *
     * @return a path, not null
     */
    public static Path ofPath(String path) {
        for (Path predefined : JFC.getPredefined()) {
            try {
                String name = JFC.nameFromPath(predefined);
                if (name.equals(path) || (name + ".jfc").equals(path)) {
                    return predefined;
                }
            } catch (IOException e) {
                throw new InternalError("Error in predefined .jfc file", e);
            }
        }
        return Path.of(path);
    }


    private static String nullSafeFileName(Path file) throws IOException {
        Path filename = file.getFileName();
        if (filename == null) {
            throw new IOException("Path has no file name");
        }
        return filename.toString();
    }

    public static String nameFromPath(Path file) throws IOException {
        String f = nullSafeFileName(file);
        if (f.endsWith(JFCParser.FILE_EXTENSION)) {
            return f.substring(0, f.length() - JFCParser.FILE_EXTENSION.length());
        } else  {
            return f;
        }
    }

    // Invoked by DCmdStart
    public static Configuration createKnown(String name) throws IOException, ParseException {
        for (KnownConfiguration known : getKnownConfigurations()) {
            if (known.isNamed(name)) {
                return known.getConfigurationFile();
            }
        }
        // Check JFC directory
        Path path = JFC_DIRECTORY;
        if (path != null && Files.exists(path)) {
            for (String extension : Arrays.asList("", JFCParser.FILE_EXTENSION)) {
                Path file = path.resolveSibling(name + extension);
                if (Files.exists(file) && !Files.isDirectory(file)) {
                    try (Reader r = Files.newBufferedReader(file)) {
                        String jfcName = nameFromPath(file);
                        return JFCParser.createConfiguration(jfcName, r);
                    }
                }
            }
        }

        // Assume path included in name

        Path localPath = Paths.get(name);
        String jfcName = nameFromPath(localPath);
        try (Reader r = Files.newBufferedReader(localPath)) {
            return JFCParser.createConfiguration(jfcName, r);
        }
    }

    private static String readContent(InputStream source) throws IOException {
        byte[] bytes = read(source, BUFFER_SIZE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // copied from java.io.file.Files to avoid dependency on JDK 9 code
    private static byte[] read(InputStream source, int initialSize) throws IOException {
        int capacity = initialSize;
        byte[] buf = new byte[capacity];
        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initialSize (eg: file
            // is truncated while we are reading)
            while ((n = source.read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if last call to source.read() returned -1, we are done
            // otherwise, try to read one more byte; if that failed we're done too
            if (n < 0 || (n = source.read()) < 0)
                break;

            // one more byte was read; need to allocate a larger buffer
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = Math.max(capacity << 1, BUFFER_SIZE);
            } else {
                if (capacity == MAX_BUFFER_SIZE)
                    throw new OutOfMemoryError("Required array size too large");
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
            buf[nread++] = (byte)n;
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }


    /**
     * Returns list of predefined configurations available.
     *
     * @return list of configurations, not null
     */
    public static List<Configuration> getConfigurations() {
        List<Configuration> configs = new ArrayList<>();
        for (KnownConfiguration knownConfig : getKnownConfigurations()) {
            try {
                configs.add(knownConfig.getConfigurationFile());
            } catch (IOException e) {
                Logger.log(LogTag.JFR, LogLevel.WARN, "Could not load configuration " + knownConfig.getName() + ". " + e.getMessage());
            } catch (ParseException e) {
                Logger.log(LogTag.JFR, LogLevel.WARN, "Could not parse configuration " + knownConfig.getName() + ". " + e.getMessage());
            }
        }
        return configs;
    }

    private static List<KnownConfiguration> getKnownConfigurations() {
        if (knownConfigurations == null) {
            List<KnownConfiguration> configProxies = new ArrayList<>();
            for (Path p : JFC.getPredefined()) {
                try {
                    configProxies.add(new KnownConfiguration(p));
                } catch (IOException ioe) {
                    // ignore
                }
            }
            knownConfigurations = configProxies;
        }
        return knownConfigurations;
    }

    public static Configuration getPredefined(String name) throws IOException, ParseException {
        for (KnownConfiguration knownConfig : getKnownConfigurations()) {
            if (knownConfig.getName().equals(name)) {
                return knownConfig.getConfigurationFile();
            }
        }
        throw new NoSuchFileException("Could not locate configuration with name " + name);
    }

    public static Reader newReader(Path sf) throws IOException {
        for (KnownConfiguration c : getKnownConfigurations()) {
            if (c.path.equals(sf)) {
                return new StringReader(c.content);
            }
        }
        return Files.newBufferedReader(sf.toFile().toPath(), StandardCharsets.UTF_8);
    }

    public static String formatException(String prefix, Exception e, String input) {
        String message = prefix + " " + JFC.exceptionToVerb(e) + " file '" + input + "'";
        String details = e.getMessage();
        if (e instanceof JFCModelException) {
            return message +  ". " + details;
        }
        if (e instanceof ParseException && !details.isEmpty()) {
            return message +  ". " + details;
        }
        return message;
    }

    private static String exceptionToVerb(Exception e) {
        return switch (e) {
            case FileNotFoundException f -> "find";
            case NoSuchFileException n -> "find";
            case ParseException p -> "parse";
            case JFCModelException j -> "use";
            case AccessDeniedException a -> "access";
            default -> "open"; // InvalidPath, IOException
        };
    }
}
