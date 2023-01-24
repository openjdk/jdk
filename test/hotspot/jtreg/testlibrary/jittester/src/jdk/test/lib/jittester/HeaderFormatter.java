/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

public final class HeaderFormatter {
    public static final String DISABLE_WARNINGS = "-XX:-PrintWarnings";

    private final Builder builder;

    public static class Builder {
        private Function<String, String[]>  preRunActions = s -> new String[0];
        private String jtDriverOptions = "-Xcomp";
        private String libraries = "/ ../";
        private boolean printJitTesterHierarchy = true;

        public Builder preRunActions(Function<String, String[]> from) {
            preRunActions = from;
            return this;
        }

        public Builder jtDriverOptions(String from) {
            jtDriverOptions = from;
            return this;
        }

        public Builder libraries(String from) {
            libraries = from;
            return this;
        }

        public Builder printJitTesterHierarchy(boolean from) {
            printJitTesterHierarchy = from;
            return this;
        }

        public HeaderFormatter build() {
            return new HeaderFormatter(this);
        }
    }

    private HeaderFormatter(Builder builder) {
        this.builder = builder;
    }

    public  String getJtregHeader(String mainClassName) {
        String synopsis = "seed = '" + ProductionParams.seed.value() + "'"
                + ", specificSeed = '" + PseudoRandom.getCurrentSeed() + "'";
        StringBuilder header = new StringBuilder();
        header.append("/*\n * @test\n * @summary ")
              .append(synopsis)
              .append(" \n * @library " + builder.libraries + "\n");
        header.append(" * @run build jdk.test.lib.jittester.jtreg.JitTesterDriver "
                        + "jdk.test.lib.jittester.jtreg.Printer\n");
        for (String action : builder.preRunActions.apply(mainClassName)) {
            header.append(" * ")
                  .append(action)
                  .append("\n");
        }
        header.append(" * @run driver jdk.test.lib.jittester.jtreg.JitTesterDriver ")
              .append(DISABLE_WARNINGS)
              .append(" ")
              .append(builder.jtDriverOptions)
              .append(" ")
              .append(mainClassName)
              .append("\n */\n\n");
        if (ProductionParams.printHierarchy.value() && builder.printJitTesterHierarchy) {
            header.append("/*\n")
                  .append(printHierarchy())
                  .append("*/\n");
        }
        return header.toString();
    }

    private static String printHierarchy() {
        return TypeList.getAll()
                .stream()
                .filter(t -> t instanceof TypeKlass)
                .map(t -> typeDescription((TypeKlass) t))
                .collect(Collectors.joining("\n", "CLASS HIERARCHY:\n", "\n"));
    }

    private static String typeDescription(TypeKlass type) {
        StringBuilder result = new StringBuilder();
        String parents = type.getParentsNames().stream().collect(Collectors.joining(","));
        result.append(type.isAbstract() ? "abstract " : "")
              .append(type.isFinal() ? "final " : "")
              .append(type.isInterface() ? "interface " : "class ")
              .append(type.getName())
              .append(parents.isEmpty() ? "" : ": " + parents);
        return result.toString();
    }

}
