/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.io.PrintStream;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.reflect.Layer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import jdk.internal.module.ModuleHashes;

/**
 * The resolver used by {@link Configuration#resolveRequires} and
 * {@link Configuration#resolveRequiresAndUses}.
 *
 * @implNote The resolver is used at VM startup and so deliberately avoids
 * using lambda and stream usages in code paths used during startup.
 */

final class Resolver {

    private final ModuleFinder beforeFinder;
    private final List<Configuration> parents;
    private final ModuleFinder afterFinder;
    private final PrintStream traceOutput;

    // maps module name to module reference
    private final Map<String, ModuleReference> nameToReference = new HashMap<>();


    Resolver(ModuleFinder beforeFinder,
             List<Configuration> parents,
             ModuleFinder afterFinder,
             PrintStream traceOutput) {
        this.beforeFinder = beforeFinder;
        this.parents = parents;
        this.afterFinder = afterFinder;
        this.traceOutput = traceOutput;
    }


    /**
     * Resolves the given named modules.
     *
     * @throws ResolutionException
     */
    Resolver resolveRequires(Collection<String> roots) {

        // create the visit stack to get us started
        Deque<ModuleDescriptor> q = new ArrayDeque<>();
        for (String root : roots) {

            // find root module
            ModuleReference mref = findWithBeforeFinder(root);
            if (mref == null) {

                if (findInParent(root) != null) {
                    // in parent, nothing to do
                    continue;
                }

                mref = findWithAfterFinder(root);
                if (mref == null) {
                    fail("Module %s not found", root);
                }
            }

            if (isTracing()) {
                trace("Root module %s located", root);
                mref.location().ifPresent(uri -> trace("  (%s)", uri));
            }

            assert mref.descriptor().name().equals(root);
            nameToReference.put(root, mref);
            q.push(mref.descriptor());
        }

        resolve(q);

        return this;
    }

    /**
     * Resolve all modules in the given queue. On completion the queue will be
     * empty and any resolved modules will be added to {@code nameToReference}.
     *
     * @return The set of module resolved by this invocation of resolve
     */
    private Set<ModuleDescriptor> resolve(Deque<ModuleDescriptor> q) {
        Set<ModuleDescriptor> resolved = new HashSet<>();

        while (!q.isEmpty()) {
            ModuleDescriptor descriptor = q.poll();
            assert nameToReference.containsKey(descriptor.name());

            // process dependences
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {

                // only required at compile-time
                if (requires.modifiers().contains(Modifier.STATIC))
                    continue;

                String dn = requires.name();

                // find dependence
                ModuleReference mref = findWithBeforeFinder(dn);
                if (mref == null) {

                    if (findInParent(dn) != null) {
                        // dependence is in parent
                        continue;
                    }

                    mref = findWithAfterFinder(dn);
                    if (mref == null) {
                        fail("Module %s not found, required by %s",
                                dn, descriptor.name());
                    }
                }

                if (!nameToReference.containsKey(dn)) {
                    nameToReference.put(dn, mref);
                    q.offer(mref.descriptor());
                    resolved.add(mref.descriptor());

                    if (isTracing()) {
                        trace("Module %s located, required by %s",
                                dn, descriptor.name());
                        mref.location().ifPresent(uri -> trace("  (%s)", uri));
                    }
                }

            }

            resolved.add(descriptor);
        }

        return resolved;
    }

