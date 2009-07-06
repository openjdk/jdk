/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 6402506
 * @summary Test that getSourceVersion works properly
 * @author  Joseph D. Darcy
 * @compile TestSourceVersion.java
 * @compile -processor TestSourceVersion -proc:only -source 1.2 -AExpectedVersion=RELEASE_2 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source 1.3 -AExpectedVersion=RELEASE_3 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source 1.4 -AExpectedVersion=RELEASE_4 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source 1.5 -AExpectedVersion=RELEASE_5 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source   5 -AExpectedVersion=RELEASE_5 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source 1.6 -AExpectedVersion=RELEASE_6 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source   6 -AExpectedVersion=RELEASE_6 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source 1.7 -AExpectedVersion=RELEASE_7 HelloWorld.java
 * @compile -processor TestSourceVersion -proc:only -source   7 -AExpectedVersion=RELEASE_7 HelloWorld.java
 */

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import static javax.tools.Diagnostic.Kind.*;

/**
 * This processor checks that ProcessingEnvironment.getSourceVersion()
 * is consistent with the setting of the -source option.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("ExpectedVersion")
public class TestSourceVersion extends AbstractProcessor {

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnvironment) {
        SourceVersion expectedVersion =
            SourceVersion.valueOf(processingEnv.getOptions().get("ExpectedVersion"));
        SourceVersion actualVersion =  processingEnv.getSourceVersion();
        System.out.println("Expected SourceVersion " + expectedVersion +
                           " actual SourceVersion "  + actualVersion);
        if (expectedVersion != actualVersion)
            throw new RuntimeException();

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
