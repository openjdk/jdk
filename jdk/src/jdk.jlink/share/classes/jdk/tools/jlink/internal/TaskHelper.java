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
package jdk.tools.jlink.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.plugins.ExcludeJmodSectionPlugin;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Plugin.Category;
import jdk.tools.jlink.builder.DefaultImageBuilder;
import jdk.tools.jlink.builder.ImageBuilder;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.Jlink.PluginsConfiguration;
import jdk.tools.jlink.internal.plugins.PluginsResourceBundle;
import jdk.tools.jlink.internal.plugins.DefaultCompressPlugin;
import jdk.tools.jlink.internal.plugins.StripDebugPlugin;
import jdk.internal.misc.SharedSecrets;

/**
 *
 * JLink and JImage tools shared helper.
 */
public final class TaskHelper {

    public static final String JLINK_BUNDLE = "jdk.tools.jlink.resources.jlink";
    public static final String JIMAGE_BUNDLE = "jdk.tools.jimage.resources.jimage";

    private static final String DEFAULTS_PROPERTY = "jdk.jlink.defaults";

    public final class BadArgs extends Exception {

        static final long serialVersionUID = 8765093759964640721L;

        private BadArgs(String key, Object... args) {
            super(bundleHelper.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        public BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
        public final String key;
        public final Object[] args;
        public boolean showUsage;
    }

    public static class Option<T> implements Comparable<T> {
        public interface Processing<T> {

            void process(T task, String opt, String arg) throws BadArgs;
        }

        final boolean hasArg;
        final Processing<T> processing;
        final boolean hidden;
        final String name;
        final String shortname;

        public Option(boolean hasArg, Processing<T> processing, boolean hidden, String name, String shortname) {
            if (!name.startsWith("--")) {
                throw new RuntimeException("option name missing --, " + name);
            }
            if (!shortname.isEmpty() && !shortname.startsWith("-")) {
                throw new RuntimeException("short name missing -, " + shortname);
            }

            this.hasArg = hasArg;
            this.processing = processing;
            this.hidden = hidden;
            this.name = name;
            this.shortname = shortname;
        }

        public Option(boolean hasArg, Processing<T> processing, String name, String shortname) {
            this(hasArg, processing, false, name, shortname);
        }

        public Option(boolean hasArg, Processing<T> processing, boolean hidden, String name) {
            this(hasArg, processing, hidden, name, "");
        }

        public Option(boolean hasArg, Processing<T> processing, String name) {
            this(hasArg, processing, false, name, "");
        }

        public boolean isHidden() {
            return hidden;
        }

        public boolean matches(String opt) {
            return opt.equals(name) ||
                   opt.equals(shortname) ||
                   hasArg && opt.startsWith("--") && opt.startsWith(name + "=");
         }

        public boolean ignoreRest() {
            return false;
        }

        void process(T task, String opt, String arg) throws BadArgs {
            processing.process(task, opt, arg);
        }

        public String getName() {
            return name;
        }

        public String resourceName() {
            return resourcePrefix() + name.substring(2);
        }

        public String getShortname() {
            return shortname;
        }

        public String resourcePrefix() {
            return "main.opt.";
        }

        @Override
        public int compareTo(Object object) {
            if (!(object instanceof Option<?>)) {
                throw new RuntimeException("comparing non-Option");
            }

            Option<?> option = (Option<?>)object;

            return name.compareTo(option.name);
        }

    }

    private static class PluginOption extends Option<PluginsHelper> {
        public PluginOption(boolean hasArg,
                Processing<PluginsHelper> processing, boolean hidden, String name, String shortname) {
            super(hasArg, processing, hidden, name, shortname);
        }

        public PluginOption(boolean hasArg,
                Processing<PluginsHelper> processing, boolean hidden, String name) {
            super(hasArg, processing, hidden, name, "");
        }

        public String resourcePrefix() {
            return "plugin.opt.";
        }
    }

    private final class PluginsHelper {

        private static final String PLUGINS_PATH = "--plugin-module-path";

        private Layer pluginsLayer = Layer.boot();
        private final List<Plugin> plugins;
        private String lastSorter;
        private boolean listPlugins;
        private Path existingImage;

        // plugin to args maps. Each plugin may be used more than once in command line.
        // Each such occurrence results in a Map of arguments. So, there could be multiple
        // args maps per plugin instance.
        private final Map<Plugin, List<Map<String, String>>> pluginToMaps = new HashMap<>();
        private final List<PluginOption> pluginsOptions = new ArrayList<>();
        private final List<PluginOption> mainOptions = new ArrayList<>();

        private PluginsHelper(String pp) throws BadArgs {

            if (pp != null) {
                String[] dirs = pp.split(File.pathSeparator);
                List<Path> paths = new ArrayList<>(dirs.length);
                for (String dir : dirs) {
                    paths.add(Paths.get(dir));
                }

                pluginsLayer = createPluginsLayer(paths);
            }

            plugins = PluginRepository.getPlugins(pluginsLayer);

            Set<String> optionsSeen = new HashSet<>();
            for (Plugin plugin : plugins) {
                if (!Utils.isDisabled(plugin)) {
                    addOrderedPluginOptions(plugin, optionsSeen);
                }
            }
            mainOptions.add(new PluginOption(false,
                    (task, opt, arg) -> {
                        // This option is handled prior
                        // to have the options parsed.
                    },
                    false, "--plugin-module-path"));
            mainOptions.add(new PluginOption(true, (task, opt, arg) -> {
                    for (Plugin plugin : plugins) {
                        if (plugin.getName().equals(arg)) {
                            pluginToMaps.remove(plugin);
                            return;
                        }
                    }
                    throw newBadArgs("err.no.such.plugin", arg);
                },
                false, "--disable-plugin"));
            mainOptions.add(new PluginOption(true, (task, opt, arg) -> {
                Path path = Paths.get(arg);
                if (!Files.exists(path) || !Files.isDirectory(path)) {
                    throw newBadArgs("err.image.must.exist", path);
                }
                existingImage = path.toAbsolutePath();
            }, true, "--post-process-path"));
            mainOptions.add(new PluginOption(true,
                    (task, opt, arg) -> {
                        lastSorter = arg;
                    },
                    true, "--resources-last-sorter"));
            mainOptions.add(new PluginOption(false,
                    (task, opt, arg) -> {
                        listPlugins = true;
                    },
                    false, "--list-plugins"));
        }

        private List<Map<String, String>> argListFor(Plugin plugin) {
            List<Map<String, String>> mapList = pluginToMaps.get(plugin);
            if (mapList == null) {
                mapList = new ArrayList<>();
                pluginToMaps.put(plugin, mapList);
            }
            return mapList;
        }

        private void addEmptyArgumentMap(Plugin plugin) {
            argListFor(plugin).add(Collections.emptyMap());
        }

        private Map<String, String> addArgumentMap(Plugin plugin) {
            Map<String, String> map = new HashMap<>();
            argListFor(plugin).add(map);
            return map;
        }

        private void addOrderedPluginOptions(Plugin plugin,
            Set<String> optionsSeen) throws BadArgs {
            String option = plugin.getOption();
            if (option == null) {
                return;
            }

            // make sure that more than one plugin does not use the same option!
            if (optionsSeen.contains(option)) {
                throw new BadArgs("err.plugin.mutiple.options",
                        option);
            }
            optionsSeen.add(option);

            PluginOption plugOption
                    = new PluginOption(plugin.hasArguments(),
                            (task, opt, arg) -> {
                                if (!Utils.isFunctional(plugin)) {
                                    throw newBadArgs("err.provider.not.functional",
                                            option);
                                }

                                if (! plugin.hasArguments()) {
                                    addEmptyArgumentMap(plugin);
                                    return;
                                }

                                Map<String, String> m = addArgumentMap(plugin);
                                // handle one or more arguments
                                if (arg.indexOf(':') == -1) {
                                    // single argument case
                                    m.put(option, arg);
                                } else {
                                    // This option can accept more than one arguments
                                    // like --option_name=arg_value:arg2=value2:arg3=value3

                                    // ":" followed by word char condition takes care of args that
                                    // like Windows absolute paths "C:\foo", "C:/foo" [cygwin] etc.
                                    // This enforces that key names start with a word character.
                                    String[] args = arg.split(":(?=\\w)", -1);
                                    String firstArg = args[0];
                                    if (firstArg.isEmpty()) {
                                        throw newBadArgs("err.provider.additional.arg.error",
                                            option, arg);
                                    }
                                    m.put(option, firstArg);
                                    // process the additional arguments
                                    for (int i = 1; i < args.length; i++) {
                                        String addArg = args[i];
                                        int eqIdx = addArg.indexOf('=');
                                        if (eqIdx == -1) {
                                            throw newBadArgs("err.provider.additional.arg.error",
                                                option, arg);
                                        }

                                        String addArgName = addArg.substring(0, eqIdx);
                                        String addArgValue = addArg.substring(eqIdx+1);
                                        if (addArgName.isEmpty() || addArgValue.isEmpty()) {
                                            throw newBadArgs("err.provider.additional.arg.error",
                                                option, arg);
                                        }
                                        m.put(addArgName, addArgValue);
                                    }
                                }
                            },
                            false, "--" + option);
            pluginsOptions.add(plugOption);

            if (Utils.isFunctional(plugin)) {
                if (Utils.isAutoEnabled(plugin)) {
                    addEmptyArgumentMap(plugin);
                }

                if (plugin instanceof DefaultCompressPlugin) {
                    plugOption
                        = new PluginOption(false,
                            (task, opt, arg) -> {
                                Map<String, String> m = addArgumentMap(plugin);
                                m.put(DefaultCompressPlugin.NAME, DefaultCompressPlugin.LEVEL_2);
                            }, false, "--compress", "-c");
                    mainOptions.add(plugOption);
                } else if (plugin instanceof StripDebugPlugin) {
                    plugOption
                        = new PluginOption(false,
                            (task, opt, arg) -> {
                                addArgumentMap(plugin);
                            }, false, "--strip-debug", "-G");
                    mainOptions.add(plugOption);
                } else if (plugin instanceof ExcludeJmodSectionPlugin) {
                    plugOption = new PluginOption(false, (task, opt, arg) -> {
                            Map<String, String> m = addArgumentMap(plugin);
                            m.put(ExcludeJmodSectionPlugin.NAME,
                                  ExcludeJmodSectionPlugin.MAN_PAGES);
                        }, false, "--no-man-pages");
                    mainOptions.add(plugOption);

                    plugOption = new PluginOption(false, (task, opt, arg) -> {
                        Map<String, String> m = addArgumentMap(plugin);
                        m.put(ExcludeJmodSectionPlugin.NAME,
                              ExcludeJmodSectionPlugin.INCLUDE_HEADER_FILES);
                    }, false, "--no-header-files");
                    mainOptions.add(plugOption);
                }
            }
        }

        private PluginOption getOption(String name) throws BadArgs {
            for (PluginOption o : pluginsOptions) {
                if (o.matches(name)) {
                    return o;
                }
            }
            for (PluginOption o : mainOptions) {
                if (o.matches(name)) {
                    return o;
                }
            }
            return null;
        }

        private PluginsConfiguration getPluginsConfig(Path output
                    ) throws IOException, BadArgs {
            if (output != null) {
                if (Files.exists(output)) {
                    throw new PluginException(PluginsResourceBundle.
                            getMessage("err.dir.already.exits", output));
                }
            }

            List<Plugin> pluginsList = new ArrayList<>();
            for (Entry<Plugin, List<Map<String, String>>> entry : pluginToMaps.entrySet()) {
                Plugin plugin = entry.getKey();
                List<Map<String, String>> argsMaps = entry.getValue();

                // same plugin option may be used multiple times in command line.
                // we call configure once for each occurrence. It is upto the plugin
                // to 'merge' and/or 'override' arguments.
                for (Map<String, String> map : argsMaps) {
                    try {
                        plugin.configure(Collections.unmodifiableMap(map));
                    } catch (IllegalArgumentException e) {
                        if (JlinkTask.DEBUG) {
                            System.err.println("Plugin " + plugin.getName() + " threw exception with config: " + map);
                            e.printStackTrace();
                        }
                        throw e;
                    }
                }

                if (!Utils.isDisabled(plugin)) {
                    pluginsList.add(plugin);
                }
            }

            // recreate or postprocessing don't require an output directory.
            ImageBuilder builder = null;
            if (output != null) {
                builder = new DefaultImageBuilder(output);

            }
            return new Jlink.PluginsConfiguration(pluginsList,
                    builder, lastSorter);
        }
    }

    private static final class ResourceBundleHelper {

        private final ResourceBundle bundle;
        private final ResourceBundle pluginBundle;

        ResourceBundleHelper(String path) {
            Locale locale = Locale.getDefault();
            try {
                bundle = ResourceBundle.getBundle(path, locale);
                pluginBundle = ResourceBundle.getBundle("jdk.tools.jlink.resources.plugins", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jlink resource bundle for locale " + locale);
            }
        }

        String getMessage(String key, Object... args) {
            String val;
            try {
                val = bundle.getString(key);
            } catch (MissingResourceException e) {
                // XXX OK, check in plugin bundle
                val = pluginBundle.getString(key);
            }
            return MessageFormat.format(val, args);
        }

    }

    public final class OptionsHelper<T> {

        private final List<Option<T>> options;
        private String[] command;
        private String defaults;

        OptionsHelper(List<Option<T>> options) {
            this.options = options;
        }

        private boolean hasArgument(String optionName) throws BadArgs {
            Option<?> opt = getOption(optionName);
            if (opt == null) {
                opt = pluginOptions.getOption(optionName);
                if (opt == null) {
                    throw new BadArgs("err.unknown.option", optionName).
                            showUsage(true);
                }
            }
            return opt.hasArg;
        }

        public boolean shouldListPlugins() {
            return pluginOptions.listPlugins;
        }

        private String getPluginsPath(String[] args) throws BadArgs {
            String pp = null;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals(PluginsHelper.PLUGINS_PATH)) {
                    if (i == args.length - 1) {
                        throw new BadArgs("err.no.plugins.path").showUsage(true);
                    } else {
                        i += 1;
                        pp = args[i];
                        if (!pp.isEmpty() && pp.charAt(0) == '-') {
                            throw new BadArgs("err.no.plugins.path").showUsage(true);
                        }
                        break;
                    }
                }
            }
            return pp;
        }

        // used by jimage. Return unhandled arguments like "create", "describe".
        public List<String> handleOptions(T task, String[] args) throws BadArgs {
            return handleOptions(task, args, true);
        }

        // used by jlink. No unhandled arguments like "create", "describe".
        void handleOptionsNoUnhandled(T task, String[] args) throws BadArgs {
            handleOptions(task, args, false);
        }

        // shared code that handles options for both jlink and jimage. jimage uses arguments like
        // "create", "describe" etc. as "task names". Those arguments are unhandled here and returned
        // as "unhandled arguments list". jlink does not want such arguments. "collectUnhandled" flag
        // tells whether to allow for unhandled arguments or not.
        private List<String> handleOptions(T task, String[] args, boolean collectUnhandled) throws BadArgs {
            // findbugs warning, copy instead of keeping a reference.
            command = Arrays.copyOf(args, args.length);

            // Must extract it prior to do any option analysis.
            // Required to interpret custom plugin options.
            // Unit tests can call Task multiple time in same JVM.
            pluginOptions = new PluginsHelper(getPluginsPath(args));

            // First extract plugins path if any
            String pp = null;
            List<String> filteredArgs = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals(PluginsHelper.PLUGINS_PATH)) {
                    if (i == args.length - 1) {
                        throw new BadArgs("err.no.plugins.path").showUsage(true);
                    } else {
                        warning("warn.thirdparty.plugins.enabled");
                        log.println(bundleHelper.getMessage("warn.thirdparty.plugins"));
                        i += 1;
                        String arg = args[i];
                        if (!arg.isEmpty() && arg.charAt(0) == '-') {
                            throw new BadArgs("err.no.plugins.path").showUsage(true);
                        }
                        pp = args[i];
                    }
                } else {
                    filteredArgs.add(args[i]);
                }
            }
            String[] arr = new String[filteredArgs.size()];
            args = filteredArgs.toArray(arr);

