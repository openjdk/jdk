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
package jdk.tools.jlink.plugin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.tools.jlink.internal.plugins.PluginsResourceBundle;

/**
 * Base interface that jlink plugins should implement.
 */
public interface Plugin {

    /**
     * Type of plugin.
     */
    public interface PluginType {

        public String getName();
    }

    /**
     * Order of categories:
     * <ol>
     * <li>FILTER: Filter in/out resources or files.</li>
     * <li>TRANSFORMER: Transform resources or files(eg: refactoring, bytecode
     * manipulation).</li>
     * <li>MODULEINFO_TRANSFORMER: Transform only module-info.class</li>
     * <li>SORTER: Sort resources within the resource container.</li>
     * <li>COMPRESSOR: Compress resource within the resouce containers.</li>
     * <li>VERIFIER: Does some image verification.</li>
     * <li>PROCESSOR: Does some post processing on image.</li>
     * <li>PACKAGER: Final processing</li>
     * </ol>
     */
    public enum CATEGORY implements PluginType {
        FILTER("FILTER"),
        TRANSFORMER("TRANSFORMER"),
        MODULEINFO_TRANSFORMER("MODULEINFO_TRANSFORMER"),
        SORTER("SORTER"),
        COMPRESSOR("COMPRESSOR"),
        VERIFIER("VERIFIER"),
        PROCESSOR("PROCESSOR"),
        PACKAGER("PACKAGER");

        private final String name;

        CATEGORY(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * Plugin state:
     * <ul>
     * <li>DISABLED: The plugin is not exposed in help and will be not called.</li>
     * <li>AUTO_ENABLED: The plugin is enabled by default. It doesn't require its
     * option to be present to be called.<li>
     * <li>FUNCTIONAL: The plugin is properly configured and can operate.
     * Non functional plugin must advertise their status in the
     * {@link #getStateDescription() getStateDescription} method</li>
     * </ul>
     */
    public enum STATE {
        DISABLED,
        AUTO_ENABLED,
        FUNCTIONAL
    }

    /**
     * The Plugin set of types.
     * @return The set of types.
     */
    public default Set<PluginType> getType() {
        return Collections.emptySet();
    }

    /**
     * The Plugin set of states.
     * @return The set of states.
     */
    public default Set<STATE> getState() {
        return EnumSet.of(STATE.FUNCTIONAL);
    }

    /**
     * The set of plugin names that must be located, within the stack of plugins,
     * before this plugin.
     * @return The set of names. By default this set is empty.
     */
    public default Set<String> isBefore() {
        return Collections.emptySet();
    }

    /**
     * The set of plugin names that must be located, within the stack of plugins,
     * after this plugin.
     * @return The set of names. By default this set is empty.
     */
    public default Set<String> isAfter() {
        return Collections.emptySet();
    }

    /**
     * The plugin name.
     * @return The name.
     */
    public default String getName() {
        return getClass().getName().replace('.', '-');
    }

    /**
     * The plugin description.
     * @return  The description.
     */
    public default String getDescription() {
        return "";
    }

    /**
     * The option that identifies this plugin. This may be null.
     * "--" is prefixed to the String (when non-null) when invoking
     * this plugin from jlink command line.
     *
     * @return The plugin option.
     */
    public default String getOption() {
        return getName();
    }

    /**
     * Has this plugin require one or more arguments?
     * A plugin can have one or more optional arguments.
     * <br>
     * A plugin option with a single argument is specified as follows:
     * <pre>
     *     --plugin-option=arg_value
     * </pre>
     * If there are more than arguments, command line option looks like:
     * <pre>
     *     --plugin-option=arg_value:arg2=value2:arg3=value3...
     *</pre>
     *
     * @return true if arguments are needed.
     */
    public default boolean hasArguments() {
        return false;
    }

    /**
     * The plugin argument(s) description.
     * @return  The argument(s) description.
     */
    public default String getArgumentsDescription() {
        return "";
    }

    /**
     * Return a message indicating the status of the provider.
     *
     * @return A status description.
     */
    public default String getStateDescription() {
        return getState().contains(STATE.FUNCTIONAL)
                ? PluginsResourceBundle.getMessage("main.status.ok")
                : PluginsResourceBundle.getMessage("main.status.not.ok");
    }

    /**
     * Configure the plugin based on the passed configuration.
     * This method is called prior to invoke the plugin.
     *
     * @param config The plugin configuration.
     */
    public default void configure(Map<String, String> config) {
    }

    /**
     * Configure the plugin based on the passed configuration.
     * This method is called prior to invoke the plugin.
     *
     * @param config The plugin configuration.
     * @param ctx The plugin context
     */
    public default void configure(Map<String, String> config, PluginContext ctx) {
        configure(config);
    }
}
