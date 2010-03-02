/*
 * Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package com.sun.tools.apt.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.apt.util.Bark;
import com.sun.tools.javac.util.Position;

import java.util.*;
import java.util.regex.*;
import java.lang.reflect.*;
import java.lang.reflect.InvocationTargetException;
import java.io.IOException;

import com.sun.tools.apt.*;
import com.sun.tools.apt.comp.*;
import com.sun.tools.javac.code.Symbol.*;

import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.mirror.apt.*;
// import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessors;

import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.apt.mirror.apt.FilerImpl;
import com.sun.tools.apt.mirror.apt.AnnotationProcessorEnvironmentImpl;


import static com.sun.tools.apt.mirror.declaration.DeclarationMaker.isJavaIdentifier;

/**
 * Apt compiler phase.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 */
@SuppressWarnings("deprecation")
public class Apt extends ListBuffer<Env<AttrContext>> {
    java.util.Set<String> genSourceFileNames = new java.util.LinkedHashSet<String>();
    public java.util.Set<String> getSourceFileNames() {
        return genSourceFileNames;
    }

    /** List of names of generated class files.
     */
    java.util.Set<String> genClassFileNames  = new java.util.LinkedHashSet<String>();
    public java.util.Set<String> getClassFileNames() {
        return genClassFileNames;
    }

    /* AptEnvironment */
    AptEnv aptenv;

    private Context context;

    /** The context key for the todo list. */

    protected static final Context.Key<Apt> aptKey =
        new Context.Key<Apt>();

    /** Get the Apt instance for this context. */
    public static Apt instance(Context context) {
        Apt instance = context.get(aptKey);
        if (instance == null)
            instance = new Apt(context);
        return instance;
    }

    /** Create a new apt list. */
    protected Apt(Context context) {
        this.context = context;

        context.put(aptKey, this);
        aptenv = AptEnv.instance(context);
    }

    /**
     * Used to scan javac trees to build data structures needed for
     * bootstrapping the apt environment.  In particular:
     *
     * <ul>
     *
     * <li> Generate list of canonical names of annotation types that
     * appear in source files given on the command line
     *
     * <li> Collect list of javac symbols representing source files
     * given on the command line
     *
     * </ul>
     */
    static class AptTreeScanner extends TreeScanner {

        // Set of fully qualified names of annotation types present in
        // examined source
        private Set<String> annotationSet;

        // Symbols to build bootstrapping declaration list
        private Collection<ClassSymbol> specifiedDeclCollection;
        private Collection<ClassSymbol> declCollection;

        public Set<String> getAnnotationSet() {
            return annotationSet;
        }

        public AptTreeScanner() {
            annotationSet = new  LinkedHashSet<String>();
            specifiedDeclCollection = new LinkedHashSet<ClassSymbol>();
            declCollection = new LinkedHashSet<ClassSymbol>();
        }

        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            super.visitTopLevel(tree);
            // Print out contents -- what are we dealing with?

            for(JCTree d: tree.defs) {
                if (d instanceof JCTree.JCClassDecl)
                    specifiedDeclCollection.add(((JCTree.JCClassDecl) d).sym);
            }

        }

        public void visitBlock(JCTree.JCBlock tree) {
            ; // Do nothing.
        }


