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

import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Purpose of this plug-in is to generate an archive of pregenerated information
 * that can be read quickly during JFR startup
 */
public final class JFRPlugin extends AbstractPlugin {

    public JFRPlugin() {
        super("generate-jfr-archive");
    }

    public Set<State> getState() {
        if (ModuleLayer.boot().findModule("jdk.jfr").isPresent()) {
            return EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL);
        } else {
            return EnumSet.of(State.DISABLED);
        }
    }

    public Category getType() {
        return Category.ADDER;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        var module = in.moduleView().findModule("jdk.jfr");
        if (module.isPresent()) {
            byte[] bytes = getArchiveBytes();
            if (bytes != null) { // JFR classes are not available, ignore
                in.transformAndCopy(Function.identity(), out);
                String name = "/jdk.jfr/jdk/jfr/internal/startup/archive.bin";
                out.add(ResourcePoolEntry.create(name, getArchiveBytes()));
                return out.build();
            }
        }
        return in;
    }

    private static byte[] getArchiveBytes() {
        try {
            Class<?> c = Class.forName("jdk.jfr.internal.startup.ArchiveWriter");
            return (byte[]) c.getMethod("write").invoke(null);
        } catch (ClassNotFoundException e1) {
            throw new PluginException("Could not find JFR classes for jfr startup archive: " + e1.getMessage(), e1);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e2) {
            throw new PluginException("Could not generate jfr startup archive: " + e2.getMessage(), e2);
        }
    }
}
