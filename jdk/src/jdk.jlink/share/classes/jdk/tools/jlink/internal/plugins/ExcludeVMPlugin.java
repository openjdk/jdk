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
package jdk.tools.jlink.internal.plugins;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.internal.Utils;
import jdk.tools.jlink.plugin.PluginException;

/**
 *
 * Exclude VM plugin
 */
public final class ExcludeVMPlugin implements TransformerPlugin {

    private static final class JvmComparator implements Comparator<Jvm> {

        @Override
        public int compare(Jvm o1, Jvm o2) {
            return o1.getEfficience() - o2.getEfficience();
        }
    }

    private enum Jvm {
        // The efficience order server - client - minimal.
        SERVER("server", 3), CLIENT("client", 2), MINIMAL("minimal", 1);
        private final String name;
        private final int efficience;

        Jvm(String name, int efficience) {
            this.name = name;
            this.efficience = efficience;
        }

        private String getName() {
            return name;
        }

        private int getEfficience() {
            return efficience;
        }
    }

    private static final String JVM_CFG = "jvm.cfg";

    public static final String NAME = "vm";
    private static final String ALL = "all";
    private static final String CLIENT = "client";
    private static final String SERVER = "server";
    private static final String MINIMAL = "minimal";

    private Predicate<String> predicate;
    private Jvm target;
    private boolean keepAll;

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * VM paths:
     * /java.base/native/{architecture}/{server|client|minimal}/{shared lib}
     * e.g.: /java.base/native/amd64/server/libjvm.so
     * /java.base/native/server/libjvm.dylib
     */
    private List<Pool.ModuleData> getVMs(Pool in) {
        String jvmlib = jvmlib();
        List<Pool.ModuleData> ret = in.getModule("java.base").getContent().stream().filter((t) -> {
            return t.getPath().endsWith("/" + jvmlib);
        }).collect(Collectors.toList());
        return ret;
    }

    @Override
    public void visit(Pool in, Pool out) {
        String jvmlib = jvmlib();
        TreeSet<Jvm> existing = new TreeSet<>(new JvmComparator());
        TreeSet<Jvm> removed = new TreeSet<>(new JvmComparator());
        if (!keepAll) {
            // First retrieve all available VM names and removed VM
            List<Pool.ModuleData> jvms = getVMs(in);
            for (Jvm jvm : Jvm.values()) {
                for (Pool.ModuleData md : jvms) {
                    if (md.getPath().endsWith("/" + jvm.getName() + "/" + jvmlib)) {
                        existing.add(jvm);
                        if (isRemoved(md)) {
                            removed.add(jvm);
                        }
                    }
                }
            }
        }
        // Check that target exists
        if (!keepAll) {
            if (!existing.contains(target)) {
                throw new PluginException("Selected VM " + target.getName() + " doesn't exist.");
            }
        }

        // Rewrite the jvm.cfg file.
        in.visit((file) -> {
            if (!keepAll) {
                if (file.getType().equals(ModuleDataType.NATIVE_LIB)) {
                    if (file.getPath().endsWith(JVM_CFG)) {
                        try {
                            file = handleJvmCfgFile(file, existing, removed);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
                }
                file = isRemoved(file) ? null : file;
            }
            return file;
        }, out);

    }

    private boolean isRemoved(Pool.ModuleData file) {
        return !predicate.test(file.getPath());
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.FILTER);
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        try {
            String value = config.get(NAME);
            String exclude = "";
            switch (value) {
                case ALL: {
                    // no filter.
                    keepAll = true;
                    break;
                }
                case CLIENT: {
                    target = Jvm.CLIENT;
                    exclude = "/java.base/native*server/*,/java.base/native*minimal/*";
                    break;
                }
                case SERVER: {
                    target = Jvm.SERVER;
                    exclude = "/java.base/native*client/*,/java.base/native*minimal/*";
                    break;
                }
                case MINIMAL: {
                    target = Jvm.MINIMAL;
                    exclude = "/java.base/native*server/*,/java.base/native*client/*";
                    break;
                }
                default: {
                    throw new PluginException("Unknown option " + value);
                }
            }
            predicate = new ResourceFilter(Utils.listParser.apply(exclude), true);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Pool.ModuleData handleJvmCfgFile(Pool.ModuleData orig,
            TreeSet<Jvm> existing,
            TreeSet<Jvm> removed) throws IOException {
        if (keepAll) {
            return orig;
        }
        StringBuilder builder = new StringBuilder();
        // Keep comments
        try (BufferedReader reader
                = new BufferedReader(new InputStreamReader(orig.stream(),
                        StandardCharsets.UTF_8))) {
            reader.lines().forEach((s) -> {
                if (s.startsWith("#")) {
                    builder.append(s).append("\n");
                }
            });
        }
        TreeSet<Jvm> remaining = new TreeSet<>(new JvmComparator());
        // Add entry in jvm.cfg file from the more efficient to less efficient.
        for (Jvm platform : existing) {
            if (!removed.contains(platform)) {
                remaining.add(platform);
                builder.append("-").append(platform.getName()).append(" KNOWN\n");
            }
        }

        // removed JVM are aliased to the most efficient remaining JVM (last one).
        // The order in the file is from most to less efficient platform
        for (Jvm platform : removed.descendingSet()) {
            builder.append("-").append(platform.getName()).
                    append(" ALIASED_TO -").
                    append(remaining.last().getName()).append("\n");
        }

        byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);

        return Pool.newImageFile(orig.getModule(),
                orig.getPath(),
                orig.getType(),
                new ByteArrayInputStream(content), content.length);
    }

    private static String jvmlib() {
        String lib = "libjvm.so";
        if (isWindows()) {
            lib = "jvm.dll";
        } else if (isMac()) {
            lib = "libjvm.dylib";
        }
        return lib;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static boolean isMac() {
        return System.getProperty("os.name").startsWith("Mac OS");
    }
}
