/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.TransformerPlugin;

/**
 * Plugin to generate java.lang.invoke classes.
 */
public final class GenerateJLIClassesPlugin implements TransformerPlugin {

    private static final String NAME = "generate-jli-classes";

    private static final String BMH_PARAM = "bmh";

    private static final String BMH_SPECIES_PARAM = "bmh-species";

    private static final String DESCRIPTION = PluginsResourceBundle.getDescription(NAME);

    private static final String BMH = "java/lang/invoke/BoundMethodHandle";

    private static final Method FACTORY_METHOD;

    List<String> speciesTypes;

    public GenerateJLIClassesPlugin() {
    }

    @Override
    public Set<Category> getType() {
        return Collections.singleton(Category.TRANSFORMER);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Set<State> getState() {
        return EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    /**
     * @return the default Species forms to generate.
     *
     * This list was derived from running a Java concatenating strings
     * with -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT set
     * plus a subset of octane. A better long-term solution is to define
     * and run a set of quick generators and extracting this list as a
     * step in the build process.
     */
    public static List<String> defaultSpecies() {
        return List.of("LL", "L3", "L4", "L5", "L6", "L7", "L7I",
                "L7II", "L7IIL", "L8", "L9", "L10", "L10I", "L10II", "L10IIL",
                "L11", "L12", "L13", "LI", "D", "L3I", "LIL", "LLI", "LLIL",
                "LILL", "I", "LLILL");
    }

    @Override
    public void configure(Map<String, String> config) {
        String mainArgument = config.get(NAME);

        // Enable by default
        boolean bmhEnabled = true;
        if (mainArgument != null) {
            Set<String> args = Arrays.stream(mainArgument.split(","))
                    .collect(Collectors.toSet());
            if (!args.contains(BMH_PARAM)) {
                bmhEnabled = false;
            }
        }

        if (!bmhEnabled) {
            speciesTypes = List.of();
        } else {
            String args = config.get(BMH_SPECIES_PARAM);
            List<String> bmhSpecies;
            if (args != null && !args.isEmpty()) {
                bmhSpecies = Arrays.stream(args.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            } else {
                bmhSpecies = defaultSpecies();
            }

            // Expand BMH species signatures
            speciesTypes = bmhSpecies.stream()
                    .map(type -> expandSignature(type))
                    .collect(Collectors.toList());

            // Validation check
            for (String type : speciesTypes) {
                for (char c : type.toCharArray()) {
                    if ("LIJFD".indexOf(c) < 0) {
                        throw new PluginException("All characters must "
                                + "correspond to a basic field type: LIJFD");
                    }
                }
            }
        }
    }

    @Override
    public void visit(ModulePool in, ModulePool out) {
        in.entries().forEach(data -> {
            if (("/java.base/" + BMH + ".class").equals(data.getPath())) {
                // Add BoundMethodHandle unchanged
                out.add(data);
                speciesTypes.forEach(types -> generateConcreteClass(types, data, out));
            } else {
                if (!out.contains(data)) {
                    out.add(data);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void generateConcreteClass(String types, ModuleEntry data, ModulePool out) {
        try {
            // Generate class
            Map.Entry<String, byte[]> result = (Map.Entry<String, byte[]>)
                    FACTORY_METHOD.invoke(null, types);
            String className = result.getKey();
            byte[] bytes = result.getValue();

            // Add class to pool
            ModuleEntry ndata = ModuleEntry.create(data.getModule(),
                    "/java.base/" + className + ".class",
                    ModuleEntry.Type.CLASS_OR_RESOURCE,
                    new ByteArrayInputStream(bytes), bytes.length);
            if (!out.contains(ndata)) {
                out.add(ndata);
            }
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }

    static {
        try {
            Class<?> BMHFactory = Class.forName("java.lang.invoke.BoundMethodHandle$Factory");
            Method genClassMethod = BMHFactory.getDeclaredMethod("generateConcreteBMHClassBytes",
                    String.class);
            genClassMethod.setAccessible(true);
            FACTORY_METHOD = genClassMethod;
        } catch (Exception e) {
            throw new PluginException(e);
        }
    }

    // Convert LL -> LL, L3 -> LLL
    private static String expandSignature(String signature) {
        StringBuilder sb = new StringBuilder();
        char last = 'X';
        int count = 0;
        for (int i = 0; i < signature.length(); i++) {
            char c = signature.charAt(i);
            if (c >= '0' && c <= '9') {
                count *= 10;
                count += (c - '0');
            } else {
                for (int j = 1; j < count; j++) {
                    sb.append(last);
                }
                sb.append(c);
                last = c;
                count = 0;
            }
        }
        for (int j = 1; j < count; j++) {
            sb.append(last);
        }
        return sb.toString();
    }
}
