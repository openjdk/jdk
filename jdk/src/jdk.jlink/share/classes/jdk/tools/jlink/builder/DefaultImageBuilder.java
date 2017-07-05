
/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.builder;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import jdk.tools.jlink.internal.BasicImageWriter;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin.SymImageFile;
import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.PluginException;

/**
 *
 * Default Image Builder. This builder creates the default runtime image layout.
 */
public final class DefaultImageBuilder implements ImageBuilder {

    /**
     * The default java executable Image.
     */
    static final class DefaultExecutableImage implements ExecutableImage {

        private final Path home;
        private final List<String> args;
        private final Set<String> modules;

        public DefaultExecutableImage(Path home, Set<String> modules) {
            this(home, modules, createArgs(home));
        }

        private DefaultExecutableImage(Path home, Set<String> modules,
                List<String> args) {
            Objects.requireNonNull(home);
            Objects.requireNonNull(args);
            if (!Files.exists(home)) {
                throw new IllegalArgumentException("Invalid image home");
            }
            this.home = home;
            this.modules = Collections.unmodifiableSet(modules);
            this.args = Collections.unmodifiableList(args);
        }

        private static List<String> createArgs(Path home) {
            Objects.requireNonNull(home);
            List<String> javaArgs = new ArrayList<>();
            javaArgs.add(home.resolve("bin").
                    resolve(getJavaProcessName()).toString());
            return javaArgs;
        }

        @Override
        public Path getHome() {
            return home;
        }

        @Override
        public Set<String> getModules() {
            return modules;
        }

        @Override
        public List<String> getExecutionArgs() {
            return args;
        }

