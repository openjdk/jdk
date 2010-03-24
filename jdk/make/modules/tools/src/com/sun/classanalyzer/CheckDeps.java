/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.classanalyzer;

import java.io.*;
import java.util.*;

/**
 * A simple tool to check module dependencies against a known list of
 * dependencies. The tool fails (by throwing a RuntimeException) is an
 * unexpected dependency is detected.
 */

public class CheckDeps {

    /**
     * Represents a dependency from one module to another module. The dependency
     * may be optional.
     */
    static class Dependency {
        private final String module;
        private final String other;
        private final boolean optional;

        private Dependency(String module, String other, boolean optional) {
            this.module = module;
            this.other = other;
            this.optional = optional;
        }

        String module()      { return module; }
        String other()       { return other; }
        boolean isOptional() { return optional; }

        /**
         * Parses a dependency in one of the following forms:
         *   a -> b
         *   [optional] a -> b
         */
        static Dependency fromString(String s) {
            String[] components = s.split(" ");
            int count = components.length;
            if (count != 3 && count != 4)
                throw new IllegalArgumentException(s);
            boolean optional = (count == 4);
            if (optional && !components[0].equals("[optional]"))
                throw new IllegalArgumentException(s);
            String arrow = optional ? components[2] : components[1];
            if (!arrow.equals("->"))
                throw new IllegalArgumentException(s);
            String module = optional ? components[1] : components[0];
            String other = optional ? components[3] : components[2];
            return new Dependency(module, other, optional);
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            if (optional)
                sb.append("[optional] ");
            sb.append(module);
            sb.append(" -> ");
            sb.append(other);
            return sb.toString();
        }
    }

    /**
     * Represents the "tail"
     */
    static class DependencyTail {
        private final String module;
        private final boolean optional;

        DependencyTail(String module, boolean optional) {
            this.module = module;
            this.optional = optional;
        }
        String module()      { return module; }
        boolean isOptional() { return optional; }
    }

    static void usage() {
        System.out.println("java CheckDeps file1 file2");
        System.out.println("  where file1 is the expected dependencies and file2 is");
        System.out.println("  the actual dependencies. Both files are assumed to be");
        System.out.println("  in modules.summary format (see ClassAnalyzer tool).");
        System.out.println();
        System.out.println("Example usages:");
        System.out.println("  java CheckDeps make/modules/modules.summary " +
            "$(OUTPUTDIR)/modules.summary");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            usage();

        // maps a module to the list of modules that it depends on
        Map<String,List<DependencyTail>> expected =
            new HashMap<String,List<DependencyTail>>();

        // parse the expected dependencies file
        Scanner s;
        s = new Scanner(new FileInputStream(args[0]));
        try {
            while (s.hasNextLine()) {
                Dependency ref = Dependency.fromString(s.nextLine());
                if (ref != null) {
                    String module = ref.module();
                    List<DependencyTail> list = expected.get(module);
                    if (list == null) {
                        list = new ArrayList<DependencyTail>();
                        expected.put(module, list);
                    }
                    list.add(new DependencyTail(ref.other(), ref.isOptional()));
                }
            }
        } finally {
            s.close();
        }

        // parse the actual dependencies file, checking each dependency
        // against the expected list.
        boolean fail = false;
        s = new Scanner(new FileInputStream(args[1]));
        try {
            while (s.hasNextLine()) {
                Dependency dep = Dependency.fromString(s.nextLine());

                // check if this dependency is expected
                List<DependencyTail> list = expected.get(dep.module());
                DependencyTail tail = null;
                if (list != null) {
                    for (DependencyTail t: list) {
                        if (t.module().equals(dep.other())) {
                            tail = t;
                            break;
                        }
                    }
                }
                if (tail == null) {
                    System.err.println("Unexpected dependency: " + dep);
                    fail = true;
                } else {
                    // hard dependency when optional dependency is expected
                    if (tail.isOptional() != dep.isOptional()) {
                        if (tail.isOptional()) {
                            System.err.println("Unexpected dependency: " + dep);
                            fail = true;
                        }
                    }
                }
            }
        } finally {
            s.close();
        }

        if (fail)
            throw new RuntimeException("Unexpected dependencies found");
    }
}
