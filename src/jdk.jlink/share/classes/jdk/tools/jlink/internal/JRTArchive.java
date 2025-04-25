/*
 * Copyright (c) 2024, Red Hat, Inc.
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

import static jdk.tools.jlink.internal.LinkableRuntimeImage.RESPATH_PATTERN;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.util.OperatingSystem;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;
import jdk.tools.jlink.internal.runtimelink.ResourceDiff;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolEntry.Type;

/**
 * An archive implementation based on the JDK's run-time image. That is, classes
 * and resources from the modules image (lib/modules, or jimage) and other
 * associated files from the filesystem of the JDK installation.
 */
public class JRTArchive implements Archive {

    private final String module;
    private final Path path;
    private final ModuleReference ref;
    // The collection of files of this module
    private final List<JRTFile> files = new ArrayList<>();
    // Files not part of the lib/modules image of the JDK install.
    // Thus, native libraries, binaries, legal files, etc.
    private final List<String> otherRes;
    // Maps a module resource path to the corresponding diff to packaged
    // modules for that resource (if any)
    private final Map<String, ResourceDiff> resDiff;
    private final boolean errorOnModifiedFile;
    private final TaskHelper taskHelper;
    private final Set<String> upgradeableFiles;

    /**
     * JRTArchive constructor
     *
     * @param module The module name this archive refers to
     * @param path The JRT filesystem path.
     * @param errorOnModifiedFile Whether or not modified files of the JDK
     *        install aborts the link.
     * @param perModDiff The lib/modules (a.k.a jimage) diff for this module,
     *                   possibly an empty list if there are no differences.
     * @param taskHelper The task helper instance.
     * @param upgradeableFiles The set of files that are allowed for upgrades.
     */
    JRTArchive(String module,
               Path path,
               boolean errorOnModifiedFile,
               List<ResourceDiff> perModDiff,
               TaskHelper taskHelper,
               Set<String> upgradeableFiles) {
        this.module = module;
        this.path = path;
        this.ref = ModuleFinder.ofSystem()
                               .find(module)
                               .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Module " + module +
                                            " not part of the JDK install"));
        this.errorOnModifiedFile = errorOnModifiedFile;
        this.otherRes = readModuleResourceFile(module);
        this.resDiff = Objects.requireNonNull(perModDiff).stream()
                            .collect(Collectors.toMap(ResourceDiff::getName, Function.identity()));
        this.taskHelper = taskHelper;
        this.upgradeableFiles = upgradeableFiles;
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
        return (obj instanceof JRTArchive other &&
                   Objects.equals(module, other.module) &&
                   Objects.equals(path, other.path));
    }

    private void collectFiles() throws IOException {
        if (files.isEmpty()) {
            addNonClassResources();
            // Add classes/resources from the run-time image,
            // patched with the run-time image diff
            files.addAll(ref.open().list()
                                   .filter(i -> {
                                           String lookupKey = String.format("/%s/%s", module, i);
                                           ResourceDiff rd = resDiff.get(lookupKey);
                                           // Filter all resources with a resource diff
                                           // that are of kind MODIFIED.
                                           // Note that REMOVED won't happen since in
                                           // that case the module listing won't have
                                           // the resource anyway.
                                           // Note as well that filter removes files
                                           // of kind ADDED. Those files are not in
                                           // the packaged modules, so ought not to
                                           // get returned from the pipeline.
                                           return (rd == null ||
                                                   rd.getKind() == ResourceDiff.Kind.MODIFIED);
                                   })
                                   .map(s -> {
                                           String lookupKey = String.format("/%s/%s", module, s);
                                           return new JRTArchiveFile(JRTArchive.this, s,
                                                           EntryType.CLASS_OR_RESOURCE,
                                                           null /* hashOrTarget */,
                                                           false /* symlink */,
                                                           resDiff.get(lookupKey));
                                   })
                                   .toList());
            // Finally add all files only present in the resource diff
            // That is, removed items in the run-time image.
            files.addAll(resDiff.values().stream()
                                         .filter(rd -> rd.getKind() == ResourceDiff.Kind.REMOVED)
                                         .map(s -> {
                                                 int secondSlash = s.getName().indexOf("/", 1);
                                                 assert secondSlash != -1;
                                                 String pathWithoutModule = s.getName().substring(secondSlash + 1);
                                                 return new JRTArchiveFile(JRTArchive.this,
                                                         pathWithoutModule,
                                                         EntryType.CLASS_OR_RESOURCE,
                                                         null  /* hashOrTarget */,
                                                         false /* symlink */,
                                                         s);
                                         })
                                         .toList());
        }
    }

    /*
     * no need to keep track of the warning produced since this is eagerly
     * checked once.
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
                        if (!isUpgradeableFile(m.resPath) &&
                                shaSumMismatch(path, m.hashOrTarget, m.symlink)) {
                            if (errorOnModifiedFile) {
                                String msg = taskHelper.getMessage("err.runtime.link.modified.file", path.toString());
                                IOException cause = new IOException(msg);
                                throw new UncheckedIOException(cause);
                            } else {
                                taskHelper.warning("err.runtime.link.modified.file", path.toString());
                            }
                        }

                        return new JRTArchiveFile(JRTArchive.this,
                                                  m.resPath,
                                                  toEntryType(m.resType),
                                                  m.hashOrTarget,
                                                  m.symlink,
                                                  /* diff only for resources */
                                                  null);
                 })
                 .toList());
        }
    }

    /**
     * Certain files in a module are considered upgradeable. That is,
     * their hash sums aren't checked.
     *
     * @param resPath The resource path of the file to check for upgradeability.
     * @return {@code true} if the file is upgradeable. {@code false} otherwise.
     */
    private boolean isUpgradeableFile(String resPath) {
        return upgradeableFiles.contains(resPath);
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
            case TOP -> throw new IllegalArgumentException(
                           "TOP files should be handled by ReleaseInfoPlugin!");
            default -> throw new IllegalArgumentException("Unknown type: " + input);
        };
    }

    public record ResourceFileEntry(Type resType,
                                    boolean symlink,
                                    String hashOrTarget,
                                    String resPath) {
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

        private static final Map<Integer, Type> typeMap = Arrays.stream(Type.values())
                .collect(Collectors.toMap(Type::ordinal, Function.identity()));

        public String encodeToString() {
            return String.format(TYPE_FILE_FORMAT,
                                 resType.ordinal(),
                                 symlink ? 1 : 0,
                                 hashOrTarget,
                                 resPath);
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
                type = typeMap.get(typeInt);
                if (type == null) {
                    throw new AssertionError("Illegal type ordinal: " + typeInt);
                }
                symlinkNum = Integer.valueOf(tokens[1]);
            } catch (NumberFormatException e) {
                throw new AssertionError(e); // must not happen
            }
            if (symlinkNum < 0 || symlinkNum > 1) {
                throw new AssertionError(
                        "Symlink designator out of range [0,1] got: " +
                        symlinkNum);
            }
            return new ResourceFileEntry(type,
                                         symlinkNum == 1,
                                         tokens[2] /* hash or target */,
                                         tokens[3] /* resource path */);
        }

        public static ResourceFileEntry toResourceFileEntry(ResourcePoolEntry entry,
                                                            Platform platform) {
            String resPathWithoutMod = dropModuleFromPath(entry, platform);
            // Symlinks don't have a hash sum, but a link to the target instead
            String hashOrTarget = entry.linkedTarget() == null
                                        ? computeSha512(entry)
                                        : dropModuleFromPath(entry.linkedTarget(),
                                                             platform);
            return new ResourceFileEntry(entry.type(),
                                         entry.linkedTarget() != null,
                                         hashOrTarget,
                                         resPathWithoutMod);
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
            } catch (Exception e) {
                throw new AssertionError("Failed to generate hash sum for " +
                                         entry.path());
            }
        }

        private static String dropModuleFromPath(ResourcePoolEntry entry,
                                                 Platform platform) {
            String resPath = entry.path()
                                  .substring(
                                      // + 2 => prefixed and suffixed '/'
                                      // For example: '/java.base/'
                                      entry.moduleName().length() + 2);
            if (!isWindows(platform)) {
                return resPath;
            }
            // For Windows the libraries live in the 'bin' folder rather than
            // the 'lib' folder in the final image. Note that going by the
            // NATIVE_LIB type only is insufficient since only files with suffix
            // .dll/diz/map/pdb are transplanted to 'bin'.
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
                    return BIN_DIRNAME + "/" +
                               resPath.substring((LIB_DIRNAME + "/").length());
                }
            }
            return resPath;
        }
        private static final String BIN_DIRNAME = "bin";
        private static final String LIB_DIRNAME = "lib";
    }

    private static final Path BASE = Paths.get(System.getProperty("java.home"));

    interface JRTFile {
        Entry toEntry();
    }

    record JRTArchiveFile(Archive archive,
                          String resPath,
                          EntryType resType,
                          String sha,
                          boolean symlink,
                          ResourceDiff diff) implements JRTFile {
        public Entry toEntry() {
            return new Entry(archive,
                             String.format("/%s/%s",
                                           archive.moduleName(),
                                           resPath),
                             resPath,
                             resType) {
                @Override
                public long size() {
                    try {
                        if (resType != EntryType.CLASS_OR_RESOURCE) {
                            // Read from the base JDK image, special casing
                            // symlinks, which have the link target in the
                            // hashOrTarget field
                            if (symlink) {
                                return Files.size(BASE.resolve(sha));
                            }
                            return Files.size(BASE.resolve(resPath));
                        } else {
                            if (diff != null) {
                                // If the resource has a diff to the
                                // packaged modules, use the diff. Diffs of kind
                                // ADDED have been filtered out in collectFiles();
                                assert diff.getKind() != ResourceDiff.Kind.ADDED;
                                assert diff.getName().equals(String.format("/%s/%s",
                                                                           archive.moduleName(),
                                                                           resPath));
                                return diff.getResourceBytes().length;
                            }
                            // Read from the module image. This works, because
                            // the underlying base path is a JrtPath with the
                            // JrtFileSystem underneath which is able to handle
                            // this size query.
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
                        // Read from the module image. Use the diff to the
                        // packaged modules if we have one. Diffs of kind
                        // ADDED have been filtered out in collectFiles();
                        if (diff != null) {
                            assert diff.getKind() != ResourceDiff.Kind.ADDED;
                            assert diff.getName().equals(String.format("/%s/%s",
                                                                       archive.moduleName(),
                                                                       resPath));
                            return new ByteArrayInputStream(diff.getResourceBytes());
                        }
                        String module = archive.moduleName();
                        ModuleReference mRef = ModuleFinder.ofSystem()
                                                    .find(module).orElseThrow();
                        return mRef.open().open(resPath).orElseThrow();
                    }
                }

            };
        }
    }

    private static List<String> readModuleResourceFile(String modName) {
        String resName = String.format(RESPATH_PATTERN, modName);
        try {
            try (InputStream inStream = JRTArchive.class.getModule()
                                                  .getResourceAsStream(resName)) {
                String input = new String(inStream.readAllBytes(), StandardCharsets.UTF_8);
                if (input.isEmpty()) {
                    // Not all modules have non-class resources
                    return Collections.emptyList();
                } else {
                    return Arrays.asList(input.split("\n"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to process resources from the " +
                                           "run-time image for module " + modName, e);
        }
    }
}
