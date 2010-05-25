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
import java.util.Map;
import java.util.Arrays;


import static java.util.Collections.*;
import static com.sun.mirror.util.DeclarationVisitors.*;

/*
 * Construct a processor that does nothing but report a warning.
 */
public class WarnAPF implements AnnotationProcessorFactory {
    static class WarnAP implements AnnotationProcessor {
        AnnotationProcessorEnvironment env;
        WarnAP(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        public void process() {
            Messager messager = env.getMessager();
            messager.printWarning("Beware the ides of March!");

            for(TypeDeclaration typeDecl : env.getSpecifiedTypeDeclarations()) {
                messager.printNotice(typeDecl.getPosition(),  "You are about to be warned");
                messager.printWarning(typeDecl.getPosition(), "Strange class name");

                for(AnnotationMirror annotMirror : typeDecl.getAnnotationMirrors()) {
                    messager.printNotice("MIRROR " + annotMirror.getPosition().toString());

                     Map<AnnotationTypeElementDeclaration,AnnotationValue> map =
                         annotMirror.getElementValues();
                     if (map.keySet().size() > 0)
                         for(AnnotationTypeElementDeclaration key : map.keySet() ) {
                             AnnotationValue annotValue = map.get(key);
                             Object o = annotValue.getValue();
                             // asserting getPosition is non-null
                             messager.printNotice("VALUE " + annotValue.getPosition().toString());
                         }
                     else {
                         Collection<AnnotationTypeElementDeclaration> ateds =
                         annotMirror.getAnnotationType().getDeclaration().getMethods();
                         for(AnnotationTypeElementDeclaration ated : ateds ) {
                             AnnotationValue annotValue = ated.getDefaultValue();
                             Object o = annotValue.getValue();
                             messager.printNotice("VALUE " + "HelloAnnotation.java:5");
                         }
                     }
                }
            }
        }
    }

    static final Collection<String> supportedTypes;
    static {
        String types[] = {"*"};
        supportedTypes = unmodifiableCollection(Arrays.asList(types));
    }
    public Collection<String> supportedAnnotationTypes() {return supportedTypes;}

    static final Collection<String> supportedOptions;
    static {
        String options[] = {""};
        supportedOptions = unmodifiableCollection(Arrays.asList(options));
    }
    public Collection<String> supportedOptions() {return supportedOptions;}

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                               AnnotationProcessorEnvironment env) {
        return new WarnAP(env);
    }
}
