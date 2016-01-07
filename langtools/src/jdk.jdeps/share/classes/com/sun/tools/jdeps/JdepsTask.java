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

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Dependencies;
import com.sun.tools.classfile.Dependencies.ClassFileError;
import com.sun.tools.classfile.Dependency;
import com.sun.tools.classfile.Dependency.Location;
import static com.sun.tools.jdeps.Analyzer.Type.*;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

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
        new Option(true, "-cp", "-classpath") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.classpath = arg;
            }
        },
        new Option(true, "-p", "-package") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.packageNames.add(arg);
            }
        },
        new Option(true, "-e", "-regex") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.regex = arg;
            }
        },

        new Option(true, "-f", "-filter") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.filterRegex = arg;
            }
        },
        new Option(false, "-filter:package",
                          "-filter:archive",
                          "-filter:none") {
            void process(JdepsTask task, String opt, String arg) {
                switch (opt) {
                    case "-filter:package":
                        task.options.filterSamePackage = true;
                        task.options.filterSameArchive = false;
                        break;
                    case "-filter:archive":
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
        new Option(false, "-M", "-module") {
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
        new Option(false, "-jdkinternals") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.findJDKInternals = true;
                task.options.verbose = CLASS;
                if (task.options.includePattern == null) {
                    task.options.includePattern = Pattern.compile(".*");
                }
            }
        },
            new HiddenOption(false, "-verify:access") {
                void process(JdepsTask task, String opt, String arg) {
                    task.options.verifyAccess = true;
                    task.options.verbose = VERBOSE;
                    task.options.filterSameArchive = false;
                    task.options.filterSamePackage = false;
                }
            },
            new HiddenOption(true, "-mp") {
                void process(JdepsTask task, String opt, String arg) throws BadArgs {
                    task.options.mpath = Paths.get(arg);
                    if (!Files.isDirectory(task.options.mpath)) {
                        throw new BadArgs("err.invalid.path", arg);
                    }
                    if (task.options.includePattern == null) {
                        task.options.includePattern = Pattern.compile(".*");
                    }
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
        new HiddenOption(false, "-q", "-quiet") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.nowarning = true;
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
            if (classes.isEmpty() && options.includePattern == null) {
                if (options.help || options.version || options.fullVersion) {
                    return EXIT_OK;
                } else {
                    showHelp();
                    return EXIT_CMDERR;
                }
            }
            if (options.regex != null && options.packageNames.size() > 0) {
                showHelp();
                return EXIT_CMDERR;
            }
            if ((options.findJDKInternals || options.verifyAccess) &&
                   (options.regex != null || options.packageNames.size() > 0 || options.showSummary)) {
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
        } catch (IOException e) {
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    private final List<Archive> sourceLocations = new ArrayList<>();
    private final List<Archive> classpaths = new ArrayList<>();
    private final List<Archive> initialArchives = new ArrayList<>();
    private boolean run() throws IOException {
        buildArchives();

        if (options.verifyAccess) {
            return verifyModuleAccess();
        } else {
            return analyzeDeps();
        }
    }

    private boolean analyzeDeps() throws IOException {
        Analyzer analyzer = new Analyzer(options.verbose, new Analyzer.Filter() {
            @Override
            public boolean accepts(Location origin, Archive originArchive,
                                   Location target, Archive targetArchive)
            {
                if (options.findJDKInternals) {
                    // accepts target that is JDK class but not exported
                    return isJDKModule(targetArchive) &&
                              !((Module) targetArchive).isExported(target.getClassName());
                } else if (options.filterSameArchive) {
                    // accepts origin and target that from different archive
                    return originArchive != targetArchive;
                }
                return true;
            }
        });

        // parse classfiles and find all dependencies
        findDependencies(options.apiOnly);

        // analyze the dependencies
        analyzer.run(sourceLocations);

        // output result
        if (options.dotOutputDir != null) {
            Path dir = Paths.get(options.dotOutputDir);
            Files.createDirectories(dir);
            generateDotFiles(dir, analyzer);
        } else {
            printRawOutput(log, analyzer);
        }

        if (options.findJDKInternals && !options.nowarning) {
            showReplacements(analyzer);
        }
        return true;
    }

    private boolean verifyModuleAccess() throws IOException {
        // two passes
        // 1. check API dependences where the types of dependences must be re-exported
        // 2. check all dependences where types must be accessible

        // pass 1
        findDependencies(true /* api only */);
        Analyzer analyzer = Analyzer.getExportedAPIsAnalyzer();
        boolean pass1 = analyzer.run(sourceLocations);
        if (!pass1) {
            System.out.println("ERROR: Failed API access verification");
        }
        // pass 2
        findDependencies(false);
        analyzer =  Analyzer.getModuleAccessAnalyzer();
        boolean pass2 = analyzer.run(sourceLocations);
        if (!pass2) {
            System.out.println("ERROR: Failed module access verification");
        }
        if (pass1 & pass2) {
            System.out.println("Access verification succeeded.");
        }
        return pass1 & pass2;
    }

    private void generateSummaryDotFile(Path dir, Analyzer analyzer) throws IOException {
        // If verbose mode (-v or -verbose option),
        // the summary.dot file shows package-level dependencies.
        Analyzer.Type summaryType =
            (options.verbose == PACKAGE || options.verbose == SUMMARY) ? SUMMARY : PACKAGE;
        Path summary = dir.resolve("summary.dot");
        try (PrintWriter sw = new PrintWriter(Files.newOutputStream(summary));
             SummaryDotFile dotfile = new SummaryDotFile(sw, summaryType)) {
            for (Archive archive : sourceLocations) {
                if (!archive.isEmpty()) {
                    if (options.verbose == PACKAGE || options.verbose == SUMMARY) {
                        if (options.showLabel) {
                            // build labels listing package-level dependencies
                            analyzer.visitDependences(archive, dotfile.labelBuilder(), PACKAGE);
                        }
                    }
                    analyzer.visitDependences(archive, dotfile, summaryType);
                }
            }
        }
    }

    private void generateDotFiles(Path dir, Analyzer analyzer) throws IOException {
        // output individual .dot file for each archive
        if (options.verbose != SUMMARY) {
            for (Archive archive : sourceLocations) {
                if (analyzer.hasDependences(archive)) {
                    Path dotfile = dir.resolve(archive.getName() + ".dot");
                    try (PrintWriter pw = new PrintWriter(Files.newOutputStream(dotfile));
                         DotFileFormatter formatter = new DotFileFormatter(pw, archive)) {
                        analyzer.visitDependences(archive, formatter);
                    }
                }
            }
        }
        // generate summary dot file
        generateSummaryDotFile(dir, analyzer);
    }

    private void printRawOutput(PrintWriter writer, Analyzer analyzer) {
        RawOutputFormatter depFormatter = new RawOutputFormatter(writer);
        RawSummaryFormatter summaryFormatter = new RawSummaryFormatter(writer);
        for (Archive archive : sourceLocations) {
            if (!archive.isEmpty()) {
                analyzer.visitDependences(archive, summaryFormatter, SUMMARY);
                if (analyzer.hasDependences(archive) && options.verbose != SUMMARY) {
                    analyzer.visitDependences(archive, depFormatter);
                }
            }
        }
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

    /*
     * Dep Filter configured based on the input jdeps option
     * 1. -p and -regex to match target dependencies
     * 2. -filter:package to filter out same-package dependencies
     *
     * This filter is applied when jdeps parses the class files
     * and filtered dependencies are not stored in the Analyzer.
     *
     * -filter:archive is applied later in the Analyzer as the
     * containing archive of a target class may not be known until
     * the entire archive
     */
    class DependencyFilter implements Dependency.Filter {
        final Dependency.Filter filter;
        final Pattern filterPattern;
        DependencyFilter() {
            if (options.regex != null) {
                this.filter = Dependencies.getRegexFilter(Pattern.compile(options.regex));
            } else if (options.packageNames.size() > 0) {
                this.filter = Dependencies.getPackageFilter(options.packageNames, false);
            } else {
                this.filter = null;
            }

            this.filterPattern =
                options.filterRegex != null ? Pattern.compile(options.filterRegex) : null;
        }
        @Override
        public boolean accepts(Dependency d) {
            if (d.getOrigin().equals(d.getTarget())) {
                return false;
            }
            String pn = d.getTarget().getPackageName();
            if (options.filterSamePackage && d.getOrigin().getPackageName().equals(pn)) {
                return false;
            }

            if (filterPattern != null && filterPattern.matcher(pn).matches()) {
                return false;
            }
            return filter != null ? filter.accepts(d) : true;
        }
    }

    /**
     * Tests if the given class matches the pattern given in the -include option
     */
    private boolean matches(String classname) {
        if (options.includePattern != null) {
            return options.includePattern.matcher(classname.replace('/', '.')).matches();
        } else {
            return true;
        }
    }

    private void buildArchives() throws IOException {
        for (String s : classes) {
            Path p = Paths.get(s);
            if (Files.exists(p)) {
                initialArchives.add(Archive.getInstance(p));
            }
        }
        sourceLocations.addAll(initialArchives);

        classpaths.addAll(getClassPathArchives(options.classpath));
        if (options.includePattern != null) {
            initialArchives.addAll(classpaths);
        }
        classpaths.addAll(PlatformClassPath.getModules(options.mpath));
        if (options.mpath != null) {
            initialArchives.addAll(PlatformClassPath.getModules(options.mpath));
        } else {
            classpaths.addAll(PlatformClassPath.getJarFiles());
        }
        // add all classpath archives to the source locations for reporting
        sourceLocations.addAll(classpaths);
    }

    private void findDependencies(boolean apiOnly) throws IOException {
        Dependency.Finder finder =
            apiOnly ? Dependencies.getAPIFinder(AccessFlags.ACC_PROTECTED)
                    : Dependencies.getClassDependencyFinder();
        Dependency.Filter filter = new DependencyFilter();

        Deque<String> roots = new LinkedList<>();
        for (String s : classes) {
            Path p = Paths.get(s);
            if (!Files.exists(p)) {
                if (isValidClassName(s)) {
                    roots.add(s);
                } else {
                    warning("warn.invalid.arg", s);
                }
            }
        }

        // Work queue of names of classfiles to be searched.
        // Entries will be unique, and for classes that do not yet have
        // dependencies in the results map.
        Deque<String> deque = new LinkedList<>();
        Set<String> doneClasses = new HashSet<>();

        // get the immediate dependencies of the input files
        for (Archive a : initialArchives) {
            for (ClassFile cf : a.reader().getClassFiles()) {
                String classFileName;
                try {
                    classFileName = cf.getName();
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }

                // tests if this class matches the -include or -apiOnly option if specified
                if (!matches(classFileName) || (apiOnly && !cf.access_flags.is(AccessFlags.ACC_PUBLIC))) {
                    continue;
                }

                if (!doneClasses.contains(classFileName)) {
                    doneClasses.add(classFileName);
                }

                for (Dependency d : finder.findDependencies(cf)) {
                    if (filter.accepts(d)) {
                        String cn = d.getTarget().getName();
                        if (!doneClasses.contains(cn) && !deque.contains(cn)) {
                            deque.add(cn);
                        }
                        a.addClass(d.getOrigin(), d.getTarget());
                    } else {
                        // ensure that the parsed class is added the archive
                        a.addClass(d.getOrigin());
                    }
                }
                for (String name : a.reader().skippedEntries()) {
                    warning("warn.skipped.entry", name, a.getPathName());
                }
            }
        }

        // add Archive for looking up classes from the classpath
        // for transitive dependency analysis
        Deque<String> unresolved = roots;
        int depth = options.depth > 0 ? options.depth : Integer.MAX_VALUE;
        do {
            String name;
            while ((name = unresolved.poll()) != null) {
                if (doneClasses.contains(name)) {
                    continue;
                }
                ClassFile cf = null;
                for (Archive a : classpaths) {
                    cf = a.reader().getClassFile(name);
                    if (cf != null) {
                        String classFileName;
                        try {
                            classFileName = cf.getName();
                        } catch (ConstantPoolException e) {
                            throw new ClassFileError(e);
                        }
                        if (!doneClasses.contains(classFileName)) {
                            // if name is a fully-qualified class name specified
                            // from command-line, this class might already be parsed
                            doneClasses.add(classFileName);

                            for (Dependency d : finder.findDependencies(cf)) {
                                if (depth == 0) {
                                    // ignore the dependency
                                    a.addClass(d.getOrigin());
                                    break;
                                } else if (filter.accepts(d)) {
                                    a.addClass(d.getOrigin(), d.getTarget());
                                    String cn = d.getTarget().getName();
                                    if (!doneClasses.contains(cn) && !deque.contains(cn)) {
                                        deque.add(cn);
                                    }
                                } else {
                                    // ensure that the parsed class is added the archive
                                    a.addClass(d.getOrigin());
                                }
                            }
                        }
                        break;
                    }
                }
                if (cf == null) {
                    doneClasses.add(name);
                }
            }
            unresolved = deque;
            deque = new LinkedList<>();
        } while (!unresolved.isEmpty() && depth-- > 0);
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
        boolean nowarning;
        // default is to show package-level dependencies
        // and filter references from same package
        Analyzer.Type verbose = PACKAGE;
        boolean filterSamePackage = true;
        boolean filterSameArchive = false;
        String filterRegex;
        String dotOutputDir;
        String classpath = "";
        int depth = 1;
        Set<String> packageNames = new HashSet<>();
        String regex;             // apply to the dependences
        Pattern includePattern;   // apply to classes
        // module boundary access check
        boolean verifyAccess;
        Path mpath;
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
    private List<Archive> getClassPathArchives(String cpaths)
            throws IOException
    {
        List<Archive> result = new ArrayList<>();
        if (cpaths.isEmpty()) {
            return result;
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
        for (Path path : paths) {
            boolean found = initialArchives.stream()
                                           .map(Archive::path)
                                           .anyMatch(p -> isSameFile(path, p));
            if (!found && Files.exists(path)) {
                result.add(Archive.getInstance(path));
            }
        }
        return result;
    }

    private boolean isSameFile(Path p1, Path p2) {
        try {
            return Files.isSameFile(p1, p2);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    class RawOutputFormatter implements Analyzer.Visitor {
        private final PrintWriter writer;
        private String pkg = "";
        RawOutputFormatter(PrintWriter writer) {
            this.writer = writer;
        }
        @Override
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive) {
            String tag = toTag(target, targetArchive);
            if (options.verbose == VERBOSE) {
                writer.format("   %-50s -> %-50s %s%n", origin, target, tag);
            } else {
                if (!origin.equals(pkg)) {
                    pkg = origin;
                    writer.format("   %s (%s)%n", origin, originArchive.getName());
                }
                writer.format("      -> %-50s %s%n", target, tag);
            }
        }
    }

    class RawSummaryFormatter implements Analyzer.Visitor {
        private final PrintWriter writer;
        RawSummaryFormatter(PrintWriter writer) {
            this.writer = writer;
        }
        @Override
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive) {
            String targetName =  targetArchive.getPathName();
            if (options.showModule && isJDKModule(targetArchive)) {
                targetName = ((Module)targetArchive).name();
            }
            writer.format("%s -> %s", originArchive.getName(), targetName);
            if (options.showProfile && isJDKModule(targetArchive)) {
                writer.format(" (%s)", target);
            }
            writer.format("%n");
        }
    }

    class DotFileFormatter implements Analyzer.Visitor, AutoCloseable {
        private final PrintWriter writer;
        private final String name;
        DotFileFormatter(PrintWriter writer, Archive archive) {
            this.writer = writer;
            this.name = archive.getName();
            writer.format("digraph \"%s\" {%n", name);
            writer.format("    // Path: %s%n", archive.getPathName());
        }

        @Override
        public void close() {
            writer.println("}");
        }

        @Override
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive) {
            String tag = toTag(target, targetArchive);
            writer.format("   %-50s -> \"%s\";%n",
                          String.format("\"%s\"", origin),
                          tag.isEmpty() ? target
                                        : String.format("%s (%s)", target, tag));
        }
    }

    class SummaryDotFile implements Analyzer.Visitor, AutoCloseable {
        private final PrintWriter writer;
        private final Analyzer.Type type;
        private final Map<Archive, Map<Archive,StringBuilder>> edges = new HashMap<>();
        SummaryDotFile(PrintWriter writer, Analyzer.Type type) {
            this.writer = writer;
            this.type = type;
            writer.format("digraph \"summary\" {%n");
        }

        @Override
        public void close() {
            writer.println("}");
        }

        @Override
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive) {
            String targetName = type == PACKAGE ? target : targetArchive.getName();
            if (isJDKModule(targetArchive)) {
                Module m = (Module)targetArchive;
                String n = showProfileOrModule(m);
                if (!n.isEmpty()) {
                    targetName += " (" + n + ")";
                }
            } else if (type == PACKAGE) {
                targetName += " (" + targetArchive.getName() + ")";
            }
            String label = getLabel(originArchive, targetArchive);
            writer.format("  %-50s -> \"%s\"%s;%n",
                          String.format("\"%s\"", origin), targetName, label);
        }

        String getLabel(Archive origin, Archive target) {
            if (edges.isEmpty())
                return "";

            StringBuilder label = edges.get(origin).get(target);
            return label == null ? "" : String.format(" [label=\"%s\",fontsize=9]", label.toString());
        }

        Analyzer.Visitor labelBuilder() {
            // show the package-level dependencies as labels in the dot graph
            return new Analyzer.Visitor() {
                @Override
                public void visitDependence(String origin, Archive originArchive, String target, Archive targetArchive) {
                    edges.putIfAbsent(originArchive, new HashMap<>());
                    edges.get(originArchive).putIfAbsent(targetArchive, new StringBuilder());
                    StringBuilder sb = edges.get(originArchive).get(targetArchive);
                    String tag = toTag(target, targetArchive);
                    addLabel(sb, origin, target, tag);
                }

                void addLabel(StringBuilder label, String origin, String target, String tag) {
                    label.append(origin).append(" -> ").append(target);
                    if (!tag.isEmpty()) {
                        label.append(" (" + tag + ")");
                    }
                    label.append("\\n");
                }
            };
        }
    }

    /**
     * Test if the given archive is part of the JDK
     */
    private boolean isJDKModule(Archive archive) {
        return Module.class.isInstance(archive);
    }

    /**
     * If the given archive is JDK archive, this method returns the profile name
     * only if -profile option is specified; it accesses a private JDK API and
     * the returned value will have "JDK internal API" prefix
     *
     * For non-JDK archives, this method returns the file name of the archive.
     */
    private String toTag(String name, Archive source) {
        if (!isJDKModule(source)) {
            return source.getName();
        }

        Module module = (Module)source;
        boolean isExported = false;
        if (options.verbose == CLASS || options.verbose == VERBOSE) {
            isExported = module.isExported(name);
        } else {
            isExported = module.isExportedPackage(name);
        }
        if (isExported) {
            // exported API
            return showProfileOrModule(module);
        } else {
            return "JDK internal API (" + source.getName() + ")";
        }
    }

    private String showProfileOrModule(Module m) {
        String tag = "";
        if (options.showProfile) {
            Profile p = Profile.getProfile(m);
            if (p != null) {
                tag = p.profileName();
            }
        } else if (options.showModule) {
            tag = m.name();
        }
        return tag;
    }

    private Profile getProfile(String name) {
        String pn = name;
        if (options.verbose == CLASS || options.verbose == VERBOSE) {
            int i = name.lastIndexOf('.');
            pn = i > 0 ? name.substring(0, i) : "";
        }
        return Profile.getProfile(pn);
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

    private void showReplacements(Analyzer analyzer) {
        Map<String,String> jdkinternals = new TreeMap<>();
        boolean useInternals = false;
        for (Archive source : sourceLocations) {
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
