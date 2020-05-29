/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * StandardBundlerParam
 *
 * A parameter to a bundler.
 *
 * Also contains static definitions of all of the common bundler parameters.
 * (additional platform specific and mode specific bundler parameters
 * are defined in each of the specific bundlers)
 *
 * Also contains static methods that operate on maps of parameters.
 */
class StandardBundlerParam<T> extends BundlerParamInfo<T> {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MainResources");
    private static final String JAVABASEJMOD = "java.base.jmod";
    private final static String DEFAULT_VERSION = "1.0";
    private final static String DEFAULT_RELEASE = "1";
    private final static String[] DEFAULT_JLINK_OPTIONS = {
            "--strip-native-commands",
            "--strip-debug",
            "--no-man-pages",
            "--no-header-files"};

    StandardBundlerParam(String id, Class<T> valueType,
            Function<Map<String, ? super Object>, T> defaultValueFunction,
            BiFunction<String, Map<String, ? super Object>, T> stringConverter)
    {
        this.id = id;
        this.valueType = valueType;
        this.defaultValueFunction = defaultValueFunction;
        this.stringConverter = stringConverter;
    }

    static final StandardBundlerParam<RelativeFileSet> APP_RESOURCES =
            new StandardBundlerParam<>(
                    BundleParams.PARAM_APP_RESOURCES,
                    RelativeFileSet.class,
                    null, // no default.  Required parameter
                    null  // no string translation,
                          // tool must provide complex type
            );

    @SuppressWarnings("unchecked")
    static final
            StandardBundlerParam<List<RelativeFileSet>> APP_RESOURCES_LIST =
            new StandardBundlerParam<>(
                    BundleParams.PARAM_APP_RESOURCES + "List",
                    (Class<List<RelativeFileSet>>) (Object) List.class,
                    // Default is appResources, as a single item list
                    p -> new ArrayList<>(Collections.singletonList(
                            APP_RESOURCES.fetchFrom(p))),
                    StandardBundlerParam::createAppResourcesListFromString
            );

