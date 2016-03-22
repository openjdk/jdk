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

import java.lang.module.ModuleDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

import jdk.tools.jlink.internal.Utils;
import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.plugin.PluginContext;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.PostProcessorPlugin;

/**
 * This plugin adds/deletes information for 'release' file.
 */
public final class ReleaseInfoPlugin implements PostProcessorPlugin {
    // option name
    public static final String NAME = "release-info";
    public static final String KEYS = "keys";

    @Override
    public Set<PluginType> getType() {
        return Collections.singleton(CATEGORY.PROCESSOR);
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
    public Set<STATE> getState() {
        return EnumSet.of(STATE.FUNCTIONAL);
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
    public void configure(Map<String, String> config, PluginContext ctx) {
        Properties release = ctx != null? ctx.getReleaseProperties() : null;
        if (release != null) {
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
                    try (FileInputStream fis = new FileInputStream(operation)) {
                        release.load(fis);
                    } catch (IOException exp) {
                        throw new RuntimeException(exp);
                    }
                }
                break;
            }
        }
    }

    @Override
    public List<String> process(ExecutableImage image) {
        // Nothing to do! Release info copied already during configure!
        return Collections.emptyList();
    }
}
