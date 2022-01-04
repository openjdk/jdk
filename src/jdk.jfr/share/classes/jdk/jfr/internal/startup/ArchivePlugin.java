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
package jdk.jfr.internal.startup;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jdk.jfr.Configuration;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Purpose of this Jlink plug-in is to generate an archive of pregenerated
 * information that can be read quickly during JFR startup
 */
public final class ArchivePlugin implements Plugin {
    public final static String FILENAME = "/jdk/jfr/internal/startup/archive.bin";

    public ArchivePlugin() {
    }

    public Set<State> getState() {
        return EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL);
    }

    public Category getType() {
        return Category.ADDER;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        System.out.println("koko och jag");
        var module = in.moduleView().findModule("jdk.jfr");
        if (module.isPresent()) {
            try {
                byte[] bytes = getArchiveBytes();
                if (bytes != null) { // JFR classes are not available, ignore
                    in.transformAndCopy(Function.identity(), out);
                    out.add(ResourcePoolEntry.create(FILENAME, bytes));
                    return out.build();
                }

            } catch (IOException | ParseException e) {
                throw new PluginException("Could not generate jfr archive. " + e.getMessage(), e);
            }
        }
        return in;
    }

    public static byte[] getArchiveBytes() throws IOException, ParseException {
        try (var baos = new ByteArrayOutputStream(); var daos = new DataOutputStream(baos)) {
            writeSettings(daos, "default");
            daos.flush();
            return baos.toByteArray();
        }
    }

    private static void writeSettings(DataOutputStream daos, String name) throws IOException, ParseException {
        Configuration c = Configuration.getConfiguration(name);
        Map<String, String> settings = c.getSettings();
        daos.writeInt(settings.size());
        for (var entry : settings.entrySet()) {
            daos.writeUTF(entry.getKey());
            daos.writeUTF(entry.getValue());
        }
    }

    public String getDescription() {
        return "JFR plugin: generate jfr archive if the runtime image supports the JFR feature";
    }

    public String getUsage() {
        return "--generate-jfr-archive    Generate JFR archive if the runtime image supports\n" + "                          the JFR feature";
    }
}
