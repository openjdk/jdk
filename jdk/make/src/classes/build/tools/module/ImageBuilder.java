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

package build.tools.module;

import jdk.internal.jimage.Archive;
import jdk.internal.jimage.ImageFile;
import jdk.internal.jimage.ImageModules;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tool for building a runtime image.
 *
 * java build.tools.module.ImageBuilder <options> --output <path> top/modules.xml,...
 *  Possible options are:
 *  --cmds                  Location of native commands
 *  --configs               Location of config files
 *  --help                  Print this usage message
 *  --classes               Location of module classes files
 *  --libs                  Location of native libraries
 *  --mods                  Comma separated list of module names
 *  --output                Location of the output path
 *  --endian                Byte order of the target runtime; {little,big}
 */
class ImageBuilder {
    static class BadArgs extends Exception {
        private static final long serialVersionUID = 0L;
        BadArgs(String format, Object... args) {
            super(String.format(format, args));
            this.format = format;
            this.args = args;
        }
        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
        final String format;
        final Object[] args;
        boolean showUsage;
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
        abstract void process(ImageBuilder task, String opt, String arg) throws BadArgs;
        abstract String description();
    }

    private static Path CWD = Paths.get("");

    private static List<Path> splitPath(String arg, String separator)
        throws BadArgs
    {
        List<Path> paths = new ArrayList<>();
        for (String p: arg.split(separator)) {
            if (p.length() > 0) {
                try {
                    Path path = CWD.resolve(p);
                    if (Files.notExists(path))
                        throw new BadArgs("path not found: %s", path);
                    paths.add(path);
                } catch (InvalidPathException x) {
                    throw new BadArgs("path not valid: %s", p);
                }
            }
        }
        return paths;
    }

