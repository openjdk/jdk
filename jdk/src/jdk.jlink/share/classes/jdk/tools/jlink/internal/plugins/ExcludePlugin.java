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
package jdk.tools.jlink.internal.plugins;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.internal.Utils;

/**
 *
 * Exclude resources plugin
 */
public final class ExcludePlugin implements TransformerPlugin {

    public static final String NAME = "exclude-resources";
    private Predicate<String> predicate;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void visit(Pool in, Pool out) {
        in.visit((resource) -> {
            if (resource.getType().equals(Pool.ModuleDataType.CLASS_OR_RESOURCE)) {
                resource = predicate.test(resource.getPath()) ? resource : null;
            }
            return resource;
        }, out);
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.FILTER);
        return Collections.unmodifiableSet(set);
    }

    @Override
    public void configure(Map<String, String> config) {
        try {
            String val = config.get(NAME);
            predicate = new ResourceFilter(Utils.listParser.apply(val), true);
        } catch (IOException ex) {
            throw new PluginException(ex);
        }
    }
}
