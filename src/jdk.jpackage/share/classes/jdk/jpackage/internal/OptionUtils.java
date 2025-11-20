/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.cli.StandardOption.BUNDLING_OPERATION_DESCRIPTOR;
import static jdk.jpackage.internal.cli.StandardOption.DEST;
import static jdk.jpackage.internal.cli.StandardOption.MAIN_JAR;
import static jdk.jpackage.internal.cli.StandardOption.MODULE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;

import java.nio.file.Path;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardBundlingOperation;

final class OptionUtils {

    static boolean isRuntimeInstaller(Options options) {
        return PREDEFINED_RUNTIME_IMAGE.containsIn(options)
                && !PREDEFINED_APP_IMAGE.containsIn(options)
                && !MAIN_JAR.containsIn(options)
                && !MODULE.containsIn(options);
    }

    static Path outputDir(Options options) {
        return DEST.getFrom(options);
    }

    static StandardBundlingOperation bundlingOperation(Options options) {
        return StandardBundlingOperation.valueOf(BUNDLING_OPERATION_DESCRIPTOR.getFrom(options)).orElseThrow();
    }
}
