/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8174840
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestOverrides
 * @compile -processor TestOverrides -proc:only S.java
 */

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static javax.lang.model.element.ElementKind.METHOD;

public class TestOverrides extends JavacTestingAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!round.processingOver()) {
            var sm = mIn(elements.getTypeElement("S"));
            for (var subtypeName : new String[]{"T1", "T2", "T3", "T4", "T5"}) {
                var t = elements.getTypeElement(subtypeName);
                var tm = mIn(t);
                if (!elements.overrides(tm, sm, t))
                    messager.printError(String.format(
                            "%s does not override from %s method %s", tm, t.getQualifiedName(), sm));
            }
        }
        return true;
    }

    private ExecutableElement mIn(TypeElement t) {
        return t.getEnclosedElements().stream()
                .filter(e -> e.getKind() == METHOD)
                .filter(e -> e.getSimpleName().toString().equals("m"))
                .map(e -> (ExecutableElement) e)
                .findAny()
                .get();
    }
}
