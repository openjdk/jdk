/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.jdeps;

import static com.sun.tools.jdeps.Analyzer.Type.*;
import static com.sun.tools.jdeps.JdepsWriter.*;
import static com.sun.tools.jdeps.ModuleAnalyzer.Graph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.ResolutionException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation for the jdeps tool for static class dependency analysis.
 */
class JdepsTask {
    static class BadArgs extends Exception {
        static final long serialVersionUID = 8765093759964640721L;
        BadArgs(String key, Object... args) {
            super(JdepsTask.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
        final String key;
        final Object[] args;
        boolean showUsage;
    }

    static abstract class Option {
        Option(boolean hasArg, String... aliases) {
            this.hasArg = hasArg;
            this.aliases = aliases;
        }

        boolean isHidden() {
            return false;
        }

        boolean matches(String opt) {
            for (String a : aliases) {
                if (a.equals(opt))
                    return true;
                if (hasArg && opt.startsWith(a + "="))
                    return true;
            }
            return false;
        }

        boolean ignoreRest() {
            return false;
        }

        abstract void process(JdepsTask task, String opt, String arg) throws BadArgs;
        final boolean hasArg;
        final String[] aliases;
    }

    static abstract class HiddenOption extends Option {
        HiddenOption(boolean hasArg, String... aliases) {
            super(hasArg, aliases);
        }

        boolean isHidden() {
            return true;
        }
    }

    static Option[] recognizedOptions = {
        new Option(false, "-h", "-?", "-help") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.help = true;
            }
        },
        new Option(true, "-dotoutput") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                Path p = Paths.get(arg);
                if (Files.exists(p) && (!Files.isDirectory(p) || !Files.isWritable(p))) {
                    throw new BadArgs("err.invalid.path", arg);
                }
                task.options.dotOutputDir = arg;
            }
        },
        new Option(false, "-s", "-summary") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.showSummary = true;
                task.options.verbose = SUMMARY;
            }
        },
        new Option(false, "-v", "-verbose",
                          "-verbose:package",
                          "-verbose:class") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                switch (opt) {
                    case "-v":
                    case "-verbose":
                        task.options.verbose = VERBOSE;
                        task.options.filterSameArchive = false;
                        task.options.filterSamePackage = false;
                        break;
                    case "-verbose:package":
                        task.options.verbose = PACKAGE;
                        break;
                    case "-verbose:class":
                        task.options.verbose = CLASS;
                        break;
                    default:
                        throw new BadArgs("err.invalid.arg.for.option", opt);
                }
            }
        },
        new Option(true, "-p", "-package") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.packageNames.add(arg);
            }
        },
        new Option(true, "-e", "-regex") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.regex = Pattern.compile(arg);
            }
        },
        new Option(true, "-module") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.requires.add(arg);
            }
        },
        new Option(true, "-f", "-filter") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.filterRegex = Pattern.compile(arg);
            }
        },
        new Option(false, "-filter:package",
                          "-filter:archive", "-filter:module",
                          "-filter:none") {
            void process(JdepsTask task, String opt, String arg) {
                switch (opt) {
                    case "-filter:package":
                        task.options.filterSamePackage = true;
                        task.options.filterSameArchive = false;
                        break;
                    case "-filter:archive":
                    case "-filter:module":
                        task.options.filterSameArchive = true;
                        task.options.filterSamePackage = false;
                        break;
                    case "-filter:none":
                        task.options.filterSameArchive = false;
                        task.options.filterSamePackage = false;
                        break;
                }
            }
        },
        new Option(true, "-include") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.includePattern = Pattern.compile(arg);
            }
        },
        new Option(false, "-P", "-profile") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.showProfile = true;
                task.options.showModule = false;
            }
        },
        new Option(false, "-M") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.showModule = true;
                task.options.showProfile = false;
            }
        },
        new Option(false, "-apionly") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.apiOnly = true;
            }
        },
        new Option(false, "-R", "-recursive") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.depth = 0;
                // turn off filtering
                task.options.filterSameArchive = false;
                task.options.filterSamePackage = false;
            }
        },
        new Option(true, "-genmoduleinfo") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                Path p = Paths.get(arg);
                if (Files.exists(p) && (!Files.isDirectory(p) || !Files.isWritable(p))) {
                    throw new BadArgs("err.invalid.path", arg);
                }
                task.options.genModuleInfo = arg;
            }
        },
        new Option(false, "-ct", "-compile-time") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.compileTimeView = true;
                task.options.filterSamePackage = true;
                task.options.filterSameArchive = true;
                task.options.depth = 0;
            }
        },
        new Option(false, "-jdkinternals") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.findJDKInternals = true;
                task.options.verbose = CLASS;
                if (task.options.includePattern == null) {
                    task.options.includePattern = Pattern.compile(".*");
                }
            }
        },
        new Option(true, "-cp", "-classpath") {
            void process(JdepsTask task, String opt, String arg) {
                    task.options.classpath = arg;
                }
        },
        new Option(true, "-mp", "-modulepath") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.modulePath = arg;
                task.options.showModule = true;
            }
        },
        new Option(true, "-upgrademodulepath") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.upgradeModulePath = arg;
                task.options.showModule = true;
            }
        },
        new Option(true, "-m") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.rootModule = arg;
                task.options.includes.add(arg);
                task.options.showModule = true;
            }
        },
        new Option(false, "-check") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.checkModuleDeps = true;
            }
        },
        new HiddenOption(true, "-include-modules") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                Arrays.stream(arg.split(","))
                        .forEach(task.options.includes::add);
                task.options.showModule = true;
            }
        },
        new HiddenOption(true, "-exclude-modules") {
                void process(JdepsTask task, String opt, String arg) throws BadArgs {
                    Arrays.stream(arg.split(","))
                            .forEach(task.options.excludes::add);
                    task.options.showModule = true;
                }
        },
        new Option(false, "-q", "-quiet") {
            void process(JdepsTask task, String opt, String arg) {
                    task.options.nowarning = true;
                }
        },

        new Option(false, "-version") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.version = true;
            }
        },
        new HiddenOption(false, "-fullversion") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.fullVersion = true;
            }
        },
        new HiddenOption(false, "-showlabel") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.showLabel = true;
            }
        },

        new HiddenOption(true, "-depth") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                try {
                    task.options.depth = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new BadArgs("err.invalid.arg.for.option", opt);
                }
            }
        },
    };

    private static final String PROGNAME = "jdeps";
    private final Options options = new Options();
    private final List<String> classes = new ArrayList<>();

    private PrintWriter log;
    void setLog(PrintWriter out) {
        log = out;
    }

    /**
     * Result codes.
     */
    static final int EXIT_OK = 0, // Completed with no errors.
                     EXIT_ERROR = 1, // Completed but reported errors.
                     EXIT_CMDERR = 2, // Bad command-line arguments
                     EXIT_SYSERR = 3, // System error or resource exhaustion.
                     EXIT_ABNORMAL = 4;// terminated abnormally

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
            if (options.rootModule != null && !classes.isEmpty()) {
                reportError("err.invalid.module.option", options.rootModule, classes);
                return EXIT_CMDERR;
            }
            if (options.checkModuleDeps && options.rootModule == null) {
                reportError("err.root.module.not.set");
                return EXIT_CMDERR;
            }
            if (classes.isEmpty() && options.rootModule == null && options.includePattern == null) {
                if (options.help || options.version || options.fullVersion) {
                    return EXIT_OK;
                } else {
                    showHelp();
                    return EXIT_CMDERR;
                }
            }
            if (options.genModuleInfo != null) {
                if (options.dotOutputDir != null || !options.classpath.isEmpty() || options.hasFilter()) {
                    showHelp();
                    return EXIT_CMDERR;
                }
                // default to compile time view analysis
                options.compileTimeView = true;
                for (String fn : classes) {
                    Path p = Paths.get(fn);
                    if (!Files.exists(p) || !fn.endsWith(".jar")) {
                        reportError("err.genmoduleinfo.not.jarfile", fn);
                        return EXIT_CMDERR;
                    }
                }
            }

            if (options.numFilters() > 1) {
                reportError("err.invalid.filters");
                return EXIT_CMDERR;
            }

            if ((options.findJDKInternals) && (options.hasFilter() || options.showSummary)) {
                showHelp();
                return EXIT_CMDERR;
            }
            if (options.showSummary && options.verbose != SUMMARY) {
                showHelp();
                return EXIT_CMDERR;
            }

            boolean ok = run();
            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (BadArgs e) {
            reportError(e.key, e.args);
            if (e.showUsage) {
                log.println(getMessage("main.usage.summary", PROGNAME));
            }
            return EXIT_CMDERR;
        } catch (ResolutionException e) {
            reportError("err.exception.message", e.getMessage());
            return EXIT_CMDERR;
        } catch (IOException e) {
            e.printStackTrace();
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    private ModulePaths modulePaths;
    private boolean run() throws BadArgs, IOException {
        DependencyFinder dependencyFinder =
            new DependencyFinder(options.compileTimeView);

        buildArchive(dependencyFinder);

        if (options.rootModule != null &&
                (options.checkModuleDeps || (options.dotOutputDir != null &&
                                      options.verbose == SUMMARY))) {
            // -dotfile -s prints the configuration of the given root
            // -checkModuleDeps prints the suggested module-info.java
            return analyzeModules(dependencyFinder);
        }

        // otherwise analyze the dependencies
        if (options.genModuleInfo != null) {
            return genModuleInfo(dependencyFinder);
        } else {
            return analyzeDeps(dependencyFinder);
        }
    }

    private void buildArchive(DependencyFinder dependencyFinder)
            throws BadArgs, IOException
    {
        // If -genmoduleinfo is specified, the input arguments must be JAR files
        // Treat them as automatic modules for analysis
        List<Path> jarfiles = options.genModuleInfo != null
                                    ?  classes.stream().map(Paths::get)
                                              .collect(Collectors.toList())
                                    : Collections.emptyList();
        // Set module paths
        this.modulePaths = new ModulePaths(options.upgradeModulePath, options.modulePath, jarfiles);

        // add modules to dependency finder for analysis
        Map<String, Module> modules = modulePaths.getModules();
        modules.values().stream()
               .forEach(dependencyFinder::addModule);

        // If -m option is set, add the specified module and its transitive dependences
        // to the root set
        if (options.rootModule != null) {
            modulePaths.dependences(options.rootModule)
                       .forEach(dependencyFinder::addRoot);
        }

        // check if any module specified in -requires is missing
        Optional<String> req = options.requires.stream()
                .filter(mn -> !modules.containsKey(mn))
                .findFirst();
        if (req.isPresent()) {
            throw new BadArgs("err.module.not.found", req.get());
        }

        // classpath
        for (Path p : getClassPaths(options.classpath)) {
            if (Files.exists(p)) {
                dependencyFinder.addClassPathArchive(p);
            }
        }

        // if -genmoduleinfo is not set, the input arguments are considered as
        // unnamed module.  Add them to the root set
        if (options.genModuleInfo == null) {
            // add root set
            for (String s : classes) {
                Path p = Paths.get(s);
                if (Files.exists(p)) {
                    // add to the initial root set
                    dependencyFinder.addRoot(p);
                } else {
                    if (isValidClassName(s)) {
                        dependencyFinder.addClassName(s);
                    } else {
                        warning("warn.invalid.arg", s);
                    }
                }
            }
        }
    }

    private boolean analyzeDeps(DependencyFinder dependencyFinder) throws IOException {
        JdepsFilter filter = dependencyFilter();

        // parse classfiles and find all dependencies
        findDependencies(dependencyFinder, filter, options.apiOnly);

        // analyze the dependencies collected
        Analyzer analyzer = new Analyzer(options.verbose, filter);
        analyzer.run(dependencyFinder.archives());

        // output result
        final JdepsWriter writer;
        if (options.dotOutputDir != null) {
            Path dir = Paths.get(options.dotOutputDir);
            Files.createDirectories(dir);
            writer = new DotFileWriter(dir, options.verbose,
                                       options.showProfile,
                                       options.showModule,
                                       options.showLabel);
        } else {
            writer = new SimpleWriter(log, options.verbose,
                                      options.showProfile,
                                      options.showModule);
        }

        // Targets for reporting - include the root sets and other analyzed archives
        final List<Archive> targets;
        if (options.rootModule == null) {
            // no module as the root set
            targets = dependencyFinder.archives()
                                      .filter(filter::accept)
                                      .filter(a -> !a.getModule().isNamed())
                                      .collect(Collectors.toList());
        } else {
            // named modules in topological order
            Stream<Module> modules = dependencyFinder.archives()
                                                     .filter(a -> a.getModule().isNamed())
                                                     .map(Archive::getModule);
            Graph<Module> graph = ModuleAnalyzer.graph(modulePaths, modules.toArray(Module[]::new));
            // then add unnamed module
            targets = graph.orderedNodes()
                           .filter(filter::accept)
                           .collect(Collectors.toList());

            // in case any reference not found
            dependencyFinder.archives()
                    .filter(a -> !a.getModule().isNamed())
                    .forEach(targets::add);
        }

        writer.generateOutput(targets, analyzer);
        if (options.findJDKInternals && !options.nowarning) {
            showReplacements(targets, analyzer);
        }
        return true;
    }

    private JdepsFilter dependencyFilter() {
        // Filter specified by -filter, -package, -regex, and -module options
        JdepsFilter.Builder builder = new JdepsFilter.Builder();

        // Exclude JDK modules from analysis and reporting if -m specified.
        modulePaths.getModules().values().stream()
                   .filter(m -> m.isJDK())
                   .map(Module::name)
                   .forEach(options.excludes::add);

        // source filters
        builder.includePattern(options.includePattern);
        builder.includeModules(options.includes);
        builder.excludeModules(options.excludes);

        builder.filter(options.filterSamePackage, options.filterSameArchive);
        builder.findJDKInternals(options.findJDKInternals);

        // -module
        if (!options.requires.isEmpty()) {
            Map<String, Module> modules = modulePaths.getModules();
            builder.packages(options.requires.stream()
                    .map(modules::get)
                    .flatMap(m -> m.packages().stream())
                    .collect(Collectors.toSet()));
        }
        // -regex
        if (options.regex != null)
            builder.regex(options.regex);
        // -package
        if (!options.packageNames.isEmpty())
            builder.packages(options.packageNames);
        // -filter
        if (options.filterRegex != null)
            builder.filter(options.filterRegex);

        return builder.build();
    }

    private void findDependencies(DependencyFinder dependencyFinder,
                                  JdepsFilter filter,
                                  boolean apiOnly)
        throws IOException
    {
        dependencyFinder.findDependencies(filter, apiOnly, options.depth);

        // print skipped entries, if any
        for (Archive a : dependencyFinder.roots()) {
            for (String name : a.reader().skippedEntries()) {
                warning("warn.skipped.entry", name, a.getPathName());
            }
        }
    }

    private boolean genModuleInfo(DependencyFinder dependencyFinder) throws IOException {
        ModuleInfoBuilder builder = new ModuleInfoBuilder(modulePaths, dependencyFinder);
        boolean result = builder.run(options.verbose, options.nowarning);
        builder.build(Paths.get(options.genModuleInfo));
        return result;
    }

    private boolean analyzeModules(DependencyFinder dependencyFinder)
            throws IOException
    {
        ModuleAnalyzer analyzer = new ModuleAnalyzer(modulePaths,
                                                     dependencyFinder,
                                                     options.rootModule);
        if (options.checkModuleDeps) {
            return analyzer.run();
        }
        if (options.dotOutputDir != null && options.verbose == SUMMARY) {
            Path dir = Paths.get(options.dotOutputDir);
            Files.createDirectories(dir);
            analyzer.genDotFile(dir);
            return true;
        }
        return false;
    }

    private boolean isValidClassName(String name) {
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i=1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '.'  && !Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return true;
    }

    public void handleOptions(String[] args) throws BadArgs {
        // process options
        for (int i=0; i < args.length; i++) {
            if (args[i].charAt(0) == '-') {
                String name = args[i];
                Option option = getOption(name);
                String param = null;
                if (option.hasArg) {
                    if (name.startsWith("-") && name.indexOf('=') > 0) {
                        param = name.substring(name.indexOf('=') + 1, name.length());
                    } else if (i + 1 < args.length) {
                        param = args[++i];
                    }
                    if (param == null || param.isEmpty() || param.charAt(0) == '-') {
                        throw new BadArgs("err.missing.arg", name).showUsage(true);
                    }
                }
                option.process(this, name, param);
                if (option.ignoreRest()) {
                    i = args.length;
                }
            } else {
                // process rest of the input arguments
                for (; i < args.length; i++) {
                    String name = args[i];
                    if (name.charAt(0) == '-') {
                        throw new BadArgs("err.option.after.class", name).showUsage(true);
                    }
                    classes.add(name);
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
            if (o.isHidden() || name.equals("h") || name.startsWith("filter:")) {
                continue;
            }
            log.println(getMessage("main.opt." + name));
        }
    }

    private void showVersion(boolean full) {
        log.println(version(full ? "full" : "release"));
    }

    private String version(String key) {
        // key=version:  mm.nn.oo[-milestone]
        // key=full:     mm.mm.oo[-milestone]-build
        if (ResourceBundleHelper.versionRB == null) {
            return System.getProperty("java.version");
        }
        try {
            return ResourceBundleHelper.versionRB.getString(key);
        } catch (MissingResourceException e) {
            return getMessage("version.unknown", System.getProperty("java.version"));
        }
    }

    static String getMessage(String key, Object... args) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private static class Options {
        boolean help;
        boolean version;
        boolean fullVersion;
        boolean showProfile;
        boolean showModule;
        boolean showSummary;
        boolean apiOnly;
        boolean showLabel;
        boolean findJDKInternals;
        boolean nowarning = false;
        // default is to show package-level dependencies
        // and filter references from same package
        Analyzer.Type verbose = PACKAGE;
        boolean filterSamePackage = true;
        boolean filterSameArchive = false;
        Pattern filterRegex;
        String dotOutputDir;
        String genModuleInfo;
        String classpath = "";
        int depth = 1;
        Set<String> requires = new HashSet<>();
        Set<String> packageNames = new HashSet<>();
        Pattern regex;             // apply to the dependences
        Pattern includePattern;    // apply to classes
        boolean compileTimeView = false;
        boolean checkModuleDeps = false;
        String upgradeModulePath;
        String modulePath;
        String rootModule;
        // modules to be included or excluded
        Set<String> includes = new HashSet<>();
        Set<String> excludes = new HashSet<>();

        boolean hasFilter() {
            return numFilters() > 0;
        }

        int numFilters() {
            int count = 0;
            if (requires.size() > 0) count++;
            if (regex != null) count++;
            if (packageNames.size() > 0) count++;
            return count;
        }

        boolean isRootModule() {
            return rootModule != null;
        }
    }
    private static class ResourceBundleHelper {
        static final ResourceBundle versionRB;
        static final ResourceBundle bundle;
        static final ResourceBundle jdkinternals;

        static {
            Locale locale = Locale.getDefault();
            try {
                bundle = ResourceBundle.getBundle("com.sun.tools.jdeps.resources.jdeps", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jdeps resource bundle for locale " + locale);
            }
            try {
                versionRB = ResourceBundle.getBundle("com.sun.tools.jdeps.resources.version");
            } catch (MissingResourceException e) {
                throw new InternalError("version.resource.missing");
            }
            try {
                jdkinternals = ResourceBundle.getBundle("com.sun.tools.jdeps.resources.jdkinternals");
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jdkinternals resource bundle");
            }
        }
    }

    /*
     * Returns the list of Archive specified in cpaths and not included
     * initialArchives
     */
    private List<Path> getClassPaths(String cpaths) throws IOException
    {
        if (cpaths.isEmpty()) {
            return Collections.emptyList();
        }
        List<Path> paths = new ArrayList<>();
        for (String p : cpaths.split(File.pathSeparator)) {
            if (p.length() > 0) {
                // wildcard to parse all JAR files e.g. -classpath dir/*
                int i = p.lastIndexOf(".*");
                if (i > 0) {
                    Path dir = Paths.get(p.substring(0, i));
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
                        for (Path entry : stream) {
                            paths.add(entry);
                        }
                    }
                } else {
                    paths.add(Paths.get(p));
                }
            }
        }
        return paths;
    }

    /**
     * Returns the recommended replacement API for the given classname;
     * or return null if replacement API is not known.
     */
    private String replacementFor(String cn) {
        String name = cn;
        String value = null;
        while (value == null && name != null) {
            try {
                value = ResourceBundleHelper.jdkinternals.getString(name);
            } catch (MissingResourceException e) {
                // go up one subpackage level
                int i = name.lastIndexOf('.');
                name = i > 0 ? name.substring(0, i) : null;
            }
        }
        return value;
    };

    private void showReplacements(List<Archive> archives, Analyzer analyzer) {
        Map<String,String> jdkinternals = new TreeMap<>();
        boolean useInternals = false;
        for (Archive source : archives) {
            useInternals = useInternals || analyzer.hasDependences(source);
            for (String cn : analyzer.dependences(source)) {
                String repl = replacementFor(cn);
                if (repl != null) {
                    jdkinternals.putIfAbsent(cn, repl);
                }
            }
        }
        if (useInternals) {
            log.println();
            warning("warn.replace.useJDKInternals", getMessage("jdeps.wiki.url"));
        }
        if (!jdkinternals.isEmpty()) {
            log.println();
            log.format("%-40s %s%n", "JDK Internal API", "Suggested Replacement");
            log.format("%-40s %s%n", "----------------", "---------------------");
            for (Map.Entry<String,String> e : jdkinternals.entrySet()) {
                log.format("%-40s %s%n", e.getKey(), e.getValue());
            }
        }
    }

}
