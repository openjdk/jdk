/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.JavaLangInvokeAccess;
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

    private static final String DESCRIPTION = PluginsResourceBundle.getDescription(NAME);

    private static final String DEFAULT_TRACE_FILE = "default_jli_trace.txt";

    private static final String DIRECT_HOLDER = "java/lang/invoke/DirectMethodHandle$Holder";
    private static final String DMH_INVOKE_VIRTUAL = "invokeVirtual";
    private static final String DMH_INVOKE_STATIC = "invokeStatic";
    private static final String DMH_INVOKE_SPECIAL = "invokeSpecial";
    private static final String DMH_NEW_INVOKE_SPECIAL = "newInvokeSpecial";
    private static final String DMH_INVOKE_INTERFACE = "invokeInterface";
    private static final String DMH_INVOKE_STATIC_INIT = "invokeStaticInit";
    private static final String DMH_INVOKE_SPECIAL_IFC = "invokeSpecialIFC";

    private static final String DELEGATING_HOLDER = "java/lang/invoke/DelegatingMethodHandle$Holder";
    private static final String BASIC_FORMS_HOLDER = "java/lang/invoke/LambdaForm$Holder";

    private static final String INVOKERS_HOLDER_NAME = "java.lang.invoke.Invokers$Holder";
    private static final String INVOKERS_HOLDER_INTERNAL_NAME = INVOKERS_HOLDER_NAME.replace('.', '/');

    private static final JavaLangInvokeAccess JLIA
            = SharedSecrets.getJavaLangInvokeAccess();

    Set<String> speciesTypes = Set.of();

    Set<String> invokerTypes = Set.of();

    Set<String> callSiteTypes = Set.of();

    Map<String, Set<String>> dmhMethods = Map.of();

    String mainArgument;

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
    public static Set<String> defaultSpecies() {
        return Set.of("LL", "L3", "L4", "L5", "L6", "L7", "L7I",
                "L7II", "L7IIL", "L8", "L9", "L10", "L10I", "L10II", "L10IIL",
                "L11", "L12", "L13", "LI", "D", "L3I", "LIL", "LLI", "LLIL",
                "LILL", "I", "LLILL");
    }

    /**
     * @return the default invoker forms to generate.
     */
    private static Set<String> defaultInvokers() {
        return Set.of("LL_L", "LL_I", "LLLL_L", "LLLL_I", "LLIL_L", "LLIL_I",
                "L6_L");
    }

    /**
     * @return the default call site forms to generate (linkToTargetMethod).
     */
    private static Set<String> defaultCallSiteTypes() {
        return Set.of("L5_L", "LIL3_L", "ILL_L");
    }

    /**
     * @return the list of default DirectMethodHandle methods to generate.
     */
    private static Map<String, Set<String>> defaultDMHMethods() {
        return Map.of(
            DMH_INVOKE_INTERFACE, Set.of("LL_L", "L3_I", "L3_V"),
            DMH_INVOKE_VIRTUAL, Set.of("L_L", "LL_L", "LLI_I", "L3_V"),
            DMH_INVOKE_SPECIAL, Set.of("LL_I", "LL_L", "LLF_L", "LLD_L",
                "L3_I", "L3_L", "L4_L", "L5_L", "L6_L", "L7_L", "L8_L",
                "LLI_I", "LLI_L", "LLIL_I", "LLIL_L", "LLII_I", "LLII_L",
                "L3I_L", "L3I_I", "L3ILL_L", "LLILI_I", "LLIIL_L", "LLIILL_L",
                "LLIILL_I", "LLIIL_I", "LLILIL_I", "LLILILL_I", "LLILII_I",
                "LLI3_I", "LLI3L_I", "LLI3LL_I", "LLI3_L", "LLI4_I"),
            DMH_INVOKE_STATIC, Set.of("LII_I", "LIL_I", "LILIL_I", "LILII_I",
                "L_I", "L_L", "L_V", "LD_L", "LF_L", "LI_I", "LII_L", "LLI_L",
                "LL_I", "LLILL_L", "LLIL3_L", "LL_V", "LL_L", "L3_I", "L3_L",
                "L3_V", "L4_I", "L4_L", "L5_L", "L6_L", "L7_L", "L8_L", "L9_L",
                "L10_L", "L10I_L", "L10II_L", "L10IIL_L", "L11_L", "L12_L",
                "L13_L", "L14_L", "L14I_L", "L14II_L"),
            DMH_NEW_INVOKE_SPECIAL, Set.of("L_L", "LL_L"),
            DMH_INVOKE_SPECIAL_IFC, Set.of("L5_I")
        );
    }

    // Map from DirectMethodHandle method type to internal ID, matching values
    // of the corresponding constants in java.lang.invoke.MethodTypeForm
    private static final Map<String, Integer> DMH_METHOD_TYPE_MAP =
            Map.of(
                DMH_INVOKE_VIRTUAL,     0,
                DMH_INVOKE_STATIC,      1,
                DMH_INVOKE_SPECIAL,     2,
                DMH_NEW_INVOKE_SPECIAL, 3,
                DMH_INVOKE_INTERFACE,   4,
                DMH_INVOKE_STATIC_INIT, 5,
                DMH_INVOKE_SPECIAL_IFC, 20
            );

    @Override
    public void configure(Map<String, String> config) {
        mainArgument = config.get(NAME);
    }

    public void initialize(ResourcePool in) {
        // Start with the default configuration
        speciesTypes = defaultSpecies().stream()
                .map(type -> expandSignature(type))
                .collect(Collectors.toSet());

        invokerTypes = defaultInvokers();
        validateMethodTypes(invokerTypes);

        callSiteTypes = defaultCallSiteTypes();

        dmhMethods = defaultDMHMethods();
        for (Set<String> dmhMethodTypes : dmhMethods.values()) {
            validateMethodTypes(dmhMethodTypes);
        }

        // Extend the default configuration with the contents in the supplied
        // input file - if none was supplied we look for the default file
        if (mainArgument == null || !mainArgument.startsWith("@")) {
            try (InputStream traceFile =
                    this.getClass().getResourceAsStream(DEFAULT_TRACE_FILE)) {
                if (traceFile != null) {
                    readTraceConfig(
                        new BufferedReader(
                            new InputStreamReader(traceFile)).lines());
                }
            } catch (Exception e) {
                throw new PluginException("Couldn't read " + DEFAULT_TRACE_FILE, e);
            }
        } else {
            File file = new File(mainArgument.substring(1));
            if (file.exists()) {
                readTraceConfig(fileLines(file));
            }
        }
    }

    private void readTraceConfig(Stream<String> lines) {
        // Use TreeSet/TreeMap to keep things sorted in a deterministic
        // order to avoid scrambling the layout on small changes and to
        // ease finding methods in the generated code
        speciesTypes = new TreeSet<>(speciesTypes);
        invokerTypes = new TreeSet<>(invokerTypes);
        callSiteTypes = new TreeSet<>(callSiteTypes);

        TreeMap<String, Set<String>> newDMHMethods = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : dmhMethods.entrySet()) {
            newDMHMethods.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        dmhMethods = newDMHMethods;
        lines.map(line -> line.split(" "))
             .forEach(parts -> {
                switch (parts[0]) {
                    case "[SPECIES_RESOLVE]":
                        // Allow for new types of species data classes being resolved here
                        if (parts.length == 3 && parts[1].startsWith("java.lang.invoke.BoundMethodHandle$Species_")) {
                            String species = parts[1].substring("java.lang.invoke.BoundMethodHandle$Species_".length());
                            if (!"L".equals(species)) {
                                speciesTypes.add(expandSignature(species));
                            }
                        }
                        break;
                    case "[LF_RESOLVE]":
                        String methodType = parts[3];
                        validateMethodType(methodType);
                        if (parts[1].equals(INVOKERS_HOLDER_NAME)) {
                            if ("linkToTargetMethod".equals(parts[2]) ||
                                    "linkToCallSite".equals(parts[2])) {
                                callSiteTypes.add(methodType);
                            } else {
                                invokerTypes.add(methodType);
                            }
                        } else if (parts[1].contains("DirectMethodHandle")) {
                            String dmh = parts[2];
                            // ignore getObject etc for now (generated
                            // by default)
                            if (DMH_METHOD_TYPE_MAP.containsKey(dmh)) {
                                addDMHMethodType(dmh, methodType);
                            }
                        }
                        break;
                    default: break; // ignore
                }
            });
    }

    private void addDMHMethodType(String dmh, String methodType) {
        validateMethodType(methodType);
        Set<String> methodTypes = dmhMethods.get(dmh);
        if (methodTypes == null) {
            methodTypes = new TreeSet<>();
            dmhMethods.put(dmh, methodTypes);
        }
        methodTypes.add(methodType);
    }

    private Stream<String> fileLines(File file) {
        try {
            return Files.lines(file.toPath());
        } catch (IOException io) {
            throw new PluginException("Couldn't read file");
        }
    }

    private void validateMethodTypes(Set<String> dmhMethodTypes) {
        for (String type : dmhMethodTypes) {
            validateMethodType(type);
        }
    }

    private void validateMethodType(String type) {
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

    private static void requireBasicType(char c) {
        if ("LIJFD".indexOf(c) < 0) {
            throw new PluginException(
                    "Character " + c + " must correspond to a basic field type: LIJFD");
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        initialize(in);
        // Copy all but DMH_ENTRY to out
        in.transformAndCopy(entry -> {
                // filter out placeholder entries
                String path = entry.path();
                if (path.equals(DIRECT_METHOD_HOLDER_ENTRY) ||
                    path.equals(DELEGATING_METHOD_HOLDER_ENTRY) ||
                    path.equals(INVOKERS_HOLDER_ENTRY) ||
                    path.equals(BASIC_FORMS_HOLDER_ENTRY)) {
                    return null;
                } else {
                    return entry;
                }
            }, out);

        // Generate BMH Species classes
        speciesTypes.forEach(types -> generateBMHClass(types, out));

        // Generate LambdaForm Holder classes
        generateHolderClasses(out);

        // Let it go
        speciesTypes = null;
        invokerTypes = null;
        dmhMethods = null;

        return out.build();
    }

    @SuppressWarnings("unchecked")
    private void generateBMHClass(String types, ResourcePoolBuilder out) {
        try {
            // Generate class
            Map.Entry<String, byte[]> result =
                    JLIA.generateConcreteBMHClassBytes(types);
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

    private void generateHolderClasses(ResourcePoolBuilder out) {
        int count = 0;
        for (Set<String> entry : dmhMethods.values()) {
            count += entry.size();
        }
        MethodType[] directMethodTypes = new MethodType[count];
        int[] dmhTypes = new int[count];
        int index = 0;
        for (Map.Entry<String, Set<String>> entry : dmhMethods.entrySet()) {
            String dmhType = entry.getKey();
            for (String type : entry.getValue()) {
                // The DMH type to actually ask for is retrieved by removing
                // the first argument, which needs to be of Object.class
                MethodType mt = asMethodType(type);
                if (mt.parameterCount() < 1 ||
                    mt.parameterType(0) != Object.class) {
                    throw new PluginException(
                            "DMH type parameter must start with L");
                }
                directMethodTypes[index] = mt.dropParameterTypes(0, 1);
                dmhTypes[index] = DMH_METHOD_TYPE_MAP.get(dmhType);
                index++;
            }
        }

        // The invoker type to ask for is retrieved by removing the first
        // and the last argument, which needs to be of Object.class
        MethodType[] invokerMethodTypes = new MethodType[this.invokerTypes.size()];
        int i = 0;
        for (String invokerType : invokerTypes) {
            MethodType mt = asMethodType(invokerType);
            final int lastParam = mt.parameterCount() - 1;
            if (mt.parameterCount() < 2 ||
                    mt.parameterType(0) != Object.class ||
                    mt.parameterType(lastParam) != Object.class) {
                throw new PluginException(
                        "Invoker type parameter must start and end with Object: " + invokerType);
            }
            mt = mt.dropParameterTypes(lastParam, lastParam + 1);
            invokerMethodTypes[i] = mt.dropParameterTypes(0, 1);
            i++;
        }

        // The callSite type to ask for is retrieved by removing the last
        // argument, which needs to be of Object.class
        MethodType[] callSiteMethodTypes = new MethodType[this.callSiteTypes.size()];
        i = 0;
        for (String callSiteType : callSiteTypes) {
            MethodType mt = asMethodType(callSiteType);
            final int lastParam = mt.parameterCount() - 1;
            if (mt.parameterCount() < 1 ||
                    mt.parameterType(lastParam) != Object.class) {
                throw new PluginException(
                        "CallSite type parameter must end with Object: " + callSiteType);
            }
            callSiteMethodTypes[i] = mt.dropParameterTypes(lastParam, lastParam + 1);
            i++;
        }
        try {
            byte[] bytes = JLIA.generateDirectMethodHandleHolderClassBytes(
                    DIRECT_HOLDER, directMethodTypes, dmhTypes);
            ResourcePoolEntry ndata = ResourcePoolEntry
                    .create(DIRECT_METHOD_HOLDER_ENTRY, bytes);
            out.add(ndata);

            bytes = JLIA.generateDelegatingMethodHandleHolderClassBytes(
                    DELEGATING_HOLDER, directMethodTypes);
            ndata = ResourcePoolEntry.create(DELEGATING_METHOD_HOLDER_ENTRY, bytes);
            out.add(ndata);

            bytes = JLIA.generateInvokersHolderClassBytes(INVOKERS_HOLDER_INTERNAL_NAME,
                    invokerMethodTypes, callSiteMethodTypes);
            ndata = ResourcePoolEntry.create(INVOKERS_HOLDER_ENTRY, bytes);
            out.add(ndata);

            bytes = JLIA.generateBasicFormsClassBytes(BASIC_FORMS_HOLDER);
            ndata = ResourcePoolEntry.create(BASIC_FORMS_HOLDER_ENTRY, bytes);
            out.add(ndata);
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }
    private static final String DIRECT_METHOD_HOLDER_ENTRY =
            "/java.base/" + DIRECT_HOLDER + ".class";
    private static final String DELEGATING_METHOD_HOLDER_ENTRY =
            "/java.base/" + DELEGATING_HOLDER + ".class";
    private static final String BASIC_FORMS_HOLDER_ENTRY =
            "/java.base/" + BASIC_FORMS_HOLDER + ".class";
    private static final String INVOKERS_HOLDER_ENTRY =
            "/java.base/" + INVOKERS_HOLDER_INTERNAL_NAME + ".class";

    // Convert LL -> LL, L3 -> LLL
    public static String expandSignature(String signature) {
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
        Class<?> rtype = simpleType(parts[1].charAt(0));
        if (parameters.isEmpty()) {
            return MethodType.methodType(rtype);
        } else {
            Class<?>[] ptypes = new Class<?>[parameters.length()];
            for (int i = 0; i < ptypes.length; i++) {
                ptypes[i] = simpleType(parameters.charAt(i));
            }
            return MethodType.methodType(rtype, ptypes);
        }
    }

    private static Class<?> simpleType(char c) {
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