            List<String> rest = collectUnhandled? new ArrayList<>() : null;
            // process options
            for (int i = 0; i < args.length; i++) {
                if (args[i].charAt(0) == '-') {
                    String name = args[i];
                    PluginOption pluginOption = null;
                    Option<T> option = getOption(name);
                    if (option == null) {
                        pluginOption = pluginOptions.getOption(name);
                        if (pluginOption == null) {

                            throw new BadArgs("err.unknown.option", name).
                                    showUsage(true);
                        }
                    }
                    Option<?> opt = pluginOption == null ? option : pluginOption;
                    String param = null;
                    if (opt.hasArg) {
                        if (name.startsWith("--") && name.indexOf('=') > 0) {
                            param = name.substring(name.indexOf('=') + 1,
                                    name.length());
                        } else if (i + 1 < args.length) {
                            param = args[++i];
                        }
                        if (param == null || param.isEmpty()
                                || (param.length() >= 2 && param.charAt(0) == '-'
                                && param.charAt(1) == '-')) {
                            throw new BadArgs("err.missing.arg", name).
                                    showUsage(true);
                        }
                    }
                    if (pluginOption != null) {
                        pluginOption.process(pluginOptions, name, param);
                    } else {
                        option.process(task, name, param);
                    }
                    if (opt.ignoreRest()) {
                        i = args.length;
                    }
                } else {
                    if (collectUnhandled) {
                        rest.add(args[i]);
                    } else {
                        throw new BadArgs("err.orphan.argument", args[i]).
                            showUsage(true);
                    }
                }
            }
            return rest;
        }

