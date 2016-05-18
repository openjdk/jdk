/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import jdk.tools.jlink.internal.Utils;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.Plugin.Category;
import jdk.tools.jlink.plugin.Plugin.State;
import jdk.tools.jlink.plugin.TransformerPlugin;

/**
 * This plugin adds/deletes information for 'release' file.
 */
public final class ReleaseInfoPlugin implements TransformerPlugin {
    // option name
    public static final String NAME = "release-info";
    public static final String KEYS = "keys";
    private final Map<String, String> release = new HashMap<>();

    @Override
    public Set<Category> getType() {
        return Collections.singleton(Category.METAINFO_ADDER);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public Set<State> getState() {
        return EnumSet.of(State.FUNCTIONAL);
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
    public void configure(Map<String, String> config) {
        String operation = config.get(NAME);
        switch (operation) {
            case "add": {
                // leave it to open-ended! source, java_version, java_full_version
                // can be passed via this option like:
                //
                //     --release-info add:build_type=fastdebug,source=openjdk,java_version=9
                // and put whatever value that was passed in command line.

                config.keySet().stream().
                    filter(s -> !NAME.equals(s)).
                    forEach(s -> release.put(s, config.get(s)));
            }
            break;

            case "del": {
                // --release-info del:keys=openjdk,java_version
                String[] keys = Utils.listParser.apply(config.get(KEYS));
                for (String k : keys) {
                    release.remove(k);
                }
            }
            break;

            default: {
                // --release-info <file>
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(operation)) {
                    props.load(fis);
                } catch (IOException exp) {
                    throw new RuntimeException(exp);
                }
                props.forEach((k, v) -> release.put(k.toString(), v.toString()));
            }
            break;
        }
    }

    @Override
    public void visit(ModulePool in, ModulePool out) {
        in.transformAndCopy(Function.identity(), out);
        out.getReleaseProperties().putAll(in.getReleaseProperties());
        out.getReleaseProperties().putAll(release);
    }
}
