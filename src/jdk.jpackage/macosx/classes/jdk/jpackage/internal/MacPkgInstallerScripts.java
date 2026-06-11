/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.Optional;
import java.util.function.Supplier;
import jdk.jpackage.internal.PackageScripts.ResourceConfig;

/**
 * MacOS PKG installer scripts.
 */
final class MacPkgInstallerScripts {

    enum AppScripts implements Supplier<OverridableResource> {
        preinstall(new ResourceConfig(Optional.empty(),
                "resource.pkg-preinstall-script")),
        postinstall(new ResourceConfig(Optional.empty(),
                "resource.pkg-postinstall-script"));

        AppScripts(ResourceConfig cfg) {
            this.cfg = cfg;
        }

        @Override
        public OverridableResource get() {
            return cfg.createResource().setPublicName(name());
        }

        private final ResourceConfig cfg;
    }

    enum ServicesScripts implements Supplier<OverridableResource> {
        preinstall(new ResourceConfig("services-preinstall.template",
                "resource.pkg-services-preinstall-script")),
        postinstall(new ResourceConfig("services-postinstall.template",
                "resource.pkg-services-postinstall-script"));

        ServicesScripts(ResourceConfig cfg) {
            this.cfg = cfg;
        }

        @Override
        public OverridableResource get() {
            return cfg.createResource();
        }

        private final ResourceConfig cfg;
    }

    static PackageScripts<AppScripts> createAppScripts() {
        return PackageScripts.create(AppScripts.class);
    }

    static PackageScripts<ServicesScripts> createServicesScripts() {
        return PackageScripts.create(ServicesScripts.class);
    }
}
