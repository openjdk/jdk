/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication;
import static jdk.jpackage.internal.ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT;
import static jdk.jpackage.internal.model.RuntimeBuilder.getDefaultModulePath;

/**
 * Standard bundler parameters.
 *
 * Contains static definitions of all of the common bundler parameters.
 * (additional platform specific and mode specific bundler parameters
 * are defined in each of the specific bundlers)
 *
 * Also contains static methods that operate on maps of parameters.
 */
final class StandardBundlerParam {

    private static final String JAVABASEJMOD = "java.base.jmod";
    private static final String DEFAULT_VERSION = "1.0";
    private static final String DEFAULT_RELEASE = "1";
    private static final String[] DEFAULT_JLINK_OPTIONS = {
            "--strip-native-commands",
            "--strip-debug",
            "--no-man-pages",
            "--no-header-files"};

    static final BundlerParamInfo<LauncherData> LAUNCHER_DATA = BundlerParamInfo.createBundlerParam(
            LauncherData.class, LauncherData::create);

    static final BundlerParamInfo<Path> SOURCE_DIR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.INPUT.getId(),
                    Path.class,
                    p -> null,
                    (s, p) -> Path.of(s)
            );

    static final BundlerParamInfo<Path> OUTPUT_DIR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.OUTPUT.getId(),
                    Path.class,
                    p -> Path.of("").toAbsolutePath(),
                    (s, p) -> Path.of(s)
            );

    // note that each bundler is likely to replace this one with
    // their own converter
    static final BundlerParamInfo<Path> MAIN_JAR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.MAIN_JAR.getId(),
                    Path.class,
                    params -> LAUNCHER_DATA.fetchFrom(params).mainJarName(),
                    null
            );

    static final BundlerParamInfo<ExternalApplication> PREDEFINED_APP_IMAGE_FILE = BundlerParamInfo.createBundlerParam(
            ExternalApplication.class, params -> {
                if (hasPredefinedAppImage(params)) {
                    var appImage = getPredefinedAppImage(params);
                    return AppImageFile.load(appImage, PLATFORM_APPLICATION_LAYOUT);
                } else {
                    return null;
                }
            });

    static final BundlerParamInfo<String> MAIN_CLASS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.APPCLASS.getId(),
                    String.class,
                    params -> {
                        if (isRuntimeInstaller(params)) {
                            return null;
                        } else if (hasPredefinedAppImage(params)) {
                            PREDEFINED_APP_IMAGE_FILE.fetchFrom(params).getMainClass();
                        }
                        return LAUNCHER_DATA.fetchFrom(params).qualifiedClassName();
                    },
                    (s, p) -> s
            );

    static final BundlerParamInfo<Path> PREDEFINED_RUNTIME_IMAGE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId(),
                    Path.class,
                    params -> null,
                    (s, p) -> Path.of(s)
            );

    static final BundlerParamInfo<Path> PREDEFINED_APP_IMAGE =
            new BundlerParamInfo<>(
            Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId(),
            Path.class,
            params -> null,
            (s, p) -> Path.of(s));

    // this is the raw --app-name arg - used in APP_NAME and INSTALLER_NAME
    static final BundlerParamInfo<String> NAME =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.NAME.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s
            );

    // this is the application name, either from the app-image (if given),
    // the name (if given) derived from the main-class, or the runtime image
    static final BundlerParamInfo<String> APP_NAME =
            new BundlerParamInfo<>(
                    "application-name",
                    String.class,
                    params -> {
                        String appName = NAME.fetchFrom(params);
                        if (hasPredefinedAppImage(params)) {
                            appName = PREDEFINED_APP_IMAGE_FILE.fetchFrom(params).getLauncherName();
                        } else if (appName == null) {
                            String s = MAIN_CLASS.fetchFrom(params);
                            if (s != null) {
                                int idx = s.lastIndexOf(".");
                                appName = (idx < 0) ? s : s.substring(idx+1);
                            } else if (isRuntimeInstaller(params)) {
                                Path f = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
                                if (f != null) {
                                    appName = f.getFileName().toString();
                                }
                            }
                        }
                        return appName;
                    },
                    (s, p) -> s
            );

    static final BundlerParamInfo<String> INSTALLER_NAME =
            new BundlerParamInfo<>(
                    "installer-name",
                    String.class,
                    params -> {
                        String installerName = NAME.fetchFrom(params);
                        return (installerName != null) ? installerName :
                                APP_NAME.fetchFrom(params);
                    },
                    (s, p) -> s
            );

    static final BundlerParamInfo<Path> ICON =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.ICON.getId(),
                    Path.class,
                    params -> null,
                    (s, p) -> Path.of(s)
            );

    static final BundlerParamInfo<String> ABOUT_URL =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.ABOUT_URL.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s
            );

    static final BundlerParamInfo<String> VENDOR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.VENDOR.getId(),
                    String.class,
                    params -> I18N.getString("param.vendor.default"),
                    (s, p) -> s
            );

    static final BundlerParamInfo<String> DESCRIPTION =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.DESCRIPTION.getId(),
                    String.class,
                    params -> params.containsKey(APP_NAME.getID())
                            ? APP_NAME.fetchFrom(params)
                            : I18N.getString("param.description.default"),
                    (s, p) -> s
            );

    static final BundlerParamInfo<String> COPYRIGHT =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.COPYRIGHT.getId(),
                    String.class,
                    params -> MessageFormat.format(I18N.getString(
                            "param.copyright.default"), new Date()),
                    (s, p) -> s
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<String>> ARGUMENTS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.ARGUMENTS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<String>> JAVA_OPTIONS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.JAVA_OPTIONS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> Arrays.asList(s.split("\n\n"))
            );

    static final BundlerParamInfo<String> VERSION =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.VERSION.getId(),
                    String.class,
                    StandardBundlerParam::getDefaultAppVersion,
                    (s, p) -> s
            );

    static final BundlerParamInfo<String> RELEASE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.RELEASE.getId(),
                    String.class,
                    params -> DEFAULT_RELEASE,
                    (s, p) -> s
            );

    public static final BundlerParamInfo<String> LICENSE_FILE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.LICENSE_FILE.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s
            );

    static final BundlerParamInfo<Path> TEMP_ROOT =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.TEMP_ROOT.getId(),
                    Path.class,
                    params -> {
                        try {
                            return Files.createTempDirectory("jdk.jpackage");
                        } catch (IOException ioe) {
                            return null;
                        }
                    },
                    (s, p) -> Path.of(s)
            );

    public static final BundlerParamInfo<Path> CONFIG_ROOT =
            new BundlerParamInfo<>(
                "configRoot",
                Path.class,
                params -> {
                    Path root = TEMP_ROOT.fetchFrom(params).resolve("config");
                    try {
                        Files.createDirectories(root);
                    } catch (IOException ioe) {
                        return null;
                    }
                    return root;
                },
                (s, p) -> null
            );

    static final BundlerParamInfo<Boolean> VERBOSE  =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.VERBOSE.getId(),
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );

    static final BundlerParamInfo<Boolean> SHORTCUT_HINT  =
            new BundlerParamInfo<>(
                    "shortcut-hint", // not directly related to a CLI option
                    Boolean.class,
                    params -> true,  // defaults to true
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );

    static final BundlerParamInfo<Boolean> MENU_HINT  =
            new BundlerParamInfo<>(
                    "menu-hint", // not directly related to a CLI option
                    Boolean.class,
                    params -> true,  // defaults to true
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );

    static final BundlerParamInfo<Path> RESOURCE_DIR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.RESOURCE_DIR.getId(),
                    Path.class,
                    params -> null,
                    (s, p) -> Path.of(s)
            );

    static final BundlerParamInfo<String> INSTALL_DIR =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.INSTALL_DIR.getId(),
                    String.class,
                     params -> null,
                    (s, p) -> s
    );

    static final BundlerParamInfo<Boolean> LAUNCHER_AS_SERVICE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.LAUNCHER_AS_SERVICE.getId(),
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s)
            );


    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<Map<String, ? super Object>>> ADD_LAUNCHERS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.ADD_LAUNCHER.getId(),
                    (Class<List<Map<String, ? super Object>>>) (Object)
                            List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo
            <List<Map<String, ? super Object>>> FILE_ASSOCIATIONS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.FILE_ASSOCIATIONS.getId(),
                    (Class<List<Map<String, ? super Object>>>) (Object)
                            List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<String>> FA_EXTENSIONS =
            new BundlerParamInfo<>(
                    "fileAssociation.extension",
                    (Class<List<String>>) (Object) List.class,
                    params -> null, // null means not matched to an extension
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<String>> FA_CONTENT_TYPE =
            new BundlerParamInfo<>(
                    "fileAssociation.contentType",
                    (Class<List<String>>) (Object) List.class,
                    params -> null,
                            // null means not matched to a content/mime type
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    static final BundlerParamInfo<String> FA_DESCRIPTION =
            new BundlerParamInfo<>(
                    "fileAssociation.description",
                    String.class,
                    p -> null,
                    (s, p) -> s
            );

    static final BundlerParamInfo<Path> FA_ICON =
            new BundlerParamInfo<>(
                    "fileAssociation.icon",
                    Path.class,
                    ICON::fetchFrom,
                    (s, p) -> Path.of(s)
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<Path>> DMG_CONTENT =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.DMG_CONTENT.getId(),
                    (Class<List<Path>>) (Object)List.class,
                    p -> Collections.emptyList(),
                    (s, p) -> Stream.of(s.split(",")).map(Path::of).toList()
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<Path>> APP_CONTENT =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.APP_CONTENT.getId(),
                    (Class<List<Path>>) (Object)List.class,
                    p->Collections.emptyList(),
                    (s, p) -> Stream.of(s.split(",")).map(Path::of).toList()
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<Path>> MODULE_PATH =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.MODULE_PATH.getId(),
                    (Class<List<Path>>) (Object)List.class,
                    p -> getDefaultModulePath(),
                    (s, p) -> {
                        List<Path> modulePath = Stream.of(s.split(File.pathSeparator))
                                .map(Path::of)
                                .toList();
                        Path javaBasePath = findPathOfModule(modulePath, JAVABASEJMOD);

                        // Add the default JDK module path to the module path.
                        if (javaBasePath == null) {
                            List<Path> jdkModulePath = getDefaultModulePath();

                            if (jdkModulePath != null) {
                                modulePath = Stream.concat(modulePath.stream(),
                                        jdkModulePath.stream()).toList();
                                javaBasePath = findPathOfModule(modulePath, JAVABASEJMOD);
                            }
                        }

                        if (javaBasePath == null ||
                                !Files.exists(javaBasePath)) {
                            Log.error(String.format(I18N.getString(
                                    "warning.no.jdk.modules.found")));
                        }

                        return modulePath;
                    });

    // Returns the path to the JDK modules in the user defined module path.
    private static Path findPathOfModule( List<Path> modulePath, String moduleName) {

        for (Path path : modulePath) {
            Path moduleNamePath = path.resolve(moduleName);

            if (Files.exists(moduleNamePath)) {
                return path;
            }
        }

        return null;
    }

    static final BundlerParamInfo<String> MODULE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.MODULE.getId(),
                    String.class,
                    p -> null,
                    (s, p) -> {
                        return String.valueOf(s);
                    });

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<Set<String>> ADD_MODULES =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.ADD_MODULES.getId(),
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet<String>(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<List<String>> JLINK_OPTIONS =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.JLINK_OPTIONS.getId(),
                    (Class<List<String>>) (Object) List.class,
                    p -> Arrays.asList(DEFAULT_JLINK_OPTIONS),
                    (s, p) -> null);

    @SuppressWarnings("unchecked")
    static final BundlerParamInfo<Set<String>> LIMIT_MODULES =
            new BundlerParamInfo<>(
                    "limit-modules",
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet<String>(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    static final BundlerParamInfo<Boolean> SIGN_BUNDLE =
            new BundlerParamInfo<>(
                    Arguments.CLIOptions.MAC_SIGN.getId(),
                    Boolean.class,
                    params -> false,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                    null : Boolean.valueOf(s)
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

    static boolean hasPredefinedAppImage(Map<String, ? super Object> params) {
        return params.containsKey(PREDEFINED_APP_IMAGE.getID());
    }

    static Path getPredefinedAppImage(Map<String, ? super Object> params) {
        Path applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
        if (applicationImage != null && !IOUtils.exists(applicationImage)) {
            throw new RuntimeException(
                    MessageFormat.format(I18N.getString(
                            "message.app-image-dir-does-not-exist"),
                            PREDEFINED_APP_IMAGE.getID(),
                            applicationImage.toString()));
        }
        return applicationImage;
    }

    private static String getDefaultAppVersion(Map<String, ? super Object> params) {
        String appVersion = DEFAULT_VERSION;

        if (isRuntimeInstaller(params)) {
            return appVersion;
        }

        LauncherData launcherData = null;
        try {
            launcherData = LAUNCHER_DATA.fetchFrom(params);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof ConfigException) {
                return appVersion;
            }
            throw ex;
        }

        if (launcherData.isModular()) {
            String moduleVersion = launcherData.getAppVersion();
            if (moduleVersion != null) {
                Log.verbose(MessageFormat.format(I18N.getString(
                        "message.module-version"),
                        moduleVersion,
                        launcherData.moduleName()));
                appVersion = moduleVersion;
            }
        }

        return appVersion;
    }
}