    static Option[] recognizedOptions = {
        new Option(true, "--cmds") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                task.options.cmds = splitPath(arg, File.pathSeparator);
            }
            String description() { return "Location of native commands"; }
        },
        new Option(true, "--configs") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                task.options.configs = splitPath(arg, File.pathSeparator);
            }
            String description() { return "Location of config files"; }
        },
        new Option(false, "--help") {
            void process(ImageBuilder task, String opt, String arg) {
                task.options.help = true;
            }
            String description() { return "Print this usage message"; }
        },
        new Option(true, "--classes") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                task.options.classes = splitPath(arg, File.pathSeparator);
            }
            String description() { return "Location of module classes files"; }
        },
        new Option(true, "--libs") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                task.options.libs = splitPath(arg, File.pathSeparator);
            }
            String description() { return "Location of native libraries"; }
        },
        new Option(true, "--mods") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                for (String mn : arg.split(",")) {
                    if (mn.isEmpty())
                        throw new BadArgs("Module not found", mn);
                    task.options.mods.add(mn);
                }
            }
            String description() { return "Comma separated list of module names"; }
        },
        new Option(true, "--output") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                Path path = Paths.get(arg);
                task.options.output = path;
            }
            String description() { return "Location of the output path"; }
        },
        new Option(true, "--endian") {
            void process(ImageBuilder task, String opt, String arg) throws BadArgs {
                if (arg.equals("little"))
                    task.options.endian = ByteOrder.LITTLE_ENDIAN;
                else if (arg.equals("big"))
                    task.options.endian = ByteOrder.BIG_ENDIAN;
                else
                    throw new BadArgs("Unknown byte order " + arg);
            }
            String description() { return "Byte order of the target runtime; {little,big}"; }
        }
    };

    private final Options options = new Options();

    private PrintWriter log;
    void setLog(PrintWriter out) {
        log = out;
    }

    Set<Module> moduleGraph = new java.util.HashSet<>();

    /** Module list files */
    private static final String BOOT_MODULES = "boot.modules";
    private static final String EXT_MODULES = "ext.modules";

    /**
     * Result codes.
     */
    static final int EXIT_OK = 0,       // Completed with no errors.
                     EXIT_ERROR = 1,    // Completed but reported errors.
                     EXIT_CMDERR = 2,   // Bad command-line arguments
                     EXIT_SYSERR = 3,   // System error or resource exhaustion.
                     EXIT_ABNORMAL = 4; // terminated abnormally


    static class Options {
        boolean help;
        List<Path> classes;
        List<Path> cmds;
        List<Path> configs;
        List<Path> libs;
        Set<String> mods = new HashSet<>();
        Path output;
        ByteOrder endian = ByteOrder.nativeOrder(); // default, if not specified
    }

    public static void main(String[] args) throws Exception {
        ImageBuilder builder = new ImageBuilder();
        int rc = builder.run(args);
        System.exit(rc);
    }

    int run(String[] args) {
        if (log == null)
            log = new PrintWriter(System.out);

        try {
            handleOptions(args);
            if (options.help) {
                showHelp();
                return EXIT_OK;
            }

            if (options.classes == null)
                throw new BadArgs("--classes must be specified").showUsage(true);

            Path output = options.output;
            if (output == null)
                throw new BadArgs("--output must be specified").showUsage(true);
            Files.createDirectories(output);
            if (Files.list(output).findFirst().isPresent())
                throw new BadArgs("dir not empty", output);

            if (options.mods.isEmpty())
                throw new BadArgs("--mods must be specified").showUsage(true);

            if (moduleGraph.isEmpty())
                throw new BadArgs("modules.xml must be specified").showUsage(true);

            if (options.cmds == null || options.cmds.isEmpty())
                warning("--commands is not set");
            if (options.libs == null || options.libs.isEmpty())
                warning("--libs is not set");
            //if (options.configs == null || options.configs.isEmpty())
            //    warning("--configs is not set");

            // additional option combination validation

            boolean ok = run();
            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (BadArgs e) {
            reportError(e.format, e.args);
            if (e.showUsage)
                log.println(USAGE_SUMMARY);
            return EXIT_CMDERR;
        } catch (Exception x) {
            x.printStackTrace();
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    private boolean run() throws IOException {
        createImage();
        return true;
    }

    class SimpleResolver {
        private final Set<Module> initialMods;
        private final Map<String,Module> nameToModule = new HashMap<>();

        SimpleResolver(Set<String> mods, Set<Module> graph) {
            graph.stream()
                 .forEach(m -> nameToModule.put(m.name(), m));
            initialMods = mods.stream()
                         .map(this::nameToModule)
                         .collect(Collectors.toSet());
        }

        /** Returns the transitive closure, in topological order */
        List<String> resolve() {
            List<Module> result = new LinkedList<>();
            Set<Module> visited = new HashSet<>();
            Set<Module> done = new HashSet<>();
            for (Module m : initialMods) {
                if (!visited.contains(m))
                    visit(m, visited, result, done);
            }
            return result.stream()
                         .map(m -> m.name())
                         .collect(Collectors.toList());
        }

        private void visit(Module m, Set<Module> visited,
                           List<Module> result, Set<Module> done) {
            if (visited.contains(m)) {
                if (!done.contains(m))
                    throw new IllegalArgumentException("Cyclic detected: " +
                            m + " " + getModuleDependences(m));
                return;
            }
            visited.add(m);
            getModuleDependences(m).stream()
                                   .forEach(d -> visit(d, visited, result, done));
            done.add(m);
            result.add(m);
        }

        private Module nameToModule(String name) {
            Module m = nameToModule.get(name);
            if (m == null)
                throw new RuntimeException("No module definition for " + name);
            return m;
        }

        private Set<Module> getModuleDependences(Module m) {
            return m.requires().stream()
                    .map(d -> d.name())
                    .map(this::nameToModule)
                    .collect(Collectors.toSet());
        }
    }

    private List<String> resolve(Set<String> mods ) {
        return (new SimpleResolver(mods, moduleGraph)).resolve();
    }

    /**
     * chmod ugo+x file
     */
    private void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private void createImage() throws IOException {
        Collection<String> modules = resolve(options.mods);
        log.print(modules.stream().collect(Collectors.joining(" ")));
        ImageFileHelper imageHelper = new ImageFileHelper(modules);
        imageHelper.createModularImage(options.output);

        // jspawnhelper, might be in lib or lib/ARCH
        Path jspawnhelper = Paths.get("jspawnhelper");
        Path lib = options.output.resolve("lib");
        Optional<Path> helper = Files.walk(lib, 2)
                                     .filter(f -> f.getFileName().equals(jspawnhelper))
                                     .findFirst();
        if (helper.isPresent())
            setExecutable(helper.get());
    }

    private class ImageFileHelper {
        final Collection<String> modules;
        final Set<String> bootModules;
        final Set<String> extModules;
        final Set<String> appModules;
        final ImageModules imf;

        ImageFileHelper(Collection<String> modules) throws IOException {
            this.modules = modules;
            this.bootModules = modulesFor(BOOT_MODULES).stream()
                     .filter(modules::contains)
                     .collect(Collectors.toSet());
            this.extModules = modulesFor(EXT_MODULES).stream()
                    .filter(modules::contains)
                    .collect(Collectors.toSet());
            this.appModules = modules.stream()
                    .filter(m -> !bootModules.contains(m) && !extModules.contains(m))
                    .collect(Collectors.toSet());

            this.imf = new ImageModules(bootModules, extModules, appModules);
        }

        void createModularImage(Path output) throws IOException {
            Set<Archive> archives = modules.stream()
                                            .map(this::toModuleArchive)
                                            .collect(Collectors.toSet());
            ImageFile.create(output, archives, imf, options.endian);
        }

        ModuleArchive toModuleArchive(String mn) {
            return new ModuleArchive(mn,
                                     moduleToPath(mn, options.classes, false/*true*/),
                                     moduleToPath(mn, options.cmds, false),
                                     moduleToPath(mn, options.libs, false),
                                     moduleToPath(mn, options.configs, false));
        }

        private Path moduleToPath(String name, List<Path> paths, boolean expect) {
            Set<Path> foundPaths = new HashSet<>();
            if (paths != null) {
                for (Path p : paths) {
                    Path rp = p.resolve(name);
                    if (Files.exists(rp))
                        foundPaths.add(rp);
                }
            }
            if (foundPaths.size() > 1)
                throw new RuntimeException("Found more that one path for " + name);
            if (expect && foundPaths.size() != 1)
                throw new RuntimeException("Expected to find classes path for " + name);
            return foundPaths.size() == 0 ? null : foundPaths.iterator().next();
        }

        private List<String> modulesFor(String name) throws IOException {
            try (InputStream is = ImageBuilder.class.getResourceAsStream(name);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.toList());
            }
        }
    }

    public void handleOptions(String[] args) throws BadArgs {
        // process options
        for (int i=0; i < args.length; i++) {
            if (args[i].charAt(0) == '-') {
                String name = args[i];
                Option option = getOption(name);
                String param = null;
                if (option.hasArg) {
                    if (name.startsWith("--") && name.indexOf('=') > 0) {
                        param = name.substring(name.indexOf('=') + 1, name.length());
                    } else if (i + 1 < args.length) {
                        param = args[++i];
                    }
                    if (param == null || param.isEmpty() || param.charAt(0) == '-') {
                        throw new BadArgs("Missing arg for %n", name).showUsage(true);
                    }
                }
                option.process(this, name, param);
                if (option.ignoreRest()) {
                    i = args.length;
                }
            } else {
                // process rest of the input arguments
                Path p = Paths.get(args[i]);
                try {
                    moduleGraph.addAll(ModulesXmlReader.readModules(p)
                            .stream()
                            .collect(Collectors.toSet()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Option getOption(String name) throws BadArgs {
        for (Option o : recognizedOptions) {
            if (o.matches(name)) {
                return o;
            }
        }
        throw new BadArgs("Unknown option %s", name).showUsage(true);
    }

    private void reportError(String format, Object... args) {
        log.format("Error: " + format + "%n", args);
    }

    private void warning(String format, Object... args) {
        log.format("Warning: " + format + "%n", args);
    }

    private static final String USAGE =
            "ImageBuilder <options> --output <path> path-to-modules-xml\n";

    private static final String USAGE_SUMMARY =
            USAGE + "Use --help for a list of possible options.";

    private void showHelp() {
        log.format(USAGE);
        log.format("Possible options are:%n");
        for (Option o : recognizedOptions) {
            String name = o.aliases[0].substring(1); // there must always be at least one name
            name = name.charAt(0) == '-' ? name.substring(1) : name;
            if (o.isHidden() || name.equals("h"))
                continue;

            log.format("  --%s\t\t\t%s%n", name, o.description());
        }
    }
}
