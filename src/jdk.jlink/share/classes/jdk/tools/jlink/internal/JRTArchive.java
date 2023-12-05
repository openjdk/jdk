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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.plugin.ResourcePoolEntry.Type;

/**
 * An archive implementation based on the run-time image (lib/modules, or jimage)
 * and associated files from the filesystem if any (e.g. native libraries).
 */
public class JRTArchive implements Archive {

    // File marker in lib/modules file for jdk.jlink indicating it got created
    // with a run-time image type link.
    public static final String RUNIMAGE_SINGLE_HOP_STAMP = "jdk/tools/jlink/internal/runtimeimage.link.stamp";
    private static final String JDK_JLINK_MODULE = "jdk.jlink";
    private final String module;
    private final Path path;
    private final ModuleReference ref;
    private final List<JRTArchiveFile> files = new ArrayList<>();
    private final List<String> otherRes;
    private final boolean singleHop;

    JRTArchive(String module, Path path, boolean singleHop, List<String> otherRes) {
        this.module = module;
        this.path = path;
        this.ref = ModuleFinder.ofSystem()
                    .find(module)
                    .orElseThrow(() ->
                        new IllegalArgumentException("Module " + module + " not part of the JDK install"));
        this.singleHop = singleHop;
        this.otherRes = otherRes;
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
        return files.stream()
                    .sorted((a, b) -> {return a.resPath.compareTo(b.resPath);})
                    .map(f -> { return f.toEntry();});
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
                                   .map(s -> {
                return new JRTArchiveFile(JRTArchive.this, s,
                        Type.CLASS_OR_RESOURCE, null /* sha */, false /* symlink */, singleHop);
            }).collect(Collectors.toList()));
            // add/persist a special, empty file for jdk.jlink so as to support
            // the single-hop-only run-time image jlink
            if (singleHop && JDK_JLINK_MODULE.equals(module)) {
                files.add(createRuntimeImageSingleHopStamp());
            }
        }
    }

    private JRTArchiveFile createRuntimeImageSingleHopStamp() {
        return new JRTArchiveStampFile(this, RUNIMAGE_SINGLE_HOP_STAMP, Type.CLASS_OR_RESOURCE, null, false, singleHop);
    }

    private void addNonClassResources() throws IOException {
        // Not all modules will have other resources like bin, lib, legal etc.
        // files. In that case the list will be empty.
        if (!otherRes.isEmpty()) {
            files.addAll(otherRes.stream()
                    .map(s -> {
                        TypePathMapping m = mappingResource(s);
                        return new JRTArchiveFile(JRTArchive.this, m.resPath, m.resType, m.sha, m.symlink, singleHop);
                    })
                    .filter(m -> m != null)
                    .collect(Collectors.toList()));
        }
    }

    /**
     *  line: <int>|<int>|<sha>|<path>
     *
     *  Take the integer before '|' convert it to a Type. The second
     *  token is an integer representing symlinks (or not). The third token is
     *  a hash sum (sha512) of the file denoted by the fourth token (path).
     */
    private static TypePathMapping mappingResource(String line) {
        if (line.isEmpty()) {
            return null;
        }
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
        boolean isSymlink = symlinkNum > 0;
        return new TypePathMapping(tokens[2], tokens[3], type, isSymlink);
    }

    static class TypePathMapping {
        final String resPath;
        final String sha;
        final Type resType;
        final boolean symlink;
        TypePathMapping(String sha, String resPath, Type resType, boolean symlink) {
            this.resPath = resPath;
            this.resType = resType;
            this.sha = Objects.requireNonNull(sha);
            this.symlink = symlink;
        }
    }

    static class JRTArchiveFile {
        private static final String JAVA_HOME = System.getProperty("java.home");
        private static final Path BASE = Paths.get(JAVA_HOME);
        private static final String MISMATCH_FORMAT = "%s has been modified.%s%n";
        final String resPath;
        final Archive.Entry.EntryType resType;
        final Archive archive;
        final String sha; // Checksum for non-resource files
        final boolean symlink;
        final boolean failOnMod; // Only allow non-failure in multi-hop mode

        JRTArchiveFile(Archive archive, String resPath, Type resType, String sha, boolean symlink, boolean failOnMod) {
            this.resPath = resPath;
            this.resType = toEntryType(resType);
            this.archive = archive;
            this.sha = sha;
            this.symlink = symlink;
            this.failOnMod = failOnMod;
        }

        Entry toEntry() {
            return new Entry(archive, resPath, resPath, resType) {

                private boolean warningProduced = false;

                @Override
                public long size() {
                    try {
                        if (resType != Archive.Entry.EntryType.CLASS_OR_RESOURCE) {
                            // Read from the base JDK image, special casing
                            // symlinks, which have the link target in the sha field
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
                    if (resType != Archive.Entry.EntryType.CLASS_OR_RESOURCE) {
                        // Read from the base JDK image.
                        Path path = BASE.resolve(resPath);
                        if (shaSumMismatch(path, sha, symlink)) {
                            if (failOnMod) {
                                String hint = " You may force the link with '--unlock-run-image'.";
                                String msg = String.format(MISMATCH_FORMAT, path.toString(), hint);
                                IllegalArgumentException ise = new IllegalArgumentException(msg);
                                throw new RuntimeImageLinkException(ise);
                            } else if (!warningProduced) {
                                String msg = String.format(MISMATCH_FORMAT, path.toString(), "");
                                System.err.printf("WARNING: %s", msg);
                                warningProduced = true;
                            }
                        }
                        if (symlink) {
                            path = BASE.resolve(sha);
                            return Files.newInputStream(path);
                        }
                        return Files.newInputStream(path);
                    } else {
                        // Read from the module image.
                        String module = archive.moduleName();
                        ModuleReference mRef = ModuleFinder.ofSystem().find(module).orElseThrow();
                        return mRef.open().open(resPath).orElseThrow();
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

            };
        }

        private static Archive.Entry.EntryType toEntryType(Type input) {
            switch(input) {
            case CLASS_OR_RESOURCE:
                return Archive.Entry.EntryType.CLASS_OR_RESOURCE;
            case CONFIG:
                return Archive.Entry.EntryType.CONFIG;
            case HEADER_FILE:
                return Archive.Entry.EntryType.HEADER_FILE;
            case LEGAL_NOTICE:
                return Archive.Entry.EntryType.LEGAL_NOTICE;
            case MAN_PAGE:
                return Archive.Entry.EntryType.MAN_PAGE;
            case NATIVE_CMD:
                return Archive.Entry.EntryType.NATIVE_CMD;
            case NATIVE_LIB:
                return Archive.Entry.EntryType.NATIVE_LIB;
            case TOP:
                throw new IllegalArgumentException("TOP files should be handled by ReleaseInfoPlugin!");
            default:
                throw new IllegalArgumentException("Unknown type: " + input);
            }
        }
    }

    // Stamp file marker for single-hop implementation
    static class JRTArchiveStampFile extends JRTArchiveFile {
        JRTArchiveStampFile(Archive archive, String resPath, Type resType, String sha, boolean symlink, boolean failOnMod) {
            super(archive, resPath, resType, sha, symlink, failOnMod);
        }

        @Override
        Entry toEntry() {
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

}
