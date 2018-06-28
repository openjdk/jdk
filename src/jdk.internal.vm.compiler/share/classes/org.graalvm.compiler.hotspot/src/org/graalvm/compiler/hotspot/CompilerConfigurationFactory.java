/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRPhaseSuite;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.InitTimer;

/**
 * A factory that creates the {@link CompilerConfiguration} the Graal compiler will use. Each
 * factory must have a unique {@link #name} and {@link #autoSelectionPriority}. The latter imposes a
 * total ordering between factories for the purpose of auto-selecting the factory to use.
 */
public abstract class CompilerConfigurationFactory implements Comparable<CompilerConfigurationFactory> {

    enum ShowConfigurationLevel {
        none,
        info,
        verbose
    }

    static class Options {
        // @formatter:off
        @Option(help = "Names the Graal compiler configuration to use. If ommitted, the compiler configuration " +
                       "with the highest auto-selection priority is used. To see the set of available configurations, " +
                       "supply the value 'help' to this option.", type = OptionType.Expert)
        public static final OptionKey<String> CompilerConfiguration = new OptionKey<>(null);
        @Option(help = "Writes to the VM log information about the Graal compiler configuration selected.", type = OptionType.User)
        public static final OptionKey<ShowConfigurationLevel> ShowConfiguration = new EnumOptionKey<>(ShowConfigurationLevel.none);
        // @formatter:on
    }

    /**
     * The name of this factory. This must be unique across all factory instances and is used when
     * selecting a factory based on the value of {@link Options#CompilerConfiguration}.
     */
    private final String name;

    /**
     * The priority of this factory. This must be unique across all factory instances and is used
     * when selecting a factory when {@link Options#CompilerConfiguration} is omitted
     */
    private final int autoSelectionPriority;

    protected CompilerConfigurationFactory(String name, int autoSelectionPriority) {
        this.name = name;
        this.autoSelectionPriority = autoSelectionPriority;
    }

    public abstract CompilerConfiguration createCompilerConfiguration();

    /**
     * Collect the set of available {@linkplain HotSpotBackendFactory backends} for this compiler
     * configuration.
     */
    public BackendMap createBackendMap() {
        // default to backend with the same name as the compiler configuration
        return new DefaultBackendMap(name);
    }

    /**
     * Returns a name that should uniquely identify this compiler configuration.
     */
    public final String getName() {
        return name;
    }

    public interface BackendMap {
        HotSpotBackendFactory getBackendFactory(Architecture arch);
    }

    public static class DefaultBackendMap implements BackendMap {

        private final EconomicMap<Class<? extends Architecture>, HotSpotBackendFactory> backends = EconomicMap.create();

        @SuppressWarnings("try")
        public DefaultBackendMap(String backendName) {
            try (InitTimer t = timer("HotSpotBackendFactory.register")) {
                for (HotSpotBackendFactory backend : GraalServices.load(HotSpotBackendFactory.class)) {
                    if (backend.getName().equals(backendName)) {
                        Class<? extends Architecture> arch = backend.getArchitecture();
                        HotSpotBackendFactory oldEntry = backends.put(arch, backend);
                        assert oldEntry == null || oldEntry == backend : "duplicate Graal backend";
                    }
                }
            }
        }

        @Override
        public final HotSpotBackendFactory getBackendFactory(Architecture arch) {
            return backends.get(arch.getClass());
        }
    }

    @Override
    public int compareTo(CompilerConfigurationFactory o) {
        if (autoSelectionPriority > o.autoSelectionPriority) {
            return -1;
        }
        if (autoSelectionPriority < o.autoSelectionPriority) {
            return 1;
        }
        assert this == o : "distinct compiler configurations cannot have the same auto selection priority";
        return 0;
    }

    /**
     * Asserts uniqueness of {@link #name} and {@link #autoSelectionPriority} for {@code factory} in
     * {@code factories}.
     */
    private static boolean checkUnique(CompilerConfigurationFactory factory, List<CompilerConfigurationFactory> factories) {
        for (CompilerConfigurationFactory other : factories) {
            if (other != factory) {
                assert !other.name.equals(factory.name) : factory.getClass().getName() + " cannot have the same selector as " + other.getClass().getName() + ": " + factory.name;
                assert other.autoSelectionPriority != factory.autoSelectionPriority : factory.getClass().getName() + " cannot have the same auto-selection priority as " +
                                other.getClass().getName() +
                                ": " + factory.autoSelectionPriority;
            }
        }
        return true;
    }

