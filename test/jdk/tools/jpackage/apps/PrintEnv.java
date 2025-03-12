/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PrintEnv {

    public static void main(String[] args) {
        List<String> lines = printArgs(args);
        lines.forEach(System.out::println);
    }

    private static List<String> printArgs(String[] args) {
        List<String> lines = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith(PRINT_ENV_VAR)) {
                String name = arg.substring(PRINT_ENV_VAR.length());
                lines.add(name + "=" + System.getenv(name));
            } else if (arg.startsWith(PRINT_SYS_PROP)) {
                String name = arg.substring(PRINT_SYS_PROP.length());
                lines.add(name + "=" + System.getProperty(name));
            } else if (arg.startsWith(PRINT_MODULES)) {
                lines.add(ModuleFinder.ofSystem().findAll().stream()
                        .map(ModuleReference::descriptor)
                        .map(ModuleDescriptor::name)
                        .collect(Collectors.joining(",")));
            } else {
                throw new IllegalArgumentException();
            }
        }

        return lines;
    }

    private final static String PRINT_ENV_VAR = "--print-env-var=";
    private final static String PRINT_SYS_PROP = "--print-sys-prop=";
    private final static String PRINT_MODULES = "--print-modules";
}
