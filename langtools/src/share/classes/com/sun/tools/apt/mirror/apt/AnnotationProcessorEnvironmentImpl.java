/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.apt.mirror.apt;

import com.sun.mirror.declaration.*;
import com.sun.mirror.util.*;
import com.sun.mirror.apt.*;
import com.sun.tools.apt.mirror.apt.*;
import com.sun.tools.apt.mirror.declaration.DeclarationMaker;
import com.sun.tools.apt.mirror.util.*;
import com.sun.tools.apt.util.Bark;
import com.sun.tools.javac.util.Context;

import com.sun.tools.apt.mirror.apt.FilerImpl;
import com.sun.tools.apt.mirror.apt.MessagerImpl;
import com.sun.tools.apt.mirror.apt.RoundStateImpl;
import com.sun.tools.apt.mirror.apt.RoundCompleteEventImpl;

import com.sun.tools.javac.util.Context;

import java.util.*;
import static com.sun.mirror.util.DeclarationVisitors.*;

/*
 * Annotation Processor Environment implementation.
 */
@SuppressWarnings("deprecation")
public class AnnotationProcessorEnvironmentImpl implements AnnotationProcessorEnvironment {

    Collection<TypeDeclaration> spectypedecls;
    Collection<TypeDeclaration> typedecls;
    Map<String, String> origOptions;
    DeclarationMaker declMaker;
    Declarations declUtils;
    Types typeUtils;
    Messager messager;
    FilerImpl filer;
    Bark bark;
    Set<RoundCompleteListener> roundCompleteListeners;

    public AnnotationProcessorEnvironmentImpl(Collection<TypeDeclaration> spectypedecls,
                                              Collection<TypeDeclaration> typedecls,
                                              Map<String, String> origOptions,
                                              Context context) {
        // Safer to copy collections before applying unmodifiable
        // wrapper.
        this.spectypedecls = Collections.unmodifiableCollection(spectypedecls);
        this.typedecls = Collections.unmodifiableCollection(typedecls);
        this.origOptions = Collections.unmodifiableMap(origOptions);

        declMaker = DeclarationMaker.instance(context);
        declUtils = DeclarationsImpl.instance(context);
        typeUtils = TypesImpl.instance(context);
        messager = MessagerImpl.instance(context);
        filer = FilerImpl.instance(context);
        bark = Bark.instance(context);
        roundCompleteListeners = new LinkedHashSet<RoundCompleteListener>();
    }

    public Map<String,String> getOptions() {
        return origOptions;
    }

    public Messager getMessager() {
        return messager;
    }

    public Filer getFiler() {
        return filer;
    }

    public Collection<TypeDeclaration> getSpecifiedTypeDeclarations() {
        return spectypedecls;
    }

    public PackageDeclaration getPackage(String name) {
        return declMaker.getPackageDeclaration(name);
    }

    public TypeDeclaration getTypeDeclaration(String name) {
        return declMaker.getTypeDeclaration(name);
    }

    public Collection<TypeDeclaration> getTypeDeclarations() {
        return typedecls;
    }

    public Collection<Declaration> getDeclarationsAnnotatedWith(
                                                AnnotationTypeDeclaration a) {
        /*
         * create collection of Declarations annotated with a given
         * annotation.
         */

        CollectingAP proc = new CollectingAP(this, a);
        proc.process();
        return proc.decls;
    }

    private static class CollectingAP implements AnnotationProcessor {
        AnnotationProcessorEnvironment env;
        Collection<Declaration> decls;
        AnnotationTypeDeclaration atd;
        CollectingAP(AnnotationProcessorEnvironment env,
                     AnnotationTypeDeclaration atd) {
            this.env = env;
            this.atd = atd;
            decls = new HashSet<Declaration>();
        }

        private class CollectingVisitor extends SimpleDeclarationVisitor {
            public void visitDeclaration(Declaration d) {
                for(AnnotationMirror am: d.getAnnotationMirrors()) {
                    if (am.getAnnotationType().getDeclaration().equals(CollectingAP.this.atd))
                        CollectingAP.this.decls.add(d);
                }
            }
        }

        public void process() {
            for(TypeDeclaration d: env.getSpecifiedTypeDeclarations())
                d.accept(getSourceOrderDeclarationScanner(new CollectingVisitor(),
                                                          NO_OP));
        }
    }

    public Declarations getDeclarationUtils() {
        return declUtils;
    }

    public Types getTypeUtils() {
        return typeUtils;
    }

    public void addListener(AnnotationProcessorListener listener) {
        if (listener == null)
            throw new NullPointerException();
        else {
            if (listener instanceof RoundCompleteListener)
                roundCompleteListeners.add((RoundCompleteListener)listener);
        }
    }

    public void removeListener(AnnotationProcessorListener listener) {
        if (listener == null)
            throw new NullPointerException();
        else
            roundCompleteListeners.remove(listener);
    }

    public void roundComplete() {
        RoundState roundState  = new RoundStateImpl(bark.nerrors > 0,
                                                    filer.getSourceFileNames().size() > 0,
                                                    filer.getClassFileNames().size() > 0,
                                                    origOptions);
        RoundCompleteEvent roundCompleteEvent = new RoundCompleteEventImpl(this, roundState);

        filer.roundOver();
        for(RoundCompleteListener rcl: roundCompleteListeners)
            rcl.roundComplete(roundCompleteEvent);
    }
}