    /**
     * @return sorted list of {@link CompilerConfigurationFactory}s
     */
    private static List<CompilerConfigurationFactory> getAllCandidates() {
        List<CompilerConfigurationFactory> candidates = new ArrayList<>();
        for (CompilerConfigurationFactory candidate : GraalServices.load(CompilerConfigurationFactory.class)) {
            assert checkUnique(candidate, candidates);
            candidates.add(candidate);
        }
        Collections.sort(candidates);
        return candidates;
    }

    /**
     * Selects and instantiates a {@link CompilerConfigurationFactory}. The selection algorithm is
     * as follows: if {@code name} is non-null, then select the factory with the same name else if
     * {@code Options.CompilerConfiguration.getValue()} is non-null then select the factory whose
     * name matches the value else select the factory with the highest
     * {@link #autoSelectionPriority} value.
     *
     * @param name the name of the compiler configuration to select (optional)
     */
    @SuppressWarnings("try")
    public static CompilerConfigurationFactory selectFactory(String name, OptionValues options) {
        CompilerConfigurationFactory factory = null;
        try (InitTimer t = timer("CompilerConfigurationFactory.selectFactory")) {
            String value = name == null ? Options.CompilerConfiguration.getValue(options) : name;
            if ("help".equals(value)) {
                System.out.println("The available Graal compiler configurations are:");
                for (CompilerConfigurationFactory candidate : getAllCandidates()) {
                    System.out.println("    " + candidate.name);
                }
                System.exit(0);
            } else if (value != null) {
                for (CompilerConfigurationFactory candidate : GraalServices.load(CompilerConfigurationFactory.class)) {
                    if (candidate.name.equals(value)) {
                        factory = candidate;
                        break;
                    }
                }
                if (factory == null) {
                    throw new GraalError("Graal compiler configuration '%s' not found. Available configurations are: %s", value,
                                    getAllCandidates().stream().map(c -> c.name).collect(Collectors.joining(", ")));
                }
            } else {
                List<CompilerConfigurationFactory> candidates = getAllCandidates();
                if (candidates.isEmpty()) {
                    throw new GraalError("No %s providers found", CompilerConfigurationFactory.class.getName());
                }
                factory = candidates.get(0);
            }
        }
        ShowConfigurationLevel level = Options.ShowConfiguration.getValue(options);
        if (level != ShowConfigurationLevel.none) {
            switch (level) {
                case info: {
                    printConfigInfo(factory);
                    break;
                }
                case verbose: {
                    printConfigInfo(factory);
                    CompilerConfiguration config = factory.createCompilerConfiguration();
                    TTY.println("High tier: " + phaseNames(config.createHighTier(options)));
                    TTY.println("Mid tier: " + phaseNames(config.createMidTier(options)));
                    TTY.println("Low tier: " + phaseNames(config.createLowTier(options)));
                    TTY.println("Pre regalloc stage: " + phaseNames(config.createPreAllocationOptimizationStage(options)));
                    TTY.println("Regalloc stage: " + phaseNames(config.createAllocationStage(options)));
                    TTY.println("Post regalloc stage: " + phaseNames(config.createPostAllocationOptimizationStage(options)));
                    config.createAllocationStage(options);
                    break;
                }
            }
        }
        return factory;
    }

    private static void printConfigInfo(CompilerConfigurationFactory factory) {
        URL location = factory.getClass().getResource(factory.getClass().getSimpleName() + ".class");
        TTY.printf("Using Graal compiler configuration '%s' provided by %s loaded from %s%n", factory.name, factory.getClass().getName(), location);
    }

    private static <C> List<String> phaseNames(PhaseSuite<C> suite) {
        Collection<BasePhase<? super C>> phases = suite.getPhases();
        List<String> res = new ArrayList<>(phases.size());
        for (BasePhase<?> phase : phases) {
            res.add(phase.contractorName());
        }
        Collections.sort(res);
        return res;
    }

    private static <C> List<String> phaseNames(LIRPhaseSuite<C> suite) {
        List<LIRPhase<C>> phases = suite.getPhases();
        List<String> res = new ArrayList<>(phases.size());
        for (LIRPhase<?> phase : phases) {
            res.add(phase.getClass().getName());
        }
        Collections.sort(res);
        return res;
    }
}
