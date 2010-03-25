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
 * @bug     6424358
 * @summary Synthesized static enum method values() is final
 * @author  Peter von der Ah\u00e9
 * @compile T6424358.java
 * @compile -processor T6424358 -proc:only T6424358.java
 */

import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.lang.model.SourceVersion;
import static javax.tools.Diagnostic.Kind.*;

@interface TestMe {}

@SupportedAnnotationTypes("*")
public class T6424358 extends AbstractProcessor {
    @TestMe enum Test { FOO; }

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnvironment) {
        final Messager log = processingEnv.getMessager();
        final Elements elements = processingEnv.getElementUtils();
        final TypeElement testMe = elements.getTypeElement("TestMe");
        class Scan extends ElementScanner6<Void,Void> {
            @Override
            public Void visitExecutable(ExecutableElement e, Void p) {
                System.err.println("Looking at " + e);
                if ("values".contentEquals(e.getSimpleName()) &&
                    e.getModifiers().contains(Modifier.FINAL)) {
                    log.printMessage(ERROR, "final modifier on values()", e);
                    throw new AssertionError("final modifier on values()"); // See bug 6403468
                }
                return null;
            }
        }
        Scan scan = new Scan();
        for (Element e : roundEnvironment.getElementsAnnotatedWith(testMe))
            scan.scan(e);
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
