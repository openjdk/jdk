/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.util.ModuleAPI.addExports;
import static org.graalvm.compiler.core.common.util.ModuleAPI.getModule;
import static org.graalvm.compiler.core.common.util.Util.JAVA_SPECIFICATION_VERSION;

import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.services.JVMCIServiceLocator;

@ServiceProvider(JVMCIServiceLocator.class)
public final class HotSpotGraalJVMCIServiceLocator extends JVMCIServiceLocator {

    private boolean exportsAdded;

    /**
     * Dynamically exports various internal JDK packages to the Graal module. This requires only
     * {@code --add-exports=java.base/jdk.internal.module=org.graalvm.compiler.graal_core} on the VM
     * command line instead of a {@code --add-exports} instance for each JDK internal package used
     * by Graal.
     */
    private void addExports() {
        if (JAVA_SPECIFICATION_VERSION >= 9 && !exportsAdded) {
            Object javaBaseModule = getModule.invoke(String.class);
            Object graalModule = getModule.invoke(getClass());
            addExports.invokeStatic(javaBaseModule, "jdk.internal.misc", graalModule);
            addExports.invokeStatic(javaBaseModule, "jdk.internal.jimage", graalModule);
            addExports.invokeStatic(javaBaseModule, "com.sun.crypto.provider", graalModule);
            exportsAdded = true;
        }
    }

    private HotSpotGraalRuntime graalRuntime;

    @Override
    public <T> T getProvider(Class<T> service) {
        if (service == JVMCICompilerFactory.class) {
            addExports();
            return service.cast(new HotSpotGraalCompilerFactory(this));
        } else if (service == HotSpotVMEventListener.class) {
            if (graalRuntime != null) {
                addExports();
                return service.cast(new HotSpotGraalVMEventListener(graalRuntime));
            }
        }
        return null;
    }

    public void onCompilerCreation(HotSpotGraalCompiler compiler) {
        assert this.graalRuntime == null : "only expect a single JVMCICompiler to be created";
        this.graalRuntime = (HotSpotGraalRuntime) compiler.getGraalRuntime();
    }
}
