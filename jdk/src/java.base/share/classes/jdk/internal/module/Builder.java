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
package jdk.internal.module;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * This builder is optimized for reconstituting ModuleDescriptor
 * for installed modules.  The validation should be done at jlink time.
 *
 * 1. skip name validation
 * 2. ignores dependency hashes.
 * 3. ModuleDescriptor skips the defensive copy and directly uses the
 *    sets/maps created in this Builder.
 *
 * SystemModules should contain modules for the boot layer.
 */
final class Builder {
    private static final JavaLangModuleAccess jlma =
        SharedSecrets.getJavaLangModuleAccess();

    private static final Set<Requires.Modifier> MANDATED =
        Collections.singleton(Requires.Modifier.MANDATED);
    private static final Set<Requires.Modifier> PUBLIC =
        Collections.singleton(Requires.Modifier.PUBLIC);

    // Static cache of the most recently seen Version to cheaply deduplicate
    // most Version objects.  JDK modules have the same version.
    static Version cachedVersion;

    final String name;
    final Set<Requires> requires;
    final Set<Exports> exports;
    final Map<String, Provides> provides;
    final Set<String> conceals;
    final int numPackages;
    Set<String> uses;
    Version version;
    String mainClass;
    String osName;
    String osArch;
    String osVersion;

    Builder(String name, int reqs, int exports,
            int provides, int conceals, int packages) {
        this.name = name;
        this.requires = reqs > 0 ? new HashSet<>(reqs) : Collections.emptySet();
        this.exports  = exports > 0 ? new HashSet<>(exports) : Collections.emptySet();
        this.provides = provides > 0 ? new HashMap<>(provides) : Collections.emptyMap();
        this.conceals = conceals > 0 ? new HashSet<>(conceals) : Collections.emptySet();
        this.uses = Collections.emptySet();
        this.numPackages = packages;
    }

    /**
     * Adds a module dependence with the given (and possibly empty) set
     * of modifiers.
     */
    public Builder requires(Set<Requires.Modifier> mods, String mn) {
        requires.add(jlma.newRequires(Collections.unmodifiableSet(mods), mn));
        return this;
    }

    /**
     * Adds a module dependence with an empty set of modifiers.
     */
    public Builder requires(String mn) {
        requires.add(jlma.newRequires(Collections.emptySet(), mn));
        return this;
    }

    /**
     * Adds a module dependence with the given modifier.
     */
    public Builder requires(Requires.Modifier mod, String mn) {
        if (mod == Requires.Modifier.MANDATED) {
            requires.add(jlma.newRequires(MANDATED, mn));
        } else if (mod == Requires.Modifier.PUBLIC) {
            requires.add(jlma.newRequires(PUBLIC, mn));
        } else {
            requires.add(jlma.newRequires(Collections.singleton(mod), mn));
        }
        return this;
    }

    /**
     * Sets the set of service dependences.
     */
    public Builder uses(Set<String> uses) {
        this.uses = uses;
        return this;
    }

    /**
     * Adds an export to a set of target modules.
     */
    public Builder exports(String pn, Set<String> targets) {
        exports.add(jlma.newExports(pn, targets));
        return this;
    }

    /**
     * Adds an export to a target module.
     */
    public Builder exports(String pn, String target) {
        return exports(pn, Collections.singleton(target));
    }

    /**
     * Adds an export.
     */
    public Builder exports(String pn) {
        exports.add(jlma.newExports(pn));
        return this;
    }

    /**
     * Provides service {@code st} with implementations {@code pcs}.
     */
    public Builder provides(String st, Set<String> pcs) {
        if (provides.containsKey(st))
            throw new IllegalStateException("Providers of service "
                    + st + " already declared");
        provides.put(st, jlma.newProvides(st, pcs));
        return this;
    }

    /**
     * Provides service {@code st} with implementation {@code pc}.
     */
    public Builder provides(String st, String pc) {
        return provides(st, Collections.singleton(pc));
    }

    /**
     * Adds a set of (possible empty) concealed packages.
     */
    public Builder conceals(Set<String> packages) {
        conceals.addAll(packages);
        return this;
    }

    /**
     * Adds a concealed package.
     */
    public Builder conceals(String pn) {
        conceals.add(pn);
        return this;
    }

    /**
     * Sets the module version.
     *
     * @throws IllegalArgumentException if {@code v} is null or cannot be
     *         parsed as a version string
     * @throws IllegalStateException if the module version is already set
     *
     * @see Version#parse(String)
     */
    public Builder version(String v) {
        if (version != null)
            throw new IllegalStateException("module version already set");
        Version ver = cachedVersion;
        if (ver != null && v.equals(ver.toString())) {
            version = ver;
        } else {
            cachedVersion = version = Version.parse(v);
        }
        return this;
    }

    /**
     * Sets the module main class.
     *
     * @throws IllegalStateException if already set
     */
    public Builder mainClass(String mc) {
        if (mainClass != null)
            throw new IllegalStateException("main class already set");
        mainClass = mc;
        return this;
    }

    /**
     * Sets the OS name.
     *
     * @throws IllegalStateException if already set
     */
    public Builder osName(String name) {
        if (osName != null)
            throw new IllegalStateException("OS name already set");
        this.osName = name;
        return this;
    }

    /**
     * Sets the OS arch.
     *
     * @throws IllegalStateException if already set
     */
    public Builder osArch(String arch) {
        if (osArch != null)
            throw new IllegalStateException("OS arch already set");
        this.osArch = arch;
        return this;
    }

    /**
     * Sets the OS version.
     *
     * @throws IllegalStateException if already set
     */
    public Builder osVersion(String version) {
        if (osVersion != null)
            throw new IllegalStateException("OS version already set");
        this.osVersion = version;
        return this;
    }

    /**
     * Returns the set of packages that is the union of the exported and
     * concealed packages.
     */
    private Set<String> computePackages(Set<Exports> exports, Set<String> conceals) {
        if (exports.isEmpty())
            return conceals;

        Set<String> pkgs = new HashSet<>(numPackages);
        pkgs.addAll(conceals);
        for (Exports e : exports) {
            pkgs.add(e.source());
        }
        return pkgs;
    }

    /**
     * Builds a {@code ModuleDescriptor} from the components.
     */
    public ModuleDescriptor build() {
        assert name != null;

        return jlma.newModuleDescriptor(name,
                                        false,    // automatic
                                        false,    // assume not synthetic for now
                                        requires,
                                        uses,
                                        exports,
                                        provides,
                                        version,
                                        mainClass,
                                        osName,
                                        osArch,
                                        osVersion,
                                        conceals,
                                        computePackages(exports, conceals));
    }
}
