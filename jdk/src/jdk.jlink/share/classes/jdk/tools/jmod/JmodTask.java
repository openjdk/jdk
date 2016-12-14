/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jmod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import jdk.internal.jmod.JmodFile;
import jdk.internal.jmod.JmodFile.Section;
import jdk.internal.joptsimple.BuiltinHelpFormatter;
import jdk.internal.joptsimple.NonOptionArgumentSpec;
import jdk.internal.joptsimple.OptionDescriptor;
import jdk.internal.joptsimple.OptionException;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import jdk.internal.joptsimple.OptionSpec;
import jdk.internal.joptsimple.ValueConverter;
import jdk.internal.loader.ResourceHelper;
import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.ModuleHashes;
import jdk.internal.module.ModuleInfoExtender;
import jdk.tools.jlink.internal.Utils;

import static java.util.stream.Collectors.joining;

/**
 * Implementation for the jmod tool.
 */
public class JmodTask {

    static class CommandException extends RuntimeException {
        private static final long serialVersionUID = 0L;
        boolean showUsage;

        CommandException(String key, Object... args) {
            super(getMessageOrKey(key, args));
        }

        CommandException showUsage(boolean b) {
            showUsage = b;
            return this;
        }

        private static String getMessageOrKey(String key, Object... args) {
            try {
                return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
            } catch (MissingResourceException e) {
                return key;
            }
        }
    }

    private static final String PROGNAME = "jmod";
    private static final String MODULE_INFO = "module-info.class";

    private static final Path CWD = Paths.get("");

    private Options options;
    private PrintWriter out = new PrintWriter(System.out, true);
    void setLog(PrintWriter out, PrintWriter err) {
        this.out = out;
    }

    /* Result codes. */
    static final int EXIT_OK = 0, // Completed with no errors.
                     EXIT_ERROR = 1, // Completed but reported errors.
                     EXIT_CMDERR = 2, // Bad command-line arguments
                     EXIT_SYSERR = 3, // System error or resource exhaustion.
                     EXIT_ABNORMAL = 4;// terminated abnormally

    enum Mode {
        CREATE,
        EXTRACT,
        LIST,
        DESCRIBE,
        HASH
    };

    static class Options {
        Mode mode;
        Path jmodFile;
        boolean help;
        boolean version;
        List<Path> classpath;
        List<Path> cmds;
        List<Path> configs;
        List<Path> libs;
        List<Path> headerFiles;
        List<Path> manPages;
        List<Path> legalNotices;;
        ModuleFinder moduleFinder;
        Version moduleVersion;
        String mainClass;
        String osName;
        String osArch;
        String osVersion;
        Pattern modulesToHash;
        boolean dryrun;
        List<PathMatcher> excludes;
        Path extractDir;
    }

    public int run(String[] args) {

        try {
            handleOptions(args);
            if (options == null) {
                showUsageSummary();
                return EXIT_CMDERR;
            }
            if (options.help) {
                showHelp();
                return EXIT_OK;
            }
            if (options.version) {
                showVersion();
                return EXIT_OK;
            }

            boolean ok;
            switch (options.mode) {
                case CREATE:
                    ok = create();
                    break;
                case EXTRACT:
                    ok = extract();
                    break;
                case LIST:
                    ok = list();
                    break;
                case DESCRIBE:
                    ok = describe();
                    break;
                case HASH:
                    ok = hashModules();
                    break;
                default:
                    throw new AssertionError("Unknown mode: " + options.mode.name());
            }

            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (CommandException e) {
            reportError(e.getMessage());
            if (e.showUsage)
                showUsageSummary();
            return EXIT_CMDERR;
        } catch (Exception x) {
            reportError(x.getMessage());
            x.printStackTrace();
            return EXIT_ABNORMAL;
        } finally {
            out.flush();
        }
    }

    private boolean list() throws IOException {
        ZipFile zip = null;
        try {
            try {
                zip = new ZipFile(options.jmodFile.toFile());
            } catch (IOException x) {
                throw new IOException("error opening jmod file", x);
            }

            // Trivially print the archive entries for now, pending a more complete implementation
            zip.stream().forEach(e -> out.println(e.getName()));
            return true;
        } finally {
            if (zip != null)
                zip.close();
        }
    }

    private boolean extract() throws IOException {
        Path dir = options.extractDir != null ? options.extractDir : CWD;
        try (JmodFile jf = new JmodFile(options.jmodFile)) {
            jf.stream().forEach(e -> {
                try {
                    ZipEntry entry = e.zipEntry();
                    String name = entry.getName();
                    int index = name.lastIndexOf("/");
                    if (index != -1) {
                        Path p = dir.resolve(name.substring(0, index));
                        if (Files.notExists(p))
                            Files.createDirectories(p);
                    }

                    try (OutputStream os = Files.newOutputStream(dir.resolve(name))) {
                        jf.getInputStream(e).transferTo(os);
                    }
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                }
            });

            return true;
        }
    }

    private boolean hashModules() {
        return new Hasher(options.moduleFinder).run();
    }

    private boolean describe() throws IOException {
        try (JmodFile jf = new JmodFile(options.jmodFile)) {
            try (InputStream in = jf.getInputStream(Section.CLASSES, MODULE_INFO)) {
                ModuleDescriptor md = ModuleDescriptor.read(in);
                printModuleDescriptor(md);
                return true;
            } catch (IOException e) {
                throw new CommandException("err.module.descriptor.not.found");
            }
        }
    }

    static <T> String toString(Collection<T> c) {
        if (c.isEmpty()) { return ""; }
        return c.stream().map(e -> e.toString().toLowerCase(Locale.ROOT))
                  .collect(joining(" "));
    }

    private static final JavaLangModuleAccess JLMA = SharedSecrets.getJavaLangModuleAccess();

    private void printModuleDescriptor(ModuleDescriptor md)
        throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(md.toNameAndVersion());

        md.requires().stream()
            .sorted(Comparator.comparing(Requires::name))
            .forEach(r -> {
                sb.append("\n  requires ");
                if (!r.modifiers().isEmpty())
                    sb.append(toString(r.modifiers())).append(" ");
                sb.append(r.name());
            });

        md.uses().stream().sorted()
            .forEach(s -> sb.append("\n  uses ").append(s));

        md.exports().stream()
            .sorted(Comparator.comparing(Exports::source))
            .forEach(p -> sb.append("\n  exports ").append(p));

        md.opens().stream()
            .sorted(Comparator.comparing(Opens::source))
            .forEach(p -> sb.append("\n  opens ").append(p));

        Set<String> concealed = new HashSet<>(md.packages());
        md.exports().stream().map(Exports::source).forEach(concealed::remove);
        md.opens().stream().map(Opens::source).forEach(concealed::remove);
        concealed.stream().sorted()
                 .forEach(p -> sb.append("\n  contains ").append(p));

        md.provides().stream()
            .sorted(Comparator.comparing(Provides::service))
            .forEach(p -> sb.append("\n  provides ").append(p.service())
                            .append(" with ")
                            .append(toString(p.providers())));

        md.mainClass().ifPresent(v -> sb.append("\n  main-class " + v));

        md.osName().ifPresent(v -> sb.append("\n  operating-system-name " + v));

        md.osArch().ifPresent(v -> sb.append("\n  operating-system-architecture " + v));

        md.osVersion().ifPresent(v -> sb.append("\n  operating-system-version " + v));

        JLMA.hashes(md).ifPresent(
            hashes -> hashes.names().stream().sorted().forEach(
                mod -> sb.append("\n  hashes ").append(mod).append(" ")
                         .append(hashes.algorithm()).append(" ")
                         .append(hashes.hashFor(mod))));

        out.println(sb.toString());
    }

