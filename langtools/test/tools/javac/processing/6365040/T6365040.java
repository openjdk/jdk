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
 * @bug 6365040 6358129
 * @summary Test -processor foo,bar,baz
 * @author  Joseph D. Darcy
 * @compile ProcFoo.java
 * @compile ProcBar.java
 * @compile T6365040.java
 * @compile      -processor ProcFoo,ProcBar,T6365040  -proc:only T6365040.java
 * @compile      -processor T6365040                  -proc:only T6365040.java
 * @compile      -processor T6365040,NotThere,        -proc:only T6365040.java
 * @compile/fail -processor NotThere                  -proc:only T6365040.java
 * @compile/fail -processor NotThere,T6365040         -proc:only T6365040.java
 */

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import static javax.tools.Diagnostic.Kind.*;

@SupportedAnnotationTypes("*")
public class T6365040 extends AbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnvironment) {
        if (!roundEnvironment.processingOver())
            processingEnv.getMessager().printMessage(NOTE,
                                                     "Hello from T6365040");
        return true;
    }
}