        private Option<T> getOption(String name) {
            for (Option<T> o : options) {
                if (o.matches(name)) {
                    return o;
                }
            }
            return null;
        }

        public void showHelp(String progName) {
            log.println(bundleHelper.getMessage("main.usage", progName));
            Stream.concat(options.stream(), pluginOptions.mainOptions.stream())
                .filter(option -> !option.isHidden())
                .sorted()
                .forEach(option -> {
                     log.println(bundleHelper.getMessage(option.resourceName()));
                });

            log.println(bundleHelper.getMessage("main.command.files"));
        }

        public void listPlugins() {
            log.println("\n" + bundleHelper.getMessage("main.extended.help"));
            List<Plugin> pluginList = PluginRepository.
                    getPlugins(pluginOptions.pluginsLayer);

            for (Plugin plugin : Utils.getSortedPlugins(pluginList)) {
                showPlugin(plugin, log);
            }

            log.println("\n" + bundleHelper.getMessage("main.extended.help.footer"));
        }

        private void showPlugin(Plugin plugin, PrintWriter log) {
            if (showsPlugin(plugin)) {
                log.println("\n" + bundleHelper.getMessage("main.plugin.name")
                        + ": " + plugin.getName());

                // print verbose details for non-builtin plugins
                if (!Utils.isBuiltin(plugin)) {
                    log.println(bundleHelper.getMessage("main.plugin.class")
                         + ": " + plugin.getClass().getName());
                    log.println(bundleHelper.getMessage("main.plugin.module")
                         + ": " + plugin.getClass().getModule().getName());
                    Category category = plugin.getType();
                    log.println(bundleHelper.getMessage("main.plugin.category")
                         + ": " + category.getName());
                    log.println(bundleHelper.getMessage("main.plugin.state")
                        + ": " + plugin.getStateDescription());
                }

                String option = plugin.getOption();
                if (option != null) {
                    log.println(bundleHelper.getMessage("main.plugin.option")
                        + ": --" + plugin.getOption()
                        + (plugin.hasArguments()? ("=" + plugin.getArgumentsDescription()) : ""));
                }

                // description can be long spanning more than one line and so
                // print a newline after description label.
                log.println(bundleHelper.getMessage("main.plugin.description")
                        + ": " + plugin.getDescription());
            }
        }

