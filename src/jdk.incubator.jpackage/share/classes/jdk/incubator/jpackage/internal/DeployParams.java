/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * DeployParams
 *
 * This class is generated and used in Arguments.processArguments() as
 * intermediate step in generating the BundleParams and ultimately the Bundles
 */
public class DeployParams {

    String targetFormat = null; // means default type for this platform

    File outdir = null;

    // raw arguments to the bundler
    Map<String, ? super Object> bundlerArguments = new LinkedHashMap<>();

    public void setOutput(File output) {
        outdir = output;
    }

    static class Template {
        File in;
        File out;

        Template(File in, File out) {
            this.in = in;
            this.out = out;
        }
    }

    // we need to expand as in some cases
    // (most notably jpackage)
    // we may get "." as filename and assumption is we include
    // everything in the given folder
    // (IOUtils.copyfiles() have recursive behavior)
    List<File> expandFileset(File root) {
        List<File> files = new LinkedList<>();
        if (!Files.isSymbolicLink(root.toPath())) {
            if (root.isDirectory()) {
                File[] children = root.listFiles();
                if (children != null && children.length > 0) {
                    for (File f : children) {
                        files.addAll(expandFileset(f));
                    }
                } else {
                    // Include empty folders
                    files.add(root);
                }
            } else {
                files.add(root);
            }
        }
        return files;
    }

    static void validateName(String s, boolean forApp)
            throws PackagerException {

        String exceptionKey = forApp ?
            "ERR_InvalidAppName" : "ERR_InvalidSLName";

        if (s == null) {
            if (forApp) {
                return;
            } else {
                throw new PackagerException(exceptionKey, s);
            }
        }
        if (s.length() == 0 || s.charAt(s.length() - 1) == '\\') {
            throw new PackagerException(exceptionKey, s);
        }
        try {
            // name must be valid path element for this file system
            Path p = (new File(s)).toPath();
            // and it must be a single name element in a path
            if (p.getNameCount() != 1) {
                throw new PackagerException(exceptionKey, s);
            }
        } catch (InvalidPathException ipe) {
            throw new PackagerException(ipe, exceptionKey, s);
        }

        for (int i = 0; i < s.length(); i++) {
            char a = s.charAt(i);
            // We check for ASCII codes first which we accept. If check fails,
            // check if it is acceptable extended ASCII or unicode character.
            if (a < ' ' || a > '~') {
                // Accept anything else including special chars like copyright
                // symbols. Note: space will be included by ASCII check above,
                // but other whitespace like tabs or new line will be rejected.
                if (Character.isISOControl(a)  ||
                        Character.isWhitespace(a)) {
                    throw new PackagerException(exceptionKey, s);
                }
            } else if (a == '"' || a == '%') {
                throw new PackagerException(exceptionKey, s);
            }
        }
    }