        // should add nested classes to packages, etc.
        public void visitClassDef(JCTree.JCClassDecl tree) {
            if (tree.sym == null) {
                // could be an anon class w/in an initializer
                return;
            }

            super.visitClassDef(tree);

            declCollection.add(tree.sym);
        }

        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            super.visitMethodDef(tree);
        }

        public void visitVarDef(JCTree.JCVariableDecl tree) {
            super.visitVarDef(tree);
        }

        public void visitAnnotation(JCTree.JCAnnotation tree) {
            super.visitAnnotation(tree);
            annotationSet.add(tree.type.tsym.toString());
        }
    }

    Set<String> computeAnnotationSet(Collection<ClassSymbol> classSymbols) {
        Set<String> annotationSet = new HashSet<String>();

        for(ClassSymbol classSymbol: classSymbols) {
            computeAnnotationSet(classSymbol, annotationSet);
        }
        return annotationSet;
    }

    void computeAnnotationSet(Symbol symbol, Set<String> annotationSet) {
        if (symbol != null ) {
            if (symbol.getAnnotationMirrors() != null)
                for(Attribute.Compound compound: symbol.getAnnotationMirrors())
                    annotationSet.add(compound.type.tsym.toString()); // should fullName be used instead of toString?

            if (symbol instanceof Symbol.MethodSymbol) // add parameter annotations
                for(Symbol param: ((MethodSymbol) symbol).params())
                    computeAnnotationSet(param, annotationSet);

            if (symbol.members() != null) {
                for(Scope.Entry e = symbol.members().elems; e != null; e = e.sibling)
                    computeAnnotationSet(e.sym, annotationSet);
            }
        }
    }

    public void main(com.sun.tools.javac.util.List<JCTree.JCCompilationUnit> treeList,
                     ListBuffer<ClassSymbol> classes,
                     Map<String, String> origOptions,
                     ClassLoader aptCL,
                     AnnotationProcessorFactory providedFactory,
                     java.util.Set<Class<? extends AnnotationProcessorFactory> > productiveFactories) {
        Bark bark = Bark.instance(context);
        java.io.PrintWriter out = bark.warnWriter;
        Options options = Options.instance(context);

        Collection<TypeDeclaration> spectypedecls =     new LinkedHashSet<TypeDeclaration>();
        Collection<TypeDeclaration> typedecls =         new LinkedHashSet<TypeDeclaration>();
        Set<String> unmatchedAnnotations =              new LinkedHashSet<String>();
        Set<AnnotationTypeDeclaration> emptyATDS =      Collections.emptySet();
        Set<Class<? extends AnnotationProcessorFactory> > currentRoundFactories =
            new LinkedHashSet<Class<? extends AnnotationProcessorFactory> >();

        // Determine what annotations are present on the input source
        // files, create collections of specified type declarations,
        // and type declarations.
        AptTreeScanner ats = new AptTreeScanner();
        for(JCTree t: treeList) {
            t.accept(ats);
        }

        // Turn collection of ClassSymbols into Collection of apt decls
        for (ClassSymbol cs : ats.specifiedDeclCollection) {
            TypeDeclaration decl = aptenv.declMaker.getTypeDeclaration(cs);
            spectypedecls.add(decl);
        }

        for (ClassSymbol cs : ats.declCollection) {
            TypeDeclaration decl = aptenv.declMaker.getTypeDeclaration(cs);
            typedecls.add(decl);
        }

        unmatchedAnnotations.addAll(ats.getAnnotationSet());

        // Process input class files
        for(ClassSymbol cs : classes) {
            TypeDeclaration decl = aptenv.declMaker.getTypeDeclaration(cs);
            // System.out.println("Adding a class to spectypedecls");
            spectypedecls.add(decl);
            typedecls.add(decl);
            computeAnnotationSet(cs, unmatchedAnnotations);
        }

        if (options.get("-XListAnnotationTypes") != null) {
            out.println("Set of annotations found:" +
                        (new TreeSet<String>(unmatchedAnnotations)).toString());
        }

        AnnotationProcessorEnvironmentImpl trivAPE =
            new AnnotationProcessorEnvironmentImpl(spectypedecls, typedecls, origOptions, context);

        if (options.get("-XListDeclarations") != null) {
            out.println("Set of Specified Declarations:" +
                        spectypedecls);

            out.println("Set of Included Declarations: " +
                           typedecls);
        }

        if (options.get("-print") != null) {
            if (spectypedecls.size() == 0 )
                throw new UsageMessageNeededException();

            // Run the printing processor
            AnnotationProcessor proc = (new BootstrapAPF()).getProcessorFor(new HashSet<AnnotationTypeDeclaration>(),
                                                                            trivAPE);
            proc.process();
        } else {
            // Discovery process

            // List of annotation processory factory instances
            java.util.Iterator<AnnotationProcessorFactory> providers = null;
            {
                /*
                 * If a factory is provided by the user, the
                 * "-factory" and "-factorypath" options are not used.
                 *
                 * Otherwise, if the "-factory" option is used, search
                 * the appropriate path for the named class.
                 * Otherwise, use sun.misc.Service to implement the
                 * default discovery policy.
                 */

                java.util.List<AnnotationProcessorFactory> list =
                    new LinkedList<AnnotationProcessorFactory>();
                String factoryName = options.get("-factory");

                if (providedFactory != null) {
                    list.add(providedFactory);
                    providers = list.iterator();
                } else if (factoryName != null) {
                    try {
                        AnnotationProcessorFactory factory =
                            (AnnotationProcessorFactory) (aptCL.loadClass(factoryName).newInstance());
                        list.add(factory);
                    } catch (ClassNotFoundException cnfe) {
                        bark.aptWarning("FactoryNotFound", factoryName);
                    } catch (ClassCastException cce) {
                        bark.aptWarning("FactoryWrongType", factoryName);
                    } catch (Exception e ) {
                        bark.aptWarning("FactoryCantInstantiate", factoryName);
                    } catch(Throwable t) {
                        throw new AnnotationProcessingError(t);
                    }

                    providers = list.iterator();
                } else {
                    @SuppressWarnings("unchecked")
                    Iterator<AnnotationProcessorFactory> iter =
                            sun.misc.Service.providers(AnnotationProcessorFactory.class, aptCL);
                    providers = iter;

                }
            }

            java.util.Map<AnnotationProcessorFactory, Set<AnnotationTypeDeclaration>> factoryToAnnotation =
                new LinkedHashMap<AnnotationProcessorFactory, Set<AnnotationTypeDeclaration>>();

            if (!providers.hasNext() && productiveFactories.size() == 0) {
                if (unmatchedAnnotations.size() > 0)
                    bark.aptWarning("NoAnnotationProcessors");
                if (spectypedecls.size() == 0)
                    throw new UsageMessageNeededException();
                return; // no processors; nothing else to do
            } else {
                // If there are no annotations, still give
                // processors that match everything a chance to
                // run.

                if(unmatchedAnnotations.size() == 0)
                    unmatchedAnnotations.add("");

                Set<String> emptyStringSet = new HashSet<String>();
                emptyStringSet.add("");
                emptyStringSet = Collections.unmodifiableSet(emptyStringSet);

                while (providers.hasNext() ) {
                    Object provider = providers.next();
                    try {
                        Set<String> matchedStrings = new HashSet<String>();

                        AnnotationProcessorFactory apf = (AnnotationProcessorFactory) provider;
                        Collection<String> supportedTypes = apf.supportedAnnotationTypes();

                        Collection<Pattern> supportedTypePatterns = new LinkedList<Pattern>();
                        for(String s: supportedTypes)
                            supportedTypePatterns.add(importStringToPattern(s));

                        for(String s: unmatchedAnnotations) {
                            for(Pattern p: supportedTypePatterns) {
                                if (p.matcher(s).matches()) {
                                    matchedStrings.add(s);
                                    break;
                                }
                            }
                        }

                        unmatchedAnnotations.removeAll(matchedStrings);

                        if (options.get("-XPrintFactoryInfo") != null) {
                            out.println("Factory " + apf.getClass().getName() +
                                        " matches " +
                                        ((matchedStrings.size() == 0)?
                                         "nothing.": matchedStrings));
                        }

                        if (matchedStrings.size() > 0) {
                            // convert annotation names to annotation
                            // type decls
                            Set<AnnotationTypeDeclaration> atds = new HashSet<AnnotationTypeDeclaration>();

                            // If a "*" processor is called on the
                            // empty string, pass in an empty set of
                            // annotation type declarations.
                            if (!matchedStrings.equals(emptyStringSet)) {
                                for(String s: matchedStrings) {
                                    TypeDeclaration decl = aptenv.declMaker.getTypeDeclaration(s);
                                    AnnotationTypeDeclaration annotdecl;
                                    if (decl == null) {
                                        bark.aptError("DeclarationCreation", s);
                                    } else {
                                        try {
                                            annotdecl = (AnnotationTypeDeclaration)decl;
                                            atds.add(annotdecl);

                                        } catch (ClassCastException cce) {
                                            bark.aptError("BadDeclaration", s);
                                        }
                                    }
                                }
                            }

                            currentRoundFactories.add(apf.getClass());
                            productiveFactories.add(apf.getClass());
                            factoryToAnnotation.put(apf, atds);
                        } else if (productiveFactories.contains(apf.getClass())) {
                            // If a factory provided a processor in a
                            // previous round but doesn't match any
                            // annotations this round, call it with an
                            // empty set of declarations.
                            currentRoundFactories.add(apf.getClass());
                            factoryToAnnotation.put(apf, emptyATDS );
                        }

                        if (unmatchedAnnotations.size() == 0)
                            break;

                    } catch (ClassCastException cce) {
                        bark.aptWarning("BadFactory", cce);
                    }
                }

                unmatchedAnnotations.remove("");
            }

            // If the set difference of productiveFactories and
            // currentRoundFactories is non-empty, call the remaining
            // productive factories with an empty set of declarations.
            {
                java.util.Set<Class<? extends AnnotationProcessorFactory> > neglectedFactories =
                    new LinkedHashSet<Class<? extends AnnotationProcessorFactory>>(productiveFactories);
                neglectedFactories.removeAll(currentRoundFactories);
                for(Class<? extends AnnotationProcessorFactory> working : neglectedFactories) {
                    try {
                        AnnotationProcessorFactory factory = working.newInstance();
                        factoryToAnnotation.put(factory, emptyATDS);
                    } catch (Exception e ) {
                        bark.aptWarning("FactoryCantInstantiate", working.getName());
                    } catch(Throwable t) {
                        throw new AnnotationProcessingError(t);
                    }
                }
            }

            if (unmatchedAnnotations.size() > 0)
                bark.aptWarning("AnnotationsWithoutProcessors", unmatchedAnnotations);

            Set<AnnotationProcessor> processors = new LinkedHashSet<AnnotationProcessor>();

            // If there were no source files AND no factory matching "*",
            // make sure the usage message is printed
            if (spectypedecls.size() == 0 &&
                factoryToAnnotation.keySet().size() == 0 )
                throw new UsageMessageNeededException();

            try {
                for(AnnotationProcessorFactory apFactory: factoryToAnnotation.keySet()) {
                    AnnotationProcessor processor = apFactory.getProcessorFor(factoryToAnnotation.get(apFactory),
                                                                              trivAPE);
                    if (processor != null)
                        processors.add(processor);
                    else
                        bark.aptWarning("NullProcessor", apFactory.getClass().getName());
                }
            } catch(Throwable t) {
                throw new AnnotationProcessingError(t);
            }

            LinkedList<AnnotationProcessor> temp = new LinkedList<AnnotationProcessor>();
            temp.addAll(processors);

            AnnotationProcessor proc = AnnotationProcessors.getCompositeAnnotationProcessor(temp);

            try {
                proc.process();
            } catch (Throwable t) {
                throw new AnnotationProcessingError(t);
            }

            // Invoke listener callback mechanism
            trivAPE.roundComplete();

            FilerImpl filerimpl = (FilerImpl)trivAPE.getFiler();
            genSourceFileNames = filerimpl.getSourceFileNames();
            genClassFileNames = filerimpl.getClassFileNames();
            filerimpl.flush(); // Make sure new files are written out
        }
    }

    /**
     * Convert import-style string to regex matching that string.  If
     * the string is a valid import-style string, return a regex that
     * won't match anything.
     */
    Pattern importStringToPattern(String s) {
        if (com.sun.tools.javac.processing.JavacProcessingEnvironment.isValidImportString(s)) {
            return com.sun.tools.javac.processing.JavacProcessingEnvironment.validImportStringToPattern(s);
        } else {
            Bark bark = Bark.instance(context);
            bark.aptWarning("MalformedSupportedString", s);
            return com.sun.tools.javac.processing.JavacProcessingEnvironment.noMatches;
        }
    }
}
