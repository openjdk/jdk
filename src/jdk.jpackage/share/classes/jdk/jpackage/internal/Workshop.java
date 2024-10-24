/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;

interface Workshop {

    Path buildRoot();

    Path resourceDir();

    /**
     * Returns path to application image directory.
     *
     * The return value is supposed to be used as a parameter for
     * ApplicationLayout#resolveAt function.
     */
    default Path appImageDir() {
        return buildRoot().resolve("image");
    }

    default Path configDir() {
        return buildRoot().resolve("config");
    }

    default OverridableResource createResource(String defaultName) {
        return new OverridableResource(defaultName).setResourceDir(resourceDir());
    }

    record Impl(Path buildRoot, Path resourceDir) implements Workshop {

    }

    static Workshop withAppImageDir(Workshop workshop, Path appImageDir) {
        return new Proxy(workshop) {
            @Override
            public Path appImageDir() {
                return appImageDir;
            }
        };
    }

    static class Proxy implements Workshop {

        Proxy(Workshop target) {
            this.target = target;
        }

        @Override
        public Path buildRoot() {
            return target.buildRoot();
        }

        @Override
        public Path resourceDir() {
            return target.resourceDir();
        }

        private final Workshop target;
    }
}
