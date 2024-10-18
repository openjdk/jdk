/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import javax.lang.model.element.Element;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Build list of all the preview packages, classes, constructors, fields and methods.
 */
public class PreviewAPIListBuilder extends SummaryAPIListBuilder {

    private final Map<Element, JEP> elementJeps = new HashMap<>();
    private final Map<String, JEP> jeps = new HashMap<>();
    private static final JEP NULL_SENTINEL = new JEP(0, "", "");

    /**
     * The JEP for a preview feature in this release.
     */
    public record JEP(int number, String title, String status) implements Comparable<JEP> {
        @Override
        public int compareTo(JEP o) {
            return number - o.number;
        }
    }

    /**
     * Constructor.
     *
     * @param configuration the current configuration of the doclet
     */
    public PreviewAPIListBuilder(BaseConfiguration configuration) {
        super(configuration);
        buildSummaryAPIInfo();
    }

    @Override
    protected boolean belongsToSummary(Element element) {
        if (!utils.isPreviewAPI(element)) {
            return false;
        }
        String feature = Objects.requireNonNull(utils.getPreviewFeature(element),
                "Preview feature not specified").toString();
        JEP jep = jeps.computeIfAbsent(feature, featureName -> {
            Map<String, Object> jepInfo = configuration.workArounds.getJepInfo(featureName);
            if (!jepInfo.isEmpty()) {
                int number = 0;
                String title = "";
                String status = "Preview"; // Default value is not returned by the method we used above.
                for (var entry : jepInfo.entrySet()) {
                    switch (entry.getKey()) {
                        case "number" -> number = (int) entry.getValue();
                        case "title" -> title = (String) entry.getValue();
                        case "status" -> status = (String) entry.getValue();
                        default -> throw new IllegalArgumentException(entry.getKey());
                    }
                }
                return new JEP(number, title, status);
            }
            return NULL_SENTINEL;
        });
        if (jep != NULL_SENTINEL) {
            elementJeps.put(element, jep);
            return true;
        }
        // Preview features without JEP are not included.
        return false;
    }

    /**
     * {@return a sorted set of preview feature JEPs in this release}
     */
    public Set<JEP> getJEPs() {
        return jeps.values()
                .stream()
                .filter(jep -> jep != NULL_SENTINEL)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * {@return the JEP for a preview element}
     */
    public JEP getJEP(Element e) {
        return elementJeps.get(e);
    }
}
