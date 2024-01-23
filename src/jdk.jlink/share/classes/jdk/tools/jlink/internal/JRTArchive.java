/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import static jdk.tools.jlink.internal.JlinkTask.RESPATH_PATTERN;
import static jdk.tools.jlink.internal.JlinkTask.RUNIMAGE_LINK_STAMP;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.util.OperatingSystem;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolEntry.Type;

/**
 * An archive implementation based on the run-time image (lib/modules, or jimage)
 * and associated files from the filesystem if any (e.g. native libraries).
 */
public class JRTArchive implements Archive {

    private final String module;
    private final Path path;
    private final ModuleReference ref;
    private final List<JRTFile> files = new ArrayList<>();
    private final List<String> otherRes;
    private final boolean errorOnModifiedFile;

    JRTArchive(String module, Path path, boolean errorOnModifiedFile) {
        this.module = module;
        this.path = path;
        this.ref = ModuleFinder.ofSystem()
                               .find(module)
                               .orElseThrow(() ->
                                    new IllegalArgumentException("Module " + module + " not part of the JDK install"));
        this.errorOnModifiedFile = errorOnModifiedFile;
        this.otherRes = readModuleResourceFile(module);
    }

    @Override
    public String moduleName() {
        return module;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public Stream<Entry> entries() {
        try {
            collectFiles();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeImageLinkException e) {
            // populate single-hop issue
            throw e.getReason();
        }
        return files.stream().map(JRTFile::toEntry);
    }

    @Override
    public void open() throws IOException {
        if (files.isEmpty()) {
            collectFiles();
        }
    }

    @Override
    public void close() throws IOException {
        if (!files.isEmpty()) {
            files.clear();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, path);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JRTArchive) {
            JRTArchive other = (JRTArchive)obj;
            return Objects.equals(module, other.module) &&
                   Objects.equals(path, other.path);
        }

        return false;
    }

    private void collectFiles() throws IOException {
        if (files.isEmpty()) {
            addNonClassResources();
            // Add classes/resources from image module
            files.addAll(ref.open().list()
                            .filter(s -> !RUNIMAGE_LINK_STAMP.equals(s))
                                   .map(s -> {
                return new JRTArchiveFile(JRTArchive.this, s,
                                          EntryType.CLASS_OR_RESOURCE, null /* hashOrTarget */, false /* symlink */);
            }).collect(Collectors.toList()));

            if (module.equals("jdk.jlink")) {
                // this entry represents that the image being created is based on the
                // run-time image (not the packaged modules).
                files.add(createRuntimeImageLinkStamp());
            }
        }
    }

    private JRTFile createRuntimeImageLinkStamp() {
        return new JRTArchiveStampFile(this, RUNIMAGE_LINK_STAMP, EntryType.CLASS_OR_RESOURCE, null, false);
    }

    /*
     * no need to keep track of the warning produced since this is eagerly checked once.
     */
    private void addNonClassResources() {
        // Not all modules will have other resources like bin, lib, legal etc.
        // files. In that case the list will be empty.
        if (!otherRes.isEmpty()) {
            files.addAll(otherRes.stream()
                 .filter(Predicate.not(String::isEmpty))
                 .map(s -> {
                        ResourceFileEntry m = ResourceFileEntry.decodeFromString(s);

                        // Read from the base JDK image.
                        Path path = BASE.resolve(m.resPath);
                        if (shaSumMismatch(path, m.hashOrTarget, m.symlink)) {
                            if (errorOnModifiedFile) {
                                String msg = String.format(MISMATCH_FORMAT, path.toString());
                                IllegalArgumentException ise = new IllegalArgumentException(msg);
                                throw new RuntimeImageLinkException(ise);
                            } else {
                                String msg = String.format(MISMATCH_FORMAT, path.toString());
                                System.err.printf("WARNING: %s", msg);
                            }
                        }

                        return new JRTArchiveFile(JRTArchive.this, m.resPath, toEntryType(m.resType), m.hashOrTarget, m.symlink);
                 })
                 .collect(Collectors.toList()));
        }
    }

