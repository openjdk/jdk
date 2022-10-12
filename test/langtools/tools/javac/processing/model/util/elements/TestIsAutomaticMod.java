/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8261625
 * @summary Test Elements.isAutomaticModule
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestIsAutomaticMod
 * @compile -processor TestIsAutomaticMod -proc:only TestIsAutomaticMod.java
 */

import java.io.Writer;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test basic workings of Elements.isAutomaticModule
 */
public class TestIsAutomaticMod extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            // Named module java.base
            checkMod(eltUtils.getModuleElement("java.base"), false);

            // Unnamed module for TestIsAutomaticMod
            for (Element e : roundEnv.getRootElements() ) {
                ModuleElement enclosing = elements.getModuleOf(e);
                checkMod(enclosing, false);
            }

            if ((new VacuousElements()).isAutomaticModule(null) != false) {
                throw new RuntimeException("Bad behavior from default isAutomaticModule method");
            }
        }
        return true;
    }

    private void checkMod(ModuleElement mod, boolean expectedIsAuto) {
        boolean actualIsAuto = elements.isAutomaticModule(mod);
        if (actualIsAuto != expectedIsAuto) {
            throw new RuntimeException(String.format("Unexpected isAutomatic ``%s''' for %s, expected ``%s''%n",
                                                     actualIsAuto,
                                                     mod,
                                                     expectedIsAuto));
        }
    }
}
