/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * Filter in or out a resource
 */
public class ResourceFilter implements Predicate<String> {

    private final Pattern inPatterns;
    private final Pattern outPatterns;

    static final String NEG = "^";

    public ResourceFilter(String[] patterns) throws IOException {
        this(patterns, false);
    }

    public ResourceFilter(String[] patterns, boolean negateAll) throws IOException {

        // Get the patterns from a file
        if (patterns != null && patterns.length == 1) {
            String filePath = patterns[0];
            File f = new File(filePath);
            if (f.exists()) {
                List<String> pats;
                try (FileInputStream fis = new FileInputStream(f);
                        InputStreamReader ins = new InputStreamReader(fis,
                                StandardCharsets.UTF_8);
                        BufferedReader reader = new BufferedReader(ins)) {
                    pats = reader.lines().collect(Collectors.toList());
                }
                patterns = new String[pats.size()];
                pats.toArray(patterns);
            }
        }

        if (patterns != null && negateAll) {
            String[] excluded = new String[patterns.length];
            for (int i = 0; i < patterns.length; i++) {
                excluded[i] = ResourceFilter.NEG + patterns[i];
            }
            patterns = excluded;
        }

        StringBuilder inPatternsBuilder = new StringBuilder();
        StringBuilder outPatternsBuilder = new StringBuilder();
        if (patterns != null) {
            for (int i = 0; i < patterns.length; i++) {
                String p = patterns[i];
                p = p.replaceAll(" ", "");
                StringBuilder builder = p.startsWith(NEG)
                        ? outPatternsBuilder : inPatternsBuilder;
                String pat = p.startsWith(NEG) ? p.substring(NEG.length()) : p;
                builder.append(escape(pat));
                if (i < patterns.length - 1) {
                    builder.append("|");
                }
            }
        }
        this.inPatterns = inPatternsBuilder.length() == 0 ? null
                : Pattern.compile(inPatternsBuilder.toString());
        this.outPatterns = outPatternsBuilder.length() == 0 ? null
                : Pattern.compile(outPatternsBuilder.toString());
    }

    public static String escape(String s) {
        s = s.replaceAll(" ", "");
        s = s.replaceAll("\\$", Matcher.quoteReplacement("\\$"));
        s = s.replaceAll("\\.", Matcher.quoteReplacement("\\."));
        s = s.replaceAll("\\*", ".+");
        return s;
    }

    private boolean accept(String path) {
        if (outPatterns != null) {
            Matcher mout = outPatterns.matcher(path);
            if (mout.matches()) {
                //System.out.println("Excluding file " + resource.getPath());
                return false;
            }
        }
        boolean accepted = false;
        // If the inPatterns is null, means that all resources are accepted.
        if (inPatterns == null) {
            accepted = true;
        } else {
            Matcher m = inPatterns.matcher(path);
            if (m.matches()) {
                //System.out.println("Including file " + resource.getPath());
                accepted = true;
            }
        }
        return accepted;
    }

    @Override
    public boolean test(String path) {
        return accept(path);
    }
}
