/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Build list of all the preview packages, classes, constructors, fields and methods.
 */
public class PreviewAPIListBuilder extends SummaryAPIListBuilder {

    private final Map<Element, JEP> elementJeps = new HashMap<>();
    private final Map<String, JEP> jeps = new HashMap<>();

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
        super(configuration, configuration.utils::isPreviewAPI);
        buildSummaryAPIInfo();
    }

    @Override
    protected void handleElement(Element e) {
        String feature = Objects.requireNonNull(utils.getPreviewFeature(e),
                "Preview feature not specified").toString();
        JEP jep = jeps.computeIfAbsent(feature, (featureName) -> {
            Map<? extends ExecutableElement, ? extends AnnotationValue> anno = configuration.workArounds.getJepInfo(featureName);
            int number = 0;
            String title = "";
            String status = "Preview"; // Default value is not returned by the method we use above.
            for (var entry : anno.entrySet()) {
                if ("number".equals(entry.getKey().getSimpleName().toString())) {
                    number = (int) entry.getValue().getValue();
                } else if ("title".equals(entry.getKey().getSimpleName().toString())) {
                    title = (String) entry.getValue().getValue();
                } else if ("status".equals(entry.getKey().getSimpleName().toString())) {
                    status = (String) entry.getValue().getValue();
                } else {
                    throw new IllegalArgumentException(entry.getKey().getSimpleName().toString());
                }
            }
            return new JEP(number, title, status);
        });
        elementJeps.put(e, jep);
    }

    /**
     * {@return a sorted set of preview feature JEPs in this release}
     */
    public Set<JEP> getJEPs() {
        return new TreeSet<>(jeps.values());
    }

    /**
     * {@return the JEP for a preview element}
     */
    public JEP getJEP(Element e) {
        return elementJeps.get(e);
    }
}