    /**
     * Augments the set of resolved modules with modules induced by the
     * service-use relation.
     */
    Resolver resolveUses() {

        // Scan the finders for all available service provider modules. As
        // java.base uses services then then module finders will be scanned
        // anyway.
        Map<String, Set<ModuleReference>> availableProviders = new HashMap<>();
        for (ModuleReference mref : findAll()) {
            ModuleDescriptor descriptor = mref.descriptor();
            if (!descriptor.provides().isEmpty()) {

                for (Provides provides :  descriptor.provides()) {
                    String sn = provides.service();

                    // computeIfAbsent
                    Set<ModuleReference> providers = availableProviders.get(sn);
                    if (providers == null) {
                        providers = new HashSet<>();
                        availableProviders.put(sn, providers);
                    }
                    providers.add(mref);
                }

            }
        }

        // create the visit stack
        Deque<ModuleDescriptor> q = new ArrayDeque<>();

        // the initial set of modules that may use services
        Set<ModuleDescriptor> initialConsumers;
        if (Layer.boot() == null) {
            initialConsumers = new HashSet<>();
        } else {
            initialConsumers = parents.stream()
                    .flatMap(Configuration::configurations)
                    .distinct()
                    .flatMap(c -> c.descriptors().stream())
                    .collect(Collectors.toSet());
        }
        for (ModuleReference mref : nameToReference.values()) {
            initialConsumers.add(mref.descriptor());
        }

        // Where there is a consumer of a service then resolve all modules
        // that provide an implementation of that service
        Set<ModuleDescriptor> candidateConsumers = initialConsumers;
        do {
            for (ModuleDescriptor descriptor : candidateConsumers) {
                if (!descriptor.uses().isEmpty()) {
                    for (String service : descriptor.uses()) {
                        Set<ModuleReference> mrefs = availableProviders.get(service);
                        if (mrefs != null) {
                            for (ModuleReference mref : mrefs) {
                                ModuleDescriptor provider = mref.descriptor();
                                if (!provider.equals(descriptor)) {

                                    trace("Module %s provides %s, used by %s",
                                            provider.name(), service, descriptor.name());

                                    String pn = provider.name();
                                    if (!nameToReference.containsKey(pn)) {
                                        if (isTracing()) {
                                            mref.location()
                                                .ifPresent(uri -> trace("  (%s)", uri));
                                        }
                                        nameToReference.put(pn, mref);
                                        q.push(provider);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            candidateConsumers = resolve(q);
        } while (!candidateConsumers.isEmpty());

        return this;
    }


    /**
     * Execute post-resolution checks and returns the module graph of resolved
     * modules as {@code Map}. The resolved modules will be in the given
     * configuration.
     *
     * @param check {@true} to execute the post resolution checks
     */
    Map<ResolvedModule, Set<ResolvedModule>> finish(Configuration cf,
                                                    boolean check)
    {
        if (isTracing()) {
            trace("Result:");
            Set<String> names = nameToReference.keySet();
            names.stream().sorted().forEach(name -> trace("  %s", name));
        }

        if (check) {
            detectCycles();
            checkPlatformConstraints();
            checkHashes();
        }

        Map<ResolvedModule, Set<ResolvedModule>> graph = makeGraph(cf);

        if (check) {
            checkExportSuppliers(graph);
        }

        return graph;
    }

    /**
     * Checks the given module graph for cycles.
     *
     * For now the implementation is a simple depth first search on the
     * dependency graph. We'll replace this later, maybe with Tarjan.
     */
    private void detectCycles() {
        visited = new HashSet<>();
        visitPath = new LinkedHashSet<>(); // preserve insertion order
        for (ModuleReference mref : nameToReference.values()) {
            visit(mref.descriptor());
        }
        visited.clear();
    }

    // the modules that were visited
    private Set<ModuleDescriptor> visited;

    // the modules in the current visit path
    private Set<ModuleDescriptor> visitPath;

    private void visit(ModuleDescriptor descriptor) {
        if (!visited.contains(descriptor)) {
            boolean added = visitPath.add(descriptor);
            if (!added) {
                throw new ResolutionException("Cycle detected: " +
                        cycleAsString(descriptor));
            }
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                String dn = requires.name();

                ModuleReference mref = nameToReference.get(dn);
                if (mref != null) {
                    ModuleDescriptor other = mref.descriptor();
                    if (other != descriptor) {
                        // dependency is in this configuration
                        visit(other);
                    }
                }
            }
            visitPath.remove(descriptor);
            visited.add(descriptor);
        }
    }

    /**
     * Returns a String with a list of the modules in a detected cycle.
     */
    private String cycleAsString(ModuleDescriptor descriptor) {
        List<ModuleDescriptor> list = new ArrayList<>(visitPath);
        list.add(descriptor);
        int index = list.indexOf(descriptor);
        return list.stream()
                .skip(index)
                .map(ModuleDescriptor::name)
                .collect(Collectors.joining(" -> "));
    }


    /**
     * If there are platform specific modules then check that the OS name,
     * architecture and version match.
     *
     * @apiNote This method does not currently check if the OS matches
     *          platform specific modules in parent configurations.
     */
    private void checkPlatformConstraints() {

        // first module encountered that is platform specific
        String savedModuleName = null;
        String savedOsName = null;
        String savedOsArch = null;
        String savedOsVersion = null;

        for (ModuleReference mref : nameToReference.values()) {
            ModuleDescriptor descriptor = mref.descriptor();

            String osName = descriptor.osName().orElse(null);
            String osArch = descriptor.osArch().orElse(null);
            String osVersion = descriptor.osVersion().orElse(null);

            if (osName != null || osArch != null || osVersion != null) {

                if (savedModuleName == null) {

                    savedModuleName = descriptor.name();
                    savedOsName = osName;
                    savedOsArch = osArch;
                    savedOsVersion = osVersion;

                } else {

                    boolean matches = platformMatches(osName, savedOsName)
                            && platformMatches(osArch, savedOsArch)
                            && platformMatches(osVersion, savedOsVersion);

                    if (!matches) {
                        String s1 = platformAsString(savedOsName,
                                                     savedOsArch,
                                                     savedOsVersion);

                        String s2 = platformAsString(osName, osArch, osVersion);
                        fail("Mismatching constraints on target platform: "
                                + savedModuleName + ": " + s1
                                + ", " + descriptor.name() + ": " + s2);
                    }

                }

            }
        }

    }

    /**
     * Returns true if the s1 and s2 are equal or one of them is null.
     */
    private boolean platformMatches(String s1, String s2) {
        if (s1 == null || s2 == null)
            return true;
        else
            return Objects.equals(s1, s2);
    }

    /**
     * Return a string that encodes the OS name/arch/version.
     */
    private String platformAsString(String osName,
                                    String osArch,
                                    String osVersion) {

        return new StringJoiner("-")
                .add(Objects.toString(osName, "*"))
                .add(Objects.toString(osArch, "*"))
                .add(Objects.toString(osVersion, "*"))
                .toString();

    }

    /**
     * Checks the hashes in the module descriptor to ensure that they match
     * any recorded hashes.
     */
    private void checkHashes() {
        for (ModuleReference mref : nameToReference.values()) {
            ModuleDescriptor descriptor = mref.descriptor();

            // get map of module hashes
            Optional<ModuleHashes> ohashes = descriptor.hashes();
            if (!ohashes.isPresent())
                continue;
            ModuleHashes hashes = ohashes.get();

            String algorithm = hashes.algorithm();
            for (String dn : hashes.names()) {
                ModuleReference other = nameToReference.get(dn);
                if (other == null) {
                    ResolvedModule resolvedModule = findInParent(dn);
                    if (resolvedModule != null)
                        other = resolvedModule.reference();
                }

                // skip checking the hash if the module has been patched
                if (other != null && !other.isPatched()) {
                    byte[] recordedHash = hashes.hashFor(dn);
                    byte[] actualHash = other.computeHash(algorithm);
                    if (actualHash == null)
                        fail("Unable to compute the hash of module %s", dn);
                    if (!Arrays.equals(recordedHash, actualHash)) {
                        fail("Hash of %s (%s) differs to expected hash (%s)" +
                             " recorded in %s", dn, toHexString(actualHash),
                             toHexString(recordedHash), descriptor.name());
                    }
                }
            }

        }
    }

    private static String toHexString(byte[] ba) {
        StringBuilder sb = new StringBuilder(ba.length * 2);
        for (byte b: ba) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }


    /**
     * Computes the readability graph for the modules in the given Configuration.
     *
     * The readability graph is created by propagating "requires" through the
     * "requires transitive" edges of the module dependence graph. So if the
     * module dependence graph has m1 requires m2 && m2 requires transitive m3
     * then the resulting readability graph will contain m1 reads m2, m1 reads m3,
     * and m2 reads m3.
     */
    private Map<ResolvedModule, Set<ResolvedModule>> makeGraph(Configuration cf) {

        // initial capacity of maps to avoid resizing
        int capacity = 1 + (4 * nameToReference.size())/ 3;

        // the "reads" graph starts as a module dependence graph and
        // is iteratively updated to be the readability graph
        Map<ResolvedModule, Set<ResolvedModule>> g1 = new HashMap<>(capacity);

        // the "requires transitive" graph, contains requires transitive edges only
        Map<ResolvedModule, Set<ResolvedModule>> g2;

        // need "requires transitive" from the modules in parent configurations
        // as there may be selected modules that have a dependency on modules in
        // the parent configuration.
        if (Layer.boot() == null) {
            g2 = new HashMap<>(capacity);
        } else {
            g2 = parents.stream()
                .flatMap(Configuration::configurations)
                .distinct()
                .flatMap(c ->
                    c.modules().stream().flatMap(m1 ->
                        m1.descriptor().requires().stream()
                            .filter(r -> r.modifiers().contains(Modifier.TRANSITIVE))
                            .flatMap(r -> {
                                Optional<ResolvedModule> m2 = c.findModule(r.name());
                                assert m2.isPresent()
                                        || r.modifiers().contains(Modifier.STATIC);
                                return m2.stream();
                            })
                            .map(m2 -> Map.entry(m1, m2))
                    )
                )
                // stream of m1->m2
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        HashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
            ));
        }

        // populate g1 and g2 with the dependences from the selected modules

        Map<String, ResolvedModule> nameToResolved = new HashMap<>(capacity);

        for (ModuleReference mref : nameToReference.values()) {
            ModuleDescriptor descriptor = mref.descriptor();
            String name = descriptor.name();

            ResolvedModule m1 = computeIfAbsent(nameToResolved, name, cf, mref);

            Set<ResolvedModule> reads = new HashSet<>();
            Set<ResolvedModule> requiresTransitive = new HashSet<>();

            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                String dn = requires.name();

                ResolvedModule m2 = null;
                ModuleReference mref2 = nameToReference.get(dn);
                if (mref2 != null) {
                    // same configuration
                    m2 = computeIfAbsent(nameToResolved, dn, cf, mref2);
                } else {
                    // parent configuration
                    m2 = findInParent(dn);
                    if (m2 == null) {
                        assert requires.modifiers().contains(Modifier.STATIC);
                        continue;
                    }
                }

                // m1 requires m2 => m1 reads m2
                reads.add(m2);

                // m1 requires transitive m2
                if (requires.modifiers().contains(Modifier.TRANSITIVE)) {
                    requiresTransitive.add(m2);
                }

            }

            // automatic modules read all selected modules and all modules
            // in parent configurations
            if (descriptor.isAutomatic()) {

                // reads all selected modules
                // `requires transitive` all selected automatic modules
                for (ModuleReference mref2 : nameToReference.values()) {
                    ModuleDescriptor descriptor2 = mref2.descriptor();
                    String name2 = descriptor2.name();

                    if (!name.equals(name2)) {
                        ResolvedModule m2
                            = computeIfAbsent(nameToResolved, name2, cf, mref2);
                        reads.add(m2);
                        if (descriptor2.isAutomatic())
                            requiresTransitive.add(m2);
                    }
                }

                // reads all modules in parent configurations
                // `requires transitive` all automatic modules in parent
                // configurations
                for (Configuration parent : parents) {
                    parent.configurations()
                            .map(Configuration::modules)
                            .flatMap(Set::stream)
                            .forEach(m -> {
                                reads.add(m);
                                if (m.reference().descriptor().isAutomatic())
                                    requiresTransitive.add(m);
                            });
                }
            }

            g1.put(m1, reads);
            g2.put(m1, requiresTransitive);
        }

        // Iteratively update g1 until there are no more requires transitive
        // to propagate
        boolean changed;
        List<ResolvedModule> toAdd = new ArrayList<>();
        do {
            changed = false;
            for (Set<ResolvedModule> m1Reads : g1.values()) {
                for (ResolvedModule m2 : m1Reads) {
                    Set<ResolvedModule> m2RequiresTransitive = g2.get(m2);
                    if (m2RequiresTransitive != null) {
                        for (ResolvedModule m3 : m2RequiresTransitive) {
                            if (!m1Reads.contains(m3)) {
                                // m1 reads m2, m2 requires transitive m3
                                // => need to add m1 reads m3
                                toAdd.add(m3);
                            }
                        }
                    }
                }
                if (!toAdd.isEmpty()) {
                    m1Reads.addAll(toAdd);
                    toAdd.clear();
                    changed = true;
                }
            }
        } while (changed);

        return g1;
    }

    /**
     * Equivalent to
     * <pre>{@code
     *     map.computeIfAbsent(name, k -> new ResolvedModule(cf, mref))
     * </pre>}
     */
    private ResolvedModule computeIfAbsent(Map<String, ResolvedModule> map,
                                           String name,
                                           Configuration cf,
                                           ModuleReference mref)
    {
        ResolvedModule m = map.get(name);
        if (m == null) {
            m = new ResolvedModule(cf, mref);
            map.put(name, m);
        }
        return m;
    }


    /**
     * Checks the readability graph to ensure that no two modules export the
     * same package to a module. This includes the case where module M has
     * a local package P and M reads another module that exports P to M.
     * Also checks the uses/provides of module M to ensure that it reads a
     * module that exports the package of the service type to M.
     */
    private void checkExportSuppliers(Map<ResolvedModule, Set<ResolvedModule>> graph) {

        for (Map.Entry<ResolvedModule, Set<ResolvedModule>> e : graph.entrySet()) {
            ModuleDescriptor descriptor1 = e.getKey().descriptor();

            // the map of packages that are local or exported to descriptor1
            Map<String, ModuleDescriptor> packageToExporter = new HashMap<>();

            // local packages
            Set<String> packages = descriptor1.packages();
            for (String pn : packages) {
                packageToExporter.put(pn, descriptor1);
            }

            // descriptor1 reads descriptor2
            Set<ResolvedModule> reads = e.getValue();
            for (ResolvedModule endpoint : reads) {
                ModuleDescriptor descriptor2 = endpoint.descriptor();

                for (ModuleDescriptor.Exports export : descriptor2.exports()) {

                    if (export.isQualified()) {
                        if (!export.targets().contains(descriptor1.name()))
                            continue;
                    }

                    // source is exported to descriptor2
                    String source = export.source();
                    ModuleDescriptor other
                        = packageToExporter.putIfAbsent(source, descriptor2);

                    if (other != null && other != descriptor2) {
                        // package might be local to descriptor1
                        if (other == descriptor1) {
                            fail("Module %s contains package %s"
                                 + ", module %s exports package %s to %s",
                                    descriptor1.name(),
                                    source,
                                    descriptor2.name(),
                                    source,
                                    descriptor1.name());
                        } else {
                            fail("Modules %s and %s export package %s to module %s",
                                    descriptor2.name(),
                                    other.name(),
                                    source,
                                    descriptor1.name());
                        }

                    }
                }
            }

            // uses/provides checks not applicable to automatic modules
            if (!descriptor1.isAutomatic()) {

                // uses S
                for (String service : descriptor1.uses()) {
                    String pn = packageName(service);
                    if (!packageToExporter.containsKey(pn)) {
                        fail("Module %s does not read a module that exports %s",
                             descriptor1.name(), pn);
                    }
                }

                // provides S
                for (ModuleDescriptor.Provides provides : descriptor1.provides()) {
                    String pn = packageName(provides.service());
                    if (!packageToExporter.containsKey(pn)) {
                        fail("Module %s does not read a module that exports %s",
                             descriptor1.name(), pn);
                    }

                    for (String provider : provides.providers()) {
                        if (!packages.contains(packageName(provider))) {
                            fail("Provider %s not in module %s",
                                 provider, descriptor1.name());
                        }
                    }
                }

            }

        }

    }

    /**
     * Find a module of the given name in the parent configurations
     */
    private ResolvedModule findInParent(String mn) {
        for (Configuration parent : parents) {
            Optional<ResolvedModule> om = parent.findModule(mn);
            if (om.isPresent())
                return om.get();
        }
        return null;
    }


    /**
     * Invokes the beforeFinder to find method to find the given module.
     */
    private ModuleReference findWithBeforeFinder(String mn) {
        try {
            return beforeFinder.find(mn).orElse(null);
        } catch (FindException e) {
            // unwrap
            throw new ResolutionException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Invokes the afterFinder to find method to find the given module.
     */
    private ModuleReference findWithAfterFinder(String mn) {
        try {
            return afterFinder.find(mn).orElse(null);
        } catch (FindException e) {
            // unwrap
            throw new ResolutionException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Returns the set of all modules that are observable with the before
     * and after ModuleFinders.
     */
    private Set<ModuleReference> findAll() {
        try {

            Set<ModuleReference> beforeModules = beforeFinder.findAll();
            Set<ModuleReference> afterModules = afterFinder.findAll();

            if (afterModules.isEmpty())
                return beforeModules;

            if (beforeModules.isEmpty()
                    && parents.size() == 1
                    && parents.get(0) == Configuration.empty())
                return afterModules;

            Set<ModuleReference> result = new HashSet<>(beforeModules);
            for (ModuleReference mref : afterModules) {
                String name = mref.descriptor().name();
                if (!beforeFinder.find(name).isPresent()
                        && findInParent(name) == null) {
                    result.add(mref);
                }
            }

            return result;

        } catch (FindException e) {
            // unwrap
            throw new ResolutionException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Returns the package name
     */
    private static String packageName(String cn) {
        int index = cn.lastIndexOf(".");
        return (index == -1) ? "" : cn.substring(0, index);
    }

    /**
     * Throw ResolutionException with the given format string and arguments
     */
    private static void fail(String fmt, Object ... args) {
        String msg = String.format(fmt, args);
        throw new ResolutionException(msg);
    }

    /**
     * Tracing support
     */

    private boolean isTracing() {
        return traceOutput != null;
    }

    private void trace(String fmt, Object ... args) {
        if (traceOutput != null) {
            traceOutput.format("[Resolver] " + fmt, args);
            traceOutput.println();
        }
    }

}
