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

import java.io.IOException;
import java.io.File;

import static java.util.Collections.*;
import static com.sun.mirror.util.DeclarationVisitors.*;

/*
 * Factory to help test updated discovery policy.
 */
public class Round2Apf implements AnnotationProcessorFactory {
    // Process @Round2
    private static final Collection<String> supportedAnnotations
        = unmodifiableCollection(Arrays.asList("Round2"));

    // No supported options
    private static final Collection<String> supportedOptions = emptySet();

    public Collection<String> supportedAnnotationTypes() {
        return supportedAnnotations;
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    private static int round = 0;

    public AnnotationProcessor getProcessorFor(
            Set<AnnotationTypeDeclaration> atds,
            AnnotationProcessorEnvironment env) {
        return new Round2Ap(env, atds.size() == 0);
    }

    private static class Round2Ap implements AnnotationProcessor {
        private final AnnotationProcessorEnvironment env;
        private final boolean empty;

        Round2Ap(AnnotationProcessorEnvironment env, boolean empty) {
            this.env = env;
            this.empty = empty;
        }

        public void process() {
            Round2Apf.round++;
            Filer f = env.getFiler();
            try {
                f.createSourceFile("Dummy2").println("@Round2 class Dummy2{}");
                throw new RuntimeException("Duplicate file creation allowed");
            } catch (IOException io) {}

            try {
                f.createTextFile(Filer.Location.SOURCE_TREE,
                                 "",
                                 new File("foo.txt"),
                                 null).println("xxyzzy");
                throw new RuntimeException("Duplicate file creation allowed");
            } catch (IOException io) {}

            try {
                f.createClassFile("Vacant");
                throw new RuntimeException("Duplicate file creation allowed");
            } catch (IOException io) {}

            try {
                f.createBinaryFile(Filer.Location.CLASS_TREE,
                                   "",
                                   new File("onezero"));
                throw new RuntimeException("Duplicate file creation allowed");
            } catch (IOException io) {}



            try {
                if (!empty) {
                    // Create corresponding files of opposite kind to
                    // the files created by Round1Apf; these should
                    // only generate warnings
                    f.createClassFile("Dummy2");
                    f.createSourceFile("Vacant").println("class Vacant{}");

                    f.createSourceFile("Dummy3").println("@Round3 class Dummy3{}");

                    // This should generated a warning too
                    f.createClassFile("Dummy3");
                }
            } catch (java.io.IOException ioe) {
                throw new RuntimeException(ioe);
            }

            System.out.println("Round2Apf: " + round);
        }
    }
}
