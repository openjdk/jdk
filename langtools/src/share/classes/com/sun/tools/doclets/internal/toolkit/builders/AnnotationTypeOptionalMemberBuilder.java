/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;


import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * Builds documentation for optional annotation type members.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class AnnotationTypeOptionalMemberBuilder extends
    AnnotationTypeRequiredMemberBuilder {


    /**
     * Construct a new AnnotationTypeMemberBuilder.
     *
     * @param configuration the current configuration of the
     *                      doclet.
     */
    private AnnotationTypeOptionalMemberBuilder(Configuration configuration) {
        super(configuration);
    }


    /**
     * Construct a new AnnotationTypeMemberBuilder.
     *
     * @param configuration the current configuration of the doclet.
     * @param classDoc the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    public static AnnotationTypeOptionalMemberBuilder getInstance(
            Configuration configuration, ClassDoc classDoc,
            AnnotationTypeOptionalMemberWriter writer) {
        AnnotationTypeOptionalMemberBuilder builder =
            new AnnotationTypeOptionalMemberBuilder(configuration);
        builder.classDoc = classDoc;
        builder.writer = writer;
        builder.visibleMemberMap = new VisibleMemberMap(classDoc,
            VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL, configuration.nodeprecated);
        builder.members = new ArrayList<ProgramElementDoc>(
            builder.visibleMemberMap.getMembersFor(classDoc));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(builder.members,
                configuration.getMemberComparator());
        }
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "AnnotationTypeOptionalMemberDetails";
    }

    /**
     * Build the member documentation.
     *
     * @param elements the XML elements that specify how to construct this
     *                documentation.
     */
    public void buildAnnotationTypeOptionalMember(List<?> elements) {
        if (writer == null) {
            return;
        }
        for (currentMemberIndex = 0; currentMemberIndex < members.size();
            currentMemberIndex++) {
            build(elements);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void invokeMethod(String methodName, Class<?>[] paramClasses,
            Object[] params)
    throws Exception {
        if (DEBUG) {
            configuration.root.printError("DEBUG: " + this.getClass().getName()
                + "." + methodName);
        }
        Method method = this.getClass().getMethod(methodName, paramClasses);
        method.invoke(this, params);
    }

    /**
     * Document the default value for this optional member.
     */
    public void buildDefaultValueInfo() {
        ((AnnotationTypeOptionalMemberWriter) writer).writeDefaultValueInfo(
            (MemberDoc) members.get(currentMemberIndex));
    }

    /**
     * {@inheritDoc}
     */
    public AnnotationTypeRequiredMemberWriter getWriter() {
        return writer;
    }
}
