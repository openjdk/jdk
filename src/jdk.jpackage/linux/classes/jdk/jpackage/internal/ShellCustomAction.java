/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Interface to add custom actions to installers composed of shell commands.
 */
abstract class ShellCustomAction {

    List<String> requiredPackages() {
        return Collections.emptyList();
    }

    List<String> replacementStringIds() {
        return Collections.emptyList();
    }

    abstract Map<String, String> create() throws IOException;

    static String stringifyShellCommands(String... commands) {
        return stringifyShellCommands(Arrays.asList(commands));
    }

    static String stringifyShellCommands(List<String> commands) {
        return String.join(System.lineSeparator(), commands.stream().filter(
                s -> s != null && !s.isEmpty()).toList());
    }

    protected static String stringifyTextFile(String resourceName) throws IOException {
        try ( InputStream is = OverridableResource.readDefault(resourceName);
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader reader = new BufferedReader(isr)) {
            return reader.lines().collect(Collectors.joining(
                    System.lineSeparator()));
        }
    }

    protected static String escapedInstalledLauncherPath(PlatformPackage pkg,
            String launcherName) {
        String appLauncher = pkg.installedApplicationLayout().launchersDirectory().resolve(
                launcherName).toString();
        if (Pattern.compile("\\s").matcher(appLauncher).find()) {
            // Path contains whitespace(s). Enclose in double quotes.
            appLauncher = "\"" + appLauncher + "\"";
        }
        return appLauncher;
    }
}
