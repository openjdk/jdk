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
 * Construct a processor that does nothing but report an error.
 */
public class ErrorAPF implements AnnotationProcessorFactory {
    static class ErrorAP implements AnnotationProcessor {
        AnnotationProcessorEnvironment env;
        ErrorAP(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        public void process() {
            Messager messager = env.getMessager();
            messager.printError("It's a mad, mad, mad, mad world");
            messager.printError("Something wicked this way comes");

            for(TypeDeclaration typeDecl : env.getSpecifiedTypeDeclarations())
                messager.printError(typeDecl.getPosition(), "Boring class name");
        }
    }

    static Collection<String> supportedTypes;
    static {
        String types[] = {"*"};
        supportedTypes = unmodifiableCollection(Arrays.asList(types));
    }

    static Collection<String> supportedOptions;
    static {
        String options[] = {""};
        supportedOptions = unmodifiableCollection(Arrays.asList(options));
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public Collection<String> supportedAnnotationTypes() {
        return supportedTypes;
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                               AnnotationProcessorEnvironment env) {
        return new ErrorAP(env);
    }
}
