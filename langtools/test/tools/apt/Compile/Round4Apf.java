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

import static java.util.Collections.*;
import static com.sun.mirror.util.DeclarationVisitors.*;

/*
 * Factory to help test updated discovery policy.
 */
public class Round4Apf implements AnnotationProcessorFactory {
    // Process @Round4
    private static final Collection<String> supportedAnnotations
        = unmodifiableCollection(Arrays.asList("Round4"));

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
        return new Round4Ap(env, atds.size() == 0);
    }

    private static class Round4Ap implements AnnotationProcessor, RoundCompleteListener {
        private final AnnotationProcessorEnvironment env;
        private final boolean empty;

        Round4Ap(AnnotationProcessorEnvironment env, boolean empty) {
            this.env = env;
            this.empty = empty;
        }

        public void process() {
            Round4Apf.round++;
            try {
                if (!empty)
                    env.getFiler().createSourceFile("Dummy5").println("@Round5 class Dummy5{}");
            } catch (java.io.IOException ioe) {
                throw new RuntimeException(ioe);
            }

            System.out.println("Round4Apf: " + round);
            env.addListener(this);
        }

        public void roundComplete(RoundCompleteEvent event) {
            RoundState rs = event.getRoundState();

            System.out.println("\t" + rs.toString());

            System.out.println("Round4Apf: " + round + " complete");

            if (rs.finalRound()) {
                System.out.println("Valediction");
            }
        }
    }
}
