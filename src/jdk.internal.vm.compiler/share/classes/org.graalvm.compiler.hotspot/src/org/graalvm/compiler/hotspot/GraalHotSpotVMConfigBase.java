/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This is a source with different versions for various JDKs.
 */
public abstract class GraalHotSpotVMConfigBase extends HotSpotVMConfigAccess {

    GraalHotSpotVMConfigBase(HotSpotVMConfigStore store) {
        super(store);
        assert this instanceof GraalHotSpotVMConfig;
        versioned = new GraalHotSpotVMConfigVersioned(store);
        assert checkVersioned();
    }

    private boolean checkVersioned() {
        Class<? extends GraalHotSpotVMConfigVersioned> c = versioned.getClass();
        for (Field field : c.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                // javac inlines non-static final fields which means
                // versioned values are ignored in non-flattened Graal
                assert !Modifier.isFinal(modifiers) : "Non-static field in " + c.getName() + " must not be final: " + field.getName();
            }
        }
        return true;
    }

    /**
     * Contains values that are different between JDK versions.
     */
    protected final GraalHotSpotVMConfigVersioned versioned;

    /**
     * Sentinel value to use for an {@linkplain InjectedParameter injected}
     * {@link GraalHotSpotVMConfig} parameter to a {@linkplain Fold foldable} method.
     */
    public static final GraalHotSpotVMConfig INJECTED_VMCONFIG = null;
    public static final MetaAccessProvider INJECTED_METAACCESS = null;
    public static final OptionValues INJECTED_OPTIONVALUES = null;
    public static final IntrinsicContext INJECTED_INTRINSIC_CONTEXT = null;

    public final String osName = getHostOSName();
    public final String osArch = getHostArchitectureName();
    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");
    public final boolean linuxOs = System.getProperty("os.name", "").startsWith("Linux");

    /**
     * Gets the host operating system name.
     */
    private static String getHostOSName() {
        String osName = System.getProperty("os.name");
        switch (osName) {
            case "Linux":
                osName = "linux";
                break;
            case "SunOS":
                osName = "solaris";
                break;
            case "Mac OS X":
                osName = "bsd";
                break;
            default:
                // Of course Windows is different...
                if (osName.startsWith("Windows")) {
                    osName = "windows";
                } else {
                    throw new JVMCIError("Unexpected OS name: " + osName);
                }
        }
        return osName;
    }

    private static String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        return arch;
    }

    protected final Integer intRequiredOnAMD64 = osArch.equals("amd64") ? null : 0;
    protected final Long longRequiredOnAMD64 = osArch.equals("amd64") ? null : 0L;
}