    static final StandardBundlerParam<String> SOURCE_DIR =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.INPUT.getId(),
                    String.class,
                    p -> null,
                    (s, p) -> {
                        String value = String.valueOf(s);
                        if (value.charAt(value.length() - 1) ==
                                File.separatorChar) {
                            return value.substring(0, value.length() - 1);
                        }
                        else {
                            return value;
                        }
                    }
            );

    // note that each bundler is likely to replace this one with
    // their own converter
    static final StandardBundlerParam<RelativeFileSet> MAIN_JAR =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MAIN_JAR.getId(),
                    RelativeFileSet.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        return (RelativeFileSet) params.get("mainJar");
                    },
                    (s, p) -> getMainJar(s, p)
            );

    static final StandardBundlerParam<String> CLASSPATH =
            new StandardBundlerParam<>(
                    "classpath",
                    String.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        String cp = (String) params.get("classpath");
                        return cp == null ? "" : cp;
                    },
                    (s, p) -> s
            );

    static final StandardBundlerParam<String> MAIN_CLASS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.APPCLASS.getId(),
                    String.class,
                    params -> {
                        if (isRuntimeInstaller(params)) {
                            return null;
                        }
                        extractMainClassInfoFromAppResources(params);
                        String s = (String) params.get(
                                BundleParams.PARAM_APPLICATION_CLASS);
                        if (s == null) {
                            s = JLinkBundlerHelper.getMainClassFromModule(
                                    params);
                        }
                        return s;
                    },
                    (s, p) -> s
            );

    static final StandardBundlerParam<File> PREDEFINED_RUNTIME_IMAGE =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId(),
                    File.class,
                    params -> null,
                    (s, p) -> new File(s)
            );

    static final StandardBundlerParam<String> APP_NAME =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.NAME.getId(),
                    String.class,
                    params -> {
                        String s = MAIN_CLASS.fetchFrom(params);
                        if (s != null) {
                            int idx = s.lastIndexOf(".");
                            if (idx >= 0) {
                                return s.substring(idx+1);
                            }
                            return s;
                        } else if (isRuntimeInstaller(params)) {
                            File f = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
                            if (f != null) {
                                return f.getName();
                            }
                        }
                        return null;
                    },
                    (s, p) -> s
            );

    static final StandardBundlerParam<File> ICON =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.ICON.getId(),
                    File.class,
                    params -> null,
                    (s, p) -> new File(s)
            );

    static final StandardBundlerParam<String> VENDOR =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.VENDOR.getId(),
                    String.class,
                    params -> I18N.getString("param.vendor.default"),
                    (s, p) -> s
            );

    static final StandardBundlerParam<String> DESCRIPTION =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.DESCRIPTION.getId(),
                    String.class,
                    params -> params.containsKey(APP_NAME.getID())
                            ? APP_NAME.fetchFrom(params)
                            : I18N.getString("param.description.default"),
                    (s, p) -> s
            );

    static final StandardBundlerParam<String> COPYRIGHT =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.COPYRIGHT.getId(),
                    String.class,
                    params -> MessageFormat.format(I18N.getString(
                            "param.copyright.default"), new Date()),
                    (s, p) -> s
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<String>> ARGUMENTS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.ARGUMENTS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<String>> JAVA_OPTIONS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.JAVA_OPTIONS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> Arrays.asList(s.split("\n\n"))
            );

    // note that each bundler is likely to replace this one with
    // their own converter
    static final StandardBundlerParam<String> VERSION =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.VERSION.getId(),
                    String.class,
                    params -> getDefaultAppVersion(params),
                    (s, p) -> s
            );

    static final StandardBundlerParam<String> RELEASE =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.RELEASE.getId(),
                    String.class,
                    params -> DEFAULT_RELEASE,
                    (s, p) -> s
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<String> LICENSE_FILE =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.LICENSE_FILE.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s
            );

    static final StandardBundlerParam<File> TEMP_ROOT =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.TEMP_ROOT.getId(),
                    File.class,
                    params -> {
                        try {
                            return Files.createTempDirectory(
                                    "jdk.incubator.jpackage").toFile();
                        } catch (IOException ioe) {
                            return null;
                        }
                    },
                    (s, p) -> new File(s)
            );

    public static final StandardBundlerParam<File> CONFIG_ROOT =
            new StandardBundlerParam<>(
                "configRoot",
                File.class,
                params -> {
                    File root =
                            new File(TEMP_ROOT.fetchFrom(params), "config");
                    root.mkdirs();
                    return root;
                },
                (s, p) -> null
            );

    static final StandardBundlerParam<Boolean> BIND_SERVICES =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.BIND_SERVICES.getId(),
                    Boolean.class,
                    params -> false,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );


    static final StandardBundlerParam<Boolean> VERBOSE  =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.VERBOSE.getId(),
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );

    static final StandardBundlerParam<File> RESOURCE_DIR =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.RESOURCE_DIR.getId(),
                    File.class,
                    params -> null,
                    (s, p) -> new File(s)
            );

    static final BundlerParamInfo<String> INSTALL_DIR =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.INSTALL_DIR.getId(),
                    String.class,
                     params -> null,
                    (s, p) -> s
    );

    static final StandardBundlerParam<File> PREDEFINED_APP_IMAGE =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId(),
            File.class,
            params -> null,
            (s, p) -> new File(s));

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<Map<String, ? super Object>>> ADD_LAUNCHERS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.ADD_LAUNCHER.getId(),
                    (Class<List<Map<String, ? super Object>>>) (Object)
                            List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam
            <List<Map<String, ? super Object>>> FILE_ASSOCIATIONS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.FILE_ASSOCIATIONS.getId(),
                    (Class<List<Map<String, ? super Object>>>) (Object)
                            List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<String>> FA_EXTENSIONS =
            new StandardBundlerParam<>(
                    "fileAssociation.extension",
                    (Class<List<String>>) (Object) List.class,
                    params -> null, // null means not matched to an extension
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<String>> FA_CONTENT_TYPE =
            new StandardBundlerParam<>(
                    "fileAssociation.contentType",
                    (Class<List<String>>) (Object) List.class,
                    params -> null,
                            // null means not matched to a content/mime type
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    static final StandardBundlerParam<String> FA_DESCRIPTION =
            new StandardBundlerParam<>(
                    "fileAssociation.description",
                    String.class,
                    params -> APP_NAME.fetchFrom(params) + " File",
                    null
            );

    static final StandardBundlerParam<File> FA_ICON =
            new StandardBundlerParam<>(
                    "fileAssociation.icon",
                    File.class,
                    ICON::fetchFrom,
                    (s, p) -> new File(s)
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<Path>> MODULE_PATH =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MODULE_PATH.getId(),
                    (Class<List<Path>>) (Object)List.class,
                    p -> { return getDefaultModulePath(); },
                    (s, p) -> {
                        List<Path> modulePath = Arrays.asList(s
                                .split(File.pathSeparator)).stream()
                                .map(ss -> new File(ss).toPath())
                                .collect(Collectors.toList());
                        Path javaBasePath = null;
                        if (modulePath != null) {
                            javaBasePath = JLinkBundlerHelper
                                    .findPathOfModule(modulePath, JAVABASEJMOD);
                        } else {
                            modulePath = new ArrayList<Path>();
                        }

                        // Add the default JDK module path to the module path.
                        if (javaBasePath == null) {
                            List<Path> jdkModulePath = getDefaultModulePath();

                            if (jdkModulePath != null) {
                                modulePath.addAll(jdkModulePath);
                                javaBasePath =
                                        JLinkBundlerHelper.findPathOfModule(
                                        modulePath, JAVABASEJMOD);
                            }
                        }

                        if (javaBasePath == null ||
                                !Files.exists(javaBasePath)) {
                            Log.error(String.format(I18N.getString(
                                    "warning.no.jdk.modules.found")));
                        }

                        return modulePath;
                    });

    static final BundlerParamInfo<String> MODULE =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MODULE.getId(),
                    String.class,
                    p -> null,
                    (s, p) -> {
                        return String.valueOf(s);
                    });

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<Set<String>> ADD_MODULES =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.ADD_MODULES.getId(),
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet<String>(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    @SuppressWarnings("unchecked")
    static final StandardBundlerParam<List<String>> JLINK_OPTIONS =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.JLINK_OPTIONS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    p -> Arrays.asList(DEFAULT_JLINK_OPTIONS),
                    (s, p) -> null);

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<Set<String>> LIMIT_MODULES =
            new StandardBundlerParam<>(
                    "limit-modules",
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet<String>(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    static boolean isRuntimeInstaller(Map<String, ? super Object> params) {
        if (params.containsKey(MODULE.getID()) ||
                params.containsKey(MAIN_JAR.getID()) ||
                params.containsKey(PREDEFINED_APP_IMAGE.getID())) {
            return false; // we are building or are given an application
        }
        // runtime installer requires --runtime-image, if this is false
        // here then we should have thrown error validating args.
        return params.containsKey(PREDEFINED_RUNTIME_IMAGE.getID());
    }

    static File getPredefinedAppImage(Map<String, ? super Object> params) {
        File applicationImage = null;
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
            if (!applicationImage.exists()) {
                throw new RuntimeException(
                        MessageFormat.format(I18N.getString(
                                "message.app-image-dir-does-not-exist"),
                                PREDEFINED_APP_IMAGE.getID(),
                                applicationImage.toString()));
            }
        }
        return applicationImage;
    }

    static void copyPredefinedRuntimeImage(
            Map<String, ? super Object> params,
            AbstractAppImageBuilder appBuilder)
            throws IOException , ConfigException {
        File topImage = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
        if (!topImage.exists()) {
            throw new ConfigException(
                    MessageFormat.format(I18N.getString(
                    "message.runtime-image-dir-does-not-exist"),
                    PREDEFINED_RUNTIME_IMAGE.getID(),
                    topImage.toString()),
                    MessageFormat.format(I18N.getString(
                    "message.runtime-image-dir-does-not-exist.advice"),
                    PREDEFINED_RUNTIME_IMAGE.getID()));
        }
        File image = appBuilder.getRuntimeImageDir(topImage);
        // copy whole runtime, need to skip jmods and src.zip
        final List<String> excludes = Arrays.asList("jmods", "src.zip");
        IOUtils.copyRecursive(image.toPath(), appBuilder.getRuntimeRoot(), excludes);

        // if module-path given - copy modules to appDir/mods
        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        List<Path> defaultModulePath = getDefaultModulePath();
        Path dest = appBuilder.getAppModsDir();

        if (dest != null) {
            for (Path mp : modulePath) {
                if (!defaultModulePath.contains(mp)) {
                    Files.createDirectories(dest);
                    IOUtils.copyRecursive(mp, dest);
                }
            }
        }

        appBuilder.prepareApplicationFiles(params);
    }

    static void extractMainClassInfoFromAppResources(
            Map<String, ? super Object> params) {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasModule = params.containsKey(MODULE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath || hasModule ||
                isRuntimeInstaller(params)) {
            return;
        }

        // it's a pair.
        // The [0] is the srcdir [1] is the file relative to sourcedir
        List<String[]> filesToCheck = new ArrayList<>();

        if (hasMainJar) {
            RelativeFileSet rfs = MAIN_JAR.fetchFrom(params);
            for (String s : rfs.getIncludedFiles()) {
                filesToCheck.add(
                        new String[] {rfs.getBaseDirectory().toString(), s});
            }
        } else if (hasMainJarClassPath) {
            for (String s : CLASSPATH.fetchFrom(params).split("\\s+")) {
                if (APP_RESOURCES.fetchFrom(params) != null) {
                    filesToCheck.add(
                            new String[] {APP_RESOURCES.fetchFrom(params)
                            .getBaseDirectory().toString(), s});
                }
            }
        } else {
            List<RelativeFileSet> rfsl = APP_RESOURCES_LIST.fetchFrom(params);
            if (rfsl == null || rfsl.isEmpty()) {
                return;
            }
            for (RelativeFileSet rfs : rfsl) {
                if (rfs == null) continue;

                for (String s : rfs.getIncludedFiles()) {
                    filesToCheck.add(
                            new String[]{rfs.getBaseDirectory().toString(), s});
                }
            }
        }

        // presume the set iterates in-order
        for (String[] fnames : filesToCheck) {
            try {
                // only sniff jars
                if (!fnames[1].toLowerCase().endsWith(".jar")) continue;

                File file = new File(fnames[0], fnames[1]);
                // that actually exist
                if (!file.exists()) continue;

                try (JarFile jf = new JarFile(file)) {
                    Manifest m = jf.getManifest();
                    Attributes attrs = (m != null) ?
                            m.getMainAttributes() : null;

                    if (attrs != null) {
                        if (!hasMainJar) {
                            if (fnames[0] == null) {
                                fnames[0] = file.getParentFile().toString();
                            }
                            params.put(MAIN_JAR.getID(), new RelativeFileSet(
                                    new File(fnames[0]),
                                    new LinkedHashSet<>(Collections
                                    .singletonList(file))));
                        }
                        if (!hasMainJarClassPath) {
                            String cp =
                                    attrs.getValue(Attributes.Name.CLASS_PATH);
                            params.put(CLASSPATH.getID(),
                                    cp == null ? "" : cp);
                        }
                        break;
                    }
                }
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }
    }

    static void validateMainClassInfoFromAppResources(
            Map<String, ? super Object> params) throws ConfigException {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasModule = params.containsKey(MODULE.getID());
        boolean hasAppImage = params.containsKey(PREDEFINED_APP_IMAGE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath ||
               hasAppImage || isRuntimeInstaller(params)) {
            return;
        }
        if (hasModule) {
            if (JLinkBundlerHelper.getMainClassFromModule(params) == null) {
                throw new ConfigException(
                        I18N.getString("ERR_NoMainClass"), null);
            }
        } else {
            extractMainClassInfoFromAppResources(params);

            if (!params.containsKey(MAIN_CLASS.getID())) {
                if (hasMainJar) {
                    throw new ConfigException(
                            MessageFormat.format(I18N.getString(
                            "error.no-main-class-with-main-jar"),
                            MAIN_JAR.fetchFrom(params)),
                            MessageFormat.format(I18N.getString(
                            "error.no-main-class-with-main-jar.advice"),
                            MAIN_JAR.fetchFrom(params)));
                } else {
                    throw new ConfigException(
                            I18N.getString("error.no-main-class"),
                            I18N.getString("error.no-main-class.advice"));
                }
            }
        }
    }

    private static List<RelativeFileSet>
            createAppResourcesListFromString(String s,
            Map<String, ? super Object> objectObjectMap) {
        List<RelativeFileSet> result = new ArrayList<>();
        for (String path : s.split("[:;]")) {
            File f = new File(path);
            if (f.getName().equals("*") || path.endsWith("/") ||
                    path.endsWith("\\")) {
                if (f.getName().equals("*")) {
                    f = f.getParentFile();
                }
                Set<File> theFiles = new HashSet<>();
                try {
                    try (Stream<Path> stream = Files.walk(f.toPath())) {
                        stream.filter(Files::isRegularFile)
                                .forEach(p -> theFiles.add(p.toFile()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                result.add(new RelativeFileSet(f, theFiles));
            } else {
                result.add(new RelativeFileSet(f.getParentFile(),
                        Collections.singleton(f)));
            }
        }
        return result;
    }

    private static RelativeFileSet getMainJar(
            String mainJarValue, Map<String, ? super Object> params) {
        for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
            File appResourcesRoot = rfs.getBaseDirectory();
            File mainJarFile = new File(appResourcesRoot, mainJarValue);

            if (mainJarFile.exists()) {
                return new RelativeFileSet(appResourcesRoot,
                     new LinkedHashSet<>(Collections.singletonList(
                     mainJarFile)));
            }
            mainJarFile = new File(mainJarValue);
            if (mainJarFile.exists()) {
                // absolute path for main-jar may fail is not legal
                // below contains explicit error message.
            } else {
                List<Path> modulePath = MODULE_PATH.fetchFrom(params);
                modulePath.removeAll(getDefaultModulePath());
                if (!modulePath.isEmpty()) {
                    Path modularJarPath = JLinkBundlerHelper.findPathOfModule(
                            modulePath, mainJarValue);
                    if (modularJarPath != null &&
                            Files.exists(modularJarPath)) {
                        return new RelativeFileSet(appResourcesRoot,
                                new LinkedHashSet<>(Collections.singletonList(
                                modularJarPath.toFile())));
                    }
                }
            }
        }

        throw new IllegalArgumentException(
                new ConfigException(MessageFormat.format(I18N.getString(
                        "error.main-jar-does-not-exist"),
                        mainJarValue), I18N.getString(
                        "error.main-jar-does-not-exist.advice")));
    }

    static List<Path> getDefaultModulePath() {
        List<Path> result = new ArrayList<Path>();
        Path jdkModulePath = Paths.get(
                System.getProperty("java.home"), "jmods").toAbsolutePath();

        if (jdkModulePath != null && Files.exists(jdkModulePath)) {
            result.add(jdkModulePath);
        }
        else {
            // On a developer build the JDK Home isn't where we expect it
            // relative to the jmods directory. Do some extra
            // processing to find it.
            Map<String, String> env = System.getenv();

            if (env.containsKey("JDK_HOME")) {
                jdkModulePath = Paths.get(env.get("JDK_HOME"),
                        ".." + File.separator + "images"
                        + File.separator + "jmods").toAbsolutePath();

                if (jdkModulePath != null && Files.exists(jdkModulePath)) {
                    result.add(jdkModulePath);
                }
            }
        }

        return result;
    }

    static String getDefaultAppVersion(Map<String, ? super Object> params) {
        String appVersion = DEFAULT_VERSION;

        ModuleDescriptor descriptor = JLinkBundlerHelper.getMainModuleDescription(params);
        if (descriptor != null) {
            Optional<Version> oversion = descriptor.version();
            if (oversion.isPresent()) {
                Log.verbose(MessageFormat.format(I18N.getString(
                        "message.module-version"),
                        oversion.get().toString(),
                        JLinkBundlerHelper.getMainModule(params)));
                appVersion = oversion.get().toString();
            }
        }

        return appVersion;
    }
}
