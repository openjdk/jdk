/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a persistent compiled script.
 */
public final class StoredScript implements Serializable {

    /** Compilation id */
    private final int compilationId;

    /** Main class name. */
    private final String mainClassName;

    /** Map of class names to class bytes. */
    private final Map<String, byte[]> classBytes;

    /** Constants array. */
    private final Object[] constants;

    /** Function initializers */
    private final Map<Integer, FunctionInitializer> initializers;

    private static final long serialVersionUID = 2958227232195298340L;

    /**
     * Constructor.
     *
     * @param compilationId compilation id
     * @param mainClassName main class name
     * @param classBytes map of class names to class bytes
     * @param initializers initializer map, id -&gt; FunctionInitializer
     * @param constants constants array
     */
    public StoredScript(final int compilationId, final String mainClassName, final Map<String, byte[]> classBytes, final Map<Integer, FunctionInitializer> initializers, final Object[] constants) {
        this.compilationId = compilationId;
        this.mainClassName = mainClassName;
        this.classBytes = classBytes;
        this.constants = constants;
        this.initializers = initializers;
    }

    /**
     * Get the compilation id for this StoredScript
     * @return compilation id
     */
    public int getCompilationId() {
        return compilationId;
    }

    private Map<String, Class<?>> installClasses(final Source source, final CodeInstaller installer) {
        final Map<String, Class<?>> installedClasses = new HashMap<>();
        final byte[]   mainClassBytes = classBytes.get(mainClassName);
        final Class<?> mainClass      = installer.install(mainClassName, mainClassBytes);

        installedClasses.put(mainClassName, mainClass);

        for (final Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            final String className = entry.getKey();

            if (!className.equals(mainClassName)) {
                installedClasses.put(className, installer.install(className, entry.getValue()));
            }
        }

        installer.initialize(installedClasses.values(), source, constants);
        return installedClasses;
    }

    FunctionInitializer installFunction(final RecompilableScriptFunctionData data, final CodeInstaller installer) {
        final Map<String, Class<?>> installedClasses = installClasses(data.getSource(), installer);

        assert initializers != null;
        assert initializers.size() == 1;
        final FunctionInitializer initializer = initializers.values().iterator().next();

        for (int i = 0; i < constants.length; i++) {
            if (constants[i] instanceof RecompilableScriptFunctionData) {
                // replace deserialized function data with the ones we already have
                final RecompilableScriptFunctionData newData = data.getScriptFunctionData(((RecompilableScriptFunctionData) constants[i]).getFunctionNodeId());
                assert newData != null;
                newData.initTransients(data.getSource(), installer);
                constants[i] = newData;
            }
        }

        initializer.setCode(installedClasses.get(initializer.getClassName()));
        return initializer;
    }

    /**
     * Install as script.
     *
     * @param source the source
     * @param installer the installer
     * @return main script class
     */
    Class<?> installScript(final Source source, final CodeInstaller installer) {

        final Map<String, Class<?>> installedClasses = installClasses(source, installer);

        for (final Object constant : constants) {
            if (constant instanceof RecompilableScriptFunctionData) {
                final RecompilableScriptFunctionData data = (RecompilableScriptFunctionData) constant;
                data.initTransients(source, installer);
                final FunctionInitializer initializer = initializers.get(data.getFunctionNodeId());
                if (initializer != null) {
                    initializer.setCode(installedClasses.get(initializer.getClassName()));
                    data.initializeCode(initializer);
                }
            }
        }

        return installedClasses.get(mainClassName);
    }

    @Override
    public int hashCode() {
        int hash = mainClassName.hashCode();
        hash = 31 * hash + classBytes.hashCode();
        hash = 31 * hash + Arrays.hashCode(constants);
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof StoredScript)) {
            return false;
        }

        final StoredScript cs = (StoredScript) obj;
        return mainClassName.equals(cs.mainClassName)
                && classBytes.equals(cs.classBytes)
                && Arrays.equals(constants, cs.constants);
    }
}