        @Override
        public void storeLaunchArgs(List<String> args) {
            try {
                patchScripts(this, args);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private final Path root;
    private final Path mdir;
    private final Set<String> modules = new HashSet<>();

    /**
     * Default image builder constructor.
     *
     * @param root The image root directory.
     * @throws IOException
     */
    public DefaultImageBuilder(Path root) throws IOException {
        Objects.requireNonNull(root);

        this.root = root;
        this.mdir = root.resolve("lib");
        Files.createDirectories(mdir);
    }

    private void storeFiles(Set<String> modules, Map<String, String> release) throws IOException {
        if (release != null) {
            Properties props = new Properties();
            props.putAll(release);
            addModules(props, modules);
            File r = new File(root.toFile(), "release");
            try (FileOutputStream fo = new FileOutputStream(r)) {
                props.store(fo, null);
            }
        }
    }

    private void addModules(Properties props, Set<String> modules) throws IOException {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (String m : modules) {
            builder.append(m);
            if (i < modules.size() - 1) {
                builder.append(",");
            }
            i++;
        }
        props.setProperty("MODULES", builder.toString());
    }

    @Override
    public void storeFiles(ModulePool files) {
        try {
            files.entries().forEach(f -> {
                if (!f.getType().equals(ModuleEntry.Type.CLASS_OR_RESOURCE)) {
                    try {
                        accept(f);
                    } catch (IOException ioExp) {
                        throw new UncheckedIOException(ioExp);
                    }
                }
            });
            files.modules().forEach(m -> {
                // Only add modules that contain packages
                if (!m.getAllPackages().isEmpty()) {
                    // Skip the fake module used by FileCopierPlugin when copying files.
                    if (m.getName().equals(FileCopierPlugin.FAKE_MODULE)) {
                        return;
                    }
                    modules.add(m.getName());
                }
            });
            storeFiles(modules, files.getReleaseProperties());

            if (Files.getFileStore(root).supportsFileAttributeView(PosixFileAttributeView.class)) {
                // launchers in the bin directory need execute permission
                Path bin = root.resolve("bin");
                if (Files.isDirectory(bin)) {
                    Files.list(bin)
                            .filter(f -> !f.toString().endsWith(".diz"))
                            .filter(f -> Files.isRegularFile(f))
                            .forEach(this::setExecutable);
                }

                // jspawnhelper is in lib or lib/<arch>
                Path lib = root.resolve("lib");
                if (Files.isDirectory(lib)) {
                    Files.find(lib, 2, (path, attrs) -> {
                        return path.getFileName().toString().equals("jspawnhelper")
                                || path.getFileName().toString().equals("jexec");
                    }).forEach(this::setExecutable);
                }
            }

            prepareApplicationFiles(files, modules);
        } catch (IOException ex) {
            throw new PluginException(ex);
        }
    }

    /**
     * Generates launcher scripts.
     *
     * @param imageContent The image content.
     * @param modules The set of modules that the runtime image contains.
     * @throws IOException
     */
    protected void prepareApplicationFiles(ModulePool imageContent, Set<String> modules) throws IOException {
        // generate launch scripts for the modules with a main class
        for (String module : modules) {
            String path = "/" + module + "/module-info.class";
            Optional<ModuleEntry> res = imageContent.findEntry(path);
            if (!res.isPresent()) {
                throw new IOException("module-info.class not found for " + module + " module");
            }
            Optional<String> mainClass;
            ByteArrayInputStream stream = new ByteArrayInputStream(res.get().getBytes());
            mainClass = ModuleDescriptor.read(stream).mainClass();
            if (mainClass.isPresent()) {
                Path cmd = root.resolve("bin").resolve(module);
                // generate shell script for Unix platforms
                StringBuilder sb = new StringBuilder();
                sb.append("#!/bin/sh")
                        .append("\n");
                sb.append("JLINK_VM_OPTIONS=")
                        .append("\n");
                sb.append("DIR=`dirname $0`")
                        .append("\n");
                sb.append("$DIR/java $JLINK_VM_OPTIONS -m ")
                        .append(module).append('/')
                        .append(mainClass.get())
                        .append(" $@\n");

                try (BufferedWriter writer = Files.newBufferedWriter(cmd,
                        StandardCharsets.ISO_8859_1,
                        StandardOpenOption.CREATE_NEW)) {
                    writer.write(sb.toString());
                }
                if (Files.getFileStore(root.resolve("bin"))
                        .supportsFileAttributeView(PosixFileAttributeView.class)) {
                    setExecutable(cmd);
                }
                // generate .bat file for Windows
                if (isWindows()) {
                    Path bat = root.resolve("bin").resolve(module + ".bat");
                    sb = new StringBuilder();
                    sb.append("@echo off")
                            .append("\r\n");
                    sb.append("set JLINK_VM_OPTIONS=")
                            .append("\r\n");
                    sb.append("set DIR=%~dp0")
                            .append("\r\n");
                    sb.append("\"%DIR%\\java\" %JLINK_VM_OPTIONS% -m ")
                            .append(module).append('/')
                            .append(mainClass.get())
                            .append(" %*\r\n");

                    try (BufferedWriter writer = Files.newBufferedWriter(bat,
                            StandardCharsets.ISO_8859_1,
                            StandardOpenOption.CREATE_NEW)) {
                        writer.write(sb.toString());
                    }
                }
            }
        }
    }

    @Override
    public DataOutputStream getJImageOutputStream() {
        try {
            Path jimageFile = mdir.resolve(BasicImageWriter.MODULES_IMAGE_NAME);
            OutputStream fos = Files.newOutputStream(jimageFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            return new DataOutputStream(bos);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void accept(ModuleEntry file) throws IOException {
        String fullPath = file.getPath();
        String module = "/" + file.getModule() + "/";
        String filename = fullPath.substring(module.length());
        // Remove radical native|config|...
        filename = filename.substring(filename.indexOf('/') + 1);
        try (InputStream in = file.stream()) {
            switch (file.getType()) {
                case NATIVE_LIB:
                    writeEntry(in, destFile(nativeDir(filename), filename));
                    break;
                case NATIVE_CMD:
                    Path path = destFile("bin", filename);
                    writeEntry(in, path);
                    path.toFile().setExecutable(true);
                    break;
                case CONFIG:
                    writeEntry(in, destFile("conf", filename));
                    break;
                case OTHER:
                    if (file instanceof SymImageFile) {
                        SymImageFile sym = (SymImageFile) file;
                        Path target = root.resolve(sym.getTargetPath());
                        if (!Files.exists(target)) {
                            throw new IOException("Sym link target " + target
                                    + " doesn't exist");
                        }
                        writeSymEntry(root.resolve(filename), target);
                    } else {
                        writeEntry(in, root.resolve(filename));
                    }
                    break;
                default:
                    throw new InternalError("unexpected entry: " + fullPath);
            }
        }
    }

    private Path destFile(String dir, String filename) {
        return root.resolve(dir).resolve(filename);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Objects.requireNonNull(in);
        Objects.requireNonNull(dstFile);
        Files.createDirectories(Objects.requireNonNull(dstFile.getParent()));
        Files.copy(in, dstFile);
    }

    private void writeSymEntry(Path dstFile, Path target) throws IOException {
        Objects.requireNonNull(dstFile);
        Objects.requireNonNull(target);
        Files.createDirectories(Objects.requireNonNull(dstFile.getParent()));
        Files.createLink(dstFile, target);
    }

    private static String nativeDir(String filename) {
        if (isWindows()) {
            if (filename.endsWith(".dll") || filename.endsWith(".diz")
                    || filename.endsWith(".pdb") || filename.endsWith(".map")) {
                return "bin";
            } else {
                return "lib";
            }
        } else {
            return "lib";
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    /**
     * chmod ugo+x file
     */
    private void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static void createUtf8File(File file, String content) throws IOException {
        try (OutputStream fout = new FileOutputStream(file);
                Writer output = new OutputStreamWriter(fout, "UTF-8")) {
            output.write(content);
        }
    }

    @Override
    public ExecutableImage getExecutableImage() {
        return new DefaultExecutableImage(root, modules);
    }

    // This is experimental, we should get rid-off the scripts in a near future
    private static void patchScripts(ExecutableImage img, List<String> args) throws IOException {
        Objects.requireNonNull(args);
        if (!args.isEmpty()) {
            Files.find(img.getHome().resolve("bin"), 2, (path, attrs) -> {
                return img.getModules().contains(path.getFileName().toString());
            }).forEach((p) -> {
                try {
                    String pattern = "JLINK_VM_OPTIONS=";
                    byte[] content = Files.readAllBytes(p);
                    String str = new String(content, StandardCharsets.UTF_8);
                    int index = str.indexOf(pattern);
                    StringBuilder builder = new StringBuilder();
                    if (index != -1) {
                        builder.append(str.substring(0, index)).
                                append(pattern);
                        for (String s : args) {
                            builder.append(s).append(" ");
                        }
                        String remain = str.substring(index + pattern.length());
                        builder.append(remain);
                        str = builder.toString();
                        try (BufferedWriter writer = Files.newBufferedWriter(p,
                                StandardCharsets.ISO_8859_1,
                                StandardOpenOption.WRITE)) {
                            writer.write(str);
                        }
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    private static String getJavaProcessName() {
        return isWindows() ? "java.exe" : "java";
    }

    public static ExecutableImage getExecutableImage(Path root) {
        if (Files.exists(root.resolve("bin").resolve(getJavaProcessName()))) {
            return new DefaultExecutableImage(root, retrieveModules(root));
        }
        return null;
    }

    private static Set<String> retrieveModules(Path root) {
        Path releaseFile = root.resolve("release");
        Set<String> modules = new HashSet<>();
        if (Files.exists(releaseFile)) {
            Properties release = new Properties();
            try (FileInputStream fi = new FileInputStream(releaseFile.toFile())) {
                release.load(fi);
            } catch (IOException ex) {
                System.err.println("Can't read release file " + ex);
            }
            String mods = release.getProperty("MODULES");
            if (mods != null) {
                String[] arr = mods.split(",");
                for (String m : arr) {
                    modules.add(m.trim());
                }

            }
        }
        return modules;
    }
}
