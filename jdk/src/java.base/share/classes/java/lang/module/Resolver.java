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

import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.reflect.Layer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import jdk.internal.module.Hasher;

/**
 * The resolver used by {@link Configuration#resolveRequires} and
 * {@link Configuration#resolveRequiresAndUses}.
 */

final class Resolver {

    private final ModuleFinder beforeFinder;
    private final Configuration parent;
    private final ModuleFinder afterFinder;

    // maps module name to module reference
    private final Map<String, ModuleReference> nameToReference = new HashMap<>();


    Resolver(ModuleFinder beforeFinder,
             Configuration parent,
             ModuleFinder afterFinder) {
        this.beforeFinder = beforeFinder;
        this.parent = parent;
        this.afterFinder = afterFinder;
    }


    /**
     * Resolves the given named modules.
     *
     * @throws ResolutionException
     */
    Resolver resolveRequires(Collection<String> roots) {

        long start = trace_start("Resolve");

        // create the visit stack to get us started
        Deque<ModuleDescriptor> q = new ArrayDeque<>();
        for (String root : roots) {

            // find root module
            ModuleReference mref = findWithBeforeFinder(root);
            if (mref == null) {
                if (parent.findModule(root).isPresent()) {
                    // in parent, nothing to do
                    continue;
                }
                mref = findWithAfterFinder(root);
                if (mref == null) {
                    fail("Module %s not found", root);
                }
            }

            if (TRACE) {
                trace("Root module %s located", root);
                if (mref.location().isPresent())
                    trace("  (%s)", mref.location().get());
            }

            assert mref.descriptor().name().equals(root);
            nameToReference.put(root, mref);
            q.push(mref.descriptor());
        }

        resolve(q);

        if (TRACE) {
            long duration = System.currentTimeMillis() - start;
            Set<String> names = nameToReference.keySet();
            trace("Resolver completed in %s ms", duration);
            names.stream().sorted().forEach(name -> trace("  %s", name));
        }

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
                String dn = requires.name();

                // find dependence
                ModuleReference mref = findWithBeforeFinder(dn);
                if (mref == null) {
                    if (parent.findModule(dn).isPresent())
                        continue;

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

                    if (TRACE) {
                        trace("Module %s located, required by %s",
                                dn, descriptor.name());
                        if (mref.location().isPresent())
                            trace("  (%s)", mref.location().get());
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

        long start = trace_start("Bind");

        // Scan the finders for all available service provider modules. As
        // java.base uses services then then module finders will be scanned
        // anyway.
        Map<String, Set<ModuleReference>> availableProviders = new HashMap<>();
        for (ModuleReference mref : findAll()) {
            ModuleDescriptor descriptor = mref.descriptor();
            if (!descriptor.provides().isEmpty()) {

                for (String sn : descriptor.provides().keySet()) {
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
        Set<ModuleDescriptor> candidateConsumers = new HashSet<>();
        Configuration p = parent;
        while (p != null) {
            candidateConsumers.addAll(p.descriptors());
            p = p.parent().orElse(null);
        }
        for (ModuleReference mref : nameToReference.values()) {
            candidateConsumers.add(mref.descriptor());
        }


        // Where there is a consumer of a service then resolve all modules
        // that provide an implementation of that service
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

                                        if (TRACE && mref.location().isPresent())
                                            trace("  (%s)", mref.location().get());

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


        if (TRACE) {
            long duration = System.currentTimeMillis() - start;
            Set<String> names = nameToReference.keySet();
            trace("Bind completed in %s ms", duration);
            names.stream().sorted().forEach(name -> trace("  %s", name));
        }

        return this;
    }


    /**
     * Execute post-resolution checks and returns the module graph of resolved
     * modules as {@code Map}. The resolved modules will be in the given
     * configuration.
     */
    Map<ResolvedModule, Set<ResolvedModule>> finish(Configuration cf) {

        detectCycles();

        checkPlatformConstraints();

        checkHashes();

        Map<ResolvedModule, Set<ResolvedModule>> graph = makeGraph(cf);

        checkExportSuppliers(graph);

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
     * the hash of the dependency's module reference.
     */
    private void checkHashes() {

        for (ModuleReference mref : nameToReference.values()) {
            ModuleDescriptor descriptor = mref.descriptor();

            // get map of module names to hash
            Optional<Hasher.DependencyHashes> ohashes = descriptor.hashes();
            if (!ohashes.isPresent())
                continue;
            Hasher.DependencyHashes hashes = ohashes.get();

            // check dependences
            for (ModuleDescriptor.Requires d : descriptor.requires()) {
                String dn = d.name();
                String recordedHash = hashes.hashFor(dn);

                if (recordedHash != null) {

                    ModuleReference other = nameToReference.get(dn);
                    if (other == null) {
                        other = parent.findModule(dn)
                                .map(ResolvedModule::reference)
                                .orElse(null);
                    }
                    if (other == null)
                        throw new InternalError(dn + " not found");

                    String actualHash = other.computeHash(hashes.algorithm());
                    if (actualHash == null)
                        fail("Unable to compute the hash of module %s", dn);

                    if (!recordedHash.equals(actualHash)) {
                        fail("Hash of %s (%s) differs to expected hash (%s)",
                                dn, actualHash, recordedHash);
                    }

                }

            }
        }

    }


    /**
     * Computes and sets the readability graph for the modules in the given
     * Resolution object.
     *
     * The readability graph is created by propagating "requires" through the
     * "public requires" edges of the module dependence graph. So if the module
     * dependence graph has m1 requires m2 && m2 requires public m3 then the
     * resulting readability graph will contain m1 reads m2, m1
     * reads m3, and m2 reads m3.
     *
     * TODO: Use a more efficient algorithm, maybe cache the requires public
     *       in parent configurations.
     */
    private Map<ResolvedModule, Set<ResolvedModule>> makeGraph(Configuration cf) {

        // the "reads" graph starts as a module dependence graph and
        // is iteratively updated to be the readability graph
        Map<ResolvedModule, Set<ResolvedModule>> g1 = new HashMap<>();

        // the "requires public" graph, contains requires public edges only
        Map<ResolvedModule, Set<ResolvedModule>> g2 = new HashMap<>();


        // need "requires public" from the modules in parent configurations as
        // there may be selected modules that have a dependency on modules in
        // the parent configuration.

        Configuration p = parent;
        while (p != null) {
            for (ModuleDescriptor descriptor : p.descriptors()) {
                ResolvedModule x = p.findModule(descriptor.name()).orElse(null);
                if (x == null)
                    throw new InternalError();
                for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                    if (requires.modifiers().contains(Modifier.PUBLIC)) {
                        String dn = requires.name();
                        ResolvedModule y = p.findModule(dn).orElse(null);
                        if (y == null)
                            throw new InternalError(dn + " not found");
                        g2.computeIfAbsent(x, k -> new HashSet<>()).add(y);
                    }
                }
            }

            p = p.parent().orElse(null);
        }

        // populate g1 and g2 with the dependences from the selected modules
        for (ModuleReference mref : nameToReference.values()) {
            ModuleDescriptor descriptor = mref.descriptor();
            ResolvedModule x = new ResolvedModule(cf, mref);

            Set<ResolvedModule> reads = new HashSet<>();
            g1.put(x, reads);

            Set<ResolvedModule> requiresPublic = new HashSet<>();
            g2.put(x, requiresPublic);

            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                String dn = requires.name();

                ResolvedModule y;
                ModuleReference other = nameToReference.get(dn);
                if (other != null) {
                    y = new ResolvedModule(cf, other);  // cache?
                } else {
                    y = parent.findModule(dn).orElse(null);
                    if (y == null)
                        throw new InternalError("unable to find " + dn);
                }

                // m requires other => m reads other
                reads.add(y);

                // m requires public other
                if (requires.modifiers().contains(Modifier.PUBLIC)) {
                    requiresPublic.add(y);
                }

            }

            // automatic modules reads all selected modules and all modules
            // in parent configurations
            if (descriptor.isAutomatic()) {
                String name = descriptor.name();

                // reads all selected modules
                // requires public` all selected automatic modules
                for (ModuleReference mref2 : nameToReference.values()) {
                    ModuleDescriptor descriptor2 = mref2.descriptor();
                    if (!name.equals(descriptor2.name())) {
                        ResolvedModule m = new ResolvedModule(cf, mref2);
                        reads.add(m);
                        if (descriptor2.isAutomatic())
                            requiresPublic.add(m);
                    }
                }

                // reads all modules in parent configurations
                // `requires public` all automatic modules in parent configurations
                p = parent;
                while (p != null) {
                    for (ResolvedModule m : p.modules()) {
                        reads.add(m);
                        if (m.reference().descriptor().isAutomatic())
                            requiresPublic.add(m);
                    }
                    p = p.parent().orElse(null);
                }

            }

        }

        // Iteratively update g1 until there are no more requires public to propagate
        boolean changed;
        Map<ResolvedModule, Set<ResolvedModule>> changes = new HashMap<>();
        do {
            changed = false;
            for (Entry<ResolvedModule, Set<ResolvedModule>> entry : g1.entrySet()) {

                ResolvedModule m1 = entry.getKey();
                Set<ResolvedModule> m1Reads = entry.getValue();

                for (ResolvedModule m2 : m1Reads) {
                    Set<ResolvedModule> m2RequiresPublic = g2.get(m2);
                    if (m2RequiresPublic != null) {
                        for (ResolvedModule m3 : m2RequiresPublic) {
                            if (!m1Reads.contains(m3)) {

                                // computeIfAbsent
                                Set<ResolvedModule> s = changes.get(m1);
                                if (s == null) {
                                    s = new HashSet<>();
                                    changes.put(m1, s);
                                }
                                s.add(m3);
                                changed = true;

                            }
                        }
                    }
                }
            }

            if (changed) {
                for (Map.Entry<ResolvedModule, Set<ResolvedModule>> e :
                        changes.entrySet()) {
                    ResolvedModule m = e.getKey();
                    g1.get(m).addAll(e.getValue());
                }
                changes.clear();
            }

        } while (changed);


        return g1;
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
                            = packageToExporter.put(source, descriptor2);

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

            // uses S
            for (String service : descriptor1.uses()) {
                String pn = packageName(service);
                if (!packageToExporter.containsKey(pn)) {
                    fail("Module %s does not read a module that exports %s",
                            descriptor1.name(), pn);
                }
            }

            // provides S
            for (Map.Entry<String, ModuleDescriptor.Provides> entry :
                    descriptor1.provides().entrySet()) {
                String service = entry.getKey();
                ModuleDescriptor.Provides provides = entry.getValue();

                String pn = packageName(service);
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

            if (beforeModules.isEmpty() && parent == Configuration.empty())
                return afterModules;

            Set<ModuleReference> result = new HashSet<>(beforeModules);
            for (ModuleReference mref : afterModules) {
                String name = mref.descriptor().name();
                if (!beforeFinder.find(name).isPresent()
                        && !parent.findModule(name).isPresent())
                    result.add(mref);
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
     * Tracing support, limited to boot layer for now.
     */

    private final static boolean TRACE
        = Boolean.getBoolean("jdk.launcher.traceResolver")
            && (Layer.boot() == null);

    private String op;

    private long trace_start(String op) {
        this.op = op;
        return System.currentTimeMillis();
    }

    private void trace(String fmt, Object ... args) {
        if (TRACE) {
            System.out.print("[" + op + "] ");
            System.out.format(fmt, args);
            System.out.println();
        }
    }

}
