/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.util.PathUtils.resolveNullablePath;

/**
 * Application directory layout.
 */
public interface ApplicationLayout extends AppImageLayout {

    /**
     * Path to launchers directory.
     */
    Path launchersDirectory();

    /**
     * Path to application data directory.
     */
    Path appDirectory();

    /**
     * Path to application mods directory.
     */
    Path appModsDirectory();

    /**
     * Path to directory with application's desktop integration files.
     */
    Path destktopIntegrationDirectory();

    /**
     * Path to directory with additional application content.
     */
    Path contentDirectory();

    @Override
    ApplicationLayout resolveAt(Path root);

    final class Stub extends AppImageLayout.Proxy<AppImageLayout> implements ApplicationLayout {

        public Stub(AppImageLayout target, Path launchersDirectory,
                Path appDirectory, Path appModsDirectory,
                Path destktopIntegrationDirectory, Path contentDirectory) {
            super(target);
            this.launchersDirectory = launchersDirectory;
            this.appDirectory = appDirectory;
            this.appModsDirectory = appModsDirectory;
            this.destktopIntegrationDirectory = destktopIntegrationDirectory;
            this.contentDirectory = contentDirectory;
        }

        @Override
        public Path launchersDirectory() {
            return launchersDirectory;
        }

        @Override
        public Path appDirectory() {
            return appDirectory;
        }

        @Override
        public Path appModsDirectory() {
            return appModsDirectory;
        }

        @Override
        public Path destktopIntegrationDirectory() {
            return destktopIntegrationDirectory;
        }

        @Override
        public Path contentDirectory() {
            return contentDirectory;
        }

        @Override
        public ApplicationLayout resolveAt(Path base) {
            return new ApplicationLayout.Stub(target.resolveAt(base),
                    resolveNullablePath(base, launchersDirectory),
                    resolveNullablePath(base, appDirectory),
                    resolveNullablePath(base, appModsDirectory),
                    resolveNullablePath(base, destktopIntegrationDirectory),
                    resolveNullablePath(base, contentDirectory));
        }

        private final Path launchersDirectory;
        private final Path appDirectory;
        private final Path appModsDirectory;
        private final Path destktopIntegrationDirectory;
        private final Path contentDirectory;
    }

    public static Builder build() {
        return new Builder();
    }

    public static Builder buildFrom(ApplicationLayout appLayout) {
        return new Builder(appLayout);
    }

    class Proxy<T extends ApplicationLayout> extends AppImageLayout.Proxy<T> implements ApplicationLayout {

        public Proxy(T target) {
            super(target);
        }

        @Override
        final public Path launchersDirectory() {
            return target.launchersDirectory();
        }

        @Override
        final public Path appDirectory() {
            return target.appDirectory();
        }

        @Override
        final public Path appModsDirectory() {
            return target.appModsDirectory();
        }

        @Override
        final public Path destktopIntegrationDirectory() {
            return target.destktopIntegrationDirectory();
        }

        @Override
        final public Path contentDirectory() {
            return target.contentDirectory();
        }

        @Override
        public ApplicationLayout resolveAt(Path root) {
            return target.resolveAt(root);
        }
    }

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
            return new ApplicationLayout.Stub(
                    new AppImageLayout.Stub(runtimeDirectory),
                    launchersDirectory,
                    appDirectory,
                    appModsDirectory,
                    destktopIntegrationDirectory,
                    contentDirectory);
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
