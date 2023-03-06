/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.tools.jlink.internal.JlinkTask.OPTIONS_RESOURCE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Saves the arguments in the specified argument files to a resource that's read
 * by jlink in the output image. The saved arguments are prepended to the arguments
 * specified on the jlink command line.
 */
public final class SaveJlinkArgfilesPlugin extends AbstractPlugin {

    public SaveJlinkArgfilesPlugin() {
        super("save-jlink-argfiles");
    }

    private List<String> argfiles = new ArrayList<>();

    @Override
    public Category getType() {
        return Category.ADDER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        var v = config.get(getName());

        if (v == null)
            throw new AssertionError();

        for (String argfile : v.split(File.pathSeparator)) {
            argfiles.add(readArgfile(argfile));
        }
    }

    private static String readArgfile(String argfile) {
        try {
            return Files.readString(Path.of(argfile));
        } catch (IOException e) {
            throw new PluginException("Argfile " + argfile + " is not readable");
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        if (!in.moduleView().findModule("jdk.jlink").isPresent()) {
            throw new PluginException("--save-jlink-argfiles requires jdk.jlink to be in the output image");
        }
        final String jdkJlinkResource = "/jdk.jlink/" + OPTIONS_RESOURCE;
        byte[] savedOptions = argfiles.stream()
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
        final ResourcePoolEntry jdkJlinkSavedEntry = ResourcePoolEntry.create("/jdk.jlink/" + OPTIONS_RESOURCE,
                savedOptions);
        final boolean[] haveEntry = new boolean[] { false };
        in.transformAndCopy(e -> {
            if (jdkJlinkResource.equals(e.path())) {
                // override new options if present. The jmod-less plugin might
                // have the resource already in the base image
                haveEntry[0] = true;
                return jdkJlinkSavedEntry;
            } else {
                return e;
            }
        }, out);
        if (!haveEntry[0]) {
            // Add the resource if and only if there isn't one already
            out.add(jdkJlinkSavedEntry);
        }
        return out.build();
    }
}