    static boolean shaSumMismatch(Path res, String expectedSha, boolean isSymlink) {
        if (isSymlink) {
            return false;
        }
        // handle non-symlink resources
        try {
            HexFormat format = HexFormat.of();
            byte[] expected = format.parseHex(expectedSha);
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (InputStream is = Files.newInputStream(res)) {
                byte[] buf = new byte[1024];
                int readBytes = -1;
                while ((readBytes = is.read(buf)) != -1) {
                    digest.update(buf, 0, readBytes);
                }
            }
            byte[] actual = digest.digest();
            return !MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            throw new AssertionError("SHA-512 sum check failed!", e);
        }
    }

    private static EntryType toEntryType(Type input) {
        return switch(input) {
            case CLASS_OR_RESOURCE -> EntryType.CLASS_OR_RESOURCE;
            case CONFIG -> EntryType.CONFIG;
            case HEADER_FILE -> EntryType.HEADER_FILE;
            case LEGAL_NOTICE -> EntryType.LEGAL_NOTICE;
            case MAN_PAGE -> EntryType.MAN_PAGE;
            case NATIVE_CMD -> EntryType.NATIVE_CMD;
            case NATIVE_LIB -> EntryType.NATIVE_LIB;
            case TOP -> throw new IllegalArgumentException("TOP files should be handled by ReleaseInfoPlugin!");
            default -> throw new IllegalArgumentException("Unknown type: " + input);
        };
    }

    public record ResourceFileEntry(Type resType, boolean symlink, String hashOrTarget, String resPath) {
        // Type file format:
        // '<type>|{0,1}|<sha-sum>|<file-path>'
        //   (1)    (2)      (3)      (4)
        //
        // Where fields are:
        //
        // (1) The resource type as specified by ResourcePoolEntry.type()
        // (2) Symlink designator. 0 => regular resource, 1 => symlinked resource
        // (3) The SHA-512 sum of the resources' content. The link to the target
        //     for symlinked resources.
        // (4) The relative file path of the resource
        private static final String TYPE_FILE_FORMAT = "%d|%d|%s|%s";

        public String encodeToString() {
            return String.format(TYPE_FILE_FORMAT, resType.ordinal(), symlink ? 1 : 0, hashOrTarget, resPath);
        }

        /**
         *  line: <int>|<int>|<hashOrTarget>|<path>
         *
         *  Take the integer before '|' convert it to a Type. The second
         *  token is an integer representing symlinks (or not). The third token is
         *  a hash sum (sha512) of the file denoted by the fourth token (path).
         */
        static ResourceFileEntry decodeFromString(String line) {
            assert !line.isEmpty();

            String[] tokens = line.split("\\|", 4);
            Type type = null;
            int symlinkNum = -1;
            try {
                Integer typeInt = Integer.valueOf(tokens[0]);
                type = Type.fromOrdinal(typeInt);
                symlinkNum = Integer.valueOf(tokens[1]);
            } catch (NumberFormatException e) {
                throw new AssertionError(e); // must not happen
            }
            if (symlinkNum < 0 || symlinkNum > 1) {
                throw new IllegalStateException("Symlink designator out of range [0,1] got: " + symlinkNum);
            }
            return new ResourceFileEntry(type, symlinkNum == 1, tokens[2], tokens[3]);
        }

        public static ResourceFileEntry toResourceFileEntry(ResourcePoolEntry entry, Platform platform) {
            String resPathWithoutMod = dropModuleFromPath(entry, platform);
            // Symlinks don't have a hash sum, but a link to the target instead
            String hashOrTarget = entry.linkedTarget() == null
                                        ? computeSha512(entry)
                                        : dropModuleFromPath(entry.linkedTarget(), platform);
            return new ResourceFileEntry(entry.type(), entry.linkedTarget() != null, hashOrTarget, resPathWithoutMod);
        }

        private static String computeSha512(ResourcePoolEntry entry) {
            try {
                assert entry.linkedTarget() == null;
                MessageDigest digest = MessageDigest.getInstance("SHA-512");
                try (InputStream is = entry.content()) {
                    byte[] buf = new byte[1024];
                    int bytesRead = -1;
                    while ((bytesRead = is.read(buf)) != -1) {
                        digest.update(buf, 0, bytesRead);
                    }
                }
                byte[] db = digest.digest();
                HexFormat format = HexFormat.of();
                return format.formatHex(db);
            } catch (RuntimeImageLinkException e) {
                // RunImageArchive::RunImageFile.content() may throw this when
                // getting the content(). Propagate this specific exception.
                throw e;
            } catch (Exception e) {
                throw new AssertionError("Failed to generate hash sum for " + entry.path());
            }
        }

