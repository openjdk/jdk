/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.TaskHelper.BadArgs;
import static jdk.tools.jlink.internal.TaskHelper.JLINK_BUNDLE;
import jdk.tools.jlink.internal.Jlink.JlinkConfiguration;
import jdk.tools.jlink.internal.Jlink.PluginsConfiguration;
import jdk.tools.jlink.internal.TaskHelper.Option;
import jdk.tools.jlink.internal.TaskHelper.OptionsHelper;
import jdk.tools.jlink.internal.ImagePluginStack.ImageProvider;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.builder.DefaultImageBuilder;
import jdk.tools.jlink.plugin.Plugin;
import jdk.internal.module.ModulePath;
import jdk.internal.module.ModuleResolution;

/**
 * Implementation for the jlink tool.
 *
 * ## Should use jdk.joptsimple some day.
 */
public class JlinkTask {
    static final boolean DEBUG = Boolean.getBoolean("jlink.debug");

    // jlink API ignores by default. Remove when signing is implemented.
    static final boolean IGNORE_SIGNING_DEFAULT = true;

    private static final TaskHelper taskHelper
            = new TaskHelper(JLINK_BUNDLE);

    private static final Option<?>[] recognizedOptions = {
        new Option<JlinkTask>(false, (task, opt, arg) -> {
            task.options.help = true;
        }, "--help", "-h"),
        new Option<JlinkTask>(true, (task, opt, arg) -> {
            // if used multiple times, the last one wins!
            // So, clear previous values, if any.
            task.options.modulePath.clear();
            String[] dirs = arg.split(File.pathSeparator);
            int i = 0;
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
        }, "--ignore-signing-information"),};

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
        ByteOrder endian = ByteOrder.nativeOrder();
        boolean ignoreSigning = false;
        boolean bindServices = false;
        boolean suggestProviders = false;
    }

    int run(String[] args) {
        if (log == null) {
            setLog(new PrintWriter(System.out, true),
                   new PrintWriter(System.err, true));
        }
        try {
            List<String> remaining = optionsHelper.handleOptions(this, args);
            if (remaining.size() > 0 && !options.suggestProviders) {
                throw taskHelper.newBadArgs("err.orphan.arguments", toString(remaining))
                                .showUsage(true);
            }
            if (options.help) {
                optionsHelper.showHelp(PROGNAME);
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

            if (taskHelper.getExistingImage() != null) {
                postProcessOnly(taskHelper.getExistingImage());
                return EXIT_OK;
            }

            if (options.modulePath.isEmpty()) {
                throw taskHelper.newBadArgs("err.modulepath.must.be.specified")
                                .showUsage(true);
            }

            JlinkConfiguration config = initJlinkConfig();
            if (options.suggestProviders) {
                suggestProviders(config, remaining);
            } else {
                createImage(config);
                if (options.saveoptsfile != null) {
                    Files.write(Paths.get(options.saveoptsfile), getSaveOpts().getBytes());
                }
            }

            return EXIT_OK;
        } catch (PluginException | IllegalArgumentException |
                 UncheckedIOException |IOException | FindException | ResolutionException e) {
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
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
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
                                    false,
                                    null);

        // Then create the Plugin Stack
        ImagePluginStack stack = ImagePluginConfiguration.parseConfiguration(plugins);

        //Ask the stack to proceed;
        stack.operate(imageProvider);
    }

    /*
     * Jlink API entry point.
     */
    public static void postProcessImage(ExecutableImage image, List<Plugin> postProcessorPlugins)
            throws Exception {
        Objects.requireNonNull(image);
        Objects.requireNonNull(postProcessorPlugins);
        PluginsConfiguration config = new PluginsConfiguration(postProcessorPlugins);
        ImagePluginStack stack = ImagePluginConfiguration.
                parseConfiguration(config);

        stack.operate((ImagePluginStack stack1) -> image);
    }

    private void postProcessOnly(Path existingImage) throws Exception {
        PluginsConfiguration config = taskHelper.getPluginsConfig(null, null);
        ExecutableImage img = DefaultImageBuilder.getExecutableImage(existingImage);
        if (img == null) {
            throw taskHelper.newBadArgs("err.existing.image.invalid");
        }
        postProcessImage(img, config.getPlugins());
    }

    // the token for "all modules on the module path"
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";
    private JlinkConfiguration initJlinkConfig() throws BadArgs {
        if (options.addMods.isEmpty()) {
            throw taskHelper.newBadArgs("err.mods.must.be.specified", "--add-modules")
                .showUsage(true);
        }

        Set<String> roots = new HashSet<>();
        for (String mod : options.addMods) {
            if (mod.equals(ALL_MODULE_PATH)) {
                Path[] entries = options.modulePath.toArray(new Path[0]);
                ModuleFinder finder = ModulePath.of(Runtime.version(), true, entries);
                if (!options.limitMods.isEmpty()) {
                    // finder for the observable modules specified in
                    // the --module-path and --limit-modules options
                    finder = limitFinder(finder, options.limitMods, Collections.emptySet());
                }

                // all observable modules are roots
                finder.findAll()
                      .stream()
                      .map(ModuleReference::descriptor)
                      .map(ModuleDescriptor::name)
                      .forEach(mn -> roots.add(mn));
            } else {
                roots.add(mod);
            }
        }

        return new JlinkConfiguration(options.output,
                                      options.modulePath,
                                      roots,
                                      options.limitMods,
                                      options.endian);
    }

    private void createImage(JlinkConfiguration config) throws Exception {
        if (options.output == null) {
            throw taskHelper.newBadArgs("err.output.must.be.specified").showUsage(true);
        }

        // First create the image provider
        ImageProvider imageProvider = createImageProvider(config,
                                                          options.packagedModulesPath,
                                                          options.ignoreSigning,
                                                          options.bindServices,
                                                          options.verbose,
                                                          log);

        // Then create the Plugin Stack
        ImagePluginStack stack = ImagePluginConfiguration.parseConfiguration(
            taskHelper.getPluginsConfig(options.output, options.launchers));

        //Ask the stack to proceed
        stack.operate(imageProvider);
    }

    /*
     * Returns a module finder of the given module path that limits
     * the observable modules to those in the transitive closure of
     * the modules specified in {@code limitMods} plus other modules
     * specified in the {@code roots} set.
     */
    public static ModuleFinder newModuleFinder(List<Path> paths,
                                               Set<String> limitMods,
                                               Set<String> roots)
    {
        Path[] entries = paths.toArray(new Path[0]);
        ModuleFinder finder = ModulePath.of(Runtime.version(), true, entries);

        // if limitmods is specified then limit the universe
        if (!limitMods.isEmpty()) {
            finder = limitFinder(finder, limitMods, roots);
        }
        return finder;
    }

    private static Path toPathLocation(ResolvedModule m) {
        Optional<URI> ouri = m.reference().location();
        if (!ouri.isPresent())
            throw new InternalError(m + " does not have a location");
        URI uri = ouri.get();
        return Paths.get(uri);
    }


    private static ImageProvider createImageProvider(JlinkConfiguration config,
                                                     Path retainModulesPath,
                                                     boolean ignoreSigning,
                                                     boolean bindService,
                                                     boolean verbose,
                                                     PrintWriter log)
            throws IOException
    {
        Configuration cf = bindService ? config.resolveAndBind()
                                       : config.resolve();

        if (verbose && log != null) {
            // print modules to be linked in
            cf.modules().stream()
              .sorted(Comparator.comparing(ResolvedModule::name))
              .forEach(rm -> log.format("module %s (%s)%n",
                                        rm.name(), rm.reference().location().get()));

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

            if (!"".equals(im))
                log.println("WARNING: Using incubator modules: " + im);
        }

        Map<String, Path> mods = cf.modules().stream()
            .collect(Collectors.toMap(ResolvedModule::name, JlinkTask::toPathLocation));
        return new ImageHelper(cf, mods, config.getByteOrder(), retainModulesPath, ignoreSigning);
    }

    /*
     * Returns a ModuleFinder that limits observability to the given root
     * modules, their transitive dependences, plus a set of other modules.
     */
    public static ModuleFinder limitFinder(ModuleFinder finder,
                                           Set<String> roots,
                                           Set<String> otherMods) {

        // resolve all root modules
        Configuration cf = Configuration.empty()
                .resolve(finder,
                         ModuleFinder.of(),
                         roots);

        // module name -> reference
        Map<String, ModuleReference> map = new HashMap<>();
        cf.modules().forEach(m -> {
            ModuleReference mref = m.reference();
            map.put(mref.descriptor().name(), mref);
        });

        // add the other modules
        otherMods.stream()
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

    /*
     * Returns a map of each service type to the modules that use it
     */
    private static Map<String, Set<String>> uses(Set<ModuleReference> modules) {
        // collects the services used by the modules and print uses
        Map<String, Set<String>> uses = new HashMap<>();
        modules.stream()
               .map(ModuleReference::descriptor)
               .forEach(md -> md.uses().forEach(s ->
                   uses.computeIfAbsent(s, _k -> new HashSet<>()).add(md.name()))
               );
        return uses;
    }

    private static void printProviders(PrintWriter log,
                                       String header,
                                       Set<ModuleReference> modules) {
        printProviders(log, header, modules, uses(modules));
    }

    /*
     * Prints the providers that are used by the services specified in
     * the given modules.
     *
     * The specified uses maps a service type name to the modules
     * using the service type and that may or may not be present
     * the given modules.
     */
    private static void printProviders(PrintWriter log,
                                       String header,
                                       Set<ModuleReference> modules,
                                       Map<String, Set<String>> uses) {
        if (modules.isEmpty())
            return;

        // Build a map of a service type to the provider modules
        Map<String, Set<ModuleDescriptor>> providers = new HashMap<>();
        modules.stream()
            .map(ModuleReference::descriptor)
            .forEach(md -> {
                md.provides().stream()
                  .filter(p -> uses.containsKey(p.service()))
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
                       .forEach(p -> log.format("  module %s provides %s, used by %s%n",
                                                md.name(), p.service(),
                                                uses.get(p.service()).stream()
                                                    .sorted()
                                                    .collect(Collectors.joining(","))))
                 );
            });
    }

    private void suggestProviders(JlinkConfiguration config, List<String> args)
        throws BadArgs
    {
        if (args.size() > 1) {
            throw taskHelper.newBadArgs("err.orphan.argument",
                                        toString(args.subList(1, args.size())))
                            .showUsage(true);
        }

        if (options.bindServices) {
            log.println(taskHelper.getMessage("no.suggested.providers"));
            return;
        }

        ModuleFinder finder = config.finder();
        if (args.isEmpty()) {
            // print providers used by the modules resolved without service binding
            Configuration cf = config.resolve();
            Set<ModuleReference> mrefs = cf.modules().stream()
                .map(ResolvedModule::reference)
                .collect(Collectors.toSet());

            // print uses of the modules that would be linked into the image
            mrefs.stream()
                 .sorted(Comparator.comparing(mref -> mref.descriptor().name()))
                 .forEach(mref -> {
                     ModuleDescriptor md = mref.descriptor();
                     log.format("module %s located (%s)%n", md.name(),
                                mref.location().get());
                     md.uses().stream().sorted()
                       .forEach(s -> log.format("    uses %s%n", s));
                 });

            String msg = String.format("%n%s:", taskHelper.getMessage("suggested.providers.header"));
            printProviders(log, msg, finder.findAll(), uses(mrefs));

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

            // the specified services may or may not be in the modules that
            // would be linked in.  So find uses declared in all observable modules
            Map<String, Set<String>> uses = uses(finder.findAll());

            // check if any name given on the command line are unused service
            mrefs.stream()
                 .flatMap(mref -> mref.descriptor().provides().stream()
                                      .map(ModuleDescriptor.Provides::service))
                 .forEach(names::remove);
            if (!names.isEmpty()) {
                log.println(taskHelper.getMessage("warn.unused.services",
                                                  toString(names)));
            }

            String msg = String.format("%n%s:", taskHelper.getMessage("suggested.providers.header"));
            printProviders(log, msg, mrefs, uses);
        }
    }

    private static String toString(Collection<String> collection) {
        return collection.stream().sorted()
                         .collect(Collectors.joining(","));
    }

    private String getSaveOpts() {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(new Date()).append("\n");
        for (String c : optionsHelper.getInputCommand()) {
            sb.append(c).append(" ");
        }

        return sb.toString();
    }

    private static String getBomHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("#").append(new Date()).append("\n");
        sb.append("#Please DO NOT Modify this file").append("\n");
        return sb.toString();
    }

    private String genBOMContent() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(getBomHeader());
        StringBuilder command = new StringBuilder();
        for (String c : optionsHelper.getInputCommand()) {
            command.append(c).append(" ");
        }
        sb.append("command").append(" = ").append(command);
        sb.append("\n");

        return sb.toString();
    }

    private static String genBOMContent(JlinkConfiguration config,
            PluginsConfiguration plugins)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(getBomHeader());
        sb.append(config);
        sb.append(plugins);
        return sb.toString();
    }

    private static class ImageHelper implements ImageProvider {
        final ByteOrder order;
        final Path packagedModulesPath;
        final boolean ignoreSigning;
        final Set<Archive> archives;

        ImageHelper(Configuration cf,
                    Map<String, Path> modsPaths,
                    ByteOrder order,
                    Path packagedModulesPath,
                    boolean ignoreSigning) throws IOException {
            this.order = order;
            this.packagedModulesPath = packagedModulesPath;
            this.ignoreSigning = ignoreSigning;
            this.archives = modsPaths.entrySet().stream()
                                .map(e -> newArchive(e.getKey(), e.getValue()))
                                .collect(Collectors.toSet());
        }

        private Archive newArchive(String module, Path path) {
            if (path.toString().endsWith(".jmod")) {
                return new JmodArchive(module, path);
            } else if (path.toString().endsWith(".jar")) {
                ModularJarArchive modularJarArchive = new ModularJarArchive(module, path);

                Stream<Archive.Entry> signatures = modularJarArchive.entries().filter((entry) -> {
                    String name = entry.name().toUpperCase(Locale.ENGLISH);

                    return name.startsWith("META-INF/") && name.indexOf('/', 9) == -1 && (
                                name.endsWith(".SF") ||
                                name.endsWith(".DSA") ||
                                name.endsWith(".RSA") ||
                                name.endsWith(".EC") ||
                                name.startsWith("META-INF/SIG-")
                            );
                });

                if (signatures.count() != 0) {
                    if (ignoreSigning) {
                        System.err.println(taskHelper.getMessage("warn.signing", path));
                    } else {
                        throw new IllegalArgumentException(taskHelper.getMessage("err.signing", path));
                    }
                }

                return modularJarArchive;
            } else if (Files.isDirectory(path)) {
                return new DirArchive(path);
            } else {
                throw new IllegalArgumentException(
                    taskHelper.getMessage("err.not.modular.format", module, path));
            }
        }

        @Override
        public ExecutableImage retrieve(ImagePluginStack stack) throws IOException {
            ExecutableImage image = ImageFileCreator.create(archives, order, stack);
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
