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
package plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class CustomPlugin implements TransformerPlugin {

    private final static String NAME = "custom-plugin";

    public CustomPlugin() {
    }

    @Override
    public void visit(Pool in, Pool out) {
        in.visit(new Pool.Visitor() {
            @Override
            public Pool.ModuleData visit(Pool.ModuleData content) {
                return content;
            }
        }, out);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return NAME + "-description";
    }

    @Override
    public void configure(Map<String, String> config) {
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.PROCESSOR);
        return Collections.unmodifiableSet(set);
    }
}
