/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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


import com.sun.mirror.apt.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import java.util.Collection;
import java.util.Set;
import java.util.Arrays;

import java.io.*;

import static java.util.Collections.*;

/*
 * This class is used to test the ability to store static state across
 * apt rounds.
 */
public class ClassDeclApf implements AnnotationProcessorFactory {
    static int round = -1;

    // Process any set of annotations
    private static final Collection<String> supportedAnnotations
        = unmodifiableCollection(Arrays.asList("*"));

    // No supported options
    private static final Collection<String> supportedOptions = emptySet();

    public Collection<String> supportedAnnotationTypes() {
        return supportedAnnotations;
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                               AnnotationProcessorEnvironment env) {
        return new ClassDeclAp(env);
    }

    private static class ClassDeclAp implements AnnotationProcessor {
        private final AnnotationProcessorEnvironment env;
        ClassDeclAp(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        // Simple inefficient drain
        void drain(InputStream is, OutputStream os) {
            try {
            while (is.available() > 0 )
                os.write(is.read());
            } catch (java.io.IOException ioe) {
                throw new RuntimeException(ioe);
            }

        }

        public void process() {
            int size = env.getSpecifiedTypeDeclarations().size();

            try {
                round++;
                switch (size) {
                case 0:
                    if (round == 0) {
                        drain(new FileInputStream("./tmp/classes/Round1Class.class"),
                              env.getFiler().createClassFile("Round1Class"));
                    } else
                        throw new RuntimeException("Got " + size + " decl's in round " + round);
                    break;

                case 1:
                    if (round == 1) {
                        drain(new FileInputStream("./tmp/classes/AhOneClass.class"),
                              env.getFiler().createClassFile("AhOneClass"));

                        drain(new FileInputStream("./tmp/classes/AndAhTwoClass.class"),
                              env.getFiler().createClassFile("AndAhTwoClass"));
                    } else
                        throw new RuntimeException("Got " + size + " decl's in round " + round);
                    break;
                case 2:
                    if (round != 2) {
                        throw new RuntimeException("Got " + size + " decl's in round " + round);
                    }
                    break;
                }

            } catch (java.io.IOException ioe) {
                    throw new RuntimeException();
                }

            }

    }
}
