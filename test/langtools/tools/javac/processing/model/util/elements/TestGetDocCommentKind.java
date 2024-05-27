/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8307184
 * @summary Test basic operation of Elements.getDocCommentKind
 * @library /tools/lib /tools/javac/lib
 * @build   toolbox.ToolBox JavacTestingAbstractProcessor TestGetDocCommentKind
 * @compile -processor TestGetDocCommentKind -proc:only TestGetDocCommentKind.java
 */

import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.lang.model.util.Elements.DocCommentKind;

public class TestGetDocCommentKind extends JavacTestingAbstractProcessor {
    final Elements vacuousElements = new VacuousElements();

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            boolean elementSeen = false;

            for (TypeElement typeRoot : ElementFilter.typesIn(roundEnv.getRootElements()) ) {
                for (Element element : typeRoot.getEnclosedElements()) {
                    ExpectedKind expectedKind = element.getAnnotation(ExpectedKind.class);
                    if (expectedKind != null ) {
                        elementSeen = true;

                        checkKind(element, elements, expectedKind.value());
                        checkKind(element, vacuousElements, null);
                    }
                }

                if (!elementSeen) {
                    throw new RuntimeException("No elements seen.");
                }
            }
        }
        return true;
    }

    void checkKind(Element e, Elements elementUtils, DocCommentKind expectedKind) {
        var actualKind = elementUtils.getDocCommentKind(e);
        if (actualKind != expectedKind) {
            messager.printError("Unexpected doc comment kind found: " + actualKind
                    + "expected: " + expectedKind, e);
        }
    }

    @interface ExpectedKind {
        DocCommentKind value();
    }

    /**
     * Traditional comment.
     */
    @ExpectedKind(DocCommentKind.TRADITIONAL)
    public void traditionalComment() { }

    /// End-of-line comment.
    @ExpectedKind(DocCommentKind.END_OF_LINE)
    public void endOfLineComment() { }
}
