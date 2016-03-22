/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.lang.reflect.Module;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Plugin.PluginType;

/**
 *
 * @author jdenise
 */
public class Utils {

    private Utils() {}

    // current module
    private static final Module THIS_MODULE = Utils.class.getModule();

    public static final Function<String, String[]> listParser = (argument) -> {
        String[] arguments = null;
        if (argument != null) {
            arguments = argument.split(",");
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = arguments[i].trim();
            }
        }
        return arguments;
    };

    public static boolean isPostProcessor(Plugin.CATEGORY category) {
        return category.equals(Plugin.CATEGORY.VERIFIER)
                || category.equals(Plugin.CATEGORY.PROCESSOR)
                || category.equals(Plugin.CATEGORY.PACKAGER);
    }

    public static boolean isPreProcessor(Plugin.CATEGORY category) {
        return category.equals(Plugin.CATEGORY.COMPRESSOR)
                || category.equals(Plugin.CATEGORY.FILTER)
                || category.equals(Plugin.CATEGORY.MODULEINFO_TRANSFORMER)
                || category.equals(Plugin.CATEGORY.SORTER)
                || category.equals(Plugin.CATEGORY.TRANSFORMER);
    }

    public static boolean isPostProcessor(Plugin prov) {
        if (prov.getType() != null) {
            for (PluginType pt : prov.getType()) {
                if (pt instanceof Plugin.CATEGORY) {
                    return isPostProcessor((Plugin.CATEGORY) pt);
                }
            }
        }
        return false;
    }

    public static boolean isPreProcessor(Plugin prov) {
        if (prov.getType() != null) {
            for (PluginType pt : prov.getType()) {
                if (pt instanceof Plugin.CATEGORY) {
                    return isPreProcessor((Plugin.CATEGORY) pt);
                }
            }
        }
        return false;
    }

    public static Plugin.CATEGORY getCategory(Plugin provider) {
        if (provider.getType() != null) {
            for (Plugin.PluginType t : provider.getType()) {
                if (t instanceof Plugin.CATEGORY) {
                    return (Plugin.CATEGORY) t;
                }
            }
        }
        return null;
    }

    public static List<Plugin> getPostProcessors(List<Plugin> plugins) {
        List<Plugin> res = new ArrayList<>();
        for (Plugin p : plugins) {
            if (isPostProcessor(p)) {
                res.add(p);
            }
        }
        return res;
    }

    public static List<Plugin> getSortedPostProcessors(List<Plugin> plugins) {
        List<Plugin> res = getPostProcessors(plugins);
        res.sort(new Comparator<Plugin>() {
            @Override
            public int compare(Plugin o1, Plugin o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return res;
    }

    public static List<Plugin> getSortedPreProcessors(List<Plugin> plugins) {
        List<Plugin> res = getPreProcessors(plugins);
        res.sort(new Comparator<Plugin>() {
            @Override
            public int compare(Plugin o1, Plugin o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return res;
    }

    public static List<Plugin> getPreProcessors(List<Plugin> plugins) {
        List<Plugin> res = new ArrayList<>();
        for (Plugin p : plugins) {
            if (isPreProcessor(p)) {
                res.add(p);
            }
        }
        return res;
    }

    public static boolean isFunctional(Plugin prov) {
        return prov.getState().contains(Plugin.STATE.FUNCTIONAL);
    }

    public static boolean isAutoEnabled(Plugin prov) {
        return prov.getState().contains(Plugin.STATE.AUTO_ENABLED);
    }

    public static boolean isDisabled(Plugin prov) {
        return prov.getState().contains(Plugin.STATE.DISABLED);
    }

    // is this a builtin (jdk.jlink) plugin?
    public static boolean isBuiltin(Plugin prov) {
        return THIS_MODULE.equals(prov.getClass().getModule());
    }
}
