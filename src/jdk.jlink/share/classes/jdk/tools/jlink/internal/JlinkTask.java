/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import static jdk.tools.jlink.internal.TaskHelper.JLINK_BUNDLE;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.ModulePath;
import jdk.internal.module.ModuleReferenceImpl;
import jdk.internal.module.ModuleResolution;
import jdk.internal.opt.CommandLine;
import jdk.tools.jlink.internal.ImagePluginStack.ImageProvider;
import jdk.tools.jlink.internal.Jlink.JlinkConfiguration;
import jdk.tools.jlink.internal.Jlink.PluginsConfiguration;
import jdk.tools.jlink.internal.TaskHelper.BadArgs;
import jdk.tools.jlink.internal.TaskHelper.Option;
import jdk.tools.jlink.internal.TaskHelper.OptionsHelper;
import jdk.tools.jlink.plugin.PluginException;

/**
 * Implementation for the jlink tool.
 *
 * ## Should use jdk.joptsimple some day.
 */
public class JlinkTask {
    public static final boolean DEBUG = Boolean.getBoolean("jlink.debug");

    // jlink API ignores by default. Remove when signing is implemented.
    static final boolean IGNORE_SIGNING_DEFAULT = true;

    private static final TaskHelper taskHelper
            = new TaskHelper(JLINK_BUNDLE);
    private static final Option<?>[] recognizedOptions = {
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.help = true;
        }, "--help", "-h", "-?"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            // if used multiple times, the last one wins!
            // So, clear previous values, if any.
            task.options.modulePath.clear();
            String[] dirs = arg.split(File.pathSeparator);
            Arrays.stream(dirs)
                  .map(Paths::get)
                  .forEach(task.options.modulePath::add);
        }, "--module-path", "-p"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            // if used multiple times, the last one wins!
            // So, clear previous values, if any.
            task.options.limitMods.clear();
            for (String mn : arg.split(",")) {
                if (mn.isEmpty()) {
                    throw taskHelper.newBadArgs("err.mods.must.be.specified",
                            "--limit-modules");
                }
                task.options.limitMods.add(mn);
            }
        }, "--limit-modules"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            for (String mn : arg.split(",")) {
                if (mn.isEmpty()) {
                    throw taskHelper.newBadArgs("err.mods.must.be.specified",
                            "--add-modules");
                }
                task.options.addMods.add(mn);
            }
        }, "--add-modules"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            Path path = Paths.get(arg);
            task.options.output = path;
        }, "--output"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.bindServices = true;
        }, "--bind-services"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.suggestProviders = true;
        }, "--suggest-providers", "", true),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            String[] values = arg.split("=");
            // check values
            if (values.length != 2 || values[0].isEmpty() || values[1].isEmpty()) {
                throw taskHelper.newBadArgs("err.launcher.value.format", arg);
            } else {
                String commandName = values[0];
                String moduleAndMain = values[1];
                int idx = moduleAndMain.indexOf("/");
                if (idx != -1) {
                    if (moduleAndMain.substring(0, idx).isEmpty()) {
                        throw taskHelper.newBadArgs("err.launcher.module.name.empty", arg);
                    }

                    if (moduleAndMain.substring(idx + 1).isEmpty()) {
                        throw taskHelper.newBadArgs("err.launcher.main.class.empty", arg);
                    }
                }
                task.options.launchers.put(commandName, moduleAndMain);
            }
        }, "--launcher"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            if ("little".equals(arg)) {
                task.options.endian = ByteOrder.LITTLE_ENDIAN;
            } else if ("big".equals(arg)) {
                task.options.endian = ByteOrder.BIG_ENDIAN;
            } else {
                throw taskHelper.newBadArgs("err.unknown.byte.order", arg);
            }
        }, "--endian"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.verbose = true;
        }, "--verbose", "-v"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.version = true;
        }, "--version"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            Path path = Paths.get(arg);
            if (Files.exists(path)) {
                throw taskHelper.newBadArgs("err.dir.exists", path);
            }
            task.options.packagedModulesPath = path;
        }, true, "--keep-packaged-modules"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            task.options.saveoptsfile = arg;
        }, "--save-opts"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.fullVersion = true;
        }, true, "--full-version"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.ignoreSigning = true;
        }, "--ignore-signing-information"),
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.ignoreModifiedRuntime = true;
        }, true, "--ignore-modified-runtime"),
        // option for generating a runtime that can then
        // be used for linking from the run-time image.
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.generateLinkableRuntime = true;
        }, true, "--generate-linkable-runtime")
    };


    private static final String PROGNAME = "jlink";
    private final OptionsValues options = new OptionsValues();

    private static final OptionsHelper<JlinkTask> optionsHelper
            = taskHelper.newOptionsHelper(JlinkTask.class, recognizedOptions);
    private PrintWriter log;

    void setLog(PrintWriter out, PrintWriter err) {
        log = out;
        taskHelper.setLog(log);
    }

    /**
     * Result codes.
     */
    static final int
            EXIT_OK = 0, // Completed with no errors.
            EXIT_ERROR = 1, // Completed but reported errors.
            EXIT_CMDERR = 2, // Bad command-line arguments
            EXIT_SYSERR = 3, // System error or resource exhaustion.
            EXIT_ABNORMAL = 4;// terminated abnormally

    static class OptionsValues {
        boolean help;
        String  saveoptsfile;
        boolean verbose;
        boolean version;
        boolean fullVersion;
        final List<Path> modulePath = new ArrayList<>();
        final Set<String> limitMods = new HashSet<>();
        final Set<String> addMods = new HashSet<>();
        Path output;
        final Map<String, String> launchers = new HashMap<>();
        Path packagedModulesPath;
        ByteOrder endian;
        boolean ignoreSigning = false;
        boolean bindServices = false;
        boolean suggestProviders = false;
        boolean ignoreModifiedRuntime = false;
        boolean generateLinkableRuntime = false;
    }

    public static final String OPTIONS_RESOURCE = "jdk/tools/jlink/internal/options";
    // Release information stored in the java.base module
    private static final String JDK_RELEASE_RESOURCE = "jdk/internal/misc/resources/release.txt";

    /**
     * Read the release.txt from the module.
     */
    private static Optional<String> getReleaseInfo(ModuleReference mref) {
        try (var moduleReader = mref.open()) {
            Optional<InputStream> release = moduleReader.open(JDK_RELEASE_RESOURCE);

            if (release.isEmpty()) {
                return Optional.empty();
            }

            try (var r = new BufferedReader(new InputStreamReader(release.get()))) {
                return Optional.of(r.readLine());
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    int run(String[] args) {
        if (log == null) {
            setLog(new PrintWriter(System.out, true),
                   new PrintWriter(System.err, true));
        }
        Path outputPath = null;
        try {
            Module m = JlinkTask.class.getModule();
            try (InputStream savedOptions = m.getResourceAsStream(OPTIONS_RESOURCE)) {
                if (savedOptions != null) {
                    List<String> prependArgs = new ArrayList<>();
                    CommandLine.loadCmdFile(savedOptions, prependArgs);
                    if (!prependArgs.isEmpty()) {
                        prependArgs.addAll(Arrays.asList(args));
                        args = prependArgs.toArray(new String[prependArgs.size()]);
                    }
                }
            }

            List<String> remaining = optionsHelper.handleOptions(this, args);
            if (remaining.size() > 0 && !options.suggestProviders) {
                throw taskHelper.newBadArgs("err.orphan.arguments",
                                                 remaining.stream().collect(Collectors.joining(" ")))
                                .showUsage(true);
            }
            if (options.help) {
                optionsHelper.showHelp(PROGNAME, LinkableRuntimeImage.isLinkableRuntime());
                return EXIT_OK;
            }
            if (optionsHelper.shouldListPlugins()) {
                optionsHelper.listPlugins();
                return EXIT_OK;
            }
            if (options.version || options.fullVersion) {
                taskHelper.showVersion(options.fullVersion);
                return EXIT_OK;
            }

            JlinkConfiguration config = initJlinkConfig();
            outputPath = config.getOutput();
            if (options.suggestProviders) {
                suggestProviders(config, remaining);
            } else {
                createImage(config);
                if (options.saveoptsfile != null) {
                    Files.write(Paths.get(options.saveoptsfile), getSaveOpts().getBytes());
                }
            }

            return EXIT_OK;
        } catch (FindException e) {
            log.println(taskHelper.getMessage("error.prefix") + " " + e.getMessage());
            e.printStackTrace(log);
            return EXIT_ERROR;
        } catch (PluginException | UncheckedIOException | IOException e) {
            log.println(taskHelper.getMessage("error.prefix") + " " + e.getMessage());
            if (DEBUG) {
                e.printStackTrace(log);
            }
            cleanupOutput(outputPath);
            return EXIT_ERROR;
        } catch (IllegalArgumentException | ResolutionException e) {
            log.println(taskHelper.getMessage("error.prefix") + " " + e.getMessage());
            if (DEBUG) {
                e.printStackTrace(log);
            }
            return EXIT_ERROR;
        } catch (BadArgs e) {
            taskHelper.reportError(e.key, e.args);
            if (e.showUsage) {
                log.println(taskHelper.getMessage("main.usage.summary", PROGNAME));
            }
            if (DEBUG) {
                e.printStackTrace(log);
            }
            return EXIT_CMDERR;
        } catch (Throwable x) {
            log.println(taskHelper.getMessage("error.prefix") + " " + x.getMessage());
            x.printStackTrace(log);
            cleanupOutput(outputPath);
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    private void cleanupOutput(Path dir) {
        try {
            if (dir != null && Files.isDirectory(dir)) {
                deleteDirectory(dir);
            }
        } catch (IOException io) {
            log.println(taskHelper.getMessage("error.prefix") + " " + io.getMessage());
            if (DEBUG) {
                io.printStackTrace(log);
            }
        }
    }

    /*
     * Jlink API entry point.
     */
    public static void createImage(JlinkConfiguration config,
                                   PluginsConfiguration plugins)
            throws Exception {
        Objects.requireNonNull(config);
        Objects.requireNonNull(config.getOutput());
        plugins = plugins == null ? new PluginsConfiguration() : plugins;

        // First create the image provider
        ImageProvider imageProvider =
                createImageProvider(config,
                                    null,
                                    IGNORE_SIGNING_DEFAULT,
                                    false,
                                    null,
                                    false,
                                    new OptionsValues(),
                                    null);

        // Then create the Plugin Stack
        ImagePluginStack stack = ImagePluginConfiguration.parseConfiguration(plugins);

        //Ask the stack to proceed;
        stack.operate(imageProvider);
    }

    // the token for "all modules on the module path"
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";
    private JlinkConfiguration initJlinkConfig() throws BadArgs {
        // Empty module path not allowed with ALL-MODULE-PATH in --add-modules
        if (options.addMods.contains(ALL_MODULE_PATH) && options.modulePath.isEmpty()) {
            throw taskHelper.newBadArgs("err.no.module.path");
        }
        ModuleFinder appModuleFinder = newModuleFinder(options.modulePath);
        ModuleFinder finder = appModuleFinder;

        boolean isLinkFromRuntime = false;
        if (!appModuleFinder.find("java.base").isPresent()) {
            // If the application module finder doesn't contain the
            // java.base module we have one of two cases:
            // 1. A custom module is being linked into a runtime, but the JDK
            //    modules have not been provided on the module path.
            // 2. We have a run-time image based link.
            //
            // Distinguish case 2 by adding the default 'jmods' folder and try
            // the look-up again. For case 1 this will now find java.base, but
            // not for case 2, since the jmods folder is not there or doesn't
            // include the java.base module.
            Path defModPath = getDefaultModulePath();
            if (defModPath != null) {
                List<Path> combinedPaths = new ArrayList<>(options.modulePath);
                combinedPaths.add(defModPath);
                finder = newModuleFinder(combinedPaths);
            }
            // We've just added the default module path ('jmods'). If we still
            // don't find java.base, we must resolve JDK modules from the
            // current run-time image.
            if (!finder.find("java.base").isPresent()) {
                // If we don't have a linkable run-time image this is an error
                if (!LinkableRuntimeImage.isLinkableRuntime()) {
                    throw taskHelper.newBadArgs("err.runtime.link.not.linkable.runtime");
                }
                isLinkFromRuntime = true;
                // JDK modules come from the system module path
                finder = ModuleFinder.compose(ModuleFinder.ofSystem(), appModuleFinder);
            }
        }

        // Sanity check version if we use JMODs
        if (!isLinkFromRuntime) {
            assert(finder.find("java.base").isPresent());
            checkJavaBaseVersion(finder.find("java.base").get());
        }

        // Determine the roots set
        Set<String> roots = new HashSet<>();
        for (String mod : options.addMods) {
            if (mod.equals(ALL_MODULE_PATH)) {
                // Using --limit-modules with ALL-MODULE-PATH is an error
                if (!options.limitMods.isEmpty()) {
                    throw taskHelper.newBadArgs("err.limit.modules");
                }
                // all observable modules in the app module path are roots
                Set<String> initialRoots = appModuleFinder.findAll()
                        .stream()
                        .map(ModuleReference::descriptor)
                        .map(ModuleDescriptor::name)
                        .collect(Collectors.toSet());

                // Error if no module is found on the app module path
                if (initialRoots.isEmpty()) {
                    String modPath = options.modulePath.stream()
                            .map(a -> a.toString())
                            .collect(Collectors.joining(", "));
                    throw taskHelper.newBadArgs("err.empty.module.path", modPath);
                }

                // Use a module finder with limited observability, as determined
                // by initialRoots, to find the observable modules from the
                // application module path (--module-path option) only. We must
                // not include JDK modules from the default module path or the
                // run-time image.
                ModuleFinder mf = limitFinder(finder, initialRoots, Set.of());
                mf.findAll()
                  .stream()
                  .map(ModuleReference::descriptor)
                  .map(ModuleDescriptor::name)
                  .forEach(mn -> roots.add(mn));
            } else {
                roots.add(mod);
            }
        }
        finder = limitFinder(finder, options.limitMods, roots);

        // --keep-packaged-modules doesn't make sense as we are not linking
        // from packaged modules to begin with.
        if (isLinkFromRuntime && options.packagedModulesPath != null) {
            throw taskHelper.newBadArgs("err.runtime.link.packaged.mods");
        }

        return new JlinkConfiguration(options.output,
                                      roots,
                                      finder,
                                      isLinkFromRuntime,
                                      options.ignoreModifiedRuntime,
                                      options.generateLinkableRuntime);
    }

    /*
     * Creates a ModuleFinder for the given module paths.
     */
    public static ModuleFinder newModuleFinder(List<Path> paths) {
        Runtime.Version version = Runtime.version();
        Path[] entries = paths.toArray(new Path[0]);
        return ModulePath.of(version, true, entries);
    }

    private void createImage(JlinkConfiguration config) throws Exception {
        if (options.output == null) {
            throw taskHelper.newBadArgs("err.output.must.be.specified").showUsage(true);
        }
        if (options.addMods.isEmpty()) {
            throw taskHelper.newBadArgs("err.mods.must.be.specified", "--add-modules")
                            .showUsage(true);
        }

        // First create the image provider
        ImageHelper imageProvider = createImageProvider(config,
                                                        options.packagedModulesPath,
                                                        options.ignoreSigning,
                                                        options.bindServices,
                                                        options.endian,
                                                        options.verbose,
                                                        options,
                                                        log);

        // Then create the Plugin Stack
        ImagePluginStack stack = ImagePluginConfiguration.parseConfiguration(
            taskHelper.getPluginsConfig(options.output, options.launchers,
                    imageProvider.targetPlatform));

        //Ask the stack to proceed
        stack.operate(imageProvider);
    }

    /**
     * @return the system module path or null
     */
    public static Path getDefaultModulePath() {
        Path jmods = Paths.get(System.getProperty("java.home"), "jmods");
        return Files.isDirectory(jmods)? jmods : null;
    }

    /*
     * Returns a module finder of the given module finder that limits the
     * observable modules to those in the transitive closure of the modules
     * specified in {@code limitMods} plus other modules specified in the
     * {@code roots} set.
     */
    public static ModuleFinder limitFinder(ModuleFinder finder,
                                           Set<String> limitMods,
                                           Set<String> roots) {
        // if limitMods is specified then limit the universe
        if (limitMods != null && !limitMods.isEmpty()) {
            Objects.requireNonNull(roots);
            // resolve all root modules
            Configuration cf = Configuration.empty()
                    .resolve(finder,
                             ModuleFinder.of(),
                             limitMods);

            // module name -> reference
            Map<String, ModuleReference> map = new HashMap<>();
            cf.modules().forEach(m -> {
                ModuleReference mref = m.reference();
                map.put(mref.descriptor().name(), mref);
            });

            // add the other modules
            roots.stream()
                .map(finder::find)
                .flatMap(Optional::stream)
                .forEach(mref -> map.putIfAbsent(mref.descriptor().name(), mref));

            // set of modules that are observable
            Set<ModuleReference> mrefs = new HashSet<>(map.values());

            return new ModuleFinder() {
                @Override
                public Optional<ModuleReference> find(String name) {
                    return Optional.ofNullable(map.get(name));
                }

                @Override
                public Set<ModuleReference> findAll() {
                    return mrefs;
                }
            };
        }
        return finder;
    }

    private static String getCurrentRuntimeVersion() {
        ModuleReference current = ModuleLayer.boot()
                .configuration()
                .findModule("java.base")
                .get()
                .reference();
        // This jlink runtime should always have the release.txt
        return getReleaseInfo(current).get();
    }

    /*
     * Checks the release information of the java.base used for target image
     * for compatibility with the java.base used by jlink.
     *
     * @throws IllegalArgumentException  If  the `java.base` module reference `target`
     * is not compatible with this jlink.
     */
    private static void checkJavaBaseVersion(ModuleReference target) {
        String currentRelease = getCurrentRuntimeVersion();

        String targetRelease = getReleaseInfo(target).orElseThrow(() -> new IllegalArgumentException(
                taskHelper.getMessage("err.jlink.version.missing", currentRelease)));

        if (!currentRelease.equals(targetRelease)) {
            // Current runtime image and the target runtime image are not compatible build
            throw new IllegalArgumentException(taskHelper.getMessage("err.jlink.version.mismatch",
                    currentRelease,
                    targetRelease));
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (e == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed.
                    throw e;
                }
            }
        });
    }

    private static Path toPathLocation(ResolvedModule m) {
        Optional<URI> ouri = m.reference().location();
        if (ouri.isEmpty()) {
            throw new InternalError(m + " does not have a location");
        }
        URI uri = ouri.get();
        return Paths.get(uri);
    }


    private static ImageHelper createImageProvider(JlinkConfiguration config,
                                                   Path retainModulesPath,
                                                   boolean ignoreSigning,
                                                   boolean bindService,
                                                   ByteOrder endian,
                                                   boolean verbose,
                                                   OptionsValues opts,
                                                   PrintWriter log)
            throws IOException
    {
        Configuration cf = bindService ? config.resolveAndBind()
                                       : config.resolve();

        cf.modules().stream()
            .map(ResolvedModule::reference)
            .filter(mref -> mref.descriptor().isAutomatic())
            .findAny()
            .ifPresent(mref -> {
                String loc = mref.location().map(URI::toString).orElse("<unknown>");
                throw new IllegalArgumentException(
                    taskHelper.getMessage("err.automatic.module", mref.descriptor().name(), loc));
            });

        // Perform some sanity checks for linking from the run-time image
        if (config.linkFromRuntimeImage()) {
            // Do not permit linking from run-time image and also including jdk.jlink module
            if (cf.findModule(JlinkTask.class.getModule().getName()).isPresent()) {
                String msg = taskHelper.getMessage("err.runtime.link.jdk.jlink.prohibited");
                throw new IllegalArgumentException(msg);
            }
            // Do not permit linking from run-time image when the current image
            // is being patched.
            if (ModuleBootstrap.patcher().hasPatches()) {
                String msg = taskHelper.getMessage("err.runtime.link.patched.module");
                throw new IllegalArgumentException(msg);
            }

            // Print info message indicating linking from the run-time image
            if (verbose && log != null) {
                log.println(taskHelper.getMessage("runtime.link.info"));
            }
        }

        if (verbose && log != null) {
            // print modules to be linked in
            cf.modules().stream()
              .sorted(Comparator.comparing(ResolvedModule::name))
              .forEach(rm -> log.format("%s %s%s%n",
                                        rm.name(),
                                        rm.reference().location().get(),
                                        // We have a link from run-time image when scheme is 'jrt'
                                        "jrt".equals(rm.reference().location().get().getScheme())
                                                ? " " + taskHelper.getMessage("runtime.link.jprt.path.extra")
                                                : ""));

            // print provider info
            Set<ModuleReference> references = cf.modules().stream()
                .map(ResolvedModule::reference).collect(Collectors.toSet());

            String msg = String.format("%n%s:", taskHelper.getMessage("providers.header"));
            printProviders(log, msg, references);
        }

        // emit a warning for any incubating modules in the configuration
        if (log != null) {
            String im = cf.modules()
                          .stream()
                          .map(ResolvedModule::reference)
                          .filter(ModuleResolution::hasIncubatingWarning)
                          .map(ModuleReference::descriptor)
                          .map(ModuleDescriptor::name)
                          .collect(Collectors.joining(", "));

            if (!"".equals(im)) {
                log.println("WARNING: Using incubator modules: " + im);
            }
        }

        Map<String, Path> mods = cf.modules().stream()
            .collect(Collectors.toMap(ResolvedModule::name, JlinkTask::toPathLocation));
        // determine the target platform of the image being created
        Platform targetPlatform = targetPlatform(cf, mods, config.linkFromRuntimeImage());
        // if the user specified any --endian, then it must match the target platform's native
        // endianness
        if (endian != null && endian != targetPlatform.arch().byteOrder()) {
            throw new IOException(
                    taskHelper.getMessage("err.target.endianness.mismatch", endian, targetPlatform));
        }
        if (verbose && log != null) {
            Platform runtime = Platform.runtime();
            if (runtime.os() != targetPlatform.os() || runtime.arch() != targetPlatform.arch()) {
                log.format("Cross-platform image generation, using %s for target platform %s%n",
                        targetPlatform.arch().byteOrder(), targetPlatform);
            }
        }

        // use the version of java.base module, if present, as
        // the release version for multi-release JAR files
        var version = cf.findModule("java.base")
                        .map(ResolvedModule::reference)
                        .map(ModuleReference::descriptor)
                        .flatMap(ModuleDescriptor::version)
                        .map(ModuleDescriptor.Version::toString)
                        .map(Runtime.Version::parse)
                        .orElse(Runtime.version());

        Set<Archive> archives = mods.entrySet().stream()
                .map(e -> newArchive(e.getKey(),
                                     e.getValue(),
                                     version,
                                     ignoreSigning,
                                     config,
                                     log))
                .collect(Collectors.toSet());

        return new ImageHelper(archives,
                               targetPlatform,
                               retainModulesPath,
                               config.isGenerateRuntimeImage());
    }

    private static Archive newArchive(String module,
                                      Path path,
                                      Runtime.Version version,
                                      boolean ignoreSigning,
                                      JlinkConfiguration config,
                                      PrintWriter log) {
        if (path.toString().endsWith(".jmod")) {
            return new JmodArchive(module, path);
        } else if (path.toString().endsWith(".jar")) {
            ModularJarArchive modularJarArchive = new ModularJarArchive(module, path, version);
            try (Stream<Archive.Entry> entries = modularJarArchive.entries()) {
                boolean hasSignatures = entries.anyMatch((entry) -> {
                    String name = entry.name().toUpperCase(Locale.ROOT);

                    return name.startsWith("META-INF/") && name.indexOf('/', 9) == -1 && (
                            name.endsWith(".SF") ||
                                    name.endsWith(".DSA") ||
                                    name.endsWith(".RSA") ||
                                    name.endsWith(".EC") ||
                                    name.startsWith("META-INF/SIG-")
                    );
                });

                if (hasSignatures) {
                    if (ignoreSigning) {
                        System.err.println(taskHelper.getMessage("warn.signing", path));
                    } else {
                        throw new IllegalArgumentException(taskHelper.getMessage("err.signing", path));
                    }
                }
            }
            return modularJarArchive;
        } else if (Files.isDirectory(path) && !"jrt".equals(path.toUri().getScheme())) {
            // The jrt URI path scheme conditional is there since we'd otherwise
            // enter this branch for linking from the run-time image where the
            // path is a jrt path. Note that the specific module would be a
            // directory. I.e. Files.isDirectory() would be true.
            Path modInfoPath = path.resolve("module-info.class");
            if (Files.isRegularFile(modInfoPath)) {
                return new DirArchive(path, findModuleName(modInfoPath));
            } else {
                throw new IllegalArgumentException(
                        taskHelper.getMessage("err.not.a.module.directory", path));
            }
        } else if (config.linkFromRuntimeImage()) {
            return LinkableRuntimeImage.newArchive(module, path, config.ignoreModifiedRuntime(), taskHelper);
        } else {
            throw new IllegalArgumentException(
                    taskHelper.getMessage("err.not.modular.format", module, path));
        }
    }

    private static String findModuleName(Path modInfoPath) {
        try (BufferedInputStream bis = new BufferedInputStream(
                Files.newInputStream(modInfoPath))) {
            return ModuleDescriptor.read(bis).name();
        } catch (IOException exp) {
            throw new IllegalArgumentException(taskHelper.getMessage(
                    "err.cannot.read.module.info", modInfoPath), exp);
        }
    }

    private static Platform targetPlatform(Configuration cf,
                                           Map<String, Path> modsPaths,
                                           boolean runtimeImageLink) throws IOException {
        Path javaBasePath = modsPaths.get("java.base");
        assert javaBasePath != null : "java.base module path is missing";
        if (runtimeImageLink || isJavaBaseFromDefaultModulePath(javaBasePath)) {
            // this implies that the java.base module used for the target image
            // will correspond to the current platform. So this isn't an attempt to
            // build a cross-platform image. We use the current platform's endianness
            // in this case
            return Platform.runtime();
        } else {
            // this is an attempt to build a cross-platform image. We now attempt to
            // find the target platform's arch and thus its endianness from the java.base
            // module's ModuleTarget attribute
            String targetPlatformVal = readJavaBaseTargetPlatform(cf);
            try {
                return Platform.parsePlatform(targetPlatformVal);
            } catch (IllegalArgumentException iae) {
                throw new IOException(
                        taskHelper.getMessage("err.unknown.target.platform", targetPlatformVal));
            }
        }
    }

    // returns true if the default module-path is the parent of the passed javaBasePath
    private static boolean isJavaBaseFromDefaultModulePath(Path javaBasePath) throws IOException {
        Path defaultModulePath = getDefaultModulePath();
        if (defaultModulePath == null) {
            return false;
        }
        // resolve, against the default module-path dir, the java.base module file used
        // for image creation
        Path javaBaseInDefaultPath = defaultModulePath.resolve(javaBasePath.getFileName());
        if (Files.notExists(javaBaseInDefaultPath)) {
            // the java.base module used for image creation doesn't exist in the default
            // module path
            return false;
        }
        return Files.isSameFile(javaBasePath, javaBaseInDefaultPath);
    }

    // returns the targetPlatform value from the ModuleTarget attribute of the java.base module.
    // throws IOException if the targetPlatform cannot be determined.
    private static String readJavaBaseTargetPlatform(Configuration cf) throws IOException {
        Optional<ResolvedModule> javaBase = cf.findModule("java.base");
        assert javaBase.isPresent() : "java.base module is missing";
        ModuleReference ref = javaBase.get().reference();
        if (ref instanceof ModuleReferenceImpl modRefImpl
                && modRefImpl.moduleTarget() != null) {
            return modRefImpl.moduleTarget().targetPlatform();
        }
        // could not determine target platform
        throw new IOException(
                taskHelper.getMessage("err.cannot.determine.target.platform",
                        ref.location().map(URI::toString)
                                .orElse("java.base module")));
    }

    /*
     * Returns a map of each service type to the modules that use it
     * It will include services that are provided by a module but may not used
     * by any of the observable modules.
     */
    private static Map<String, Set<String>> uses(Set<ModuleReference> modules) {
        // collects the services used by the modules and print uses
        Map<String, Set<String>> services = new HashMap<>();
        modules.stream()
               .map(ModuleReference::descriptor)
               .forEach(md -> {
                   // include services that may not be used by any observable modules
                   md.provides().forEach(p ->
                       services.computeIfAbsent(p.service(), _k -> new HashSet<>()));
                   md.uses().forEach(s -> services.computeIfAbsent(s, _k -> new HashSet<>())
                                                  .add(md.name()));
               });
        return services;
    }

    private static void printProviders(PrintWriter log,
                                       String header,
                                       Set<ModuleReference> modules) {
        printProviders(log, header, modules, uses(modules));
    }

    /*
     * Prints the providers that are used by the specified services.
     *
     * The specified services maps a service type name to the modules
     * using the service type which may be empty if no observable module uses
     * that service.
     */
    private static void printProviders(PrintWriter log,
                                       String header,
                                       Set<ModuleReference> modules,
                                       Map<String, Set<String>> serviceToUses) {
        if (modules.isEmpty()) {
            return;
        }

        // Build a map of a service type to the provider modules
        Map<String, Set<ModuleDescriptor>> providers = new HashMap<>();
        modules.stream()
            .map(ModuleReference::descriptor)
            .forEach(md -> {
                md.provides().stream()
                  .filter(p -> serviceToUses.containsKey(p.service()))
                  .forEach(p -> providers.computeIfAbsent(p.service(), _k -> new HashSet<>())
                                         .add(md));
            });

        if (!providers.isEmpty()) {
            log.println(header);
        }

        // print the providers of the service types used by the specified modules
        // sorted by the service type name and then provider's module name
        providers.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                String service = e.getKey();
                e.getValue().stream()
                 .sorted(Comparator.comparing(ModuleDescriptor::name))
                 .forEach(md ->
                     md.provides().stream()
                       .filter(p -> p.service().equals(service))
                       .forEach(p -> {
                           String usedBy;
                           if (serviceToUses.get(p.service()).isEmpty()) {
                               usedBy = "not used by any observable module";
                           } else {
                               usedBy = serviceToUses.get(p.service()).stream()
                                            .sorted()
                                            .collect(Collectors.joining(",", "used by ", ""));
                           }
                           log.format("  %s provides %s %s%n",
                                      md.name(), p.service(), usedBy);
                       })
                 );
            });
    }

    private void suggestProviders(JlinkConfiguration config, List<String> args)
        throws BadArgs
    {
        if (args.size() > 1) {
            List<String> arguments = args.get(0).startsWith("-")
                                        ? args
                                        : args.subList(1, args.size());
            throw taskHelper.newBadArgs("err.invalid.arg.for.option",
                                        "--suggest-providers",
                                        arguments.stream().collect(Collectors.joining(" ")));
        }

        if (options.bindServices) {
            log.println(taskHelper.getMessage("no.suggested.providers"));
            return;
        }

        ModuleFinder finder = config.finder();
        if (args.isEmpty()) {
            // print providers used by the observable modules without service binding
            Set<ModuleReference> mrefs = finder.findAll();
            // print uses of the modules that would be linked into the image
            mrefs.stream()
                 .sorted(Comparator.comparing(mref -> mref.descriptor().name()))
                 .forEach(mref -> {
                     ModuleDescriptor md = mref.descriptor();
                     log.format("%s %s%n", md.name(),
                                mref.location().get());
                     md.uses().stream().sorted()
                       .forEach(s -> log.format("    uses %s%n", s));
                 });

            String msg = String.format("%n%s:", taskHelper.getMessage("suggested.providers.header"));
            printProviders(log, msg, mrefs, uses(mrefs));

        } else {
            // comma-separated service types, if specified
            Set<String> names = Stream.of(args.get(0).split(","))
                .collect(Collectors.toSet());
            // find the modules that provide the specified service
            Set<ModuleReference> mrefs = finder.findAll().stream()
                .filter(mref -> mref.descriptor().provides().stream()
                                    .map(ModuleDescriptor.Provides::service)
                                    .anyMatch(names::contains))
                .collect(Collectors.toSet());

            // find the modules that uses the specified services
            Map<String, Set<String>> uses = new HashMap<>();
            names.forEach(s -> uses.computeIfAbsent(s, _k -> new HashSet<>()));
            finder.findAll().stream()
                  .map(ModuleReference::descriptor)
                  .forEach(md -> md.uses().stream()
                                   .filter(names::contains)
                                   .forEach(s -> uses.get(s).add(md.name())));

            // check if any name given on the command line are not provided by any module
            mrefs.stream()
                 .flatMap(mref -> mref.descriptor().provides().stream()
                                      .map(ModuleDescriptor.Provides::service))
                 .forEach(names::remove);
            if (!names.isEmpty()) {
                log.println(taskHelper.getMessage("warn.provider.notfound",
                    names.stream().sorted().collect(Collectors.joining(","))));
            }

            String msg = String.format("%n%s:", taskHelper.getMessage("suggested.providers.header"));
            printProviders(log, msg, mrefs, uses);
        }
    }

    private String getSaveOpts() {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(new Date()).append("\n");
        for (String c : optionsHelper.getInputCommand()) {
            sb.append(c).append(" ");
        }

        return sb.toString();
    }

    private static record ImageHelper(Set<Archive> archives,
                                      Platform targetPlatform,
                                      Path packagedModulesPath,
                                      boolean generateRuntimeImage) implements ImageProvider {
        @Override
        public ExecutableImage retrieve(ImagePluginStack stack) throws IOException {
            ExecutableImage image = ImageFileCreator.create(archives,
                    targetPlatform.arch().byteOrder(), stack, generateRuntimeImage);
            if (packagedModulesPath != null) {
                // copy the packaged modules to the given path
                Files.createDirectories(packagedModulesPath);
                for (Archive a : archives) {
                    Path file = a.getPath();
                    Path dest = packagedModulesPath.resolve(file.getFileName());
                    Files.copy(file, dest);
                }
            }
            return image;
        }
    }
}