        String[] getInputCommand() {
            return command;
        }

        String getDefaults() {
            return defaults;
        }

        public Layer getPluginsLayer() {
            return pluginOptions.pluginsLayer;
        }
    }

    private PluginsHelper pluginOptions;
    private PrintWriter log;
    private final ResourceBundleHelper bundleHelper;

    public TaskHelper(String path) {
        if (!JLINK_BUNDLE.equals(path) && !JIMAGE_BUNDLE.equals(path)) {
            throw new IllegalArgumentException("Invalid Bundle");
        }
        this.bundleHelper = new ResourceBundleHelper(path);
    }

    public <T> OptionsHelper<T> newOptionsHelper(Class<T> clazz,
            Option<?>[] options) {
        List<Option<T>> optionsList = new ArrayList<>();
        for (Option<?> o : options) {
            @SuppressWarnings("unchecked")
            Option<T> opt = (Option<T>) o;
            optionsList.add(opt);
        }
        return new OptionsHelper<>(optionsList);
    }

    public BadArgs newBadArgs(String key, Object... args) {
        return new BadArgs(key, args);
    }

    public String getMessage(String key, Object... args) {
        return bundleHelper.getMessage(key, args);
    }

    public void setLog(PrintWriter log) {
        this.log = log;
    }

    public void reportError(String key, Object... args) {
        log.println(bundleHelper.getMessage("error.prefix") + " "
                + bundleHelper.getMessage(key, args));
    }

    public void reportUnknownError(String message) {
        log.println(bundleHelper.getMessage("error.prefix") + " " + message);
    }

    public void warning(String key, Object... args) {
        log.println(bundleHelper.getMessage("warn.prefix") + " "
                + bundleHelper.getMessage(key, args));
    }

    public PluginsConfiguration getPluginsConfig(Path output)
            throws IOException, BadArgs {
        return pluginOptions.getPluginsConfig(output);
    }

    public Path getExistingImage() {
        return pluginOptions.existingImage;
    }

    public void showVersion(boolean full) {
        log.println(version(full ? "full" : "release"));
    }

    public String version(String key) {
        return System.getProperty("java.version");
    }

    static Layer createPluginsLayer(List<Path> paths) {

        Path[] dirs = paths.toArray(new Path[0]);
        ModuleFinder finder = SharedSecrets.getJavaLangModuleAccess()
            .newModulePath(Runtime.version(), true, dirs);

        Configuration bootConfiguration = Layer.boot().configuration();
        try {
            Configuration cf = bootConfiguration
                .resolveRequiresAndUses(ModuleFinder.of(),
                                        finder,
                                        Collections.emptySet());
            ClassLoader scl = ClassLoader.getSystemClassLoader();
            return Layer.boot().defineModulesWithOneLoader(cf, scl);
        } catch (Exception ex) {
            // Malformed plugin modules (e.g.: same package in multiple modules).
            throw new PluginException("Invalid modules in the plugins path: " + ex);
        }
    }

    // Display all plugins
    private static boolean showsPlugin(Plugin plugin) {
        return (!Utils.isDisabled(plugin) && plugin.getOption() != null);
    }
}
