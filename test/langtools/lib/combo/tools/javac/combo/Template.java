/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package tools.javac.combo;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A template into which tags of the form {@code #\{KEY\}} or
 * {@code #\{KEY.SUBKEY\}} can be expanded.
 */
public interface Template {
    String expand(String selector);

    interface Resolver {
        public Template lookup(String key);
    }

    public static class Behavior {
        /* Looks for expandable keys.  An expandable key can take the form:
         *   #{MAJOR}
         *   #{MAJOR.}
         *   #{MAJOR.MINOR}
         * where MAJOR can be IDENTIFIER or IDENTIFIER[NUMERIC_INDEX]
         * and MINOR can be an identifier.
         *
         * The ability to have an empty minor is provided on the
         * assumption that some tests that can be written with this
         * will find it useful to make a distinction akin to
         * distinguishing F from F(), where F is a function pointer,
         * and also cases of #{FOO.#{BAR}}, where BAR expands to an
         * empty string.
         *
         * However, this being a general-purpose framework, the exact
         * use is left up to the test writers.
         */
        private static final Pattern pattern = Pattern.compile("#\\{([A-Z_][A-Z0-9_]*(?:\\[\\d+\\])?)(?:\\.([A-Z0-9_]*))?\\}");

        public static String expandTemplate(String template, final Map<String, Template> vars) {
            return expandTemplate(template, new MapResolver(vars));
        }

        public static String expandTemplate(String template, Resolver res) {
            CharSequence in = template;
            StringBuffer out = new StringBuffer();
            while (true) {
                boolean more = false;
                Matcher m = pattern.matcher(in);
                while (m.find()) {
                    String major = m.group(1);
                    String minor = m.group(2);
                    Template key = res.lookup(major);
                    if (key == null)
                        throw new IllegalStateException("Unknown major key " + major);

                    String replacement = key.expand(minor == null ? "" : minor);
                    more |= pattern.matcher(replacement).find();
                    m.appendReplacement(out, replacement);
                }
                m.appendTail(out);
                if (!more)
                    return out.toString();
                else {
                    in = out;
                    out = new StringBuffer();
                }
            }
        }

    }
}

class MapResolver implements Template.Resolver {
    private final Map<String, Template> vars;

    public MapResolver(Map<String, Template> vars) {this.vars = vars;}

    public Template lookup(String key) {
        return vars.get(key);
    }
}

class ChainedResolver implements Template.Resolver {
    private final Template.Resolver upstreamResolver, thisResolver;

    public ChainedResolver(Template.Resolver upstreamResolver, Template.Resolver thisResolver) {
        this.upstreamResolver = upstreamResolver;
        this.thisResolver = thisResolver;
    }

    public Template.Resolver getUpstreamResolver() {
        return upstreamResolver;
    }

    @Override
    public Template lookup(String key) {
        Template result = thisResolver.lookup(key);
        if (result == null)
            result = upstreamResolver.lookup(key);
        return result;
    }
}
