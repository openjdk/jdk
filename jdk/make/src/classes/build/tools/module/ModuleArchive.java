/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.module;

import jdk.internal.jimage.Archive;
import jdk.internal.jimage.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * An Archive backed by an exploded representation on disk.
 */
public class ModuleArchive implements Archive {
    private final Path classes;
    private final Path cmds;
    private final Path libs;
    private final Path configs;
    private final String moduleName;

    public ModuleArchive(String moduleName, Path classes, Path cmds,
                         Path libs, Path configs) {
        this.moduleName = moduleName;
        this.classes = classes;
        this.cmds = cmds;
        this.libs = libs;
        this.configs = configs;
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public void visitResources(Consumer<Resource> consumer) {
        if (classes == null)
            return;
        try{
            Files.walk(classes)
                    .sorted()
                    .filter(p -> !Files.isDirectory(p)
                            && !classes.relativize(p).toString().startsWith("_the.")
                            && !classes.relativize(p).toString().equals("javac_state"))
                    .map(this::toResource)
                    .forEach(consumer::accept);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private Resource toResource(Path path) {
        try {
            return new Resource(classes.relativize(path).toString().replace('\\','/'),
                                Files.size(path),
                                0 /* no compression support yet */);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private enum Section {
        CLASSES,
        CMDS,
        LIBS,
        CONFIGS
    }

    @Override
    public void visitEntries(Consumer<Entry> consumer) {
        try{
            if (classes != null)
                Files.walk(classes)
                        .sorted()
                        .filter(p -> !Files.isDirectory(p)
                                && !classes.relativize(p).toString().startsWith("_the.")
                                && !classes.relativize(p).toString().equals("javac_state"))
                        .map(p -> toEntry(p, classes, Section.CLASSES))
                        .forEach(consumer::accept);
            if (cmds != null)
                Files.walk(cmds)
                        .filter(p -> !Files.isDirectory(p))
                        .map(p -> toEntry(p, cmds, Section.CMDS))
                        .forEach(consumer::accept);
            if (libs != null)
                Files.walk(libs)
                        .filter(p -> !Files.isDirectory(p))
                        .map(p -> toEntry(p, libs, Section.LIBS))
                        .forEach(consumer::accept);
            if (configs != null)
                Files.walk(configs)
                        .filter(p -> !Files.isDirectory(p))
                        .map(p -> toEntry(p, configs, Section.CONFIGS))
                        .forEach(consumer::accept);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static class FileEntry implements Entry {
        private final String name;
        private final InputStream is;
        private final boolean isDirectory;
        private final Section section;
        FileEntry(String name, InputStream is,
                  boolean isDirectory, Section section) {
            this.name = name;
            this.is = is;
            this.isDirectory = isDirectory;
            this.section = section;
        }
        public String getName() {
            return name;
        }
        public Section getSection() {
            return section;
        }
        public InputStream getInputStream() {
            return is;
        }
        public boolean isDirectory() {
            return isDirectory;
        }
    }

    private Entry toEntry(Path entryPath, Path basePath, Section section) {
        try {
            return new FileEntry(basePath.relativize(entryPath).toString().replace('\\', '/'),
                                 Files.newInputStream(entryPath), false,
                                 section);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Consumer<Entry> defaultImageWriter(Path path, OutputStream out) {
        return new DefaultEntryWriter(path, out);
    }

    private static class DefaultEntryWriter implements Consumer<Archive.Entry> {
        private final Path root;
        private final OutputStream out;

        DefaultEntryWriter(Path root, OutputStream out) {
            this.root = root;
            this.out = out;
        }

        @Override
        public void accept(Archive.Entry entry) {
            try {
                FileEntry e = (FileEntry)entry;
                Section section = e.getSection();
                String filename = e.getName();

                try (InputStream in = entry.getInputStream()) {
                    switch (section) {
                        case CLASSES:
                            if (!filename.startsWith("_the.") && !filename.equals("javac_state"))
                                writeEntry(in);
                            break;
                        case LIBS:
                            writeEntry(in, destFile(nativeDir(filename), filename));
                            break;
                        case CMDS:
                            Path path = destFile("bin", filename);
                            writeEntry(in, path);
                            path.toFile().setExecutable(true, false);
                            break;
                        case CONFIGS:
                            writeEntry(in, destFile("conf", filename));
                            break;
                        default:
                            throw new InternalError("unexpected entry: " + filename);
                    }
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        }

        private Path destFile(String dir, String filename) {
            return root.resolve(dir).resolve(filename);
        }

        private static void writeEntry(InputStream in, Path dstFile) throws IOException {
            if (Files.notExists(dstFile.getParent()))
                Files.createDirectories(dstFile.getParent());
            Files.copy(in, dstFile);
        }

        private void writeEntry(InputStream in) throws IOException {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0)
                out.write(buf, 0, n);
        }

        private static String nativeDir(String filename) {
            if (System.getProperty("os.name").startsWith("Windows")) {
                if (filename.endsWith(".dll"))
                    return "bin";
                 else
                    return "lib";
            } else {
                return "lib";
            }
        }
    }
}

