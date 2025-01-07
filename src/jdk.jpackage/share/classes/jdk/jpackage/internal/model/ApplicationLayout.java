/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import java.util.Objects;
import jdk.jpackage.internal.util.CompositeProxy;
import static jdk.jpackage.internal.util.PathUtils.resolveNullablePath;

/**
 * Application app image layout.
 * <p>
 * Application is comprised from application files and Java runtime.
 * <p>
 * Use {@link #build()} or {@link #buildFrom(ApplicationLayout)} methods to
 * configure and construct instances of this interface.
 */
public interface ApplicationLayout extends AppImageLayout, ApplicationLayoutMixin {

    @Override
    default ApplicationLayout resolveAt(Path root) {
        return buildFrom(this).resolveAt(root).create();
    }

    /**
     * Creates an object implementing {@link ApplicationLayout} interface from
     * {@link AppImageLayout} and {@link ApplicationLayoutMixin} instances.
     *
     * @param appImage app image layout object
     * @param mixin application layout mixin for the app image layout
     * @return new object implementing {@link ApplicationLayout} interface
     */
    static ApplicationLayout create(AppImageLayout appImage, ApplicationLayoutMixin mixin) {
        return CompositeProxy.create(ApplicationLayout.class, appImage, mixin);
    }

    public static Builder build() {
        return new Builder();
    }

    public static Builder buildFrom(ApplicationLayout appLayout) {
        return new Builder(appLayout);
    }

    /**
     * Builds {@link ApplicationLayout} instances.
     */
    final class Builder {
        private Builder() {
        }

        private Builder(ApplicationLayout appLayout) {
            launchersDirectory = appLayout.launchersDirectory();
            appDirectory = appLayout.appDirectory();
            runtimeDirectory = appLayout.runtimeDirectory();
            appModsDirectory = appLayout.appModsDirectory();
            destktopIntegrationDirectory = appLayout.destktopIntegrationDirectory();
            contentDirectory = appLayout.contentDirectory();
        }

        public ApplicationLayout create() {

            Objects.requireNonNull(runtimeDirectory);
            Objects.requireNonNull(launchersDirectory);
            Objects.requireNonNull(appDirectory);
            Objects.requireNonNull(appModsDirectory);
            Objects.requireNonNull(destktopIntegrationDirectory);
            Objects.requireNonNull(contentDirectory);

            return ApplicationLayout.create(new AppImageLayout.Stub(
                    runtimeDirectory), new ApplicationLayoutMixin.Stub(
                    launchersDirectory, appDirectory, appModsDirectory,
                    destktopIntegrationDirectory, contentDirectory));
        }

        public Builder setAll(String path) {
            return setAll(Path.of(path));
        }

        public Builder setAll(Path path) {
            launchersDirectory(path);
            appDirectory(path);
            runtimeDirectory(path);
            appModsDirectory(path);
            destktopIntegrationDirectory(path);
            contentDirectory(path);
            return this;
        }

        public Builder resolveAt(Path base) {
            launchersDirectory(resolveNullablePath(base, launchersDirectory));
            appDirectory(resolveNullablePath(base, appDirectory));
            runtimeDirectory(resolveNullablePath(base, runtimeDirectory));
            appModsDirectory(resolveNullablePath(base, appModsDirectory));
            destktopIntegrationDirectory(resolveNullablePath(base, destktopIntegrationDirectory));
            contentDirectory(resolveNullablePath(base, contentDirectory));
            return this;
        }

        public Builder launchersDirectory(String v) {
            return launchersDirectory(Path.of(v));
        }

        public Builder launchersDirectory(Path v) {
            launchersDirectory = v;
            return this;
        }

        public Builder appDirectory(String v) {
            return appDirectory(Path.of(v));
        }

        public Builder appDirectory(Path v) {
            appDirectory = v;
            return this;
        }

        public Builder runtimeDirectory(String v) {
            return runtimeDirectory(Path.of(v));
        }

        public Builder runtimeDirectory(Path v) {
            runtimeDirectory = v;
            return this;
        }

        public Builder appModsDirectory(String v) {
            return appModsDirectory(Path.of(v));
        }

        public Builder appModsDirectory(Path v) {
            appModsDirectory = v;
            return this;
        }

        public Builder destktopIntegrationDirectory(String v) {
            return destktopIntegrationDirectory(Path.of(v));
        }

        public Builder destktopIntegrationDirectory(Path v) {
            destktopIntegrationDirectory = v;
            return this;
        }

        public Builder contentDirectory(String v) {
            return contentDirectory(Path.of(v));
        }

        public Builder contentDirectory(Path v) {
            contentDirectory = v;
            return this;
        }

        private Path launchersDirectory;
        private Path appDirectory;
        private Path runtimeDirectory;
        private Path appModsDirectory;
        private Path destktopIntegrationDirectory;
        private Path contentDirectory;
    }
}
