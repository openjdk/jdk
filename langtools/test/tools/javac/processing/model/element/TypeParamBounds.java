/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6423972
 * @summary Tests TypeParameter.getBounds.
 * @author  Scott Seligman
 * @build   TypeParamBounds
 * @compile -processor TypeParamBounds -proc:only TypeParamBounds.java
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

@SupportedAnnotationTypes("*")
public class TypeParamBounds extends AbstractProcessor {

    Elements elements;
    Types types;

    public void init(ProcessingEnvironment penv) {
        super.init(penv);
        elements = penv.getElementUtils();
        types = penv.getTypeUtils();
    }

    public boolean process(Set<? extends TypeElement> annoTypes,
                           RoundEnvironment round) {
        if (!round.processingOver())
            doit(annoTypes, round);
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private void doit(Set<? extends TypeElement> annoTypes,
                      RoundEnvironment round) {
        TypeElement gen = elements.getTypeElement("TypeParamBounds.Gen");

        // For each type parameter of Gen, compare its bounds with the
        // bounds that are expected.
        //
        for (TypeParameterElement tparam : gen.getTypeParameters()) {
            System.out.println(tparam);
            List<? extends TypeMirror> bounds = tparam.getBounds();
            String[] expected = Gen.boundNames.get(tparam + "");

            if (bounds.size() != expected.length)
                throw new AssertionError();
            int i = 0;
            for (TypeMirror bound : bounds) {
                Name got = types.asElement(bound).getSimpleName();
                String shoulda = expected[i++];
                System.out.println("  " + got);
                if (!got.contentEquals(shoulda))
                    throw new AssertionError(shoulda);
            }
        }
    }


    // Fodder for the processor

    static class Gen<T, U extends Object, V extends Number, W extends U,
                     X extends Runnable, Y extends CharSequence & Runnable,
                     Z extends Object & Runnable> {

        // The names of the bounds of each type parameter of Gen.
        static Map<String, String[]> boundNames =
                new HashMap<String, String[]>();

        static {
            boundNames.put("T", new String[] {"Object"});
            boundNames.put("U", new String[] {"Object"});
            boundNames.put("V", new String[] {"Number"});
            boundNames.put("W", new String[] {"U"});
            boundNames.put("X", new String[] {"Runnable"});
            boundNames.put("Y", new String[] {"CharSequence", "Runnable"});
            boundNames.put("Z", new String[] {"Object", "Runnable"});
        }
    }
}
