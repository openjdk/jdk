/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The configuration that is the result of resolution or resolution with
 * service binding.
 *
 * <h2><a name="resolution">Resolution</a></h2>
 *
 * <p> Resolution is the process of computing the transitive closure of a set
 * of root modules over a set of observable modules by resolving the
 * dependences expressed by {@code requires} clauses.
 *
 * The <em>dependence graph</em> is augmented with edges that take account of
 * implicitly declared dependences ({@code requires public}) to create a
 * <em>readability graph</em>. A {@code Configuration} encapsulates the
 * resulting graph of {@link ResolvedModule resolved modules}.
 *
 * <p> Suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { requires m2; }
 *     module m2 { requires public m3; }
 *     module m3 { }
 *     module m4 { }
 * } </pre>
 *
 * <p> If the module {@code m1} is resolved then the resulting configuration
 * contains three modules ({@code m1}, {@code m2}, {@code m3}). The edges in
 * its readability graph are: </p>
 * <pre> {@code
 *     m1 --> m2  (meaning m1 reads m2)
 *     m1 --> m3
 *     m2 --> m3
 * } </pre>
 *
 * <p> Resolution is an additive process. When computing the transitive closure
 * then the dependence relation may include dependences on modules in parent
 * configurations. The result is a <em>relative configuration</em> that is
 * relative to a parent configuration and where the readability graph may have
 * edges from modules in the configuration to modules in a parent configuration.
 * </p>
 *
 * <p> Suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { requires m2; requires java.xml; }
 *     module m2 { }
 * } </pre>
 *
 * <p> If module {@code m1} is resolved with the configuration for the {@link
 * java.lang.reflect.Layer#boot() boot} layer as the parent then the resulting
 * configuration contains two modules ({@code m1}, {@code m2}). The edges in
 * its readability graph are:
 * <pre> {@code
 *     m1 --> m2
 *     m1 --> java.xml
 * } </pre>
 * where module {@code java.xml} is in the parent configuration. For
 * simplicity, this example omits the implicitly declared dependence on the
 * {@code java.base} module.
 *
 * <a name="automaticmoduleresolution"></a>
 * <p> {@link ModuleDescriptor#isAutomatic() Automatic} modules receive special
 * treatment during resolution. Each automatic module is resolved so that it
 * reads all other modules in the configuration and all parent configurations.
 * Each automatic module is also resolved as if it {@code requires public} all
 * other automatic modules in the configuration (and all automatic modules in
 * parent configurations). </p>

 * <h2><a name="servicebinding">Service binding</a></h2>
 *
 * <p> Service binding is the process of augmenting a graph of resolved modules
 * from the set of observable modules induced by the service-use dependence
 * ({@code uses} and {@code provides} clauses). Any module that was not
 * previously in the graph requires resolution to compute its transitive
 * closure. Service binding is an iterative process in that adding a module
 * that satisfies some service-use dependence may introduce new service-use
 * dependences. </p>
 *
 * <p> Suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { exports p; uses p.S; }
 *     module m2 { requires m1; provides p.S with p2.S2; }
 *     module m3 { requires m1; requires m4; provides p.S with p3.S3; }
 *     module m4 { }
 * } </pre>
 *
 * <p> If the module {@code m1} is resolved then the resulting graph of modules
 * has one module ({@code m1}). If the graph is augmented with modules induced
 * by the service-use dependence relation then the configuration will contain
 * four modules ({@code m1}, {@code m2}, {@code m3}, {@code m4}). The edges in
 * its readability graph are: </p>
 * <pre> {@code
 *     m2 --> m1
 *     m3 --> m1
 *     m3 --> m4
 * } </pre>
 * <p> The edges in the conceptual service-use graph are: </p>
 * <pre> {@code
 *     m1 --> m2  (meaning m1 uses a service that is provided by m2)
 *     m1 --> m3
 * } </pre>
 *
 * <p> If this configuration is instantiated as a {@code Layer}, and if code in
 * module {@code m1} uses {@link java.util.ServiceLoader ServiceLoader} to
 * iterate over implementations of {@code p.S.class}, then it will iterate over
 * an instance of {@code p2.S2} and {@code p3.S3}. </p>
 *
 * <h3> Example </h3>
 *
 * <p> The following example uses the {@code resolveRequires} method to resolve
 * a module named <em>myapp</em> with the configuration for the boot layer as
 * the parent configuration. It prints the name of each resolved module and
 * the names of the modules that each module reads. </p>
 *
 * <pre>{@code
 *    ModuleFinder finder = ModuleFinder.of(dir1, dir2, dir3);
 *
 *    Configuration parent = Layer.boot().configuration();
 *
 *    Configuration cf = parent.resolveRequires(finder,
 *                                              ModuleFinder.empty(),
 *                                              Set.of("myapp"));
 *    cf.modules().forEach(m -> {
 *        System.out.format("%s -> %s%n",
 *            m.name(),
 *            m.reads().stream()
 *                .map(ResolvedModule::name)
 *                .collect(Collectors.joining(", ")));
 *    });
 * }</pre>
 *
 * @since 9
 * @see java.lang.reflect.Layer
 */
public final class Configuration {

    // @see Configuration#empty()
    private static final Configuration EMPTY_CONFIGURATION = new Configuration();

    private final Configuration parent;

    private final Map<ResolvedModule, Set<ResolvedModule>> graph;
    private final Set<ResolvedModule> modules;
    private final Map<String, ResolvedModule> nameToModule;

    private Configuration() {
        this.parent = null;
        this.graph = Collections.emptyMap();
        this.modules = Collections.emptySet();
        this.nameToModule = Collections.emptyMap();
    }

    private Configuration(Configuration parent, Resolver resolver) {
        Map<ResolvedModule, Set<ResolvedModule>> graph = resolver.finish(this);

        Map<String, ResolvedModule> nameToModule = new HashMap<>();
        for (ResolvedModule resolvedModule : graph.keySet()) {
            nameToModule.put(resolvedModule.name(), resolvedModule);
        }

        this.parent = parent;
        this.graph = graph;
        this.modules = Collections.unmodifiableSet(graph.keySet());
        this.nameToModule = Collections.unmodifiableMap(nameToModule);
    }


    /**
     * Resolves a collection of root modules, with this configuration as its
     * parent, to create a new configuration.
     *
     * <p> Each root module is located using the given {@code before} module
     * finder. If a module is not found then it is located in the parent
     * configuration as if by invoking the {@link #findModule(String)
     * findModule} method. If not found then the module is located using the
     * given {@code after} module finder. The same search order is used to
     * locate transitive dependences. Root modules or dependences that are
     * located in a parent configuration are resolved no further and are not
     * included in the resulting configuration. </p>
     *
     * <p> When all modules have been resolved then the resulting dependency
     * graph is checked to ensure that it does not contain cycles. A
     * readability graph is constructed and then, in conjunction with the
     * module exports and service use, checked for consistency. </p>
     *
     * <p> Resolution and the (post-resolution) consistency checks may fail for
     * following reasons: </p>
     *
     * <ul>
     *     <li><p> A root module, or a direct or transitive dependency, is not
     *     found. </p></li>
     *
     *     <li><p> An error occurs when attempting to find a module.
     *     Possible errors include I/O errors, errors detected parsing a module
     *     descriptor ({@code module-info.class}) or two versions of the same
     *     module are found in the same directory. </p></li>
     *
     *     <li><p> A cycle is detected, say where module {@code m1} requires
     *     module {@code m2} and {@code m2} requires {@code m1}. </p></li>
     *
     *     <li><p> Two or more modules in the configuration export the same
     *     package to a module that reads both. This includes the case where a
     *     module {@code M} containing package {@code p} reads another module
     *     that exports {@code p} to {@code M}. </p></li>
     *
     *     <li><p> A module {@code M} declares that it "{@code uses p.S}" or
     *     "{@code provides p.S with ...}" but package {@code p} is neither in
     *     module {@code M} nor exported to {@code M} by any module that
     *     {@code M} reads. </p></li>
     *
     *     <li><p> A module {@code M} declares that it
     *     "{@code provides ... with q.T}" but package {@code q} is not in
     *     module {@code M}. </p></li>
     *
     *     <li><p> Two or more modules in the configuration are specific to
     *     different {@link ModuleDescriptor#osName() operating systems},
     *     {@link ModuleDescriptor#osArch() architectures}, or {@link
     *     ModuleDescriptor#osVersion() versions}. </p></li>
     *
     *     <li><p> Other implementation specific checks, for example referential
     *     integrity checks to ensure that different versions of tighly coupled
     *     modules cannot be combined in the same configuration. </p></li>
     *
     * </ul>
     *
     * @param  before
     *         The <em>before</em> module finder to find modules
     * @param  after
     *         The <em>after</em> module finder to locate modules when not
     *         located by the {@code before} module finder or in parent
     *         configurations
     * @param  roots
     *         The possibly-empty collection of module names of the modules
     *         to resolve
     *
     * @return The configuration that is the result of resolving the given
     *         root modules
     *
     * @throws ResolutionException
     *         If resolution or the post-resolution checks fail for any of the
     *         reasons listed
     * @throws SecurityException
     *         If locating a module is denied by the security manager
     */
    public Configuration resolveRequires(ModuleFinder before,
                                         ModuleFinder after,
                                         Collection<String> roots)
    {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        Objects.requireNonNull(roots);

        Resolver resolver = new Resolver(before, this, after);
        resolver.resolveRequires(roots);

        return new Configuration(this, resolver);
    }


    /**
     * Resolves a collection of root modules, with service binding, and with
     * this configuration as its parent, to create a new configuration.
     *
     * <p> This method works exactly as specified by {@link #resolveRequires
     * resolveRequires} except that the graph of resolved modules is augmented
     * with modules induced by the service-use dependence relation. </p>
     *
     * <p> More specifically, the root modules are resolved as if by calling
     * {@code resolveRequires}. The resolved modules, and all modules in the
     * parent configurations, with {@link ModuleDescriptor#uses() service
     * dependences} are then examined. All modules found by the given module
     * finders that {@link ModuleDescriptor#provides() provide} an
     * implementation of one or more of the service types are added to the
     * module graph and then resolved as if by calling the {@code
     * resolveRequires} method. Adding modules to the module graph may
     * introduce new service-use dependences and so the process works
     * iteratively until no more modules are added. </p>
     *
     * <p> As service binding involves resolution then it may fail with {@link
     * ResolutionException} for exactly the same reasons specified in
     * {@code resolveRequires}.  </p>
     *
     * @param  before
     *         The <em>before</em> module finder to find modules
     * @param  after
     *         The <em>after</em> module finder to locate modules when not
     *         located by the {@code before} module finder or in parent
     *         configurations
     * @param  roots
     *         The possibly-empty collection of module names of the modules
     *         to resolve
     *
     * @return The configuration that is the result of resolving the given
     *         root modules
     *
     * @throws ResolutionException
     *         If resolution or the post-resolution checks fail for any of the
     *         reasons listed
     * @throws SecurityException
     *         If locating a module is denied by the security manager
     */
    public Configuration resolveRequiresAndUses(ModuleFinder before,
                                                ModuleFinder after,
                                                Collection<String> roots)
    {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        Objects.requireNonNull(roots);

        Resolver resolver = new Resolver(before, this, after);
        resolver.resolveRequires(roots).resolveUses();

        return new Configuration(this, resolver);
    }


    /**
     * Returns the <em>empty</em> configuration. The empty configuration does
     * not contain any modules and does not have a parent.
     *
     * @return The empty configuration
     */
    public static Configuration empty() {
        return EMPTY_CONFIGURATION;
    }


    /**
     * Returns this configuration's parent unless this is the {@linkplain #empty
     * empty configuration}, which has no parent.
     *
     * @return This configuration's parent
     */
    public Optional<Configuration> parent() {
        return Optional.ofNullable(parent);
    }


    /**
     * Returns an immutable set of the resolved modules in this configuration.
     *
     * @return A possibly-empty unmodifiable set of the resolved modules
     *         in this configuration
     */
    public Set<ResolvedModule> modules() {
        return modules;
    }


    /**
     * Finds a resolved module in this configuration, or if not in this
     * configuration, the {@linkplain #parent parent} configurations.
     *
     * @param  name
     *         The module name of the resolved module to find
     *
     * @return The resolved module with the given name or an empty {@code
     *         Optional} if there isn't a module with this name in this
     *         configuration or any parent configuration
     */
    public Optional<ResolvedModule> findModule(String name) {
        Objects.requireNonNull(name);
        if (parent == null)
            return Optional.empty();
        ResolvedModule m = nameToModule.get(name);
        if (m != null)
            return Optional.of(m);
        return parent().flatMap(x -> x.findModule(name));
    }


    Set<ModuleDescriptor> descriptors() {
        if (modules.isEmpty()) {
            return Collections.emptySet();
        } else {
            return modules.stream()
                    .map(ResolvedModule::reference)
                    .map(ModuleReference::descriptor)
                    .collect(Collectors.toSet());
        }
    }

    Set<ResolvedModule> reads(ResolvedModule m) {
        return Collections.unmodifiableSet(graph.get(m));
    }

    /**
     * Returns a string describing this configuration.
     *
     * @return A string describing this configuration
     */
    @Override
    public String toString() {
        return modules().stream()
                .map(ResolvedModule::name)
                .collect(Collectors.joining(", "));
    }
}
