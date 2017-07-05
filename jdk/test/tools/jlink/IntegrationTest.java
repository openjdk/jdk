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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.tools.jlink.Jlink;
import jdk.tools.jlink.Jlink.JlinkConfiguration;
import jdk.tools.jlink.Jlink.PluginsConfiguration;
import jdk.tools.jlink.builder.DefaultImageBuilder;
import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.PostProcessorPlugin;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.internal.plugins.DefaultCompressPlugin;
import jdk.tools.jlink.internal.plugins.StripDebugPlugin;
import jdk.tools.jlink.plugin.Plugin;

import tests.Helper;
import tests.JImageGenerator;

/*
 * @test
 * @summary Test integration API
 * @author Jean-Francois Denise
 * @library ../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main IntegrationTest
 */
public class IntegrationTest {

    private static final List<Integer> ordered = new ArrayList<>();

    public static class MyPostProcessor implements PostProcessorPlugin {

        public static final String NAME = "mypostprocessor";

        @Override
        public List<String> process(ExecutableImage image) {
            try {
                Files.createFile(image.getHome().resolve("toto.txt"));
                return null;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Set<PluginType> getType() {
            Set<PluginType> set = new HashSet<>();
            set.add(CATEGORY.PROCESSOR);
            return Collections.unmodifiableSet(set);
        }

        @Override
        public void configure(Map<String, String> config) {
            throw new UnsupportedOperationException("Shouldn't be called");
        }
    }

    public static class MyPlugin1 implements TransformerPlugin {

        Integer index;
        Set<String> after;
        Set<String> before;

        private MyPlugin1(Integer index, Set<String> after, Set<String> before) {
            this.index = index;
            this.after = after;
            this.before = before;
        }

        @Override
        public Set<String> isAfter() {
            return after;
        }

        @Override
        public Set<String> isBefore() {
            return before;
        }

        @Override
        public String getName() {
            return NAME + index;
        }

        @Override
        public void visit(Pool in, Pool out) {
            System.err.println(NAME + index);
            ordered.add(index);
            in.visit((file) -> {
                return file;
            }, out);
        }

        @Override
        public Set<PluginType> getType() {
            Set<PluginType> set = new HashSet<>();
            set.add(CATEGORY.TRANSFORMER);
            return Collections.unmodifiableSet(set);
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getOption() {
            return null;
        }
        static final String NAME = "myprovider";
        static final String INDEX = "INDEX";

        @Override
        public void configure(Map<String, String> config) {
            throw new UnsupportedOperationException("Shouldn't be called");
        }
    }

    public static void main(String[] args) throws Exception {

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }
        apitest();
        test();
        testOrder();
        testCycleOrder();
    }

    private static void apitest() throws Exception {
        boolean failed = false;
        Jlink jl = new Jlink();

        try {
            jl.build(null);
            failed = true;
        } catch (Exception ex) {
            // XXX OK
        }
        if (failed) {
            throw new Exception("Should have failed");
        }
        System.out.println(jl);

        JlinkConfiguration config
                = new JlinkConfiguration(null, null, null, null);

        System.out.println(config);

        Plugin p = Jlink.newPlugin("toto", Collections.emptyMap(), null);
        if (p != null) {
            throw new Exception("Plugin should be null");
        }

        Plugin p2 = Jlink.newPlugin("compress", Collections.emptyMap(), null);
        if (p2 == null) {
            throw new Exception("Plugin should not be null");
        }
    }

    private static void test() throws Exception {
        Jlink jlink = new Jlink();
        Path output = Paths.get("integrationout");
        List<Path> modulePaths = new ArrayList<>();
        File jmods
                = JImageGenerator.getJModsDir(new File(System.getProperty("test.jdk")));
        modulePaths.add(jmods.toPath());
        Set<String> mods = new HashSet<>();
        mods.add("java.management");
        Set<String> limits = new HashSet<>();
        limits.add("java.management");
        JlinkConfiguration config = new Jlink.JlinkConfiguration(output,
                modulePaths, mods, limits, null);

        List<Plugin> lst = new ArrayList<>();

        //Strip debug
        {
            Map<String, String> config1 = new HashMap<>();
            config1.put(StripDebugPlugin.NAME, "");
            Plugin strip = Jlink.newPlugin("strip-debug", config1, null);
            lst.add(strip);
        }
        // compress
        {
            Map<String, String> config1 = new HashMap<>();
            config1.put(DefaultCompressPlugin.NAME, "2");
            Plugin compress
                    = Jlink.newPlugin("compress", config1, null);
            lst.add(compress);
        }
        // Post processor
        {
            lst.add(new MyPostProcessor());
        }
        // Image builder
        DefaultImageBuilder builder = new DefaultImageBuilder(true, output);
        PluginsConfiguration plugins
                = new Jlink.PluginsConfiguration(lst, builder, null);

        jlink.build(config, plugins);

        if (!Files.exists(output)) {
            throw new AssertionError("Directory not created");
        }
        File jimage = new File(output.toString(), "lib" + File.separator + "modules");
        if (!jimage.exists()) {
            throw new AssertionError("jimage not generated");
        }
        File bom = new File(output.toString(), "bom");
        if (!bom.exists()) {
            throw new AssertionError("bom not generated");
        }
        File release = new File(output.toString(), "release");
        if (!release.exists()) {
            throw new AssertionError("release not generated");
        }

        if (!Files.exists(output.resolve("toto.txt"))) {
            throw new AssertionError("Post processing not called");
        }

    }

    private static void testOrder() throws Exception {
        Jlink jlink = new Jlink();
        Path output = Paths.get("integrationout2");
        List<Path> modulePaths = new ArrayList<>();
        File jmods
                = JImageGenerator.getJModsDir(new File(System.getProperty("test.jdk")));
        modulePaths.add(jmods.toPath());
        Set<String> mods = new HashSet<>();
        mods.add("java.management");
        Set<String> limits = new HashSet<>();
        limits.add("java.management");
        JlinkConfiguration config = new Jlink.JlinkConfiguration(output,
                modulePaths, mods, limits, null);

        List<Plugin> lst = new ArrayList<>();

        // Order is Plug1>Plug2>Plug3
        // Plug1


        // TRANSFORMER 3, must be after 2.
        {
            Set<String> after = new HashSet<>();
            after.add(MyPlugin1.NAME+"2");
            lst.add(new MyPlugin1(3, after, Collections.emptySet()));
        }

        // TRANSFORMER 2, must be after 1.
        {
            Set<String> after = new HashSet<>();
            after.add(MyPlugin1.NAME+"1");
            lst.add(new MyPlugin1(2, after, Collections.emptySet()));
        }

        // TRANSFORMER 1
        {
            Set<String> before = new HashSet<>();
            before.add(MyPlugin1.NAME+"2");
            lst.add(new MyPlugin1(1, Collections.emptySet(), before));
        }

        // Image builder
        DefaultImageBuilder builder = new DefaultImageBuilder(false, output);
        PluginsConfiguration plugins
                = new Jlink.PluginsConfiguration(lst, builder, null);

        jlink.build(config, plugins);

        if (ordered.isEmpty()) {
            throw new AssertionError("Plugins not called");
        }
        List<Integer> clone = new ArrayList<>();
        clone.addAll(ordered);
        Collections.sort(clone);
        if (!clone.equals(ordered)) {
            throw new AssertionError("Ordered is not properly sorted" + ordered);
        }
    }

    private static void testCycleOrder() throws Exception {
        Jlink jlink = new Jlink();
        Path output = Paths.get("integrationout3");
        List<Path> modulePaths = new ArrayList<>();
        File jmods
                = JImageGenerator.getJModsDir(new File(System.getProperty("test.jdk")));
        modulePaths.add(jmods.toPath());
        Set<String> mods = new HashSet<>();
        mods.add("java.management");
        Set<String> limits = new HashSet<>();
        limits.add("java.management");
        JlinkConfiguration config = new Jlink.JlinkConfiguration(output,
                modulePaths, mods, limits, null);

        List<Plugin> lst = new ArrayList<>();

        // packager 1
        {
            Set<String> before = new HashSet<>();
            before.add(MyPlugin1.NAME+"2");
            lst.add(new MyPlugin1(1, Collections.emptySet(), before));
        }

        // packager 2
        {
            Set<String> before = new HashSet<>();
            before.add(MyPlugin1.NAME+"1");
            lst.add(new MyPlugin1(2, Collections.emptySet(), before));
        }

        // Image builder
        DefaultImageBuilder builder = new DefaultImageBuilder(false, output);
        PluginsConfiguration plugins
                = new Jlink.PluginsConfiguration(lst, builder, null);
        boolean failed = false;
        try {
            jlink.build(config, plugins);
            failed = true;
        } catch (Exception ex) {
            // XXX OK
        }
        if (failed) {
            throw new AssertionError("Should have failed");
        }
    }
}
