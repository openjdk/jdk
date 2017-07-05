/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

 /*
 * @test
 * @summary Test order of plugins
 * @author Jean-Francois Denise
 * @library ../../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main/othervm PluginOrderTest
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.tools.jlink.internal.PluginOrderingGraph;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Plugin.CATEGORY;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class PluginOrderTest {

    public static void main(String[] args) throws Exception {

        validGraph0();
        validGraph1();

        boolean failed = false;

        try {
            withCycles0();
            failed = true;
        } catch (Exception ex) {
            //ok
            System.err.println(ex.getMessage());
        }
        if (failed) {
            throw new Exception("Should have failed");
        }

        try {
            withCycles1();
            failed = true;
        } catch (Exception ex) {
            //ok
            System.err.println(ex.getMessage());
        }
        if (failed) {
            throw new Exception("Should have failed");
        }

        try {
            withCycles2();
            failed = true;
        } catch (Exception ex) {
            //ok
            System.err.println(ex.getMessage());
        }
        if (failed) {
            throw new Exception("Should have failed");
        }
    }

    private static void validGraph0() throws Exception {
        Set<String> set = new HashSet<>();
        set.add("plug2");
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(new Plug("plug2", Collections.emptySet(), Collections.emptySet(),
                CATEGORY.TRANSFORMER));
        plugins.add(new Plug("plug1", set, Collections.emptySet(), CATEGORY.TRANSFORMER));
        List<Plugin> ordered = PluginOrderingGraph.sort(plugins);
        if (ordered.get(0) != plugins.get(1) || ordered.get(1) != plugins.get(0)) {
            throw new Exception("Invalid sorting");
        }
    }

    private static void validGraph1() {
        Set<String> lst1 = new HashSet<>();
        lst1.add("plug2");
        lst1.add("plug3");
        Plugin p1 = new Plug("plug1", lst1, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p2 = new Plug("plug2", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst3 = new HashSet<>();
        lst3.add("plug4");
        lst3.add("plug6");
        Plugin p3 = new Plug("plug3", lst3, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p4 = new Plug("plug4", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst5 = new HashSet<>();
        lst5.add("plug3");
        lst5.add("plug1");
        lst5.add("plug2");
        lst5.add("plug6");
        Plugin p5 = new Plug("plug5", lst5, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst6 = new HashSet<>();
        lst6.add("plug4");
        lst6.add("plug2");
        Plugin p6 = new Plug("plug6", lst6, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p7 = new Plug("plug7", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p8 = new Plug("plug8", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        List<Plugin> plugins = new ArrayList<>();
        plugins.add(p1);
        plugins.add(p2);
        plugins.add(p3);
        plugins.add(p4);
        plugins.add(p5);
        plugins.add(p6);
        plugins.add(p7);
        plugins.add(p8);

        PluginOrderingGraph.sort(plugins);
    }

    private static void withCycles0() throws Exception {
        Set<String> set2 = new HashSet<>();
        set2.add("plug1");
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(new Plug("plug2", set2, Collections.emptySet(),
                CATEGORY.TRANSFORMER));

        Set<String> set1 = new HashSet<>();
        set1.add("plug2");
        plugins.add(new Plug("plug1", set1, Collections.emptySet(), CATEGORY.TRANSFORMER));
        PluginOrderingGraph.sort(plugins);

    }

    private static void withCycles2() {
        Set<String> lst1 = new HashSet<>();
        lst1.add("plug2");
        lst1.add("plug3");
        Plugin p1 = new Plug("plug1", lst1, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p2 = new Plug("plug2", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst3 = new HashSet<>();
        lst3.add("plug4");
        lst3.add("plug6");
        Plugin p3 = new Plug("plug3", lst3, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p4 = new Plug("plug4", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst5 = new HashSet<>();
        lst5.add("plug3");
        lst5.add("plug1");
        lst5.add("plug2");
        Plugin p5 = new Plug("plug5", lst5, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst6 = new HashSet<>();
        lst6.add("plug4");
        lst6.add("plug1");
        Plugin p6 = new Plug("plug6", lst6, Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p7 = new Plug("plug7", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Plugin p8 = new Plug("plug8", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        List<Plugin> plugins = new ArrayList<>();
        plugins.add(p1);
        plugins.add(p2);
        plugins.add(p3);
        plugins.add(p4);
        plugins.add(p5);
        plugins.add(p6);
        plugins.add(p7);
        plugins.add(p8);
        PluginOrderingGraph.sort(plugins);
    }

    private static void withCycles1() {
        Set<String> lst1 = new HashSet<>();
        lst1.add("plug2");
        lst1.add("plug3");
        Plugin p = new Plug("plug1", lst1, Collections.emptySet(), CATEGORY.TRANSFORMER);
        Plugin p2 = new Plug("plug2", Collections.emptySet(), Collections.emptySet(), CATEGORY.TRANSFORMER);

        Set<String> lst3 = new HashSet<>();
        lst3.add("plug2");

        Set<String> lst4 = new HashSet<>();
        lst4.add("plug1");

        Plugin p3 = new Plug("plug3", lst4, lst3, CATEGORY.TRANSFORMER);
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(p);
        plugins.add(p2);
        plugins.add(p3);
        PluginOrderingGraph.sort(plugins);
    }

    private static class Plug implements TransformerPlugin {

        private final Set<String> isBefore;
        private final Set<String> isAfter;
        private final CATEGORY category;
        private final String name;

        private Plug(String name, Set<String> isBefore, Set<String> isAfter, CATEGORY category) {
            this.name = name;
            this.isBefore = isBefore;
            this.isAfter = isAfter;
            this.category = category;
        }

        @Override
        public Set<String> isAfter() {
            return isAfter;
        }

        @Override
        public Set<String> isBefore() {
            return isBefore;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void visit(Pool in, Pool out) {

        }

        @Override
        public Set<PluginType> getType() {
            return Collections.singleton(category);
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
