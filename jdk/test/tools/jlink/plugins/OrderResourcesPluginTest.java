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
 * @summary Test sorter plugin
 * @author Jean-Francois Denise
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 * @run main OrderResourcesPluginTest
 */

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.ModulePoolImpl;
import jdk.tools.jlink.internal.plugins.OrderResourcesPlugin;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class OrderResourcesPluginTest {

    public static void main(String[] args) throws Exception {
        new OrderResourcesPluginTest().test();
    }

    public void test() throws Exception {
        ModuleEntry[] array = {
                ModuleEntry.create("/module1/toto1.class", new byte[0]),
                ModuleEntry.create("/module2/toto2.class", new byte[0]),
                ModuleEntry.create("/module3/toto3.class", new byte[0]),
                ModuleEntry.create("/module3/toto3/module-info.class", new byte[0]),
                ModuleEntry.create("/zazou/toto.class", new byte[0]),
                ModuleEntry.create("/module4/zazou.class", new byte[0]),
                ModuleEntry.create("/module5/toto5.class", new byte[0]),
                ModuleEntry.create("/module6/toto6/module-info.class", new byte[0])
        };

        ModuleEntry[] sorted = {
                ModuleEntry.create("/zazou/toto.class", new byte[0]),
                ModuleEntry.create("/module3/toto3/module-info.class", new byte[0]),
                ModuleEntry.create("/module6/toto6/module-info.class", new byte[0]),
                ModuleEntry.create("/module1/toto1.class", new byte[0]),
                ModuleEntry.create("/module2/toto2.class", new byte[0]),
                ModuleEntry.create("/module3/toto3.class", new byte[0]),
                ModuleEntry.create("/module4/zazou.class", new byte[0]),
                ModuleEntry.create("/module5/toto5.class", new byte[0])
        };

        ModuleEntry[] sorted2 = {
            ModuleEntry.create("/module5/toto5.class", new byte[0]),
            ModuleEntry.create("/module6/toto6/module-info.class", new byte[0]),
            ModuleEntry.create("/module4/zazou.class", new byte[0]),
            ModuleEntry.create("/module3/toto3.class", new byte[0]),
            ModuleEntry.create("/module3/toto3/module-info.class", new byte[0]),
            ModuleEntry.create("/module1/toto1.class", new byte[0]),
            ModuleEntry.create("/module2/toto2.class", new byte[0]),
            ModuleEntry.create("/zazou/toto.class", new byte[0])
        };

        ModulePool resources = new ModulePoolImpl();
        for (ModuleEntry r : array) {
            resources.add(r);
        }

        {
            ModulePool out = new ModulePoolImpl();
            Map<String, String> config = new HashMap<>();
            config.put(OrderResourcesPlugin.NAME, "/zazou/*,*/module-info.class");
            TransformerPlugin p = new OrderResourcesPlugin();
            p.configure(config);
            p.visit(resources, out);
            check(out.entries().collect(Collectors.toList()), sorted);
        }

        {
            // Order of resources in the file, then un-ordered resources.
            File order = new File("resources.order");
            order.createNewFile();
            StringBuilder builder = new StringBuilder();
            // 5 first resources come from file
            for (int i = 0; i < 5; i++) {
                String path = sorted2[i].getPath();
                int index = path.indexOf('/', 1);
                path = path.substring(index + 1, path.length() - ".class".length());
                builder.append(path).append("\n");
            }
            Files.write(order.toPath(), builder.toString().getBytes());

            ModulePool out = new ModulePoolImpl();
            Map<String, String> config = new HashMap<>();
            config.put(OrderResourcesPlugin.NAME, "@" + order.getAbsolutePath());
            TransformerPlugin p = new OrderResourcesPlugin();
            p.configure(config);
            p.visit(resources, out);
            check(out.entries().collect(Collectors.toList()), sorted2);

        }
    }

    private void check(Collection<ModuleEntry> outResources,
            ModuleEntry[] sorted) {
        if (outResources.size() != sorted.length) {
            throw new AssertionError("Wrong number of resources:\n"
                    + "expected: " + Arrays.toString(sorted) + ",\n"
                    + "     got: " + outResources);
        }
        int i = 0;
        for (ModuleEntry r : outResources) {
            System.err.println("Resource: " + r);
            if (!sorted[i].getPath().equals(r.getPath())) {
                throw new AssertionError("Resource not properly sorted, difference at: " + i + "\n"
                        + "expected: " + Arrays.toString(sorted) + ",\n"
                        + "     got: " + outResources);
            }
            i++;
        }
    }
}
