/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8142968 8299504
 * @modules java.base/jdk.internal.access
 *          java.base/jdk.internal.module
 * @library /test/lib
 * @build ConfigurationTest
 *        jdk.test.lib.util.ModuleInfoWriter
 *        jdk.test.lib.util.ModuleUtils
 * @run junit ConfigurationTest
 * @summary Basic tests for java.lang.module.Configuration
 */

import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import jdk.internal.access.SharedSecrets;
import jdk.internal.module.ModuleTarget;

import jdk.test.lib.util.ModuleInfoWriter;
import jdk.test.lib.util.ModuleUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    /**
     * Creates a "non-strict" builder for building a module. This allows the
     * test the create ModuleDescriptor objects that do not require java.base.
     */
    private static ModuleDescriptor.Builder newBuilder(String mn) {
        return SharedSecrets.getJavaLangModuleAccess()
                .newModuleBuilder(mn, false, Set.of());
    }

    /**
     * Basic test of resolver
     *     m1 requires m2, m2 requires m3
     */
    @Test
    void testBasic() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());

        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        // m1 reads m2
        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));

        // m2 reads m3
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        // m3 reads nothing
        assertTrue(m3.reads().size() == 0);

        // toString
        assertTrue(cf.toString().contains("m1"));
        assertTrue(cf.toString().contains("m2"));
        assertTrue(cf.toString().contains("m3"));
    }


    /**
     * Basic test of "requires transitive":
     *     m1 requires m2, m2 requires transitive m3
     */
    @Test
    void testRequiresTransitive1() {
        // m1 requires m2, m2 requires transitive m3
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m3")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());

        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        // m1 reads m2 and m3
        assertTrue(m1.reads().size() == 2);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));

        // m2 reads m3
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        // m3 reads nothing
        assertTrue(m3.reads().size() == 0);
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m3 requires m2
     */
    @Test
    void testRequiresTransitive2() {

        // cf1: m1 and m2, m2 requires transitive m1

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();

        assertTrue(m1.reads().size() == 0);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf2: m3, m3 requires m2

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());  // in parent
        assertTrue(cf2.findModule("m2").isPresent());  // in parent
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m3 = cf2.findModule("m3").get();
        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2 requires transitive m1, m3 requires m2
     */
    @Test
    void testRequiresTransitive3() {

        // cf1: m1

        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);


        // cf2: m2, m3: m2 requires transitive m1, m3 requires m2

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2, descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());   // in parent
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m2 = cf2.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m2.configuration() == cf2);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2 requires transitive m1
     * - Configuraiton cf3: m3 requires m2
     */
    @Test
    void testRequiresTransitive4() {

        // cf1: m1

        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);


        // cf2: m2 requires transitive m1

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolve(cf1, finder2, "m2");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());  // in parent
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m2.configuration() == cf2);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf3: m3 requires m2

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder3 = ModuleUtils.finderOf(descriptor3);

        Configuration cf3 = resolve(cf2, finder3, "m3");

        assertTrue(cf3.modules().size() == 1);
        assertTrue(cf3.findModule("m1").isPresent());  // in parent
        assertTrue(cf3.findModule("m2").isPresent());  // in parent
        assertTrue(cf3.findModule("m3").isPresent());
        assertTrue(cf3.parents().size() == 1);
        assertTrue(cf3.parents().get(0) == cf2);

        ResolvedModule m3 = cf3.findModule("m3").get();

        assertTrue(m3.configuration() == cf3);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m3 requires transitive m2, m4 requires m3
     */
    @Test
    void testRequiresTransitive5() {

        // cf1: m1, m2 requires transitive m1

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();

        assertTrue(m1.configuration() == cf1);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf1);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf2: m3 requires transitive m2, m4 requires m3

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m2")
                .build();

        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m3")
                .build();


        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3, descriptor4);

        Configuration cf2 = resolve(cf1, finder2, "m3", "m4");

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());   // in parent
        assertTrue(cf2.findModule("m2").isPresent());   // in parent
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.findModule("m4").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m3 = cf2.findModule("m3").get();
        ResolvedModule m4 = cf2.findModule("m4").get();

        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));

        assertTrue(m4.configuration() == cf2);
        assertTrue(m4.reads().size() == 3);
        assertTrue(m4.reads().contains(m1));
        assertTrue(m4.reads().contains(m2));
        assertTrue(m4.reads().contains(m3));
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2 is not observable
     *     resolve m1
     */
    @Test
    void testRequiresStatic1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 1);

        ResolvedModule m1 = cf.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2
     *     resolve m1
     */
    @Test
    void testRequiresStatic2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 1);

        ResolvedModule m1 = cf.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2
     *     resolve m1, m2
     */
    @Test
    void testRequiresStatic3() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolve(finder, "m1", "m2");

        assertTrue(cf.modules().size() == 2);

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));

        assertTrue(m2.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires m2, m3
     *     m2 requires static m2
     *     m3
     */
    @Test
    void testRequiresStatic4() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.STATIC), "m3")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        assertTrue(m1.reads().size() == 2);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        assertTrue(m3.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2
     * - Configuration cf2: m3 requires m1, requires static m2
     */
    @Test
    void testRequiresStatic5() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m1", "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires static":
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m3 requires m1, requires static m2
     */
    @Test
    void testRequiresStatic6() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));
    }


    /**
     * Basic test of "requires static":
     *     (m1 not observable)
     *     m2 requires transitive static m1
     *     m3 requires m2
     */
    @Test
    void testRequiresStatic7() {
        ModuleDescriptor descriptor1 = null;  // not observable

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE,
                                Requires.Modifier.STATIC),
                         "m1")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor2, descriptor3);

        Configuration cf = resolve(finder, "m3");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();
        assertTrue(m2.reads().isEmpty());
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires static":
     * - Configuration cf1: m2 requires transitive static m1
     * - Configuration cf2: m3 requires m2
     */
    @Test
    void testRequiresStatic8() {
        ModuleDescriptor descriptor1 = null;  // not observable

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE,
                                Requires.Modifier.STATIC),
                        "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m2").isPresent());
        ResolvedModule m2 = cf1.findModule("m2").get();
        assertTrue(m2.reads().isEmpty());

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());
        ResolvedModule m3 = cf2.findModule("m3").get();
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of binding services
     *     m1 uses p.S
     *     m2 provides p.S
     */
    @Test
    void testServiceBinding1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveAndBind(finder, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertTrue(m1.configuration() == cf);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of binding services
     *     m1 uses p.S1
     *     m2 provides p.S1, m2 uses p.S2
     *     m3 provides p.S2
     */
    @Test
    void testServiceBinding2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .uses("p.S2")
                .provides("p.S1", List.of("q.Service1Impl"))
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .provides("p.S2", List.of("q.Service2Impl"))
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolveAndBind(finder, "m1");

        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        assertTrue(m1.configuration() == cf);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.configuration() == cf);
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1 uses p.S
     * - Configuration cf2: m2 provides p.S
     */
    @Test
    void testServiceBindingWithConfigurations1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolveAndBind(cf1, finder2); // no roots

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1 uses p.S && provides p.S,
     *                      m2 provides p.S
     * - Configuration cf2: m3 provides p.S
     *                      m4 provides p.S
     */
    @Test
    void testServiceBindingWithConfigurations2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .provides("p.S", List.of("p1.ServiceImpl"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("p2.ServiceImpl"))
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveAndBind(finder1, "m1");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());


        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .provides("p.S", List.of("p3.ServiceImpl"))
                .build();

        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m1")
                .provides("p.S", List.of("p4.ServiceImpl"))
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3, descriptor4);

        Configuration cf2 = resolveAndBind(cf1, finder2); // no roots

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.findModule("m4").isPresent());

        ResolvedModule m1 = cf2.findModule("m1").get();  // should find in parent
        ResolvedModule m2 = cf2.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();
        ResolvedModule m4 = cf2.findModule("m4").get();

        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));

        assertTrue(m4.reads().size() == 1);
        assertTrue(m4.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * Configuration cf1: p@1.0 provides p.S
     * Test configuration cf2: m1 uses p.S, p@2.0 provides p.S
     * Test configuration cf2: m1 uses p.S
     */
    @Test
    void testServiceBindingWithConfigurations3() {
        ModuleDescriptor service = newBuilder("s")
                .exports("p")
                .build();

        ModuleDescriptor provider_v1 = newBuilder("p")
                .version("1.0")
                .requires("s")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(service, provider_v1);

        Configuration cf1 = resolve(finder1, "p");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("s").isPresent());
        assertTrue(cf1.findModule("p").isPresent());

        // p@1.0 in cf1
        ResolvedModule p = cf1.findModule("p").get();
        assertEquals(provider_v1, p.reference().descriptor());


        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("s")
                .uses("p.S")
                .build();

        ModuleDescriptor provider_v2 = newBuilder("p")
                .version("2.0")
                .requires("s")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, provider_v2);


        // finder2 is the before ModuleFinder and so p@2.0 should be located

        Configuration cf2 = resolveAndBind(cf1, finder2, "m1");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);
        assertTrue(cf2.modules().size() == 2);

        // p should be found in cf2
        p = cf2.findModule("p").get();
        assertTrue(p.configuration() == cf2);
        assertEquals(provider_v2, p.reference().descriptor());


        // finder2 is the after ModuleFinder and so p@2.0 should not be located
        // as module p is in parent configuration.

        cf2 = resolveAndBind(cf1, ModuleFinder.of(), finder2, "m1");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);
        assertTrue(cf2.modules().size() == 1);

        // p should be found in cf1
        p = cf2.findModule("p").get();
        assertTrue(p.configuration() == cf1);
        assertEquals(provider_v1, p.reference().descriptor());
    }


    /**
     * Basic test with two module finders.
     *
     * Module m2 can be found by both the before and after finders.
     */
    @Test
    void testWithTwoFinders1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2_v1 = newBuilder("m2")
                .version("1.0")
                .build();

        ModuleDescriptor descriptor2_v2 = newBuilder("m2")
                .version("2.0")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor2_v1);
        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor2_v2);

        Configuration cf = resolve(finder1, finder2, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertEquals(descriptor1, m1.reference().descriptor());
        assertEquals(descriptor2_v1, m2.reference().descriptor());
    }


    /**
     * Basic test with two modules finders and service binding.
     *
     * The before and after ModuleFinders both locate a service provider module
     * named "m2" that provide implementations of the same service type.
     */
    @Test
    void testWithTwoFinders2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2_v1 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleDescriptor descriptor2_v2 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2_v1);
        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2_v2);

        Configuration cf = resolveAndBind(finder1, finder2, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertEquals(descriptor1, m1.reference().descriptor());
        assertEquals(descriptor2_v1, m2.reference().descriptor());
    }


    /**
     * Basic test for resolving a module that is located in the parent
     * configuration.
     */
    @Test
    void testResolvedInParent1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        Configuration cf2 = resolve(cf1, finder, "m1");

        assertTrue(cf2.modules().size() == 1);
    }


    /**
     * Basic test for resolving a module that has a dependency on a module
     * in the parent configuration.
     */
    @Test
    void testResolvedInParent2() {
        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());


        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolve(cf1, ModuleFinder.of(), finder2, "m2");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ResolvedModule m1 = cf2.findModule("m1").get();   // find in parent
        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m1.reads().size() == 0);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of resolving a module that depends on modules in two parent
     * configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2
     * - Configuration cf3(cf1,cf2): m3 requires m1, m2
     */
    @Test
    void testResolvedInMultipleParents1() {
        // Configuration cf1: m1
        ModuleDescriptor descriptor1 = newBuilder("m1").build();
        Configuration cf1 = resolve(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals(List.of(Configuration.empty()), cf1.parents());
        assertTrue(cf1.findModule("m1").isPresent());
        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.configuration() == cf1);

        // Configuration cf2: m2
        ModuleDescriptor descriptor2 = newBuilder("m2").build();
        Configuration cf2 = resolve(ModuleUtils.finderOf(descriptor2), "m2");
        assertEquals(List.of(Configuration.empty()), cf2.parents());
        assertTrue(cf2.findModule("m2").isPresent());
        ResolvedModule m2 = cf2.findModule("m2").get();
        assertTrue(m2.configuration() == cf2);

        // Configuration cf3(cf1,cf2): m3 requires m1 and m2
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .requires("m2")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor3);
        Configuration cf3 = Configuration.resolve(
                finder,
                List.of(cf1, cf2),  // parents
                ModuleFinder.of(),
                Set.of("m3"));
        assertEquals(List.of(cf1, cf2), cf3.parents());
        assertTrue(cf3.findModule("m3").isPresent());
        ResolvedModule m3 = cf3.findModule("m3").get();
        assertTrue(m3.configuration() == cf3);

        // check readability
        assertTrue(m1.reads().isEmpty());
        assertTrue(m2.reads().isEmpty());
        assertEquals(Set.of(m1, m2), m3.reads());
    }


    /**
     * Basic test of resolving a module that depends on modules in three parent
     * configurations arranged in a diamond (two direct parents).
     *
     * The test consists of four configurations:
     * - Configuration cf1: m1
     * - Configuration cf2(cf1): m2 requires m1
     * - Configuration cf3(cf3): m3 requires m1
     * - Configuration cf4(cf2,cf3): m4 requires m1,m2,m3
     */
    @Test
    void testResolvedInMultipleParents2() {
        // Configuration cf1: m1
        ModuleDescriptor descriptor1 = newBuilder("m1").build();
        Configuration cf1 = resolve(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals( List.of(Configuration.empty()), cf1.parents());
        assertTrue(cf1.findModule("m1").isPresent());
        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.configuration() == cf1);

        // Configuration cf2(cf1): m2 requires m1
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .build();
        Configuration cf2 = Configuration.resolve(
                ModuleUtils.finderOf(descriptor2),
                List.of(cf1),  // parents
                ModuleFinder.of(),
                Set.of("m2"));
        assertEquals(List.of(cf1), cf2.parents());
        assertTrue(cf2.findModule("m2").isPresent());
        ResolvedModule m2 = cf2.findModule("m2").get();
        assertTrue(m2.configuration() == cf2);

        // Configuration cf3(cf1): m3 requires m1
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m1")
                .build();
        Configuration cf3 = Configuration.resolve(
                ModuleUtils.finderOf(descriptor3),
                List.of(cf1),  // parents
                ModuleFinder.of(),
                Set.of("m3"));
        assertEquals(List.of(cf1), cf3.parents());
        assertTrue(cf3.findModule("m3").isPresent());
        ResolvedModule m3 = cf3.findModule("m3").get();
        assertTrue(m3.configuration() == cf3);

        // Configuration cf4(cf2,cf3): m4 requires m1,m2,m3
        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m1")
                .requires("m2")
                .requires("m3")
                .build();
        Configuration cf4 = Configuration.resolve(
                ModuleUtils.finderOf(descriptor4),
                List.of(cf2, cf3),  // parents
                ModuleFinder.of(),
                Set.of("m4"));
        assertEquals(List.of(cf2, cf3), cf4.parents());
        assertTrue(cf4.findModule("m4").isPresent());
        ResolvedModule m4 = cf4.findModule("m4").get();
        assertTrue(m4.configuration() == cf4);

        // check readability
        assertTrue(m1.reads().isEmpty());
        assertEquals(Set.of(m1), m2.reads());
        assertEquals(Set.of(m1), m3.reads());
        assertEquals(Set.of(m1, m2, m3), m4.reads());
    }


    /**
     * Basic test of resolving a module that depends on modules in three parent
     * configurations arranged in a diamond (two direct parents).
     *
     * The test consists of four configurations:
     * - Configuration cf1: m1@1
     * - Configuration cf2: m1@2, m2@2
     * - Configuration cf3: m1@3, m2@3, m3@3
     * - Configuration cf4(cf1,cf2,cf3): m4 requires m1,m2,m3
     */
    @Test
    void testResolvedInMultipleParents3() {
        ModuleDescriptor descriptor1, descriptor2, descriptor3;

        // Configuration cf1: m1@1
        descriptor1 = newBuilder("m1").version("1").build();
        Configuration cf1 = resolve(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals(List.of(Configuration.empty()), cf1.parents());

        // Configuration cf2: m1@2, m2@2
        descriptor1 = newBuilder("m1").version("2").build();
        descriptor2 = newBuilder("m2").version("2").build();
        Configuration cf2 = resolve(
                ModuleUtils.finderOf(descriptor1, descriptor2),
                "m1", "m2");
        assertEquals(List.of(Configuration.empty()), cf2.parents());

        // Configuration cf3: m1@3, m2@3, m3@3
        descriptor1 = newBuilder("m1").version("3").build();
        descriptor2 = newBuilder("m2").version("3").build();
        descriptor3 = newBuilder("m3").version("3").build();
        Configuration cf3 = resolve(
                ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3),
                "m1", "m2", "m3");
        assertEquals(List.of(Configuration.empty()), cf3.parents());

        // Configuration cf4(cf1,cf2,cf3): m4 requires m1,m2,m3
        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m1")
                .requires("m2")
                .requires("m3")
                .build();
        Configuration cf4 = Configuration.resolve(
                ModuleUtils.finderOf(descriptor4),
                List.of(cf1, cf2, cf3),  // parents
                ModuleFinder.of(),
                Set.of("m4"));
        assertEquals(List.of(cf1, cf2, cf3), cf4.parents());

        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf3.findModule("m1").isPresent());
        assertTrue(cf3.findModule("m2").isPresent());
        assertTrue(cf3.findModule("m3").isPresent());
        assertTrue(cf4.findModule("m4").isPresent());

        ResolvedModule m1_1 = cf1.findModule("m1").get();
        ResolvedModule m1_2 = cf2.findModule("m1").get();
        ResolvedModule m2_2 = cf2.findModule("m2").get();
        ResolvedModule m1_3 = cf3.findModule("m1").get();
        ResolvedModule m2_3 = cf3.findModule("m2").get();
        ResolvedModule m3_3 = cf3.findModule("m3").get();
        ResolvedModule m4   = cf4.findModule("m4").get();

        assertTrue(m1_1.configuration() == cf1);
        assertTrue(m1_2.configuration() == cf2);
        assertTrue(m2_2.configuration() == cf2);
        assertTrue(m1_3.configuration() == cf3);
        assertTrue(m2_3.configuration() == cf3);
        assertTrue(m3_3.configuration() == cf3);
        assertTrue(m4.configuration() == cf4);

        // check readability
        assertTrue(m1_1.reads().isEmpty());
        assertTrue(m1_2.reads().isEmpty());
        assertTrue(m2_2.reads().isEmpty());
        assertTrue(m1_3.reads().isEmpty());
        assertTrue(m2_3.reads().isEmpty());
        assertTrue(m3_3.reads().isEmpty());
        assertEquals(Set.of(m1_1, m2_2, m3_3), m4.reads());
    }


    /**
     * Basic test of using the beforeFinder to override a module in a parent
     * configuration.
     */
    @Test
    void testOverriding1() {
        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder, "m1");
        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        Configuration cf2 = resolve(cf1, finder, "m1");
        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());
    }

    /**
     * Basic test of using the beforeFinder to override a module in a parent
     * configuration.
     */
    @Test
    void testOverriding2() {
        ModuleDescriptor descriptor1 = newBuilder("m1").build();
        Configuration cf1 = resolve(ModuleUtils.finderOf(descriptor1), "m1");
        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor2 = newBuilder("m2").build();
        Configuration cf2 = resolve(ModuleUtils.finderOf(descriptor2), "m2");
        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ModuleDescriptor descriptor3 = newBuilder("m3").build();
        Configuration cf3 = resolve(ModuleUtils.finderOf(descriptor3), "m3");
        assertTrue(cf3.modules().size() == 1);
        assertTrue(cf3.findModule("m3").isPresent());

        // override m2, m1 and m3 should be found in parent configurations
        ModuleFinder finder = ModuleUtils.finderOf(descriptor2);
        Configuration cf4 = Configuration.resolve(
                finder,
                List.of(cf1, cf2, cf3),
                ModuleFinder.of(),
                Set.of("m1", "m2", "m3"));
        assertTrue(cf4.modules().size() == 1);
        assertTrue(cf4.findModule("m2").isPresent());
        ResolvedModule m2 = cf4.findModule("m2").get();
        assertTrue(m2.configuration() == cf4);
    }


    /**
     * Basic test of using the beforeFinder to override a module in the parent
     * configuration but where implied readability in the picture so that the
     * module in the parent is read.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m1, m3 requires m2
     */
    @Test
    void testOverriding3() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());

        // cf2: m3 requires m2, m1

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m1", "m3");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1_1 = cf1.findModule("m1").get();
        ResolvedModule m1_2 = cf2.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m1_1.configuration() == cf1);
        assertTrue(m1_2.configuration() == cf2);
        assertTrue(m3.configuration() == cf2);


        // check that m3 reads cf1/m1 and cf2/m2
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1_1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Root module not found
     */
    @Test
    void testRootNotFound() {
        assertThrows(FindException.class, () -> resolve(ModuleFinder.of(), "m1"));
    }


    /**
     * Direct dependency not found
     */
    @Test
    void testDirectDependencyNotFound() {
        ModuleDescriptor descriptor1 = newBuilder("m1").requires("m2").build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);
        assertThrows(FindException.class, () -> resolve(finder, "m1"));
    }


    /**
     * Transitive dependency not found
     */
    @Test
    void testTransitiveDependencyNotFound() {
        ModuleDescriptor descriptor1 = newBuilder("m1").requires("m2").build();
        ModuleDescriptor descriptor2 = newBuilder("m2").requires("m3").build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);
        assertThrows(FindException.class, () -> resolve(finder, "m1"));
    }


    /**
     * Service provider dependency not found
     */
    @Test
    void testServiceProviderDependencyNotFound() {

        // service provider dependency (on m3) not found

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .requires("m3")
                .provides("p.S", List.of("q.T"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // should throw ResolutionException because m3 is not found
        assertThrows(FindException.class, () -> resolveAndBind(finder, "m1"));
    }


    /**
     * Simple cycle.
     */
    @Test
    void testSimpleCycle() {
        ModuleDescriptor descriptor1 = newBuilder("m1").requires("m2").build();
        ModuleDescriptor descriptor2 = newBuilder("m2").requires("m3").build();
        ModuleDescriptor descriptor3 = newBuilder("m3").requires("m1").build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);
        assertThrows(ResolutionException.class, () -> resolve(finder, "m1"));
    }

    /**
     * Basic test for detecting cycles involving a service provider module
     */
    @Test
    void testCycleInProvider() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .exports("p")
                .uses("p.S")
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .requires("m3")
                .provides("p.S", List.of("q.T"))
                .build();
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        // should throw ResolutionException because of the m2 <--> m3 cycle
        assertThrows(ResolutionException.class, () -> resolveAndBind(finder, "m1"));
    }


    /**
     * Basic test to detect reading a module with the same name as itself
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m1 requires m2
     */
    @Test
    void testReadModuleWithSameNameAsSelf() {
        ModuleDescriptor descriptor1_v1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor1_v2 = newBuilder("m1")
                .requires("m2")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1_v1, descriptor2);
        Configuration cf1 = resolve(finder1, "m2");
        assertTrue(cf1.modules().size() == 2);

        // resolve should throw ResolutionException
        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1_v2);
        assertThrows(ResolutionException.class, () -> resolve(cf1, finder2, "m1"));
    }


    /**
     * Basic test to detect reading two modules with the same name
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m1, m3 requires transitive m1
     * - Configuration cf3(cf1,cf2): m4 requires m2, m3
     */
    @Test
    void testReadTwoModuleWithSameName() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);
        Configuration cf1 = resolve(finder1, "m2");
        assertTrue(cf1.modules().size() == 2);

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor3);
        Configuration cf2 = resolve(finder2, "m3");
        assertTrue(cf2.modules().size() == 2);

        // should throw ResolutionException as m4 will read modules named "m1".
        ModuleFinder finder3 = ModuleUtils.finderOf(descriptor4);
        assertThrows(ResolutionException.class,
                     () -> Configuration.resolve(finder3,
                                                 List.of(cf1, cf2),
                                                 ModuleFinder.of(),
                                                 Set.of("m4")));
    }


    /**
     * Test two modules exporting package p to a module that reads both.
     */
    @Test
    void testPackageSuppliedByTwoOthers() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .exports("p")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .exports("p", Set.of("m1"))
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        // m2 and m3 export package p to module m1
        assertThrows(ResolutionException.class, () -> resolve(finder, "m1"));
    }


    /**
     * Test the scenario where a module contains a package p and reads
     * a module that exports package p.
     */
    @Test
    void testPackageSuppliedBySelfAndOther() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .packages(Set.of("p"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .exports("p")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m1 contains package p, module m2 exports package p to m1
        assertThrows(ResolutionException.class, () -> resolve(finder, "m1"));
    }


    /**
     * Test the scenario where a module contains a package p and reads
     * a module that also contains a package p.
     */
    @Test
    void testContainsPackageInSelfAndOther() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .packages(Set.of("p"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .packages(Set.of("p"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        // m1 reads m2, m2 reads nothing
        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m2.reads().size() == 0);
    }


    /**
     * Test the scenario where a module that exports a package that is also
     * exported by a module that it reads in a parent layer.
     */
    @Test
    void testExportSamePackageAsBootLayer() {
        ModuleDescriptor descriptor = newBuilder("m1")
                .requires("java.base")
                .exports("java.lang")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration bootConfiguration = ModuleLayer.boot().configuration();

        // m1 contains package java.lang, java.base exports package java.lang to m1
        assertThrows(ResolutionException.class, () -> resolve(bootConfiguration, finder, "m1"));
    }


    /**
     * Test "uses p.S" where p is contained in the same module.
     */
    @Test
    void testContainsService1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .packages(Set.of("p"))
                .uses("p.S")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }


    /**
     * Test "uses p.S" where p is contained in a different module.
     */
    @Test
    void testContainsService2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .packages(Set.of("p"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .uses("p.S")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m2 does not read a module that exports p
        assertThrows(ResolutionException.class, () -> resolve(finder, "m2"));
    }


    /**
     * Test "provides p.S" where p is contained in the same module.
     */
    @Test
    void testContainsService3() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .packages(Set.of("p", "q"))
                .provides("p.S", List.of("q.S1"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }


    /**
     * Test "provides p.S" where p is contained in a different module.
     */
    @Test
    void testContainsService4() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .packages(Set.of("p"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m1")
                .provides("p.S", List.of("q.S1"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m2 does not read a module that exports p
        assertThrows(ResolutionException.class, () -> resolve(finder, "m2"));
    }

    /**
     * Test uses optional service.
     *
     *     module m1 { requires static m2; uses p.S; }
     */
    @Test
    void testUsesOptionalService1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .uses("p.S")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }

    /**
     * Test uses optional service.
     *
     *     module m1 { requires m2; uses p.S; }
     *     module m2 { requires static transitive m3; }
     */
    @Test
    void testUsesOptionalService2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .uses("p.S")
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.STATIC, Requires.Modifier.TRANSITIVE), "m3")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
    }

    /**
     * Test uses optional service.
     *
     *     module m1 { requires m2; uses p.S; }
     *     module m2 { requires transitive m3; }
     *     module m3 { requires static transitive m4; }
     */
    @Test
    void testUsesOptionalService3() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .uses("p.S")
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m3")
                .build();
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires(Set.of(Requires.Modifier.STATIC, Requires.Modifier.TRANSITIVE), "m4")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
    }

    /**
     * Test provides optional service.
     *
     *     module m1 { requires static m2; provides p.S with q.P; }
     */
    @Test
    void testProvidesOptionalService1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .provides("p.S", List.of("q.P"))
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }

    /**
     * Test provides optional service.
     *
     *     module m1 { requires m2; provides p.S with q.P; }
     *     module m2 { requires static transitive m3; }
     */
    @Test
    void testProvidesOptionalService2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .provides("p.S", List.of("q.P"))
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.STATIC, Requires.Modifier.TRANSITIVE), "m3")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
    }

    /**
     * Test provides optional service.
     *
     *     module m1 { requires m2; provides p.S with q.P; }
     *     module m2 { requires transitive m3; }
     *     module m3 { requires static transitive m4; }
     */
    @Test
    void testProvidesOptionalService3() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .provides("p.S", List.of("q.P"))
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m3")
                .build();
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires(Set.of(Requires.Modifier.STATIC, Requires.Modifier.TRANSITIVE), "m4")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);
        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
    }

    /**
     * Test "uses p.S" where p is not exported to the module.
     */
    @Test
    void testServiceTypePackageNotExported1() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .uses("p.S")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        // m1 does not read a module that exports p to m1
        assertThrows(ResolutionException.class, () -> resolve(finder, "m1"));
    }

    /**
     * Test "uses p.S" where p is not exported to the module.
     *
     *     module m1 { requires m2; uses p.S; }
     *     module m2 { contains p; }
     */
    @Test
    void testServiceTypePackageNotExported2() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .uses("p.S")
                .build();
        ModuleDescriptor descriptor2 = newBuilder("m2")
                .packages(Set.of("p"))
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m1 does not read a module that exports p to m1
        assertThrows(ResolutionException.class, () -> resolve(finder, "m1"));
    }


    /**
     * Test the empty configuration.
     */
    @Test
    void testEmptyConfiguration() {
        Configuration cf = Configuration.empty();

        assertTrue(cf.parents().isEmpty());

        assertTrue(cf.modules().isEmpty());
        assertFalse(cf.findModule("java.base").isPresent());
    }

    /**
     * Test creating a configuration containing platform specific modules.
     */
    @ParameterizedTest
    @CsvSource({",", "linux-aarch64,", "linux-aarch64,linux-aarch64"})
    void testPlatformMatch(String targetPlatform1, String targetPlatform2) throws IOException {
        ModuleDescriptor base =  ModuleDescriptor.newModule("java.base").build();
        Path system = writeModule(base, null);

        ModuleDescriptor descriptor1 = ModuleDescriptor.newModule("m1")
                .requires("m2")
                .build();
        Path dir1 = writeModule(descriptor1, targetPlatform1);

        ModuleDescriptor descriptor2 = ModuleDescriptor.newModule("m2").build();
        Path dir2 = writeModule(descriptor2, targetPlatform2);

        ModuleFinder finder = ModuleFinder.of(system, dir1, dir2);

        Configuration cf = resolve(finder, "m1");

        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("java.base").isPresent());
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
    }

    /**
     * Test attempting to create a configuration with modules for different platforms.
     */
    @ParameterizedTest
    @CsvSource({"linux-x64,linux-aarch64", "linux-x64,windows-x64"})
    void testPlatformMisMatch(String targetPlatform1, String targetPlatform2) throws IOException {
        assertThrows(FindException.class,
                () -> testPlatformMatch(targetPlatform1, targetPlatform2));
    }

    // no parents

    @Test
    void testResolveRequiresWithNoParents() {
        ModuleFinder empty = ModuleFinder.of();
        assertThrows(IllegalArgumentException.class,
                () -> Configuration.resolve(empty, List.of(), empty, Set.of()));
    }

    @Test
    void testResolveRequiresAndUsesWithNoParents() {
        ModuleFinder empty = ModuleFinder.of();
        assertThrows(IllegalArgumentException.class,
                () -> Configuration.resolveAndBind(empty, List.of(), empty, Set.of()));
    }


    // parents with modules for specific platforms
    @ParameterizedTest
    @CsvSource({",", "linux-aarch64,", "linux-aarch64,linux-aarch64"})
    void testResolveRequiresWithCompatibleParents(String targetPlatform1,
                                                  String targetPlatform2) throws IOException {
        ModuleDescriptor base = ModuleDescriptor.newModule("java.base").build();
        Path system = writeModule(base, null);

        ModuleDescriptor descriptor1 = ModuleDescriptor.newModule("m1").build();
        Path dir1 = writeModule(descriptor1, targetPlatform1);

        ModuleDescriptor descriptor2 = ModuleDescriptor.newModule("m2").build();
        Path dir2 = writeModule(descriptor2, targetPlatform2);

        ModuleFinder finder1 = ModuleFinder.of(system, dir1);
        Configuration cf1 = resolve(finder1, "m1");

        ModuleFinder finder2 = ModuleFinder.of(system, dir2);
        Configuration cf2 = resolve(finder2, "m2");

        Configuration cf3 = Configuration.resolve(ModuleFinder.of(),
                                                  List.of(cf1, cf2),
                                                  ModuleFinder.of(),
                                                  Set.of());
        assertTrue(cf3.parents().size() == 2);
    }


    @ParameterizedTest
    @CsvSource({"linux-x64,linux-aarch64", "linux-x64,windows-x64"})
    void testResolveRequiresWithConflictingParents(String targetPlatform1,
                                                   String targetPlatform2) throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> testResolveRequiresWithCompatibleParents(targetPlatform1, targetPlatform2));
    }


    // null handling

    @Test
    void testResolveRequiresWithNull1() {
        assertThrows(NullPointerException.class,
                () -> resolve((ModuleFinder) null, ModuleFinder.of()));
    }

    @Test
    void testResolveRequiresWithNull2() {
        assertThrows(NullPointerException.class,
                () -> resolve(ModuleFinder.of(), (ModuleFinder) null));
    }

    @Test
    void testResolveRequiresWithNull3() {
        Configuration empty = Configuration.empty();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolve(null, List.of(empty),  ModuleFinder.of(), Set.of()));
    }

    @Test
    void testResolveRequiresWithNull4() {
        ModuleFinder empty = ModuleFinder.of();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolve(empty, null, empty, Set.of()));
    }

    @Test
    void testResolveRequiresWithNull5() {
        Configuration cf = ModuleLayer.boot().configuration();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolve(ModuleFinder.of(), List.of(cf), null, Set.of()));
    }

    @Test
    void testResolveRequiresWithNull6() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration cf = ModuleLayer.boot().configuration();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolve(empty, List.of(cf), empty, null));
    }

    @Test
    void testResolveRequiresAndUsesWithNull1() {
        assertThrows(NullPointerException.class,
                () -> resolveAndBind((ModuleFinder) null, ModuleFinder.of()));
    }

    @Test
    void testResolveRequiresAndUsesWithNull2() {
        assertThrows(NullPointerException.class,
                () -> resolveAndBind(ModuleFinder.of(), (ModuleFinder) null));
    }

    @Test
    void testResolveRequiresAndUsesWithNull3() {
        Configuration empty = Configuration.empty();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolveAndBind(null, List.of(empty), ModuleFinder.of(), Set.of()));
    }

    @Test
    void testResolveRequiresAndUsesWithNull4() {
        ModuleFinder empty = ModuleFinder.of();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolveAndBind(empty, null, empty, Set.of()));
    }

    @Test
    void testResolveRequiresAndUsesWithNull5() {
        Configuration cf = ModuleLayer.boot().configuration();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolveAndBind(ModuleFinder.of(), List.of(cf), null, Set.of()));
    }

    @Test
    void testResolveRequiresAndUsesWithNull6() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration cf = ModuleLayer.boot().configuration();
        assertThrows(NullPointerException.class,
                () -> Configuration.resolveAndBind(empty, List.of(cf), empty, null));
    }

    @Test
    void testFindModuleWithNull() {
        assertThrows(NullPointerException.class, () -> Configuration.empty().findModule(null));
    }

    // unmodifiable collections

    static Stream<Configuration> configurations() {
        return Stream.of(
                Configuration.empty(),
                ModuleLayer.boot().configuration(),
                resolve(ModuleFinder.of())
        );
    }

    @ParameterizedTest
    @MethodSource("configurations")
    void testUnmodifiableParents(Configuration cf) {
        assertThrows(UnsupportedOperationException.class,
                () -> cf.parents().add(Configuration.empty()));
        assertThrows(UnsupportedOperationException.class,
                () -> cf.parents().remove(Configuration.empty()));
    }

    @ParameterizedTest
    @MethodSource("configurations")
    void testUnmodifiableModules(Configuration cf) {
        ResolvedModule module = ModuleLayer.boot()
                .configuration()
                .findModule("java.base")
                .orElseThrow();
        assertThrows(UnsupportedOperationException.class,
                () -> cf.modules().add(module));
        assertThrows(UnsupportedOperationException.class,
                () -> cf.modules().remove(module));
    }

    /**
     * Invokes parent.resolve(...)
     */
    private static Configuration resolve(Configuration parent,
                                         ModuleFinder before,
                                         ModuleFinder after,
                                         String... roots) {
        return parent.resolve(before, after, Set.of(roots));
    }

    private static Configuration resolve(Configuration parent,
                                         ModuleFinder before,
                                          String... roots) {
        return resolve(parent, before, ModuleFinder.of(), roots);
    }

    private static Configuration resolve(ModuleFinder before,
                                         ModuleFinder after,
                                         String... roots) {
        return resolve(Configuration.empty(), before, after, roots);
    }

    private static Configuration resolve(ModuleFinder before,
                                         String... roots) {
        return resolve(Configuration.empty(), before, roots);
    }


    /**
     * Invokes parent.resolveAndBind(...)
     */
    private static Configuration resolveAndBind(Configuration parent,
                                                ModuleFinder before,
                                                ModuleFinder after,
                                                String... roots) {
        return parent.resolveAndBind(before, after, Set.of(roots));
    }

    private static Configuration resolveAndBind(Configuration parent,
                                                ModuleFinder before,
                                                String... roots) {
        return resolveAndBind(parent, before, ModuleFinder.of(), roots);
    }

    private static Configuration resolveAndBind(ModuleFinder before,
                                                ModuleFinder after,
                                                String... roots) {
        return resolveAndBind(Configuration.empty(), before, after, roots);
    }

    private static Configuration resolveAndBind(ModuleFinder before,
                                                String... roots) {
        return resolveAndBind(Configuration.empty(), before, roots);
    }


    /**
     * Writes a module-info.class. If {@code targetPlatform} is not null then
     * it includes the ModuleTarget class file attribute with the target platform.
     */
    private static Path writeModule(ModuleDescriptor descriptor, String targetPlatform)
        throws IOException
    {
        ModuleTarget target;
        if (targetPlatform != null) {
            target = new ModuleTarget(targetPlatform);
        } else {
            target = null;
        }
        String name = descriptor.name();
        Path dir = Files.createTempDirectory(Paths.get(""), name);
        Path mi = dir.resolve("module-info.class");
        try (OutputStream out = Files.newOutputStream(mi)) {
            ModuleInfoWriter.write(descriptor, target, out);
        }
        return dir;
    }
}