    public void validate() throws PackagerException {
        boolean hasModule = (bundlerArguments.get(
                Arguments.CLIOptions.MODULE.getId()) != null);
        boolean hasAppImage = (bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId()) != null);
        boolean hasClass = (bundlerArguments.get(
                Arguments.CLIOptions.APPCLASS.getId()) != null);
        boolean hasMain = (bundlerArguments.get(
                Arguments.CLIOptions.MAIN_JAR.getId()) != null);
        boolean hasRuntimeImage = (bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId()) != null);
        boolean hasInput = (bundlerArguments.get(
                Arguments.CLIOptions.INPUT.getId()) != null);
        boolean hasModulePath = (bundlerArguments.get(
                Arguments.CLIOptions.MODULE_PATH.getId()) != null);
        boolean runtimeInstaller = !isTargetAppImage() &&
                !hasAppImage && !hasModule && !hasMain && hasRuntimeImage;

        if (isTargetAppImage()) {
            // Module application requires --runtime-image or --module-path
            if (hasModule) {
                if (!hasModulePath && !hasRuntimeImage) {
                    throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image or --module-path");
                }
            } else {
                if (!hasInput) {
                    throw new PackagerException(
                           "ERR_MissingArgument", "--input");
                }
            }
        } else {
            if (!runtimeInstaller) {
                if (hasModule) {
                    if (!hasModulePath && !hasRuntimeImage && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image, --module-path or --app-image");
                    }
                } else {
                    if (!hasInput && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument",
                                "--input or --app-image");
                    }
                }
            }
        }

        // if bundling non-modular image, or installer without app-image
        // then we need some resources and a main class
        if (!hasModule && !hasAppImage && !runtimeInstaller && !hasMain) {
            throw new PackagerException("ERR_MissingArgument", "--main-jar");
        }

        String name = (String)bundlerArguments.get(
                Arguments.CLIOptions.NAME.getId());
        validateName(name, true);

        // Validate app image if set
        String appImage = (String)bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId());
        if (appImage != null) {
            File appImageDir = new File(appImage);
            if (!appImageDir.exists() || appImageDir.list().length == 0) {
                throw new PackagerException("ERR_AppImageNotExist", appImage);
            }
        }

        // Validate temp dir
        String root = (String)bundlerArguments.get(
                Arguments.CLIOptions.TEMP_ROOT.getId());
        if (root != null) {
            String [] contents = (new File(root)).list();

            if (contents != null && contents.length > 0) {
                throw new PackagerException("ERR_BuildRootInvalid", root);
            }
        }

        // Validate resource dir
        String resources = (String)bundlerArguments.get(
                Arguments.CLIOptions.RESOURCE_DIR.getId());
        if (resources != null) {
            if (!(new File(resources)).exists()) {
                throw new PackagerException(
                    "message.resource-dir-does-not-exist",
                    Arguments.CLIOptions.RESOURCE_DIR.getId(), resources);
            }
        }

        // Validate predefined runtime dir
        String runtime = (String)bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId());
        if (runtime != null) {
            if (!(new File(runtime)).exists()) {
                throw new PackagerException(
                    "message.runtime-image-dir-does-not-exist",
                    Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId(),
                    runtime);
            }
        }


        // Validate license file if set
        String license = (String)bundlerArguments.get(
                Arguments.CLIOptions.LICENSE_FILE.getId());
        if (license != null) {
            File licenseFile = new File(license);
            if (!licenseFile.exists()) {
                throw new PackagerException("ERR_LicenseFileNotExit");
            }
        }

        // Validate icon file if set
        String icon = (String)bundlerArguments.get(
                Arguments.CLIOptions.ICON.getId());
        if (icon != null) {
            File iconFile = new File(icon);
            if (!iconFile.exists()) {
                throw new PackagerException("ERR_IconFileNotExit",
                        iconFile.getAbsolutePath());
            }
        }
    }

    void setTargetFormat(String t) {
        targetFormat = t;
    }

    String getTargetFormat() {
        return targetFormat;
    }

    boolean isTargetAppImage() {
        return ("app-image".equals(targetFormat));
    }

    private static final Set<String> multi_args = new TreeSet<>(Arrays.asList(
            StandardBundlerParam.JAVA_OPTIONS.getID(),
            StandardBundlerParam.ARGUMENTS.getID(),
            StandardBundlerParam.MODULE_PATH.getID(),
            StandardBundlerParam.ADD_MODULES.getID(),
            StandardBundlerParam.LIMIT_MODULES.getID(),
            StandardBundlerParam.FILE_ASSOCIATIONS.getID(),
            StandardBundlerParam.JLINK_OPTIONS.getID()
    ));

    @SuppressWarnings("unchecked")
    public void addBundleArgument(String key, Object value) {
        // special hack for multi-line arguments
        if (multi_args.contains(key)) {
            Object existingValue = bundlerArguments.get(key);
            if (existingValue instanceof String && value instanceof String) {
                String delim = "\n\n";
                if (key.equals(StandardBundlerParam.MODULE_PATH.getID())) {
                    delim = File.pathSeparator;
                } else if (key.equals(
                        StandardBundlerParam.ADD_MODULES.getID())) {
                    delim = ",";
                }
                bundlerArguments.put(key, existingValue + delim + value);
            } else if (existingValue instanceof List && value instanceof List) {
                ((List)existingValue).addAll((List)value);
            } else if (existingValue instanceof Map &&
                value instanceof String && ((String)value).contains("=")) {
                String[] mapValues = ((String)value).split("=", 2);
                ((Map)existingValue).put(mapValues[0], mapValues[1]);
            } else {
                bundlerArguments.put(key, value);
            }
        } else {
            bundlerArguments.put(key, value);
        }
    }

    BundleParams getBundleParams() {
        BundleParams bundleParams = new BundleParams();

        Map<String, String> unescapedHtmlParams = new TreeMap<>();
        Map<String, String> escapedHtmlParams = new TreeMap<>();

        // check for collisions
        TreeSet<String> keys = new TreeSet<>(bundlerArguments.keySet());
        keys.retainAll(bundleParams.getBundleParamsAsMap().keySet());

        if (!keys.isEmpty()) {
            throw new RuntimeException("Deploy Params and Bundler Arguments "
                    + "overlap in the following values:" + keys.toString());
        }

        bundleParams.addAllBundleParams(bundlerArguments);

        return bundleParams;
    }

    @Override
    public String toString() {
        return "DeployParams {" + "output: " + "}";
    }

}
