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

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.Plugin;

/**
 * Plugin to generate java.lang.invoke classes.
 */
public final class GenerateJLIClassesPlugin implements Plugin {

    private static final String NAME = "generate-jli-classes";

    private static final String BMH_PARAM = "bmh";

    private static final String BMH_SPECIES_PARAM = "bmh-species";

    private static final String DMH_PARAM = "dmh";

    private static final String DESCRIPTION = PluginsResourceBundle.getDescription(NAME);

    private static final String BMH = "java/lang/invoke/BoundMethodHandle";
    private static final Method BMH_FACTORY_METHOD;

    private static final String DMH = "java/lang/invoke/DirectMethodHandle$Holder";
    private static final String DMH_INVOKE_VIRTUAL = "invokeVirtual";
    private static final String DMH_INVOKE_STATIC = "invokeStatic";
    private static final String DMH_INVOKE_SPECIAL = "invokeSpecial";
    private static final String DMH_NEW_INVOKE_SPECIAL = "newInvokeSpecial";
    private static final String DMH_INVOKE_INTERFACE = "invokeInterface";
    private static final String DMH_INVOKE_STATIC_INIT = "invokeStaticInit";
    private static final Method DMH_FACTORY_METHOD;

    List<String> speciesTypes;

    Map<String, List<String>> dmhMethods;

