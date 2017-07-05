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
 * @run main SorterPluginTest
 */

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import jdk.tools.jlink.internal.PoolImpl;

import jdk.tools.jlink.internal.plugins.SortResourcesPlugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class SorterPluginTest {

    public static void main(String[] args) throws Exception {
        new SorterPluginTest().test();
    }

    public void test() throws Exception {
        ModuleData[] array = {
                Pool.newResource("/module1/toto1", new byte[0]),
                Pool.newResource("/module2/toto1", new byte[0]),
                Pool.newResource("/module3/toto1", new byte[0]),
                Pool.newResource("/module3/toto1/module-info.class", new byte[0]),
                Pool.newResource("/zazou/toto1", new byte[0]),
                Pool.newResource("/module4/zazou", new byte[0]),
                Pool.newResource("/module5/toto1", new byte[0]),
                Pool.newResource("/module6/toto1/module-info.class", new byte[0])
        };

        ModuleData[] sorted = {
                Pool.newResource("/zazou/toto1", new byte[0]),
                Pool.newResource("/module3/toto1/module-info.class", new byte[0]),
                Pool.newResource("/module6/toto1/module-info.class", new byte[0]),
                Pool.newResource("/module1/toto1", new byte[0]),
                Pool.newResource("/module2/toto1", new byte[0]),
                Pool.newResource("/module3/toto1", new byte[0]),
                Pool.newResource("/module4/zazou", new byte[0]),
                Pool.newResource("/module5/toto1", new byte[0]),
};

        ModuleData[] sorted2 = {
            Pool.newResource("/module5/toto1", new byte[0]),
            Pool.newResource("/module6/toto1/module-info.class", new byte[0]),
            Pool.newResource("/module4/zazou", new byte[0]),
            Pool.newResource("/module3/toto1", new byte[0]),
            Pool.newResource("/module3/toto1/module-info.class", new byte[0]),
            Pool.newResource("/module1/toto1", new byte[0]),
            Pool.newResource("/module2/toto1", new byte[0]),
            Pool.newResource("/zazou/toto1", new byte[0]),};

        Pool resources = new PoolImpl();
        for (ModuleData r : array) {
            resources.add(r);
        }

        {
            Pool out = new PoolImpl();
            Map<String, String> config = new HashMap<>();
            config.put(SortResourcesPlugin.NAME, "/zazou/*,*/module-info.class");
            TransformerPlugin p = new SortResourcesPlugin();
            p.configure(config);
            p.visit(resources, out);
            check(out.getContent(), sorted);
        }

        {
            // Order of resources in the file, then un-ordered resources.
            File order = new File("resources.order");
            order.createNewFile();
            StringBuilder builder = new StringBuilder();
            // 5 first resources come from file
            for (int i = 0; i < 5; i++) {
                builder.append(sorted2[i].getPath()).append("\n");
            }
            Files.write(order.toPath(), builder.toString().getBytes());

            Pool out = new PoolImpl();
            Map<String, String> config = new HashMap<>();
            config.put(SortResourcesPlugin.NAME, order.getAbsolutePath());
            TransformerPlugin p = new SortResourcesPlugin();
            p.configure(config);
            p.visit(resources, out);
            check(out.getContent(), sorted2);

        }
    }

    private void check(Collection<ModuleData> outResources,
            ModuleData[] sorted) {
        if (outResources.size() != sorted.length) {
            throw new AssertionError("Wrong number of resources:\n"
                    + "expected: " + Arrays.toString(sorted) + ",\n"
                    + "     got: " + outResources);
        }
        int i = 0;
        for (ModuleData r : outResources) {
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