        private static String dropModuleFromPath(ResourcePoolEntry entry, Platform platform) {
            String resPath = entry.path().substring(entry.moduleName().length() + 2 /* prefixed and suffixed '/' */);
            if (!isWindows(platform)) {
                return resPath;
            }
            // For Windows the libraries live in the 'bin' folder rather than the 'lib' folder
            // in the final image. Note that going by the NATIVE_LIB type only is insufficient since
            // only files with suffix .dll/diz/map/pdb are transplanted to 'bin'.
            // See: DefaultImageBuilder.nativeDir()
            return nativeDir(entry, resPath);
        }

        private static boolean isWindows(Platform platform) {
            return platform.os() == OperatingSystem.WINDOWS;
        }

        private static String nativeDir(ResourcePoolEntry entry, String resPath) {
            if (entry.type() != ResourcePoolEntry.Type.NATIVE_LIB) {
                return resPath;
            }
            // precondition: Native lib, windows platform
            if (resPath.endsWith(".dll") || resPath.endsWith(".diz")
                    || resPath.endsWith(".pdb") || resPath.endsWith(".map")) {
                if (resPath.startsWith(LIB_DIRNAME + "/")) {
                    return BIN_DIRNAME + "/" + resPath.substring((LIB_DIRNAME + "/").length());
                }
            }
            return resPath;
        }
        private static final String BIN_DIRNAME = "bin";
        private static final String LIB_DIRNAME = "lib";
    }

    private static final Path BASE = Paths.get(System.getProperty("java.home"));
    private static final String MISMATCH_FORMAT = "%s has been modified.%n";

    interface JRTFile {
        Entry toEntry();
    }

    record JRTArchiveFile (Archive archive, String resPath, EntryType resType, String sha, boolean symlink)
            implements JRTFile
    {
        public Entry toEntry() {
            return new Entry(archive, resPath, resPath, resType) {
                @Override
                public long size() {
                    try {
                        if (resType != EntryType.CLASS_OR_RESOURCE) {
                            // Read from the base JDK image, special casing
                            // symlinks, which have the link target in the hashOrTarget field
                            if (symlink) {
                                return Files.size(BASE.resolve(sha));
                            }
                            return Files.size(BASE.resolve(resPath));
                        } else {
                            // Read from the module image. This works, because
                            // the underlying base path is a JrtPath with the
                            // JrtFileSystem underneath which is able to handle
                            // this size query
                            return Files.size(archive.getPath().resolve(resPath));
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public InputStream stream() throws IOException {
                    if (resType != EntryType.CLASS_OR_RESOURCE) {
                        // Read from the base JDK image.
                        Path path = symlink ? BASE.resolve(sha) : BASE.resolve(resPath);
                        return Files.newInputStream(path);
                    } else {
                        // Read from the module image.
                        String module = archive.moduleName();
                        ModuleReference mRef = ModuleFinder.ofSystem().find(module).orElseThrow();
                        return mRef.open().open(resPath).orElseThrow();
                    }
                }

            };
        }
    }

    // Stamp file marker for single-hop implementation
    record JRTArchiveStampFile(Archive archive, String resPath, EntryType resType, String sha, boolean symlink)
            implements JRTFile
    {
        @Override
        public Entry toEntry() {
            return new Entry(archive, resPath, resPath, resType) {

                @Override
                public long size() {
                    // empty file
                    return 0;
                }

                @Override
                public InputStream stream() throws IOException {
                    // empty content
                    return new ByteArrayInputStream(new byte[0]);
                }

            };
        }
    }

    static List<String> readModuleResourceFile(String modName) {
        String resName = String.format(RESPATH_PATTERN, modName);
        try {
            try (InputStream inStream = JRTArchive.class.getModule().getResourceAsStream(resName)) {
                String input = new String(inStream.readAllBytes(), StandardCharsets.UTF_8);
                if (input.isEmpty()) {
                    // Not all modules have non-class resources
                    return Collections.emptyList();
                } else {
                    return Arrays.asList(input.split("\n"));
                }
            }
        } catch (IOException e) {
            throw new InternalError("Failed to process run-time image resources for " + modName);
        }
    }
}