    public GenerateJLIClassesPlugin() {
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
     * This list was derived from running a small startup benchmark.
     * A better long-term solution is to define and run a set of quick
     * generators and extracting this list as a step in the build process.
     */
    public static List<String> defaultSpecies() {
        return List.of("LL", "L3", "L4", "L5", "L6", "L7", "L7I",
                "L7II", "L7IIL", "L8", "L9", "L10", "L10I", "L10II", "L10IIL",
                "L11", "L12", "L13", "LI", "D", "L3I", "LIL", "LLI", "LLIL",
                "LILL", "I", "LLILL");
    }

    /**
     * @return the list of default DirectMethodHandle methods to generate.
     */
    public static Map<String, List<String>> defaultDMHMethods() {
        return Map.of(
            DMH_INVOKE_VIRTUAL, List.of("_L", "L_L", "LI_I", "LL_V"),
            DMH_INVOKE_SPECIAL, List.of("L_I", "L_L", "LF_L", "LD_L", "LL_L",
                "L3_L", "L4_L", "L5_L", "L6_L", "L7_L", "LI_I", "LI_L", "LIL_I",
                "LII_I", "LII_L", "LLI_L", "LLI_I", "LILI_I", "LIIL_L",
                "LIILL_L", "LIILL_I", "LIIL_I", "LILIL_I", "LILILL_I",
                "LILII_I", "LI3_I", "LI3L_I", "LI3LL_I", "LI3_L", "LI4_I"),
            DMH_INVOKE_STATIC, List.of("II_I", "IL_I", "ILIL_I", "ILII_I",
                "_I", "_L", "_V", "D_L", "F_L", "I_I", "II_L", "LI_L",
                "L_V", "L_L", "LL_L", "L3_L", "L4_L", "L5_L", "L6_L",
                "L7_L", "L8_L", "L9_L", "L9I_L", "L9II_L", "L9IIL_L",
                "L10_L", "L11_L", "L12_L", "L13_L", "L13I_L", "L13II_L")
        );
    }

    // Map from DirectMethodHandle method type to internal ID
    private static final Map<String, Integer> DMH_METHOD_TYPE_MAP =
            Map.of(
                DMH_INVOKE_VIRTUAL,     0,
                DMH_INVOKE_STATIC,      1,
                DMH_INVOKE_SPECIAL,     2,
                DMH_NEW_INVOKE_SPECIAL, 3,
                DMH_INVOKE_INTERFACE,   4,
                DMH_INVOKE_STATIC_INIT, 5
            );

    @Override
    public void configure(Map<String, String> config) {
        String mainArgument = config.get(NAME);

        // Enable by default
        boolean bmhEnabled = true;
        boolean dmhEnabled = true;
        if (mainArgument != null) {
            List<String> args = Arrays.asList(mainArgument.split(","));
            if (!args.contains(BMH_PARAM)) {
                bmhEnabled = false;
            }
            if (!args.contains(DMH_PARAM)) {
                dmhEnabled = false;
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
        }

        // DirectMethodHandles
        if (!dmhEnabled) {
            dmhMethods = Map.of();
        } else {
            dmhMethods = new HashMap<>();
            for (String dmhParam : DMH_METHOD_TYPE_MAP.keySet()) {
                String args = config.get(dmhParam);
                if (args != null && !args.isEmpty()) {
                    List<String> dmhMethodTypes = Arrays.stream(args.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    dmhMethods.put(dmhParam, dmhMethodTypes);
                    // Validation check
                    for (String type : dmhMethodTypes) {
                        String[] typeParts = type.split("_");
                        // check return type (second part)
                        if (typeParts.length != 2 || typeParts[1].length() != 1
                                || "LJIFDV".indexOf(typeParts[1].charAt(0)) == -1) {
                            throw new PluginException(
                                    "Method type signature must be of form [LJIFD]*_[LJIFDV]");
                        }
                        // expand and check arguments (first part)
                        expandSignature(typeParts[0]);
                    }
                }
            }
            if (dmhMethods.isEmpty()) {
                dmhMethods = defaultDMHMethods();
            }
        }
    }

    private static void requireBasicType(char c) {
        if ("LIJFD".indexOf(c) < 0) {
            throw new PluginException(
                    "Character " + c + " must correspond to a basic field type: LIJFD");
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        // Copy all but DMH_ENTRY to out
        in.transformAndCopy(entry -> entry.path().equals(DMH_ENTRY) ? null : entry, out);
        speciesTypes.forEach(types -> generateBMHClass(types, out));
        generateDMHClass(out);
        return out.build();
    }

    @SuppressWarnings("unchecked")
    private void generateBMHClass(String types, ResourcePoolBuilder out) {
        try {
            // Generate class
            Map.Entry<String, byte[]> result = (Map.Entry<String, byte[]>)
                    BMH_FACTORY_METHOD.invoke(null, types);
            String className = result.getKey();
            byte[] bytes = result.getValue();

            // Add class to pool
            ResourcePoolEntry ndata = ResourcePoolEntry.create(
                    "/java.base/" + className + ".class",
                    bytes);
            out.add(ndata);
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }

    private void generateDMHClass(ResourcePoolBuilder out) {
        int count = 0;
        for (List<String> entry : dmhMethods.values()) {
            count += entry.size();
        }
        MethodType[] methodTypes = new MethodType[count];
        int[] dmhTypes = new int[count];
        int index = 0;
        for (Map.Entry<String, List<String>> entry : dmhMethods.entrySet()) {
            String dmhType = entry.getKey();
            for (String type : entry.getValue()) {
                methodTypes[index] = asMethodType(type);
                dmhTypes[index] = DMH_METHOD_TYPE_MAP.get(dmhType);
                index++;
            }
        }
        try {
            byte[] bytes = (byte[])DMH_FACTORY_METHOD
                    .invoke(null,
                            DMH,
                            methodTypes,
                            dmhTypes);
            ResourcePoolEntry ndata = ResourcePoolEntry.create(DMH_ENTRY, bytes);
            out.add(ndata);
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }
    private static final String DMH_ENTRY = "/java.base/" + DMH + ".class";

    static {
        try {
            Class<?> BMHFactory = Class.forName("java.lang.invoke.BoundMethodHandle$Factory");
            BMH_FACTORY_METHOD = BMHFactory.getDeclaredMethod("generateConcreteBMHClassBytes",
                    String.class);
            BMH_FACTORY_METHOD.setAccessible(true);

            Class<?> DMHFactory = Class.forName("java.lang.invoke.DirectMethodHandle");
            DMH_FACTORY_METHOD = DMHFactory.getDeclaredMethod("generateDMHClassBytes",
                    String.class, MethodType[].class, int[].class);
            DMH_FACTORY_METHOD.setAccessible(true);
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
                requireBasicType(c);
                for (int j = 1; j < count; j++) {
                    sb.append(last);
                }
                sb.append(c);
                last = c;
                count = 0;
            }
        }

        // ended with a number, e.g., "L2": append last char count - 1 times
        if (count > 1) {
            requireBasicType(last);
            for (int j = 1; j < count; j++) {
                sb.append(last);
            }
        }
        return sb.toString();
    }

    private static MethodType asMethodType(String basicSignatureString) {
        String[] parts = basicSignatureString.split("_");
        assert(parts.length == 2);
        assert(parts[1].length() == 1);
        String parameters = expandSignature(parts[0]);
        Class<?> rtype = primitiveType(parts[1].charAt(0));
        Class<?>[] ptypes = new Class<?>[parameters.length()];
        for (int i = 0; i < ptypes.length; i++) {
            ptypes[i] = primitiveType(parameters.charAt(i));
        }
        return MethodType.methodType(rtype, ptypes);
    }

    private static Class<?> primitiveType(char c) {
        switch (c) {
            case 'F':
                return float.class;
            case 'D':
                return double.class;
            case 'I':
                return int.class;
            case 'L':
                return Object.class;
            case 'J':
                return long.class;
            case 'V':
                return void.class;
            case 'Z':
            case 'B':
            case 'S':
            case 'C':
                throw new IllegalArgumentException("Not a valid primitive: " + c +
                        " (use I instead)");
            default:
                throw new IllegalArgumentException("Not a primitive: " + c);
        }
    }
}
