/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.jdeps.PlatformClassPath.JDKArchive;
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
                    throw new BadArgs("err.dot.output.path", arg);
                }
                task.options.dotOutputDir = arg;
            }
        },
        new Option(false, "-s", "-summary") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.showSummary = true;
                task.options.verbose = Analyzer.Type.SUMMARY;
            }
        },
        new Option(false, "-v", "-verbose",
                          "-verbose:package",
                          "-verbose:class")
        {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                switch (opt) {
                    case "-v":
                    case "-verbose":
                        task.options.verbose = Analyzer.Type.VERBOSE;
                        break;
                    case "-verbose:package":
                            task.options.verbose = Analyzer.Type.PACKAGE;
                            break;
                    case "-verbose:class":
                            task.options.verbose = Analyzer.Type.CLASS;
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
        new Option(true, "-include") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.includePattern = Pattern.compile(arg);
            }
        },
        new Option(false, "-P", "-profile") {
            void process(JdepsTask task, String opt, String arg) throws BadArgs {
                task.options.showProfile = true;
                if (Profile.getProfileCount() == 0) {
                    throw new BadArgs("err.option.unsupported", opt, getMessage("err.profiles.msg"));
                }
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
            }
        },
        new Option(false, "-jdkinternals") {
            void process(JdepsTask task, String opt, String arg) {
                task.options.findJDKInternals = true;
                task.options.verbose = Analyzer.Type.CLASS;
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
    private final List<String> classes = new ArrayList<String>();

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
            if (options.findJDKInternals &&
                   (options.regex != null || options.packageNames.size() > 0 || options.showSummary)) {
                showHelp();
                return EXIT_CMDERR;
            }
            if (options.showSummary && options.verbose != Analyzer.Type.SUMMARY) {
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
    private boolean run() throws IOException {
        findDependencies();
        Analyzer analyzer = new Analyzer(options.verbose);
        analyzer.run(sourceLocations);
        if (options.dotOutputDir != null) {
            Path dir = Paths.get(options.dotOutputDir);
            Files.createDirectories(dir);
            generateDotFiles(dir, analyzer);
        } else {
            printRawOutput(log, analyzer);
        }
        return true;
    }

    private void generateDotFiles(Path dir, Analyzer analyzer) throws IOException {
        Path summary = dir.resolve("summary.dot");
        boolean verbose = options.verbose == Analyzer.Type.VERBOSE;
        DotGraph<?> graph = verbose ? new DotSummaryForPackage()
                                    : new DotSummaryForArchive();
        for (Archive archive : sourceLocations) {
            analyzer.visitArchiveDependences(archive, graph);
            if (verbose || options.showLabel) {
                // traverse detailed dependences to generate package-level
                // summary or build labels for edges
                analyzer.visitDependences(archive, graph);
            }
        }
        try (PrintWriter sw = new PrintWriter(Files.newOutputStream(summary))) {
            graph.writeTo(sw);
        }
        // output individual .dot file for each archive
        if (options.verbose != Analyzer.Type.SUMMARY) {
            for (Archive archive : sourceLocations) {
                if (analyzer.hasDependences(archive)) {
                    Path dotfile = dir.resolve(archive.getFileName() + ".dot");
                    try (PrintWriter pw = new PrintWriter(Files.newOutputStream(dotfile));
                         DotFileFormatter formatter = new DotFileFormatter(pw, archive)) {
                        analyzer.visitDependences(archive, formatter);
                    }
                }
            }
        }
    }

    private void printRawOutput(PrintWriter writer, Analyzer analyzer) {
        for (Archive archive : sourceLocations) {
            RawOutputFormatter formatter = new RawOutputFormatter(writer);
            analyzer.visitArchiveDependences(archive, formatter);
            if (options.verbose != Analyzer.Type.SUMMARY) {
                analyzer.visitDependences(archive, formatter);
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

    private Dependency.Filter getDependencyFilter() {
         if (options.regex != null) {
            return Dependencies.getRegexFilter(Pattern.compile(options.regex));
        } else if (options.packageNames.size() > 0) {
            return Dependencies.getPackageFilter(options.packageNames, false);
        } else {
            return new Dependency.Filter() {
                @Override
                public boolean accepts(Dependency dependency) {
                    return !dependency.getOrigin().equals(dependency.getTarget());
                }
            };
        }
    }

    private boolean matches(String classname, AccessFlags flags) {
        if (options.apiOnly && !flags.is(AccessFlags.ACC_PUBLIC)) {
            return false;
        } else if (options.includePattern != null) {
            return options.includePattern.matcher(classname.replace('/', '.')).matches();
        } else {
            return true;
        }
    }

    private void findDependencies() throws IOException {
        Dependency.Finder finder =
            options.apiOnly ? Dependencies.getAPIFinder(AccessFlags.ACC_PROTECTED)
                            : Dependencies.getClassDependencyFinder();
        Dependency.Filter filter = getDependencyFilter();

        List<Archive> archives = new ArrayList<>();
        Deque<String> roots = new LinkedList<>();
        for (String s : classes) {
            Path p = Paths.get(s);
            if (Files.exists(p)) {
                archives.add(new Archive(p, ClassFileReader.newInstance(p)));
            } else {
                if (isValidClassName(s)) {
                    roots.add(s);
                } else {
                    warning("warn.invalid.arg", s);
                }
            }
        }
        sourceLocations.addAll(archives);

        List<Archive> classpaths = new ArrayList<>(); // for class file lookup
        classpaths.addAll(getClassPathArchives(options.classpath));
        if (options.includePattern != null) {
            archives.addAll(classpaths);
        }
        classpaths.addAll(PlatformClassPath.getArchives());

        // add all classpath archives to the source locations for reporting
        sourceLocations.addAll(classpaths);

        // Work queue of names of classfiles to be searched.
        // Entries will be unique, and for classes that do not yet have
        // dependencies in the results map.
        Deque<String> deque = new LinkedList<>();
        Set<String> doneClasses = new HashSet<>();

        // get the immediate dependencies of the input files
        for (Archive a : archives) {
            for (ClassFile cf : a.reader().getClassFiles()) {
                String classFileName;
                try {
                    classFileName = cf.getName();
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }

                if (matches(classFileName, cf.access_flags)) {
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
                        }
                    }
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
        boolean showSummary;
        boolean wildcard;
        boolean apiOnly;
        boolean showLabel;
        boolean findJDKInternals;
        String dotOutputDir;
        String classpath = "";
        int depth = 1;
        Analyzer.Type verbose = Analyzer.Type.PACKAGE;
        Set<String> packageNames = new HashSet<>();
        String regex;             // apply to the dependences
        Pattern includePattern;   // apply to classes
    }
    private static class ResourceBundleHelper {
        static final ResourceBundle versionRB;
        static final ResourceBundle bundle;

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
        }
    }

    private List<Archive> getArchives(List<String> filenames) throws IOException {
        List<Archive> result = new ArrayList<Archive>();
        for (String s : filenames) {
            Path p = Paths.get(s);
            if (Files.exists(p)) {
                result.add(new Archive(p, ClassFileReader.newInstance(p)));
            } else {
                warning("warn.file.not.exist", s);
            }
        }
        return result;
    }

    private List<Archive> getClassPathArchives(String paths) throws IOException {
        List<Archive> result = new ArrayList<>();
        if (paths.isEmpty()) {
            return result;
        }
        for (String p : paths.split(File.pathSeparator)) {
            if (p.length() > 0) {
                List<Path> files = new ArrayList<>();
                // wildcard to parse all JAR files e.g. -classpath dir/*
                int i = p.lastIndexOf(".*");
                if (i > 0) {
                    Path dir = Paths.get(p.substring(0, i));
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
                        for (Path entry : stream) {
                            files.add(entry);
                        }
                    }
                } else {
                    files.add(Paths.get(p));
                }
                for (Path f : files) {
                    if (Files.exists(f)) {
                        result.add(new Archive(f, ClassFileReader.newInstance(f)));
                    }
                }
            }
        }
        return result;
    }

    /**
     * If the given archive is JDK archive and non-null Profile,
     * this method returns the profile name only if -profile option is specified;
     * a null profile indicates it accesses a private JDK API and this method
     * will return "JDK internal API".
     *
     * For non-JDK archives, this method returns the file name of the archive.
     */
    private String getProfileArchiveInfo(Archive source, Profile profile) {
        if (options.showProfile && profile != null)
            return profile.toString();

        if (source instanceof JDKArchive) {
            return profile == null ? "JDK internal API (" + source.getFileName() + ")" : "";
        }
        return source.getFileName();
    }

    /**
     * Returns the profile name or "JDK internal API" for JDK archive;
     * otherwise empty string.
     */
    private String profileName(Archive archive, Profile profile) {
        if (archive instanceof JDKArchive) {
            return Objects.toString(profile, "JDK internal API");
        } else {
            return "";
        }
    }

    class RawOutputFormatter implements Analyzer.Visitor {
        private final PrintWriter writer;
        RawOutputFormatter(PrintWriter writer) {
            this.writer = writer;
        }

        private String pkg = "";
        @Override
        public void visitDependence(String origin, Archive source,
                                    String target, Archive archive, Profile profile) {
            if (options.findJDKInternals &&
                    !(archive instanceof JDKArchive && profile == null)) {
                // filter dependences other than JDK internal APIs
                return;
            }
            if (options.verbose == Analyzer.Type.VERBOSE) {
                writer.format("   %-50s -> %-50s %s%n",
                              origin, target, getProfileArchiveInfo(archive, profile));
            } else {
                if (!origin.equals(pkg)) {
                    pkg = origin;
                    writer.format("   %s (%s)%n", origin, source.getFileName());
                }
                writer.format("      -> %-50s %s%n",
                              target, getProfileArchiveInfo(archive, profile));
            }
        }

        @Override
        public void visitArchiveDependence(Archive origin, Archive target, Profile profile) {
            writer.format("%s -> %s", origin.getPathName(), target.getPathName());
            if (options.showProfile && profile != null) {
                writer.format(" (%s)%n", profile);
            } else {
                writer.format("%n");
            }
        }
    }

    class DotFileFormatter extends DotGraph<String> implements AutoCloseable {
        private final PrintWriter writer;
        private final String name;
        DotFileFormatter(PrintWriter writer, Archive archive) {
            this.writer = writer;
            this.name = archive.getFileName();
            writer.format("digraph \"%s\" {%n", name);
            writer.format("    // Path: %s%n", archive.getPathName());
        }

        @Override
        public void close() {
            writer.println("}");
        }

        @Override
        public void visitDependence(String origin, Archive source,
                                    String target, Archive archive, Profile profile) {
            if (options.findJDKInternals &&
                    !(archive instanceof JDKArchive && profile == null)) {
                // filter dependences other than JDK internal APIs
                return;
            }
            // if -P option is specified, package name -> profile will
            // be shown and filter out multiple same edges.
            String name = getProfileArchiveInfo(archive, profile);
            writeEdge(writer, new Edge(origin, target, getProfileArchiveInfo(archive, profile)));
        }
        @Override
        public void visitArchiveDependence(Archive origin, Archive target, Profile profile) {
            throw new UnsupportedOperationException();
        }
    }

    class DotSummaryForArchive extends DotGraph<Archive> {
        @Override
        public void visitDependence(String origin, Archive source,
                                    String target, Archive archive, Profile profile) {
            Edge e = findEdge(source, archive);
            assert e != null;
            // add the dependency to the label if enabled and not compact1
            if (profile == Profile.COMPACT1) {
                return;
            }
            e.addLabel(origin, target, profileName(archive, profile));
        }
        @Override
        public void visitArchiveDependence(Archive origin, Archive target, Profile profile) {
            // add an edge with the archive's name with no tag
            // so that there is only one node for each JDK archive
            // while there may be edges to different profiles
            Edge e = addEdge(origin, target, "");
            if (target instanceof JDKArchive) {
                // add a label to print the profile
                if (profile == null) {
                    e.addLabel("JDK internal API");
                } else if (options.showProfile && !options.showLabel) {
                    e.addLabel(profile.toString());
                }
            }
        }
    }

    // DotSummaryForPackage generates the summary.dot file for verbose mode
    // (-v or -verbose option) that includes all class dependencies.
    // The summary.dot file shows package-level dependencies.
    class DotSummaryForPackage extends DotGraph<String> {
        private String packageOf(String cn) {
            int i = cn.lastIndexOf('.');
            return i > 0 ? cn.substring(0, i) : "<unnamed>";
        }
        @Override
        public void visitDependence(String origin, Archive source,
                                    String target, Archive archive, Profile profile) {
            // add a package dependency edge
            String from = packageOf(origin);
            String to = packageOf(target);
            Edge e = addEdge(from, to, getProfileArchiveInfo(archive, profile));

            // add the dependency to the label if enabled and not compact1
            if (!options.showLabel || profile == Profile.COMPACT1) {
                return;
            }

            // trim the package name of origin to shorten the label
            int i = origin.lastIndexOf('.');
            String n1 = i < 0 ? origin : origin.substring(i+1);
            e.addLabel(n1, target, profileName(archive, profile));
        }
        @Override
        public void visitArchiveDependence(Archive origin, Archive target, Profile profile) {
            // nop
        }
    }
    abstract class DotGraph<T> implements Analyzer.Visitor  {
        private final Set<Edge> edges = new LinkedHashSet<>();
        private Edge curEdge;
        public void writeTo(PrintWriter writer) {
            writer.format("digraph \"summary\" {%n");
            for (Edge e: edges) {
                writeEdge(writer, e);
            }
            writer.println("}");
        }

        void writeEdge(PrintWriter writer, Edge e) {
            writer.format("   %-50s -> \"%s\"%s;%n",
                          String.format("\"%s\"", e.from.toString()),
                          e.tag.isEmpty() ? e.to
                                          : String.format("%s (%s)", e.to, e.tag),
                          getLabel(e));
        }

        Edge addEdge(T origin, T target, String tag) {
            Edge e = new Edge(origin, target, tag);
            if (e.equals(curEdge)) {
                return curEdge;
            }

            if (edges.contains(e)) {
                for (Edge e1 : edges) {
                   if (e.equals(e1)) {
                       curEdge = e1;
                   }
                }
            } else {
                edges.add(e);
                curEdge = e;
            }
            return curEdge;
        }

        Edge findEdge(T origin, T target) {
            for (Edge e : edges) {
                if (e.from.equals(origin) && e.to.equals(target)) {
                    return e;
                }
            }
            return null;
        }

        String getLabel(Edge e) {
            String label = e.label.toString();
            return label.isEmpty() ? "" : String.format("[label=\"%s\",fontsize=9]", label);
        }

        class Edge {
            final T from;
            final T to;
            final String tag;  // optional tag
            final StringBuilder label = new StringBuilder();
            Edge(T from, T to, String tag) {
                this.from = from;
                this.to = to;
                this.tag = tag;
            }
            void addLabel(String s) {
                label.append(s).append("\\n");
            }
            void addLabel(String origin, String target, String profile) {
                label.append(origin).append(" -> ").append(target);
                if (!profile.isEmpty()) {
                    label.append(" (" + profile + ")");
                }
                label.append("\\n");
            }
            @Override @SuppressWarnings("unchecked")
            public boolean equals(Object o) {
                if (o instanceof DotGraph<?>.Edge) {
                    DotGraph<?>.Edge e = (DotGraph<?>.Edge)o;
                    return this.from.equals(e.from) &&
                           this.to.equals(e.to) &&
                           this.tag.equals(e.tag);
                }
                return false;
            }
            @Override
            public int hashCode() {
                int hash = 7;
                hash = 67 * hash + Objects.hashCode(this.from) +
                       Objects.hashCode(this.to) + Objects.hashCode(this.tag);
                return hash;
            }
        }
    }
}
