/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jimage;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.jimage.BasicImageReader;
import jdk.internal.jimage.BasicImageWriter;
import jdk.internal.jimage.ImageHeader;
import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.PackageModuleMap;

class JImageTask {
    static class BadArgs extends Exception {
        static final long serialVersionUID = 8765093759964640723L;  // ## re-generate
        final String key;
        final Object[] args;
        boolean showUsage;

        BadArgs(String key, Object... args) {
            super(JImageTask.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
    }

    static abstract class Option {
        final boolean hasArg;
        final String[] aliases;

        Option(boolean hasArg, String... aliases) {
            this.hasArg = hasArg;
            this.aliases = aliases;
        }

        boolean isHidden() {
            return false;
        }

        boolean matches(String opt) {
            for (String a : aliases) {
                if (a.equals(opt)) {
                    return true;
                } else if (opt.startsWith("--") && hasArg && opt.startsWith(a + "=")) {
                    return true;
                }
            }
            return false;
        }

        boolean ignoreRest() {
            return false;
        }

        abstract void process(JImageTask task, String opt, String arg) throws BadArgs;
    }

    static abstract class HiddenOption extends Option {
        HiddenOption(boolean hasArg, String... aliases) {
            super(hasArg, aliases);
        }

        @Override
        boolean isHidden() {
            return true;
        }
    }

    static Option[] recognizedOptions = {
        new Option(true, "--dir") {
            @Override
            void process(JImageTask task, String opt, String arg) throws BadArgs {
                 task.options.directory = arg;
            }
        },
        new HiddenOption(false, "--fullversion") {
            @Override
            void process(JImageTask task, String opt, String arg) {
                task.options.fullVersion = true;
            }
        },
        new Option(false, "--help") {
            @Override
            void process(JImageTask task, String opt, String arg) {
                task.options.help = true;
            }
        },
        new Option(false, "--verbose") {
            @Override
            void process(JImageTask task, String opt, String arg) throws BadArgs {
                 task.options.verbose = true;
            }
        },
        new Option(false, "--version") {
            @Override
            void process(JImageTask task, String opt, String arg) {
                task.options.version = true;
            }
        },
    };

    static class Options {
        Task task = Task.LIST;
        String directory = ".";
        boolean fullVersion;
        boolean help;
        boolean verbose;
        boolean version;
        List<File> jimages = new LinkedList<>();
    }

    private static final String PROGNAME = "jimage";
    private final Options options = new Options();

    enum Task {
        RECREATE,
        EXTRACT,
        INFO,
        LIST,
        VERIFY
    };

    private String pad(String string, int width, boolean justifyRight) {
        int length = string.length();

        if (length == width) {
            return string;
        }

        if (length > width) {
            return string.substring(0, width);
        }

        int padding = width - length;

        StringBuilder sb = new StringBuilder(width);
        if (justifyRight) {
            for (int i = 0; i < padding; i++) {
                sb.append(' ');
            }
        }

        sb.append(string);

        if (!justifyRight) {
            for (int i = 0; i < padding; i++) {
                sb.append(' ');
            }
        }

        return sb.toString();
    }

    private String pad(String string, int width) {
        return pad(string, width, false);
    }

    private String pad(long value, int width) {
        return pad(Long.toString(value), width, true);
    }

    private static final int EXIT_OK = 0;        // No errors.
    private static final int EXIT_ERROR = 1;     // Completed but reported errors.
    private static final int EXIT_CMDERR = 2;    // Bad command-line arguments and/or switches.
    private static final int EXIT_SYSERR = 3;    // System error or resource exhaustion.
    private static final int EXIT_ABNORMAL = 4;  // Terminated abnormally.

    int run(String[] args) {
        if (log == null) {
            log = new PrintWriter(System.out);
        }

        try {
            handleOptions(args);
            if (options.help) {
                showHelp();
            }
            if (options.version || options.fullVersion) {
                showVersion(options.fullVersion);
            }
            boolean ok = run();
            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (BadArgs e) {
            reportError(e.key, e.args);
            if (e.showUsage) {
                log.println(getMessage("main.usage.summary", PROGNAME));
            }
            return EXIT_CMDERR;
        } catch (Exception x) {
            x.printStackTrace();
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    static final String MODULES_ENTRY = PackageModuleMap.MODULES_ENTRY;
    static final String PACKAGES_ENTRY = "/" + PackageModuleMap.PACKAGES_ENTRY;

    private void recreate() throws IOException, BadArgs {
        File directory = new File(options.directory);
        Path dirPath = directory.toPath();
        int chop = dirPath.toString().length() + 1;

        if (!directory.isDirectory()) {
            throw new BadArgs("err.not.a.dir", directory.getAbsolutePath());
        }

        if (options.jimages.isEmpty()) {
            throw new BadArgs("err.jimage.not.specified");
        } else if (options.jimages.size() != 1) {
            throw new BadArgs("err.only.one.jimage");
        }

        File jimage = options.jimages.get(0);
        final List<File> files = new ArrayList<>();
        final BasicImageWriter writer = new BasicImageWriter();
        final Long longZero = 0L;

        // Note: code sensitive to Netbeans parser crashing.
        long total = Files.walk(dirPath).reduce(longZero, (Long offset, Path path) -> {
                    long size = 0;
                    String pathString = path.toString();

                    if (pathString.length() < chop || pathString.startsWith(".")) {
                        return 0L;
                    }

                    String name = pathString.substring(chop).replace('\\','/');

                    File file = path.toFile();

                    if (file.isFile()) {
                        if (options.verbose) {
                            log.println(name);
                        }

                        if (name.endsWith(MODULES_ENTRY) || name.endsWith(PACKAGES_ENTRY)) {
                            try {
                                try (Stream<String> lines = Files.lines(path)) {
                                    size = lines.peek(s -> writer.addString(s)).count() * 4;
                                }
                            } catch (IOException ex) {
                                // Caught again when writing file.
                                size = 0;
                            }
                        } else {
                            size = file.length();
                        }

                        writer.addLocation(name, offset, 0L, size);
                        files.add(file);
                    }

                    return offset + size;
                },
                (Long offsetL, Long offsetR) -> { return longZero; } );

        if (jimage.createNewFile()) {
            try (OutputStream os = Files.newOutputStream(jimage.toPath());
                    BufferedOutputStream bos = new BufferedOutputStream(os);
                    DataOutputStream out = new DataOutputStream(bos)) {

                byte[] index = writer.getBytes();
                out.write(index, 0, index.length);

                for (File file : files) {
                    try {
                        Path path = file.toPath();
                        String name = path.toString();

                        if (name.endsWith(MODULES_ENTRY) || name.endsWith(PACKAGES_ENTRY)) {
                            for (String line: Files.readAllLines(path)) {
                                int off = writer.addString(line);
                                out.writeInt(off);
                            }
                        } else {
                            Files.copy(path, out);
                        }
                    } catch (IOException ex) {
                        throw new BadArgs("err.cannot.read.file", file.getName());
                    }
                }
            }
        } else {
            throw new BadArgs("err.jimage.already.exists", jimage.getName());
        }

    }

    private void title(File file, BasicImageReader reader) {
        log.println("jimage: " + file.getName());
    }

    private void listTitle(File file, BasicImageReader reader) {
        title(file, reader);

        if (options.verbose) {
            log.print(pad("Offset", OFFSET_WIDTH + 1));
            log.print(pad("Size", SIZE_WIDTH + 1));
            log.print(pad("Compressed", COMPRESSEDSIZE_WIDTH + 1));
            log.println(" Entry");
        }
    }

    private interface JImageAction {
        public void apply(File file, BasicImageReader reader) throws IOException, BadArgs;
    }

    private interface ResourceAction {
        public void apply(BasicImageReader reader, String name, ImageLocation location) throws IOException, BadArgs;
    }

    private void extract(BasicImageReader reader, String name, ImageLocation location) throws IOException, BadArgs {
        File directory = new File(options.directory);
        byte[] bytes = reader.getResource(location);
        File resource =  new File(directory, name);
        File parent = resource.getParentFile();

        if (parent.exists()) {
            if (!parent.isDirectory()) {
                throw new BadArgs("err.cannot.create.dir", parent.getAbsolutePath());
            }
        } else if (!parent.mkdirs()) {
            throw new BadArgs("err.cannot.create.dir", parent.getAbsolutePath());
        }

        if (name.endsWith(MODULES_ENTRY) || name.endsWith(PACKAGES_ENTRY)) {
            List<String> names = reader.getNames(bytes);
            Files.write(resource.toPath(), names);
        } else {
            Files.write(resource.toPath(), bytes);
        }
    }

    private static final int NAME_WIDTH = 40;
    private static final int NUMBER_WIDTH = 12;
    private static final int OFFSET_WIDTH = NUMBER_WIDTH;
    private static final int SIZE_WIDTH = NUMBER_WIDTH;
    private static final int COMPRESSEDSIZE_WIDTH = NUMBER_WIDTH;

    private void print(String entry, ImageLocation location) {
        log.print(pad(location.getContentOffset(), OFFSET_WIDTH) + " ");
        log.print(pad(location.getUncompressedSize(), SIZE_WIDTH) + " ");
        log.print(pad(location.getCompressedSize(), COMPRESSEDSIZE_WIDTH) + " ");
        log.println(entry);
    }

    private void print(BasicImageReader reader, String entry) {
        if (options.verbose) {
            print(entry, reader.findLocation(entry));
        } else {
            log.println(entry);
        }
    }

    private void info(File file, BasicImageReader reader) {
        ImageHeader header = reader.getHeader();

        log.println(" Major Version:  " + header.getMajorVersion());
        log.println(" Minor Version:  " + header.getMinorVersion());
        log.println(" Location Count: " + header.getLocationCount());
        log.println(" Offsets Size:   " + header.getOffsetsSize());
        log.println(" Redirects Size: " + header.getRedirectSize());
        log.println(" Locations Size: " + header.getLocationsSize());
        log.println(" Strings Size:   " + header.getStringsSize());
        log.println(" Index Size:     " + header.getIndexSize());
    }

    private void list(BasicImageReader reader, String name, ImageLocation location) {
        print(reader, name);
    }

    void verify(BasicImageReader reader, String name, ImageLocation location) {
        if (name.endsWith(".class")) {
            byte[] bytes;
            try {
                bytes = reader.getResource(location);
            } catch (IOException ex) {
                log.println(ex);
                bytes = null;
            }

            if (bytes == null || bytes.length <= 4 ||
                (bytes[0] & 0xFF) != 0xCA ||
                (bytes[1] & 0xFF) != 0xFE ||
                (bytes[2] & 0xFF) != 0xBA ||
                (bytes[3] & 0xFF) != 0xBE) {
                log.print(" NOT A CLASS: ");
                print(reader, name);
            }
        }
    }

    private void iterate(JImageAction jimageAction, ResourceAction resourceAction) throws IOException, BadArgs {
        for (File file : options.jimages) {
            if (!file.exists() || !file.isFile()) {
                throw new BadArgs("err.not.a.jimage", file.getName());
            }

            String path = file.getCanonicalPath();
            BasicImageReader reader = BasicImageReader.open(path);

            if (jimageAction != null) {
                jimageAction.apply(file, reader);
            }

            if (resourceAction != null) {
                String[] entryNames = reader.getEntryNames(true);

                for (String name : entryNames) {
                    ImageLocation location = reader.findLocation(name);
                    resourceAction.apply(reader, name, location);
                }
            }
       }
    }

    private boolean run() throws IOException, BadArgs {
        switch (options.task) {
            case RECREATE:
                recreate();
                break;
            case EXTRACT:
                iterate(null, this::extract);
                break;
            case INFO:
                iterate(this::info, null);
                break;
            case LIST:
                iterate(this::listTitle, this::list);
                break;
            case VERIFY:
                iterate(this::title, this::verify);
                break;
            default:
                throw new BadArgs("err.invalid.task", options.task.name()).showUsage(true);
        }
        return true;
    }

    private PrintWriter log;
    void setLog(PrintWriter out) {
        log = out;
    }
    public void handleOptions(String[] args) throws BadArgs {
        // process options
        int first = 0;

        if (args.length == 0) {
            return;
        }

        String arg = args[first];

        if (!arg.startsWith("-")) {
            try {
                options.task = Enum.valueOf(Task.class, arg.toUpperCase());
                first++;
            } catch (IllegalArgumentException e) {
                throw new BadArgs("err.invalid.task", arg).showUsage(true);
            }
        }

        for (int i = first; i < args.length; i++) {
            arg = args[i];

            if (arg.charAt(0) == '-') {
                Option option = getOption(arg);
                String param = null;

                if (option.hasArg) {
                    if (arg.startsWith("--") && arg.indexOf('=') > 0) {
                        param = arg.substring(arg.indexOf('=') + 1, arg.length());
                    } else if (i + 1 < args.length) {
                        param = args[++i];
                    }

                    if (param == null || param.isEmpty() || param.charAt(0) == '-') {
                        throw new BadArgs("err.missing.arg", arg).showUsage(true);
                    }
                }

                option.process(this, arg, param);

                if (option.ignoreRest()) {
                    i = args.length;
                }
            } else {
                File file = new File(arg);
                options.jimages.add(file);
            }
        }
    }

    private Option getOption(String name) throws BadArgs {
        for (Option o : recognizedOptions) {
            if (o.matches(name)) {
                return o;
            }
        }
        throw new BadArgs("err.unknown.option", name).showUsage(true);
    }

    private void reportError(String key, Object... args) {
        log.println(getMessage("error.prefix") + " " + getMessage(key, args));
    }

    private void warning(String key, Object... args) {
        log.println(getMessage("warn.prefix") + " " + getMessage(key, args));
    }

    private void showHelp() {
        log.println(getMessage("main.usage", PROGNAME));
        for (Option o : recognizedOptions) {
            String name = o.aliases[0].substring(1); // there must always be at least one name
            name = name.charAt(0) == '-' ? name.substring(1) : name;
            if (o.isHidden() || name.equals("h")) {
                continue;
            }
            log.println(getMessage("main.opt." + name));
        }
    }

    private void showVersion(boolean full) {
        log.println(version(full ? "full" : "release"));
    }

    private String version(String key) {
        return System.getProperty("java.version");
    }

    static String getMessage(String key, Object... args) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private static class ResourceBundleHelper {
        static final ResourceBundle bundle;

        static {
            Locale locale = Locale.getDefault();
            try {
                bundle = ResourceBundle.getBundle("jdk.tools.jimage.resources.jimage", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jimage resource bundle for locale " + locale);
            }
        }
    }
}
