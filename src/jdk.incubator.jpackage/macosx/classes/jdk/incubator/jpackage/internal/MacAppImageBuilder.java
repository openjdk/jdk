/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;
import static jdk.incubator.jpackage.internal.MacBaseInstallerBundler.*;
import static jdk.incubator.jpackage.internal.MacAppBundler.*;
import static jdk.incubator.jpackage.internal.OverridableResource.createResource;

public class MacAppImageBuilder extends AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MacResources");

    private static final String TEMPLATE_BUNDLE_ICON = "java.icns";
    private static final String OS_TYPE_CODE = "APPL";
    private static final String TEMPLATE_INFO_PLIST_LITE =
            "Info-lite.plist.template";
    private static final String TEMPLATE_RUNTIME_INFO_PLIST =
            "Runtime-Info.plist.template";

    private final Path root;
    private final Path contentsDir;
    private final Path appDir;
    private final Path javaModsDir;
    private final Path resourcesDir;
    private final Path macOSDir;
    private final Path runtimeDir;
    private final Path runtimeRoot;
    private final Path mdir;

    private static List<String> keyChains;

    public static final BundlerParamInfo<Boolean>
            MAC_CONFIGURE_LAUNCHER_IN_PLIST = new StandardBundlerParam<>(
                    "mac.configure-launcher-in-plist",
                    Boolean.class,
                    params -> Boolean.FALSE,
                    (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MAC_BUNDLE_NAME.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId(),
                    String.class,
                    params -> {
                        // Get identifier from app image if user provided
                        // app image and did not provide the identifier via CLI.
                        String identifier = extractBundleIdentifier(params);
                        if (identifier != null) {
                            return identifier;
                        }

                        return MacAppBundler.getIdentifier(params);
                    },
                    (s, p) -> s);

    public static final BundlerParamInfo<File> ICON_ICNS =
            new StandardBundlerParam<>(
            "icon.icns",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".icns")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-icns"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final StandardBundlerParam<Boolean> SIGN_BUNDLE  =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.MAC_SIGN.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false, we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                    null : Boolean.valueOf(s)
        );

    public MacAppImageBuilder(Map<String, Object> params, Path imageOutDir)
            throws IOException {
        super(params, imageOutDir.resolve(APP_NAME.fetchFrom(params)
                + ".app/Contents/runtime/Contents/Home"));

        Objects.requireNonNull(imageOutDir);

        this.root = imageOutDir.resolve(APP_NAME.fetchFrom(params) + ".app");
        this.contentsDir = root.resolve("Contents");
        this.appDir = contentsDir.resolve("app");
        this.javaModsDir = appDir.resolve("mods");
        this.resourcesDir = contentsDir.resolve("Resources");
        this.macOSDir = contentsDir.resolve("MacOS");
        this.runtimeDir = contentsDir.resolve("runtime");
        this.runtimeRoot = runtimeDir.resolve("Contents/Home");
        this.mdir = runtimeRoot.resolve("lib");
        Files.createDirectories(appDir);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(macOSDir);
        Files.createDirectories(runtimeDir);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    @Override
    public Path getAppDir() {
        return appDir;
    }

    @Override
    public Path getAppModsDir() {
        return javaModsDir;
    }

    @Override
    public void prepareApplicationFiles(Map<String, ? super Object> params)
            throws IOException {
        Map<String, ? super Object> originalParams = new HashMap<>(params);
        // Generate PkgInfo
        File pkgInfoFile = new File(contentsDir.toFile(), "PkgInfo");
        pkgInfoFile.createNewFile();
        writePkgInfo(pkgInfoFile);

        Path executable = macOSDir.resolve(getLauncherName(params));

        // create the main app launcher
        try (InputStream is_launcher =
                getResourceAsStream("jpackageapplauncher")) {
            // Copy executable and library to MacOS folder
            writeEntry(is_launcher, executable);
        }
        executable.toFile().setExecutable(true, false);
        // generate main app launcher config file
        File cfg = new File(root.toFile(), getLauncherCfgName(params));
        writeCfgFile(params, cfg);

        // create additional app launcher(s) and config file(s)
        List<Map<String, ? super Object>> entryPoints =
                StandardBundlerParam.ADD_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            Map<String, ? super Object> tmp =
                    AddLauncherArguments.merge(originalParams, entryPoint);

            // add executable for add launcher
            Path addExecutable = macOSDir.resolve(getLauncherName(tmp));
            try (InputStream is = getResourceAsStream("jpackageapplauncher");) {
                writeEntry(is, addExecutable);
            }
            addExecutable.toFile().setExecutable(true, false);

            // add config file for add launcher
            cfg = new File(root.toFile(), getLauncherCfgName(tmp));
            writeCfgFile(tmp, cfg);
        }

        // Copy class path entries to Java folder
        copyClassPathEntries(appDir, params);

        /*********** Take care of "config" files *******/

        createResource(TEMPLATE_BUNDLE_ICON, params)
                .setCategory("icon")
                .setExternal(ICON_ICNS.fetchFrom(params))
                .saveToFile(resourcesDir.resolve(APP_NAME.fetchFrom(params)
                        + ".icns"));

        // copy file association icons
        for (Map<String, ?
                super Object> fa : FILE_ASSOCIATIONS.fetchFrom(params)) {
            File f = FA_ICON.fetchFrom(fa);
            if (f != null && f.exists()) {
                try (InputStream in2 = new FileInputStream(f)) {
                    Files.copy(in2, resourcesDir.resolve(f.getName()));
                }

            }
        }

        copyRuntimeFiles(params);
        sign(params);
    }

    @Override
    public void prepareJreFiles(Map<String, ? super Object> params)
            throws IOException {
        copyRuntimeFiles(params);
        sign(params);
    }

    @Override
    File getRuntimeImageDir(File runtimeImageTop) {
        File home = new File(runtimeImageTop, "Contents/Home");
        return (home.exists() ? home : runtimeImageTop);
    }

    private void copyRuntimeFiles(Map<String, ? super Object> params)
            throws IOException {
        // Generate Info.plist
        writeInfoPlist(contentsDir.resolve("Info.plist").toFile(), params);

        // generate java runtime info.plist
        writeRuntimeInfoPlist(
                runtimeDir.resolve("Contents/Info.plist").toFile(), params);

        // copy library
        Path runtimeMacOSDir = Files.createDirectories(
                runtimeDir.resolve("Contents/MacOS"));

        // JDK 9, 10, and 11 have extra '/jli/' subdir
        Path jli = runtimeRoot.resolve("lib/libjli.dylib");
        if (!Files.exists(jli)) {
            jli = runtimeRoot.resolve("lib/jli/libjli.dylib");
        }

        Files.copy(jli, runtimeMacOSDir.resolve("libjli.dylib"));
    }

    private void sign(Map<String, ? super Object> params) throws IOException {
        if (Optional.ofNullable(
                SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
            try {
                addNewKeychain(params);
            } catch (InterruptedException e) {
                Log.error(e.getMessage());
            }
            String signingIdentity =
                    DEVELOPER_ID_APP_SIGNING_KEY.fetchFrom(params);
            if (signingIdentity != null) {
                prepareEntitlements(params);
                signAppBundle(params, root, signingIdentity,
                        BUNDLE_ID_SIGNING_PREFIX.fetchFrom(params),
                        getConfig_Entitlements(params));
            }
            restoreKeychainList(params);
        }
    }

    static File getConfig_Entitlements(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                getLauncherName(params) + ".entitlements");
    }

    static void prepareEntitlements(Map<String, ? super Object> params)
            throws IOException {
        createResource("entitlements.plist", params)
                .setCategory(I18N.getString("resource.entitlements"))
                .saveToFile(getConfig_Entitlements(params));
    }

    private static String getLauncherName(Map<String, ? super Object> params) {
        if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            return MAIN_CLASS.fetchFrom(params);
        }
    }

    public static String getLauncherCfgName(
            Map<String, ? super Object> params) {
        return "Contents/app/" + APP_NAME.fetchFrom(params) + ".cfg";
    }

    private void copyClassPathEntries(Path javaDirectory,
            Map<String, ? super Object> params) throws IOException {
        List<RelativeFileSet> resourcesList =
                APP_RESOURCES_LIST.fetchFrom(params);
        if (resourcesList == null) {
            throw new RuntimeException(
                    I18N.getString("message.null-classpath"));
        }

        for (RelativeFileSet classPath : resourcesList) {
            File srcdir = classPath.getBaseDirectory();
            for (String fname : classPath.getIncludedFiles()) {
                copyEntry(javaDirectory, srcdir, fname);
            }
        }
    }

    private String getBundleName(Map<String, ? super Object> params) {
        if (MAC_CF_BUNDLE_NAME.fetchFrom(params) != null) {
            String bn = MAC_CF_BUNDLE_NAME.fetchFrom(params);
            if (bn.length() > 16) {
                Log.error(MessageFormat.format(I18N.getString(
                        "message.bundle-name-too-long-warning"),
                        MAC_CF_BUNDLE_NAME.getID(), bn));
            }
            return MAC_CF_BUNDLE_NAME.fetchFrom(params);
        } else if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            String nm = MAIN_CLASS.fetchFrom(params);
            if (nm.length() > 16) {
                nm = nm.substring(0, 16);
            }
            return nm;
        }
    }

    private void writeRuntimeInfoPlist(File file,
            Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();
        String identifier = StandardBundlerParam.isRuntimeInstaller(params) ?
                MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params) :
                "com.oracle.java." + MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params);
        data.put("CF_BUNDLE_IDENTIFIER", identifier);
        String name = StandardBundlerParam.isRuntimeInstaller(params) ?
                getBundleName(params): "Java Runtime Image";
        data.put("CF_BUNDLE_NAME", name);
        data.put("CF_BUNDLE_VERSION", VERSION.fetchFrom(params));
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", VERSION.fetchFrom(params));

        createResource(TEMPLATE_RUNTIME_INFO_PLIST, params)
                .setPublicName("Runtime-Info.plist")
                .setCategory(I18N.getString("resource.runtime-info-plist"))
                .setSubstitutionData(data)
                .saveToFile(file);
    }

    private void writeInfoPlist(File file, Map<String, ? super Object> params)
            throws IOException {
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-info-plist"), file.getAbsolutePath()));

        //prepare config for exe
        //Note: do not need CFBundleDisplayName if we don't support localization
        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ICON_FILE", APP_NAME.fetchFrom(params) + ".icns");
        data.put("DEPLOY_BUNDLE_IDENTIFIER",
                MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_NAME",
                getBundleName(params));
        data.put("DEPLOY_BUNDLE_COPYRIGHT", COPYRIGHT.fetchFrom(params));
        data.put("DEPLOY_LAUNCHER_NAME", getLauncherName(params));
        data.put("DEPLOY_BUNDLE_SHORT_VERSION", VERSION.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION", VERSION.fetchFrom(params));

        boolean hasMainJar = MAIN_JAR.fetchFrom(params) != null;
        boolean hasMainModule =
                StandardBundlerParam.MODULE.fetchFrom(params) != null;

        if (hasMainJar) {
            data.put("DEPLOY_MAIN_JAR_NAME", MAIN_JAR.fetchFrom(params).
                    getIncludedFiles().iterator().next());
        }
        else if (hasMainModule) {
            data.put("DEPLOY_MODULE_NAME",
                    StandardBundlerParam.MODULE.fetchFrom(params));
        }

        StringBuilder sb = new StringBuilder();
        List<String> jvmOptions = JAVA_OPTIONS.fetchFrom(params);

        String newline = ""; //So we don't add extra line after last append
        for (String o : jvmOptions) {
            sb.append(newline).append(
                    "    <string>").append(o).append("</string>");
            newline = "\n";
        }

        data.put("DEPLOY_JAVA_OPTIONS", sb.toString());

        sb = new StringBuilder();
        List<String> args = ARGUMENTS.fetchFrom(params);
        newline = "";
        // So we don't add unneccessary extra line after last append

        for (String o : args) {
            sb.append(newline).append("    <string>").append(o).append(
                    "</string>");
            newline = "\n";
        }
        data.put("DEPLOY_ARGUMENTS", sb.toString());

        newline = "";

        data.put("DEPLOY_LAUNCHER_CLASS", MAIN_CLASS.fetchFrom(params));

        data.put("DEPLOY_APP_CLASSPATH",
                  getCfgClassPath(CLASSPATH.fetchFrom(params)));

        StringBuilder bundleDocumentTypes = new StringBuilder();
        StringBuilder exportedTypes = new StringBuilder();
        for (Map<String, ? super Object>
                fileAssociation : FILE_ASSOCIATIONS.fetchFrom(params)) {

            List<String> extensions = FA_EXTENSIONS.fetchFrom(fileAssociation);

            if (extensions == null) {
                Log.verbose(I18N.getString(
                        "message.creating-association-with-null-extension"));
            }

            List<String> mimeTypes = FA_CONTENT_TYPE.fetchFrom(fileAssociation);
            String itemContentType = MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params)
                    + "." + ((extensions == null || extensions.isEmpty())
                    ? "mime" : extensions.get(0));
            String description = FA_DESCRIPTION.fetchFrom(fileAssociation);
            File icon = FA_ICON.fetchFrom(fileAssociation);

            bundleDocumentTypes.append("    <dict>\n")
                    .append("      <key>LSItemContentTypes</key>\n")
                    .append("      <array>\n")
                    .append("        <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("      </array>\n")
                    .append("\n")
                    .append("      <key>CFBundleTypeName</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>LSHandlerRank</key>\n")
                    .append("      <string>Owner</string>\n")
                            // TODO make a bundler arg
                    .append("\n")
                    .append("      <key>CFBundleTypeRole</key>\n")
                    .append("      <string>Editor</string>\n")
                            // TODO make a bundler arg
                    .append("\n")
                    .append("      <key>LSIsAppleDefaultForType</key>\n")
                    .append("      <true/>\n")
                            // TODO make a bundler arg
                    .append("\n");

            if (icon != null && icon.exists()) {
                bundleDocumentTypes
                        .append("      <key>CFBundleTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n");
            }
            bundleDocumentTypes.append("    </dict>\n");

            exportedTypes.append("    <dict>\n")
                    .append("      <key>UTTypeIdentifier</key>\n")
                    .append("      <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>UTTypeDescription</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("      <key>UTTypeConformsTo</key>\n")
                    .append("      <array>\n")
                    .append("          <string>public.data</string>\n")
                            //TODO expose this?
                    .append("      </array>\n")
                    .append("\n");

            if (icon != null && icon.exists()) {
                exportedTypes.append("      <key>UTTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n")
                        .append("\n");
            }

            exportedTypes.append("\n")
                    .append("      <key>UTTypeTagSpecification</key>\n")
                    .append("      <dict>\n")
                            // TODO expose via param? .append(
                            // "        <key>com.apple.ostype</key>\n");
                            // TODO expose via param? .append(
                            // "        <string>ABCD</string>\n")
                    .append("\n");

            if (extensions != null && !extensions.isEmpty()) {
                exportedTypes.append(
                        "        <key>public.filename-extension</key>\n")
                        .append("        <array>\n");

                for (String ext : extensions) {
                    exportedTypes.append("          <string>")
                            .append(ext)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            if (mimeTypes != null && !mimeTypes.isEmpty()) {
                exportedTypes.append("        <key>public.mime-type</key>\n")
                        .append("        <array>\n");

                for (String mime : mimeTypes) {
                    exportedTypes.append("          <string>")
                            .append(mime)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            exportedTypes.append("      </dict>\n")
                    .append("    </dict>\n");
        }
        String associationData;
        if (bundleDocumentTypes.length() > 0) {
            associationData =
                    "\n  <key>CFBundleDocumentTypes</key>\n  <array>\n"
                    + bundleDocumentTypes.toString()
                    + "  </array>\n\n"
                    + "  <key>UTExportedTypeDeclarations</key>\n  <array>\n"
                    + exportedTypes.toString()
                    + "  </array>\n";
        } else {
            associationData = "";
        }
        data.put("DEPLOY_FILE_ASSOCIATIONS", associationData);

        createResource(TEMPLATE_INFO_PLIST_LITE, params)
                .setCategory(I18N.getString("resource.app-info-plist"))
                .setSubstitutionData(data)
                .setPublicName("Info.plist")
                .saveToFile(file);
    }

    private void writePkgInfo(File file) throws IOException {
        //hardcoded as it does not seem we need to change it ever
        String signature = "????";

        try (Writer out = Files.newBufferedWriter(file.toPath())) {
            out.write(OS_TYPE_CODE + signature);
            out.flush();
        }
    }

    public static void addNewKeychain(Map<String, ? super Object> params)
                                    throws IOException, InterruptedException {
        if (Platform.getMajorVersion() < 10 ||
                (Platform.getMajorVersion() == 10 &&
                Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);
        if (keyChain == null || keyChain.isEmpty()) {
            return;
        }

        // get current keychain list
        String keyChainPath = new File (keyChain).getAbsolutePath().toString();
        List<String> keychainList = new ArrayList<>();
        int ret = IOUtils.getProcessOutput(
                keychainList, "security", "list-keychains");
        if (ret != 0) {
            Log.error(I18N.getString("message.keychain.error"));
            return;
        }

        boolean contains = keychainList.stream().anyMatch(
                    str -> str.trim().equals("\""+keyChainPath.trim()+"\""));
        if (contains) {
            // keychain is already added in the search list
            return;
        }

        keyChains = new ArrayList<>();
        // remove "
        keychainList.forEach((String s) -> {
            String path = s.trim();
            if (path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length()-1);
            }
            keyChains.add(path);
        });

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);
        args.add(keyChain);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb);
    }

    public static void restoreKeychainList(Map<String, ? super Object> params)
            throws IOException{
        if (Platform.getMajorVersion() < 10 ||
                (Platform.getMajorVersion() == 10 &&
                Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        if (keyChains == null || keyChains.isEmpty()) {
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb);
    }

    static void signAppBundle(
            Map<String, ? super Object> params, Path appLocation,
            String signingIdentity, String identifierPrefix, File entitlements)
            throws IOException {
        AtomicReference<IOException> toThrow = new AtomicReference<>();
        String appExecutable = "/Contents/MacOS/" + APP_NAME.fetchFrom(params);
        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);

        // sign all dylibs and executables
        try (Stream<Path> stream = Files.walk(appLocation)) {
            stream.peek(path -> { // fix permissions
                try {
                    Set<PosixFilePermission> pfp =
                            Files.getPosixFilePermissions(path);
                    if (!pfp.contains(PosixFilePermission.OWNER_WRITE)) {
                        pfp = EnumSet.copyOf(pfp);
                        pfp.add(PosixFilePermission.OWNER_WRITE);
                        Files.setPosixFilePermissions(path, pfp);
                    }
                } catch (IOException e) {
                    Log.verbose(e);
                }
            }).filter(p -> Files.isRegularFile(p) &&
                      (Files.isExecutable(p) || p.toString().endsWith(".dylib"))
                      && !(p.toString().endsWith(appExecutable)
                      || p.toString().contains("/Contents/runtime")
                      || p.toString().contains("/Contents/Frameworks"))
                     ).forEach(p -> {
                // noinspection ThrowableResultOfMethodCallIgnored
                if (toThrow.get() != null) return;

                // If p is a symlink then skip the signing process.
                if (Files.isSymbolicLink(p)) {
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.ignoring.symlink"), p.toString()));
                } else if (isFileSigned(p)) {
                    // executable or lib already signed
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.already.signed"), p.toString()));
                } else {
                    List<String> args = new ArrayList<>();
                    args.addAll(Arrays.asList("codesign",
                            "--timestamp",
                            "--options", "runtime",
                            "-s", signingIdentity,
                            "--prefix", identifierPrefix,
                            "-vvvv"));
                    if (keyChain != null && !keyChain.isEmpty()) {
                        args.add("--keychain");
                        args.add(keyChain);
                    }

                    if (Files.isExecutable(p)) {
                        if (entitlements != null) {
                            args.add("--entitlements");
                            args.add(entitlements.toString());
                        }
                    }

                    args.add(p.toString());

                    try {
                        Set<PosixFilePermission> oldPermissions =
                                Files.getPosixFilePermissions(p);
                        File f = p.toFile();
                        f.setWritable(true, true);

                        ProcessBuilder pb = new ProcessBuilder(args);

                        IOUtils.exec(pb);

                        Files.setPosixFilePermissions(p, oldPermissions);
                    } catch (IOException ioe) {
                        toThrow.set(ioe);
                    }
                }
            });
        }
        IOException ioe = toThrow.get();
        if (ioe != null) {
            throw ioe;
        }

        // sign all runtime and frameworks
        Consumer<? super Path> signIdentifiedByPList = path -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            try {
                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "--timestamp",
                        "--options", "runtime",
                        "--force",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix,
                        // use the identifier as a prefix
                        "-vvvv"));

                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(path.toString());
                ProcessBuilder pb = new ProcessBuilder(args);

                IOUtils.exec(pb);
            } catch (IOException e) {
                toThrow.set(e);
            }
        };

        Path javaPath = appLocation.resolve("Contents/runtime");
        if (Files.isDirectory(javaPath)) {
            signIdentifiedByPList.accept(javaPath);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }
        Path frameworkPath = appLocation.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            Files.list(frameworkPath)
                    .forEach(signIdentifiedByPList);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }

        // sign the app itself
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("codesign",
                "--timestamp",
                "--options", "runtime",
                "--force",
                "-s", signingIdentity,
                "-vvvv"));

        if (keyChain != null && !keyChain.isEmpty()) {
            args.add("--keychain");
            args.add(keyChain);
        }

        if (entitlements != null) {
            args.add("--entitlements");
            args.add(entitlements.toString());
        }

        args.add(appLocation.toString());

        ProcessBuilder pb =
                new ProcessBuilder(args.toArray(new String[args.size()]));

        IOUtils.exec(pb);
    }

    private static boolean isFileSigned(Path file) {
        ProcessBuilder pb =
                new ProcessBuilder("codesign", "--verify", file.toString());

        try {
            IOUtils.exec(pb);
        } catch (IOException ex) {
            return false;
        }

        return true;
    }

    private static String extractBundleIdentifier(Map<String, Object> params) {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            return null;
        }

        try {
            File infoPList = new File(PREDEFINED_APP_IMAGE.fetchFrom(params) +
                                      File.separator + "Contents" +
                                      File.separator + "Info.plist");

            DocumentBuilderFactory dbf
                    = DocumentBuilderFactory.newDefaultInstance();
            dbf.setFeature("http://apache.org/xml/features/" +
                           "nonvalidating/load-external-dtd", false);
            DocumentBuilder b = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = b.parse(new FileInputStream(
                    infoPList.getAbsolutePath()));

            XPath xPath = XPathFactory.newInstance().newXPath();
            // Query for the value of <string> element preceding <key>
            // element with value equal to CFBundleIdentifier
            String v = (String) xPath.evaluate(
                    "//string[preceding-sibling::key = \"CFBundleIdentifier\"][1]",
                    doc, XPathConstants.STRING);

            if (v != null && !v.isEmpty()) {
                return v;
            }
        } catch (Exception ex) {
            Log.verbose(ex);
        }

        return null;
    }

}