    private boolean create() throws IOException {
        JmodFileWriter jmod = new JmodFileWriter();

        // create jmod with temporary name to avoid it being examined
        // when scanning the module path
        Path target = options.jmodFile;
        Path tempTarget = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (JmodOutputStream jos = JmodOutputStream.newOutputStream(tempTarget)) {
                jmod.write(jos);
            }
            Files.move(tempTarget, target);
        } catch (Exception e) {
            if (Files.exists(tempTarget)) {
                try {
                    Files.delete(tempTarget);
                } catch (IOException ioe) {
                    e.addSuppressed(ioe);
                }
            }
            throw e;
        }
        return true;
    }

    private class JmodFileWriter {
        final List<Path> cmds = options.cmds;
        final List<Path> libs = options.libs;
        final List<Path> configs = options.configs;
        final List<Path> classpath = options.classpath;
        final List<Path> headerFiles = options.headerFiles;
        final List<Path> manPages = options.manPages;
        final List<Path> legalNotices = options.legalNotices;

        final Version moduleVersion = options.moduleVersion;
        final String mainClass = options.mainClass;
        final String osName = options.osName;
        final String osArch = options.osArch;
        final String osVersion = options.osVersion;
        final List<PathMatcher> excludes = options.excludes;
        final Hasher hasher = hasher();

        JmodFileWriter() { }

        /**
         * Writes the jmod to the given output stream.
         */
        void write(JmodOutputStream out) throws IOException {
            // module-info.class
            writeModuleInfo(out, findPackages(classpath));

            // classes
            processClasses(out, classpath);

            processSection(out, Section.CONFIG, configs);
            processSection(out, Section.HEADER_FILES, headerFiles);
            processSection(out, Section.LEGAL_NOTICES, legalNotices);
            processSection(out, Section.MAN_PAGES, manPages);
            processSection(out, Section.NATIVE_CMDS, cmds);
            processSection(out, Section.NATIVE_LIBS, libs);

        }

        /**
         * Returns a supplier of an input stream to the module-info.class
         * on the class path of directories and JAR files.
         */
        Supplier<InputStream> newModuleInfoSupplier() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (Path e: classpath) {
                if (Files.isDirectory(e)) {
                    Path mi = e.resolve(MODULE_INFO);
                    if (Files.isRegularFile(mi)) {
                        Files.copy(mi, baos);
                        break;
                    }
                } else if (Files.isRegularFile(e) && e.toString().endsWith(".jar")) {
                    try (JarFile jf = new JarFile(e.toFile())) {
                        ZipEntry entry = jf.getEntry(MODULE_INFO);
                        if (entry != null) {
                            jf.getInputStream(entry).transferTo(baos);
                            break;
                        }
                    } catch (ZipException x) {
                        // Skip. Do nothing. No packages will be added.
                    }
                }
            }
            if (baos.size() == 0) {
                return null;
            } else {
                byte[] bytes = baos.toByteArray();
                return () -> new ByteArrayInputStream(bytes);
            }
        }

        /**
         * Writes the updated module-info.class to the ZIP output stream.
         *
         * The updated module-info.class will have a Packages attribute
         * with the set of module-private/non-exported packages.
         *
         * If --module-version, --main-class, or other options were provided
         * then the corresponding class file attributes are added to the
         * module-info here.
         */
        void writeModuleInfo(JmodOutputStream out, Set<String> packages)
            throws IOException
        {
            Supplier<InputStream> miSupplier = newModuleInfoSupplier();
            if (miSupplier == null) {
                throw new IOException(MODULE_INFO + " not found");
            }

            ModuleDescriptor descriptor;
            try (InputStream in = miSupplier.get()) {
                descriptor = ModuleDescriptor.read(in);
            }

            // copy the module-info.class into the jmod with the additional
            // attributes for the version, main class and other meta data
            try (InputStream in = miSupplier.get()) {
                ModuleInfoExtender extender = ModuleInfoExtender.newExtender(in);

                // Add (or replace) the Packages attribute
                if (packages != null) {
                    extender.packages(packages);
                }

                // --main-class
                if (mainClass != null)
                    extender.mainClass(mainClass);

                // --os-name, --os-arch, --os-version
                if (osName != null || osArch != null || osVersion != null)
                    extender.targetPlatform(osName, osArch, osVersion);

                // --module-version
                if (moduleVersion != null)
                    extender.version(moduleVersion);

                if (hasher != null) {
                    ModuleHashes moduleHashes = hasher.computeHashes(descriptor.name());
                    if (moduleHashes != null) {
                        extender.hashes(moduleHashes);
                    } else {
                        warning("warn.no.module.hashes", descriptor.name());
                    }
                }

                // write the (possibly extended or modified) module-info.class
                out.writeEntry(extender.toByteArray(), Section.CLASSES, MODULE_INFO);
            }
        }

        /*
         * Hasher resolves a module graph using the --hash-modules PATTERN
         * as the roots.
         *
         * The jmod file is being created and does not exist in the
         * given modulepath.
         */
        private Hasher hasher() {
            if (options.modulesToHash == null)
                return null;

            try {
                Supplier<InputStream> miSupplier = newModuleInfoSupplier();
                if (miSupplier == null) {
                    throw new IOException(MODULE_INFO + " not found");
                }

                ModuleDescriptor descriptor;
                try (InputStream in = miSupplier.get()) {
                    descriptor = ModuleDescriptor.read(in);
                }

                URI uri = options.jmodFile.toUri();
                ModuleReference mref = new ModuleReference(descriptor, uri, new Supplier<>() {
                    @Override
                    public ModuleReader get() {
                        throw new UnsupportedOperationException();
                    }
                });

                // compose a module finder with the module path and also
                // a module finder that can find the jmod file being created
                ModuleFinder finder = ModuleFinder.compose(options.moduleFinder,
                    new ModuleFinder() {
                        @Override
                        public Optional<ModuleReference> find(String name) {
                            if (descriptor.name().equals(name))
                                return Optional.of(mref);
                            else return Optional.empty();
                        }

                        @Override
                        public Set<ModuleReference> findAll() {
                            return Collections.singleton(mref);
                        }
                    });

                return new Hasher(finder);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Returns the set of all packages on the given class path.
         */
        Set<String> findPackages(List<Path> classpath) {
            Set<String> packages = new HashSet<>();
            for (Path path : classpath) {
                if (Files.isDirectory(path)) {
                    packages.addAll(findPackages(path));
                } else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    try (JarFile jf = new JarFile(path.toString())) {
                        packages.addAll(findPackages(jf));
                    } catch (ZipException x) {
                        // Skip. Do nothing. No packages will be added.
                    } catch (IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                }
            }
            return packages;
        }

        /**
         * Returns the set of packages in the given directory tree.
         */
        Set<String> findPackages(Path dir) {
            try {
                return Files.find(dir, Integer.MAX_VALUE,
                                  ((path, attrs) -> attrs.isRegularFile()))
                        .map(dir::relativize)
                        .filter(path -> isResource(path.toString()))
                        .map(path -> toPackageName(path))
                        .filter(pkg -> pkg.length() > 0)
                        .distinct()
                        .collect(Collectors.toSet());
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        /**
         * Returns the set of packages in the given JAR file.
         */
        Set<String> findPackages(JarFile jf) {
            return jf.stream()
                     .filter(e -> !e.isDirectory() && isResource(e.getName()))
                     .map(e -> toPackageName(e))
                     .filter(pkg -> pkg.length() > 0)
                     .distinct()
                     .collect(Collectors.toSet());
        }

        /**
         * Returns true if it's a .class or a resource with an effective
         * package name.
         */
        boolean isResource(String name) {
            name = name.replace(File.separatorChar, '/');
            return name.endsWith(".class") || !ResourceHelper.isSimpleResource(name);
        }


        String toPackageName(Path path) {
            String name = path.toString();
            int index = name.lastIndexOf(File.separatorChar);
            if (index != -1)
                return name.substring(0, index).replace(File.separatorChar, '.');

            if (name.endsWith(".class") && !name.equals(MODULE_INFO)) {
                IOException e = new IOException(name  + " in the unnamed package");
                throw new UncheckedIOException(e);
            }
            return "";
        }

        String toPackageName(ZipEntry entry) {
            String name = entry.getName();
            int index = name.lastIndexOf("/");
            if (index != -1)
                return name.substring(0, index).replace('/', '.');

            if (name.endsWith(".class") && !name.equals(MODULE_INFO)) {
                IOException e = new IOException(name  + " in the unnamed package");
                throw new UncheckedIOException(e);
            }
            return "";
        }

        void processClasses(JmodOutputStream out, List<Path> classpaths)
            throws IOException
        {
            if (classpaths == null)
                return;

            for (Path p : classpaths) {
                if (Files.isDirectory(p)) {
                    processSection(out, Section.CLASSES, p);
                } else if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) {
                    try (JarFile jf = new JarFile(p.toFile())) {
                        JarEntryConsumer jec = new JarEntryConsumer(out, jf);
                        jf.stream().filter(jec).forEach(jec);
                    }
                }
            }
        }

        void processSection(JmodOutputStream out, Section section, List<Path> paths)
            throws IOException
        {
            if (paths == null)
                return;

            for (Path p : paths) {
                processSection(out, section, p);
            }
        }

        void processSection(JmodOutputStream out, Section section, Path path)
            throws IOException
        {
            Files.walkFileTree(path, Set.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException
                    {
                        Path relPath = path.relativize(file);
                        if (relPath.toString().equals(MODULE_INFO)
                                && !Section.CLASSES.equals(section))
                            warning("warn.ignore.entry", MODULE_INFO, section);

                        if (!relPath.toString().equals(MODULE_INFO)
                                && !matches(relPath, excludes)) {
                            try (InputStream in = Files.newInputStream(file)) {
                                out.writeEntry(in, section, relPath.toString());
                            } catch (IOException x) {
                                if (x.getMessage().contains("duplicate entry")) {
                                    warning("warn.ignore.duplicate.entry",
                                            relPath.toString(), section);
                                    return FileVisitResult.CONTINUE;
                                }
                                throw x;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        }

        boolean matches(Path path, List<PathMatcher> matchers) {
            if (matchers != null) {
                for (PathMatcher pm : matchers) {
                    if (pm.matches(path))
                        return true;
                }
            }
            return false;
        }

        class JarEntryConsumer implements Consumer<JarEntry>, Predicate<JarEntry> {
            final JmodOutputStream out;
            final JarFile jarfile;
            JarEntryConsumer(JmodOutputStream out, JarFile jarfile) {
                this.out = out;
                this.jarfile = jarfile;
            }
            @Override
            public void accept(JarEntry je) {
                try (InputStream in = jarfile.getInputStream(je)) {
                    out.writeEntry(in, Section.CLASSES, je.getName());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            @Override
            public boolean test(JarEntry je) {
                String name = je.getName();
                // ## no support for excludes. Is it really needed?
                return !name.endsWith(MODULE_INFO) && !je.isDirectory();
            }
        }
    }

    /**
     * Compute and record hashes
     */
    private class Hasher {
        final ModuleFinder moduleFinder;
        final Map<String, Path> moduleNameToPath;
        final Set<String> modules;
        final Configuration configuration;
        final boolean dryrun = options.dryrun;
        Hasher(ModuleFinder finder) {
            this.moduleFinder = finder;
            // Determine the modules that matches the pattern {@code modulesToHash}
            this.modules = moduleFinder.findAll().stream()
                .map(mref -> mref.descriptor().name())
                .filter(mn -> options.modulesToHash.matcher(mn).find())
                .collect(Collectors.toSet());

            // a map from a module name to Path of the packaged module
            this.moduleNameToPath = moduleFinder.findAll().stream()
                .map(mref -> mref.descriptor().name())
                .collect(Collectors.toMap(Function.identity(), mn -> moduleToPath(mn)));

            // get a resolved module graph
            Configuration config = null;
            try {
                config = Configuration.empty()
                    .resolveRequires(ModuleFinder.ofSystem(), moduleFinder, modules);
            } catch (ResolutionException e) {
                warning("warn.module.resolution.fail", e.getMessage());
            }
            this.configuration = config;
        }

        /**
         * This method is for jmod hash command.
         *
         * Identify the base modules in the module graph, i.e. no outgoing edge
         * to any of the modules to be hashed.
         *
         * For each base module M, compute the hashes of all modules that depend
         * upon M directly or indirectly.  Then update M's module-info.class
         * to record the hashes.
         */
        boolean run() {
            if (configuration == null)
                return false;

            // transposed graph containing the the packaged modules and
            // its transitive dependences matching --hash-modules
            Map<String, Set<String>> graph = new HashMap<>();
            for (String root : modules) {
                Deque<String> deque = new ArrayDeque<>();
                deque.add(root);
                Set<String> visited = new HashSet<>();
                while (!deque.isEmpty()) {
                    String mn = deque.pop();
                    if (!visited.contains(mn)) {
                        visited.add(mn);

                        if (modules.contains(mn))
                            graph.computeIfAbsent(mn, _k -> new HashSet<>());

                        ResolvedModule resolvedModule = configuration.findModule(mn).get();
                        for (ResolvedModule dm : resolvedModule.reads()) {
                            String name = dm.name();
                            if (!visited.contains(name)) {
                                deque.push(name);
                            }

                            // reverse edge
                            if (modules.contains(name) && modules.contains(mn)) {
                                graph.computeIfAbsent(name, _k -> new HashSet<>()).add(mn);
                            }
                        }
                    }
                }
            }

            if (dryrun)
                out.println("Dry run:");

            // each node in a transposed graph is a matching packaged module
            // in which the hash of the modules that depend upon it is recorded
            graph.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> {
                    String mn = e.getKey();
                    Map<String, Path> modulesForHash = e.getValue().stream()
                            .collect(Collectors.toMap(Function.identity(),
                                                      moduleNameToPath::get));
                    ModuleHashes hashes = ModuleHashes.generate(modulesForHash, "SHA-256");
                    if (dryrun) {
                        out.format("%s%n", mn);
                        hashes.names().stream()
                              .sorted()
                              .forEach(name -> out.format("  hashes %s %s %s%n",
                                  name, hashes.algorithm(), hashes.hashFor(name)));
                    } else {
                        try {
                            updateModuleInfo(mn, hashes);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
                });
            return true;
        }

        /**
         * Compute hashes of the specified module.
         *
         * It records the hashing modules that depend upon the specified
         * module directly or indirectly.
         */
        ModuleHashes computeHashes(String name) {
            if (configuration == null)
                return null;

            // the transposed graph includes all modules in the resolved graph
            Map<String, Set<String>> graph = transpose();

            // find the modules that transitively depend upon the specified name
            Deque<String> deque = new ArrayDeque<>();
            deque.add(name);
            Set<String> mods = visitNodes(graph, deque);

            // filter modules matching the pattern specified --hash-modules
            // as well as itself as the jmod file is being generated
            Map<String, Path> modulesForHash = mods.stream()
                .filter(mn -> !mn.equals(name) && modules.contains(mn))
                .collect(Collectors.toMap(Function.identity(), moduleNameToPath::get));

            if (modulesForHash.isEmpty())
                return null;

           return ModuleHashes.generate(modulesForHash, "SHA-256");
        }

        /**
         * Returns all nodes traversed from the given roots.
         */
        private Set<String> visitNodes(Map<String, Set<String>> graph,
                                       Deque<String> roots) {
            Set<String> visited = new HashSet<>();
            while (!roots.isEmpty()) {
                String mn = roots.pop();
                if (!visited.contains(mn)) {
                    visited.add(mn);
                    // the given roots may not be part of the graph
                    if (graph.containsKey(mn)) {
                        for (String dm : graph.get(mn)) {
                            if (!visited.contains(dm)) {
                                roots.push(dm);
                            }
                        }
                    }
                }
            }
            return visited;
        }

        /**
         * Returns a transposed graph from the resolved module graph.
         */
        private Map<String, Set<String>> transpose() {
            Map<String, Set<String>> transposedGraph = new HashMap<>();
            Deque<String> deque = new ArrayDeque<>(modules);

            Set<String> visited = new HashSet<>();
            while (!deque.isEmpty()) {
                String mn = deque.pop();
                if (!visited.contains(mn)) {
                    visited.add(mn);

                    transposedGraph.computeIfAbsent(mn, _k -> new HashSet<>());

                    ResolvedModule resolvedModule = configuration.findModule(mn).get();
                    for (ResolvedModule dm : resolvedModule.reads()) {
                        String name = dm.name();
                        if (!visited.contains(name)) {
                            deque.push(name);
                        }

                        // reverse edge
                        transposedGraph.computeIfAbsent(name, _k -> new HashSet<>())
                                .add(mn);
                    }
                }
            }
            return transposedGraph;
        }

        /**
         * Reads the given input stream of module-info.class and write
         * the extended module-info.class with the given ModuleHashes
         *
         * @param in       InputStream of module-info.class
         * @param out      OutputStream to write the extended module-info.class
         * @param hashes   ModuleHashes
         */
        private void recordHashes(InputStream in, OutputStream out, ModuleHashes hashes)
            throws IOException
        {
            ModuleInfoExtender extender = ModuleInfoExtender.newExtender(in);
            extender.hashes(hashes);
            extender.write(out);
        }

        private void updateModuleInfo(String name, ModuleHashes moduleHashes)
            throws IOException
        {
            Path target = moduleNameToPath.get(name);
            Path tempTarget = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                if (target.getFileName().toString().endsWith(".jmod")) {
                    updateJmodFile(target, tempTarget, moduleHashes);
                } else {
                    updateModularJar(target, tempTarget, moduleHashes);
                }
            } catch (IOException|RuntimeException e) {
                if (Files.exists(tempTarget)) {
                    try {
                        Files.delete(tempTarget);
                    } catch (IOException ioe) {
                        e.addSuppressed(ioe);
                    }
                }
                throw e;
            }

            out.println(getMessage("module.hashes.recorded", name));
            Files.move(tempTarget, target, StandardCopyOption.REPLACE_EXISTING);
        }

        private void updateModularJar(Path target, Path tempTarget,
                                      ModuleHashes moduleHashes)
            throws IOException
        {
            try (JarFile jf = new JarFile(target.toFile());
                 OutputStream out = Files.newOutputStream(tempTarget);
                 JarOutputStream jos = new JarOutputStream(out))
            {
                jf.stream().forEach(e -> {
                    try (InputStream in = jf.getInputStream(e)) {
                        if (e.getName().equals(MODULE_INFO)) {
                            // what about module-info.class in versioned entries?
                            ZipEntry ze = new ZipEntry(e.getName());
                            ze.setTime(System.currentTimeMillis());
                            jos.putNextEntry(ze);
                            recordHashes(in, jos, moduleHashes);
                            jos.closeEntry();
                        } else {
                            jos.putNextEntry(e);
                            jos.write(in.readAllBytes());
                            jos.closeEntry();
                        }
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }
                });
            }
        }

        private void updateJmodFile(Path target, Path tempTarget,
                                    ModuleHashes moduleHashes)
            throws IOException
        {

            try (JmodFile jf = new JmodFile(target);
                 JmodOutputStream jos = JmodOutputStream.newOutputStream(tempTarget))
            {
                jf.stream().forEach(e -> {
                    try (InputStream in = jf.getInputStream(e.section(), e.name())) {
                        if (e.name().equals(MODULE_INFO)) {
                            // replace module-info.class
                            ModuleInfoExtender extender =
                                ModuleInfoExtender.newExtender(in);
                            extender.hashes(moduleHashes);
                            jos.writeEntry(extender.toByteArray(), e.section(), e.name());
                        } else {
                            jos.writeEntry(in, e);
                        }
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }
                });
            }
        }

        private Path moduleToPath(String name) {
            ModuleReference mref = moduleFinder.find(name).orElseThrow(
                () -> new InternalError("Selected module " + name + " not on module path"));

            URI uri = mref.location().get();
            Path path = Paths.get(uri);
            String fn = path.getFileName().toString();
            if (!fn.endsWith(".jar") && !fn.endsWith(".jmod")) {
                throw new InternalError(path + " is not a modular JAR or jmod file");
            }
            return path;
        }
    }

    static class ClassPathConverter implements ValueConverter<Path> {
        static final ValueConverter<Path> INSTANCE = new ClassPathConverter();

        @Override
        public Path convert(String value) {
            try {
                Path path = CWD.resolve(value);
                if (Files.notExists(path))
                    throw new CommandException("err.path.not.found", path);
                if (! (Files.isDirectory(path) ||
                       (Files.isRegularFile(path) && path.toString().endsWith(".jar"))))
                    throw new CommandException("err.invalid.class.path.entry", path);
                return path;
            } catch (InvalidPathException x) {
                throw new CommandException("err.path.not.valid", value);
            }
        }

        @Override  public Class<Path> valueType() { return Path.class; }

        @Override  public String valuePattern() { return "path"; }
    }

    static class DirPathConverter implements ValueConverter<Path> {
        static final ValueConverter<Path> INSTANCE = new DirPathConverter();

        @Override
        public Path convert(String value) {
            try {
                Path path = CWD.resolve(value);
                if (Files.notExists(path))
                    throw new CommandException("err.path.not.found", path);
                if (!Files.isDirectory(path))
                    throw new CommandException("err.path.not.a.dir", path);
                return path;
            } catch (InvalidPathException x) {
                throw new CommandException("err.path.not.valid", value);
            }
        }

        @Override  public Class<Path> valueType() { return Path.class; }

        @Override  public String valuePattern() { return "path"; }
    }

    static class ExtractDirPathConverter implements ValueConverter<Path> {

        @Override
        public Path convert(String value) {
            try {
                Path path = CWD.resolve(value);
                if (Files.exists(path)) {
                    if (!Files.isDirectory(path))
                        throw new CommandException("err.cannot.create.dir", path);
                } else {
                    try {
                        Files.createDirectories(path);
                    } catch (IOException ioe) {
                        throw new CommandException("err.cannot.create.dir", path);
                    }
                }
                return path;
            } catch (InvalidPathException x) {
                throw new CommandException("err.path.not.valid", value);
            }
        }

        @Override  public Class<Path> valueType() { return Path.class; }

        @Override  public String valuePattern() { return "path"; }
    }

    static class ModuleVersionConverter implements ValueConverter<Version> {
        @Override
        public Version convert(String value) {
            try {
                return Version.parse(value);
            } catch (IllegalArgumentException x) {
                throw new CommandException("err.invalid.version", x.getMessage());
            }
        }

        @Override public Class<Version> valueType() { return Version.class; }

        @Override public String valuePattern() { return "module-version"; }
    }

    static class PatternConverter implements ValueConverter<Pattern> {
        @Override
        public Pattern convert(String value) {
            try {
                if (value.startsWith("regex:")) {
                    value = value.substring("regex:".length()).trim();
                }

                return Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                throw new CommandException("err.bad.pattern", value);
            }
        }

        @Override public Class<Pattern> valueType() { return Pattern.class; }

        @Override public String valuePattern() { return "regex-pattern"; }
    }

    static class PathMatcherConverter implements ValueConverter<PathMatcher> {
        @Override
        public PathMatcher convert(String pattern) {
            try {
                return Utils.getPathMatcher(FileSystems.getDefault(), pattern);
            } catch (PatternSyntaxException e) {
                throw new CommandException("err.bad.pattern", pattern);
            }
        }

        @Override public Class<PathMatcher> valueType() { return PathMatcher.class; }

        @Override public String valuePattern() { return "pattern-list"; }
    }

    /* Support for @<file> in jmod help */
    private static final String CMD_FILENAME = "@<filename>";

    /**
     * This formatter is adding the @filename option and does the required
     * formatting.
     */
    private static final class JmodHelpFormatter extends BuiltinHelpFormatter {

        private JmodHelpFormatter() { super(80, 2); }

        @Override
        public String format(Map<String, ? extends OptionDescriptor> options) {
            Map<String, OptionDescriptor> all = new HashMap<>();
            all.putAll(options);
            all.put(CMD_FILENAME, new OptionDescriptor() {
                @Override
                public Collection<String> options() {
                    List<String> ret = new ArrayList<>();
                    ret.add(CMD_FILENAME);
                    return ret;
                }
                @Override
                public String description() { return getMessage("main.opt.cmdfile"); }
                @Override
                public List<?> defaultValues() { return Collections.emptyList(); }
                @Override
                public boolean isRequired() { return false; }
                @Override
                public boolean acceptsArguments() { return false; }
                @Override
                public boolean requiresArgument() { return false; }
                @Override
                public String argumentDescription() { return null; }
                @Override
                public String argumentTypeIndicator() { return null; }
                @Override
                public boolean representsNonOptions() { return false; }
            });
            String content = super.format(all);
            StringBuilder builder = new StringBuilder();

            builder.append(getMessage("main.opt.mode")).append("\n  ");
            builder.append(getMessage("main.opt.mode.create")).append("\n  ");
            builder.append(getMessage("main.opt.mode.extract")).append("\n  ");
            builder.append(getMessage("main.opt.mode.list")).append("\n  ");
            builder.append(getMessage("main.opt.mode.describe")).append("\n  ");
            builder.append(getMessage("main.opt.mode.hash")).append("\n\n");

            String cmdfile = null;
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("--@")) {
                    cmdfile = line.replace("--" + CMD_FILENAME, CMD_FILENAME + "  ");
                } else if (line.startsWith("Option") || line.startsWith("------")) {
                    builder.append(" ").append(line).append("\n");
                } else if (!line.matches("Non-option arguments")){
                    builder.append("  ").append(line).append("\n");
                }
            }
            if (cmdfile != null) {
                builder.append("  ").append(cmdfile).append("\n");
            }
            return builder.toString();
        }
    }

    private final OptionParser parser = new OptionParser("hp");

    private void handleOptions(String[] args) {
        parser.formatHelpWith(new JmodHelpFormatter());

        OptionSpec<Path> classPath
                = parser.accepts("class-path", getMessage("main.opt.class-path"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(ClassPathConverter.INSTANCE);

        OptionSpec<Path> cmds
                = parser.accepts("cmds", getMessage("main.opt.cmds"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Path> config
                = parser.accepts("config", getMessage("main.opt.config"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Path> dir
                = parser.accepts("dir", getMessage("main.opt.extractDir"))
                        .withRequiredArg()
                        .withValuesConvertedBy(new ExtractDirPathConverter());

        OptionSpec<Void> dryrun
                = parser.accepts("dry-run", getMessage("main.opt.dry-run"));

        OptionSpec<PathMatcher> excludes
                = parser.accepts("exclude", getMessage("main.opt.exclude"))
                        .withRequiredArg()
                        .withValuesConvertedBy(new PathMatcherConverter());

        OptionSpec<Pattern> hashModules
                = parser.accepts("hash-modules", getMessage("main.opt.hash-modules"))
                        .withRequiredArg()
                        .withValuesConvertedBy(new PatternConverter());

        OptionSpec<Void> help
                = parser.acceptsAll(Set.of("h", "help"), getMessage("main.opt.help"))
                        .forHelp();

        OptionSpec<Path> headerFiles
                = parser.accepts("header-files", getMessage("main.opt.header-files"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Path> libs
                = parser.accepts("libs", getMessage("main.opt.libs"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Path> legalNotices
                = parser.accepts("legal-notices", getMessage("main.opt.legal-notices"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);


        OptionSpec<String> mainClass
                = parser.accepts("main-class", getMessage("main.opt.main-class"))
                        .withRequiredArg()
                        .describedAs(getMessage("main.opt.main-class.arg"));

        OptionSpec<Path> manPages
                = parser.accepts("man-pages", getMessage("main.opt.man-pages"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Path> modulePath
                = parser.acceptsAll(Set.of("p", "module-path"),
                                    getMessage("main.opt.module-path"))
                        .withRequiredArg()
                        .withValuesSeparatedBy(File.pathSeparatorChar)
                        .withValuesConvertedBy(DirPathConverter.INSTANCE);

        OptionSpec<Version> moduleVersion
                = parser.accepts("module-version", getMessage("main.opt.module-version"))
                        .withRequiredArg()
                        .withValuesConvertedBy(new ModuleVersionConverter());

        OptionSpec<String> osName
                = parser.accepts("os-name", getMessage("main.opt.os-name"))
                        .withRequiredArg()
                        .describedAs(getMessage("main.opt.os-name.arg"));

        OptionSpec<String> osArch
                = parser.accepts("os-arch", getMessage("main.opt.os-arch"))
                        .withRequiredArg()
                        .describedAs(getMessage("main.opt.os-arch.arg"));

        OptionSpec<String> osVersion
                = parser.accepts("os-version", getMessage("main.opt.os-version"))
                        .withRequiredArg()
                        .describedAs(getMessage("main.opt.os-version.arg"));

        OptionSpec<Void> version
                = parser.accepts("version", getMessage("main.opt.version"));

        NonOptionArgumentSpec<String> nonOptions
                = parser.nonOptions();

        try {
            OptionSet opts = parser.parse(args);

            if (opts.has(help) || opts.has(version)) {
                options = new Options();
                options.help = opts.has(help);
                options.version = opts.has(version);
                return;  // informational message will be shown
            }

            List<String> words = opts.valuesOf(nonOptions);
            if (words.isEmpty())
                throw new CommandException("err.missing.mode").showUsage(true);
            String verb = words.get(0);
            options = new Options();
            try {
                options.mode = Enum.valueOf(Mode.class, verb.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CommandException("err.invalid.mode", verb).showUsage(true);
            }

            if (opts.has(classPath))
                options.classpath = opts.valuesOf(classPath);
            if (opts.has(cmds))
                options.cmds = opts.valuesOf(cmds);
            if (opts.has(config))
                options.configs = opts.valuesOf(config);
            if (opts.has(dir))
                options.extractDir = opts.valueOf(dir);
            if (opts.has(dryrun))
                options.dryrun = true;
            if (opts.has(excludes))
                options.excludes = opts.valuesOf(excludes);
            if (opts.has(libs))
                options.libs = opts.valuesOf(libs);
            if (opts.has(headerFiles))
                options.headerFiles = opts.valuesOf(headerFiles);
            if (opts.has(manPages))
                options.manPages = opts.valuesOf(manPages);
            if (opts.has(legalNotices))
                options.legalNotices = opts.valuesOf(legalNotices);
            if (opts.has(modulePath)) {
                Path[] dirs = opts.valuesOf(modulePath).toArray(new Path[0]);
                options.moduleFinder = JLMA.newModulePath(Runtime.version(), true, dirs);
            }
            if (opts.has(moduleVersion))
                options.moduleVersion = opts.valueOf(moduleVersion);
            if (opts.has(mainClass))
                options.mainClass = opts.valueOf(mainClass);
            if (opts.has(osName))
                options.osName = opts.valueOf(osName);
            if (opts.has(osArch))
                options.osArch = opts.valueOf(osArch);
            if (opts.has(osVersion))
                options.osVersion = opts.valueOf(osVersion);
            if (opts.has(hashModules)) {
                options.modulesToHash = opts.valueOf(hashModules);
                // if storing hashes then the module path is required
                if (options.moduleFinder == null)
                    throw new CommandException("err.modulepath.must.be.specified")
                            .showUsage(true);
            }

            if (options.mode.equals(Mode.HASH)) {
                if (options.moduleFinder == null || options.modulesToHash == null)
                    throw new CommandException("err.modulepath.must.be.specified")
                            .showUsage(true);
            } else {
                if (words.size() <= 1)
                    throw new CommandException("err.jmod.must.be.specified").showUsage(true);
                Path path = Paths.get(words.get(1));

                if (options.mode.equals(Mode.CREATE) && Files.exists(path))
                    throw new CommandException("err.file.already.exists", path);
                else if ((options.mode.equals(Mode.LIST) ||
                            options.mode.equals(Mode.DESCRIBE) ||
                            options.mode.equals((Mode.EXTRACT)))
                         && Files.notExists(path))
                    throw new CommandException("err.jmod.not.found", path);

                if (options.dryrun) {
                    throw new CommandException("err.invalid.dryrun.option");
                }
                options.jmodFile = path;

                if (words.size() > 2)
                    throw new CommandException("err.unknown.option",
                            words.subList(2, words.size())).showUsage(true);
            }

            if (options.mode.equals(Mode.CREATE) && options.classpath == null)
                throw new CommandException("err.classpath.must.be.specified").showUsage(true);
            if (options.mainClass != null && !isValidJavaIdentifier(options.mainClass))
                throw new CommandException("err.invalid.main-class", options.mainClass);
        } catch (OptionException e) {
             throw new CommandException(e.getMessage());
        }
    }

    /**
     * Returns true if, and only if, the given main class is a legal.
     */
    static boolean isValidJavaIdentifier(String mainClass) {
        if (mainClass.length() == 0)
            return false;

        if (!Character.isJavaIdentifierStart(mainClass.charAt(0)))
            return false;

        int n = mainClass.length();
        for (int i=1; i < n; i++) {
            char c = mainClass.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && c != '.')
                return false;
        }
        if (mainClass.charAt(n-1) == '.')
            return false;

        return true;
    }

    private void reportError(String message) {
        out.println(getMessage("error.prefix") + " " + message);
    }

    private void warning(String key, Object... args) {
        out.println(getMessage("warn.prefix") + " " + getMessage(key, args));
    }

    private void showUsageSummary() {
        out.println(getMessage("main.usage.summary", PROGNAME));
    }

    private void showHelp() {
        out.println(getMessage("main.usage", PROGNAME));
        try {
            parser.printHelpOn(out);
        } catch (IOException x) {
            throw new AssertionError(x);
        }
    }

    private void showVersion() {
        out.println(version());
    }

    private String version() {
        return System.getProperty("java.version");
    }

    private static String getMessage(String key, Object... args) {
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
                bundle = ResourceBundle.getBundle("jdk.tools.jmod.resources.jmod", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jmod resource bundle for locale " + locale);
            }
        }
    }
}
