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

import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.plugin.PluginException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import jdk.tools.jlink.internal.BasicImageWriter;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin.SymImageFile;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.Module;
import jdk.tools.jlink.plugin.Pool.ModuleData;

/**
 *
 * Default Image Builder. This builder creates the default runtime image layout.
 */
public class DefaultImageBuilder implements ImageBuilder {

    /**
     * The default java executable Image.
     */
    static class DefaultExecutableImage extends ExecutableImage {

        public DefaultExecutableImage(Path home, Set<String> modules) {
            super(home, modules, createArgs(home));
        }

        private static List<String> createArgs(Path home) {
            Objects.requireNonNull(home);
            List<String> javaArgs = new ArrayList<>();
            javaArgs.add(home.resolve("bin").
                    resolve(getJavaProcessName()).toString());
            return javaArgs;
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
    private final boolean genBom;
    private final Set<String> modules = new HashSet<>();

    /**
     * Default image builder constructor.
     *
     * @param genBom true, generates a bom file.
     * @param root The image root directory.
     * @throws IOException
     */
    public DefaultImageBuilder(boolean genBom, Path root) throws IOException {
        Objects.requireNonNull(root);

        this.genBom = genBom;

        this.root = root;
        this.mdir = root.resolve("lib");
        Files.createDirectories(mdir);
    }

    private void storeFiles(Set<String> modules, String bom, Properties release) throws IOException {
        if (release != null) {
            addModules(release, modules);
            File r = new File(root.toFile(), "release");
            try (FileOutputStream fo = new FileOutputStream(r)) {
                release.store(fo, null);
            }
        }
        // Generate bom
        if (genBom) {
            File bomFile = new File(root.toFile(), "bom");
            createUtf8File(bomFile, bom);
        }
    }

    private void addModules(Properties release, Set<String> modules) throws IOException {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (String m : modules) {
            builder.append(m);
            if (i < modules.size() - 1) {
                builder.append(",");
            }
            i++;
        }
        release.setProperty("MODULES", builder.toString());
    }

    @Override
    public void storeFiles(Pool files, String bom, Properties release) {
        try {
            for (ModuleData f : files.getContent()) {
               if (!f.getType().equals(Pool.ModuleDataType.CLASS_OR_RESOURCE)) {
                    accept(f);
                }
            }
            for (Module m : files.getModules()) {
                // Only add modules that contain packages
                if (!m.getAllPackages().isEmpty()) {
                    // Skip the fake module used by FileCopierPlugin when copying files.
                    if (m.getName().equals(FileCopierPlugin.FAKE_MODULE)) {
                       continue;
                    }
                    modules.add(m.getName());
                }
            }
            storeFiles(modules, bom, release);

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
                        return path.getFileName().toString().equals("jspawnhelper") ||
                               path.getFileName().toString().equals("jexec");
                    }).forEach(this::setExecutable);
                }
            }

            prepareApplicationFiles(files, modules);
        } catch (IOException ex) {
            throw new PluginException(ex);
        }
    }

    @Override
    public void storeFiles(Pool files, String bom) {
        storeFiles(files, bom, new Properties());
    }

    /**
     * Generates launcher scripts.
     * @param imageContent The image content.
     * @param modules The set of modules that the runtime image contains.
     * @throws IOException
     */
    protected void prepareApplicationFiles(Pool imageContent, Set<String> modules) throws IOException {
        // generate launch scripts for the modules with a main class
        for (String module : modules) {
            String path = "/" + module + "/module-info.class";
            ModuleData res = imageContent.get(path);
            if (res == null) {
                throw new IOException("module-info.class not found for " + module + " module");
            }
            Optional<String> mainClass;
            ByteArrayInputStream stream = new ByteArrayInputStream(res.getBytes());
            mainClass = ModuleDescriptor.read(stream).mainClass();
            if (mainClass.isPresent()) {
                Path cmd = root.resolve("bin").resolve(module);
                if (!Files.exists(cmd)) {
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

    private void accept(ModuleData file) throws IOException {
        String fullPath = file.getPath();
        String module = "/" + file.getModule()+ "/";
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
            return new DefaultImageBuilder.DefaultExecutableImage(root,
                    retrieveModules(root));
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
