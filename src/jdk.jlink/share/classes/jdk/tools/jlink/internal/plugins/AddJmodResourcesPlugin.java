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

package jdk.tools.jlink.internal.plugins;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jdk.internal.util.OperatingSystem;
import jdk.tools.jlink.internal.Platform;
import jdk.tools.jlink.internal.RunImageLinkException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolModule;


/**
 * Plugin to collect resources from jmod which aren't classes or
 * resources. Needed for the the run-image-based jlink.
 */
public final class AddJmodResourcesPlugin extends AbstractPlugin {

    private static final int SYMLINKED_RES = 1;
    private static final int REGULAR_RES = 0;
    private static final String BIN_DIRNAME = "bin";
    private static final String LIB_DIRNAME = "lib";
    private static final String NAME = "add-jmod-resources";
    // This ought to be a package-less resource so as to not conflict with
    // packages listed in the module descriptors. Making it package-less ensures
    // it works for any module, regardless of packages present. This resource
    // is being used in JmodLessArchive class
    private static final String RESPATH = "/%s/jmod_resources";

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

    private final Map<String, List<String>> nonClassResEntries;

    public AddJmodResourcesPlugin() {
        super(NAME);
        this.nonClassResEntries = new ConcurrentHashMap<>();
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Platform targetPlatform = getTargetPlatform(in);
        in.transformAndCopy(e -> { ResourcePoolEntry retval = recordAndFilterEntry(e, targetPlatform);
                                   return retval;}, out);
        addModuleResourceEntries(out);
        return out.build();
    }

    private Platform getTargetPlatform(ResourcePool in) {
        String platform = in.moduleView().findModule("java.base")
                .map(ResourcePoolModule::targetPlatform)
                .orElse(null);
        if (platform == null) {
            throw new IllegalStateException("java.base not part of the image?");
        }
        return Platform.parsePlatform(platform);
    }

    private void addModuleResourceEntries(ResourcePoolBuilder out) {
        for (String module: keysInSortedOrder()) {
            String mResource = String.format(RESPATH, module);
            List<String> mResources = nonClassResEntries.get(module);
            if (mResources == null) {
                throw new AssertionError("Module listed, but no resources?");
            }
            String mResContent = mResources.stream().sorted().collect(Collectors.joining("\n"));
            out.add(ResourcePoolEntry.create(mResource,
                    mResContent.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private List<String> keysInSortedOrder() {
        List<String> keys = new ArrayList<>();
        keys.addAll(nonClassResEntries.keySet());
        Collections.sort(keys);
        return keys;
    }

    private ResourcePoolEntry recordAndFilterEntry(ResourcePoolEntry entry, Platform platform) {
        // Note that the jmod_resources file is a resource file, so we cannot
        // add ourselves due to this condition. However, we want to not add
        // an old version of the resource file again.
        if (entry.type() != ResourcePoolEntry.Type.CLASS_OR_RESOURCE) {
            if (entry.type() == ResourcePoolEntry.Type.TOP) {
                return entry; // Handled by ReleaseInfoPlugin, nothing to do
            }
            List<String> moduleResources = nonClassResEntries.computeIfAbsent(entry.moduleName(), a -> new ArrayList<>());
            int type = entry.type().ordinal();
            int isSymlink = entry.linkedTarget() != null ? SYMLINKED_RES : REGULAR_RES;
            String resPathWithoutMod = resPathWithoutModule(entry, platform);
            String sha512 = computeSha512(entry, platform);
            moduleResources.add(String.format(TYPE_FILE_FORMAT, type, isSymlink, sha512, resPathWithoutMod));
        } else if (entry.type() == ResourcePoolEntry.Type.CLASS_OR_RESOURCE &&
                String.format(RESPATH, entry.moduleName()).equals(entry.path())) {
            // Filter /<module>/jmod_resources file which we create later
            return null;
        }
        return entry;
    }

    private String computeSha512(ResourcePoolEntry entry, Platform platform) {
        try {
            if (entry.linkedTarget() != null) {
                // Symlinks don't have a hash sum, but a link to the target instead
                return resPathWithoutModule(entry.linkedTarget(), platform);
            } else {
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
            }
        } catch (RunImageLinkException e) {
            // JmodLessArchive::JmodLessFile.content() may throw this when
            // getting the content(). Populate the actual reason.
            throw e.getReason();
        } catch (Exception e) {
            throw new AssertionError("Failed to generate hash sum for " + entry.path());
        }
    }

    private String resPathWithoutModule(ResourcePoolEntry entry, Platform platform) {
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

    private boolean isWindows(Platform platform) {
        return platform.os() == OperatingSystem.WINDOWS;
    }

    private String nativeDir(ResourcePoolEntry entry, String resPath) {
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

    @Override
    public Set<State> getState() {
        return EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL);
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public Category getType() {
        // Ensure we run in a later stage as we need to generate
        // SHA-512 sums for non-(class/resource) files. The jmod_resources
        // files can be considered meta-info describing the universe we
        // draft from.
        return Category.METAINFO_ADDER;
    }

}
