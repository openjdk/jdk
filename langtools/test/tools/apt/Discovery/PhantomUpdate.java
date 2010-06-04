/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * JSR 269 annotation processor to test combined apt + JSR 269
 * annotation processor file generation and option passing.
 */

import javax.annotation.processing.*;
import static  javax.lang.model.SourceVersion.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.util.*;
import java.io.*;

@SupportedAnnotationTypes("*")  // Process all annotations
@SupportedSourceVersion(RELEASE_6)
public class PhantomUpdate extends AbstractProcessor {
    boolean firstRound = true;

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (firstRound) {
            verifyOptions();
            printGoodbyeWorld();
            firstRound = false;
        }
        return true; // Claim all annotations
    }

    /*
     * Expected options are "foo" and "bar=baz".
     */
    private void verifyOptions() {
        Map<String, String> actualOptions = processingEnv.getOptions();
        Map<String, String> expectedOptions = new LinkedHashMap<String, String>();
        expectedOptions.put("foo", null);
        expectedOptions.put("bar", "baz");

        if (!actualOptions.equals(expectedOptions) ) {
            System.err.println("Expected options " + expectedOptions +
                               "\n but got " + actualOptions);
            throw new RuntimeException("Options mismatch");
        }
    }

    private void printGoodbyeWorld() {
        try {
            // Create new source file
            PrintWriter pw = new PrintWriter(processingEnv.getFiler().createSourceFile("GoodbyeWorld").openWriter());
            pw.println("public class GoodbyeWorld {");
            pw.println("  // PhantomUpdate Goodbye world");
            pw.println("  public static void main(String argv[]) {");
            pw.println("    System.out.println(\"Goodbye World\");");
            pw.println("  }");
            pw.println("}");
            pw.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
