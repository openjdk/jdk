/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;


/**
 * This is a utility class for managing indentation and other basic
 * formatting for PrintWriter.
 */
public final class JFormatter {
    /** all classes and ids encountered during the collection mode **/
    /** map from short type name to ReferenceList (list of JClass and ids sharing that name) **/
    private HashMap<String,ReferenceList> collectedReferences;

    /** set of imported types (including package java types, eventhough we won't generate imports for them) */
    private HashSet<JClass> importedClasses;

    private static enum Mode {
        /**
         * Collect all the type names and identifiers.
         * In this mode we don't actually generate anything.
         */
        COLLECTING,
        /**
         * Print the actual source code.
         */
        PRINTING
    }

    /**
     * The current running mode.
     * Set to PRINTING so that a casual client can use a formatter just like before.
     */
    private Mode mode = Mode.PRINTING;

    /**
     * Current number of indentation strings to print
     */
    private int indentLevel;

    /**
     * String to be used for each indentation.
     * Defaults to four spaces.
     */
    private final String indentSpace;

    /**
     * Stream associated with this JFormatter
     */
    private final PrintWriter pw;

    /**
     * Creates a JFormatter.
     *
     * @param s
     *        PrintWriter to JFormatter to use.
     *
     * @param space
     *        Incremental indentation string, similar to tab value.
     */
    public JFormatter(PrintWriter s, String space) {
        pw = s;
        indentSpace = space;
        collectedReferences = new HashMap<String,ReferenceList>();
        //ids = new HashSet<String>();
        importedClasses = new HashSet<JClass>();
    }

    /**
     * Creates a formatter with default incremental indentations of
     * four spaces.
     */
    public JFormatter(PrintWriter s) {
        this(s, "    ");
    }

    /**
     * Creates a formatter with default incremental indentations of
     * four spaces.
     */
    public JFormatter(Writer w) {
        this(new PrintWriter(w));
    }

    /**
     * Closes this formatter.
     */
    public void close() {
        pw.close();
    }

    /**
     * Returns true if we are in the printing mode,
     * where we actually produce text.
     *
     * The other mode is the "collecting mode'
     */
    public boolean isPrinting() {
        return mode == Mode.PRINTING;
    }

    /**
     * Decrement the indentation level.
     */
    public JFormatter o() {
        indentLevel--;
        return this;
    }

    /**
     * Increment the indentation level.
     */
    public JFormatter i() {
        indentLevel++;
        return this;
    }

    private boolean needSpace(char c1, char c2) {
        if ((c1 == ']') && (c2 == '{')) return true;
        if (c1 == ';') return true;
        if (c1 == CLOSE_TYPE_ARGS) {
            // e.g., "public Foo<Bar> test;"
            if(c2=='(') // but not "new Foo<Bar>()"
                return false;
            return true;
        }
        if ((c1 == ')') && (c2 == '{')) return true;
        if ((c1 == ',') || (c1 == '=')) return true;
        if (c2 == '=') return true;
        if (Character.isDigit(c1)) {
            if ((c2 == '(') || (c2 == ')') || (c2 == ';') || (c2 == ','))
                return false;
            return true;
        }
        if (Character.isJavaIdentifierPart(c1)) {
            switch (c2) {
            case '{':
            case '}':
            case '+':
            case '>':
            case '@':
                return true;
            default:
                return Character.isJavaIdentifierStart(c2);
            }
        }
        if (Character.isJavaIdentifierStart(c2)) {
            switch (c1) {
            case ']':
            case ')':
            case '}':
            case '+':
                return true;
            default:
                return false;
            }
        }
        if (Character.isDigit(c2)) {
            if (c1 == '(') return false;
            return true;
        }
        return false;
    }

    private char lastChar = 0;
    private boolean atBeginningOfLine = true;

    private void spaceIfNeeded(char c) {
        if (atBeginningOfLine) {
            for (int i = 0; i < indentLevel; i++)
                pw.print(indentSpace);
            atBeginningOfLine = false;
        } else if ((lastChar != 0) && needSpace(lastChar, c))
            pw.print(' ');
    }

    /**
     * Print a char into the stream
     *
     * @param c the char
     */
    public JFormatter p(char c) {
        if(mode==Mode.PRINTING) {
            if(c==CLOSE_TYPE_ARGS) {
                pw.print('>');
            } else {
                spaceIfNeeded(c);
                pw.print(c);
            }
            lastChar = c;
        }
        return this;
    }

    /**
     * Print a String into the stream
     *
     * @param s the String
     */
    public JFormatter p(String s) {
        if(mode==Mode.PRINTING) {
            spaceIfNeeded(s.charAt(0));
            pw.print(s);
            lastChar = s.charAt(s.length() - 1);
        }
        return this;
    }

    public JFormatter t(JType type) {
        if(type.isReference()) {
            return t((JClass)type);
        } else {
            return g(type);
        }
    }

    /**
     * Print a type name.
     *
     * <p>
     * In the collecting mode we use this information to
     * decide what types to import and what not to.
     */
    public JFormatter t(JClass type) {
        switch(mode) {
        case PRINTING:
            // many of the JTypes in this list are either primitive or belong to package java
            // so we don't need a FQCN
            if(importedClasses.contains(type)) {
                p(type.name()); // FQCN imported or not necessary, so generate short name
            } else {
                if(type.outer()!=null)
                    t(type.outer()).p('.').p(type.name());
                else
                    p(type.fullName()); // collision was detected, so generate FQCN
            }
            break;
        case COLLECTING:
            final String shortName = type.name();
            if(collectedReferences.containsKey(shortName)) {
                collectedReferences.get(shortName).add(type);
            } else {
                ReferenceList tl = new ReferenceList();
                tl.add(type);
                collectedReferences.put(shortName, tl);
            }
            break;
        }
        return this;
    }

    /**
     * Print an identifier
     */
    public JFormatter id(String id) {
        switch(mode) {
        case PRINTING:
            p(id);
            break;
        case COLLECTING:
            // see if there is a type name that collides with this id
            if(collectedReferences.containsKey(id)) {
                if( !collectedReferences.get(id).getClasses().isEmpty() ) {
                    for( JClass type : collectedReferences.get(id).getClasses() ) {
                        if (type.outer()!=null) {
                            collectedReferences.get(id).setId(false);
                            return this;
                        }
                    }
                }
                collectedReferences.get(id).setId(true);
            } else {
                // not a type, but we need to create a place holder to
                // see if there might be a collision with a type
                ReferenceList tl = new ReferenceList();
                tl.setId(true);
                collectedReferences.put(id, tl);
            }
            break;
        }
        return this;
    }

    /**
     * Print a new line into the stream
     */
    public JFormatter nl() {
        if(mode==Mode.PRINTING) {
            pw.println();
            lastChar = 0;
            atBeginningOfLine = true;
        }
        return this;
    }

    /**
     * Cause the JGenerable object to generate source for iteself
     *
     * @param g the JGenerable object
     */
    public JFormatter g(JGenerable g) {
        g.generate(this);
        return this;
    }

    /**
     * Produces {@link JGenerable}s separated by ','
     */
    public JFormatter g(Collection<? extends JGenerable> list) {
        boolean first = true;
        if(!list.isEmpty()) {
            for (JGenerable item : list) {
                if (!first)
                    p(',');
                g(item);
                first = false;
            }
        }
        return this;
    }

    /**
     * Cause the JDeclaration to generate source for itself
     *
     * @param d the JDeclaration object
     */
    public JFormatter d(JDeclaration d) {
        d.declare(this);
        return this;
    }

    /**
     * Cause the JStatement to generate source for itself
     *
     * @param s the JStatement object
     */
    public JFormatter s(JStatement s) {
        s.state(this);
        return this;
    }

    /**
     * Cause the JVar to generate source for itself
     *
     * @param v the JVar object
     */
    public JFormatter b(JVar v) {
        v.bind(this);
        return this;
    }

    /**
     * Generates the whole source code out of the specified class.
     */
    void write(JDefinedClass c) {
        // first collect all the types and identifiers
        mode = Mode.COLLECTING;
        d(c);

        javaLang = c.owner()._package("java.lang");

        // collate type names and identifiers to determine which types can be imported
        for( ReferenceList tl : collectedReferences.values() ) {
            if(!tl.collisions(c) && !tl.isId()) {
                assert tl.getClasses().size() == 1;

                // add to list of collected types
                importedClasses.add(tl.getClasses().get(0));
            }
        }

        // the class itself that we will be generating is always accessible
        importedClasses.add(c);

        // then print the declaration
        mode = Mode.PRINTING;

        assert c.parentContainer().isPackage() : "this method is only for a pacakge-level class";
        JPackage pkg = (JPackage) c.parentContainer();
        if (!pkg.isUnnamed()) {
            nl().d(pkg);
            nl();
        }

        // generate import statements
        JClass[] imports = importedClasses.toArray(new JClass[importedClasses.size()]);
        Arrays.sort(imports);
        for (JClass clazz : imports) {
            // suppress import statements for primitive types, built-in types,
            // types in the root package, and types in
            // the same package as the current type
            if(!supressImport(clazz, c)) {
                if (clazz instanceof JNarrowedClass) {
                    clazz = clazz.erasure();
                }

                p("import").p(clazz.fullName()).p(';').nl();
            }
        }
        nl();

        d(c);
    }

    /**
     * determine if an import statement should be supressed
     *
     * @param clazz JType that may or may not have an import
     * @param c JType that is the current class being processed
     * @return true if an import statement should be suppressed, false otherwise
     */
    private boolean supressImport(JClass clazz, JClass c) {
        if (clazz instanceof JNarrowedClass) {
            clazz = clazz.erasure();
        }
        if (clazz instanceof JAnonymousClass) {
            clazz = clazz._extends();
        }

        if(clazz._package().isUnnamed())
            return true;

        final String packageName = clazz._package().name();
        if(packageName.equals("java.lang"))
            return true;    // no need to explicitly import java.lang classes

        if (clazz._package() == c._package()){
            // inner classes require an import stmt.
            // All other pkg local classes do not need an
            // import stmt for ref.
            if(clazz.outer()==null) {
                return true;    // no need to explicitly import a class into itself
            }
        }
        return false;
    }

    private JPackage javaLang;



    /**
     * Special character token we use to differenciate '>' as an operator and
     * '>' as the end of the type arguments. The former uses '>' and it requires
     * a preceding whitespace. The latter uses this, and it does not have a preceding
     * whitespace.
     */
    /*package*/ static final char CLOSE_TYPE_ARGS = '\uFFFF';

    /**
     * Used during the optimization of class imports.
     *
     * List of {@link JClass}es whose short name is the same.
     *
     * @author Ryan.Shoemaker@Sun.COM
     */
    final class ReferenceList {
        private final ArrayList<JClass> classes = new ArrayList<JClass>();

        /** true if this name is used as an identifier (like a variable name.) **/
        private boolean id;

        /**
         * Returns true if the symbol represented by the short name
         * is "importable".
         */
        public boolean collisions(JDefinedClass enclosingClass) {
            // special case where a generated type collides with a type in package java

            // more than one type with the same name
            if(classes.size() > 1)
                return true;

            // an id and (at least one) type with the same name
            if(id && classes.size() != 0)
                return true;

            for(JClass c : classes) {
                if (c instanceof JAnonymousClass) {
                    c = c._extends();
                }
                if(c._package()==javaLang) {
                    // make sure that there's no other class with this name within the same package
                    Iterator<JDefinedClass> itr = enclosingClass._package().classes();
                    while(itr.hasNext()) {
                        // even if this is the only "String" class we use,
                        // if the class called "String" is in the same package,
                        // we still need to import it.
                        JDefinedClass n = itr.next();
                        if(n.name().equals(c.name()))
                            return true;    //collision
                    }
                }
                if(c.outer()!=null)
                    return true; // avoid importing inner class to work around 6431987. Also see jaxb issue 166
            }

            return false;
        }

        public void add(JClass clazz) {
            if(!classes.contains(clazz))
                classes.add(clazz);
        }

        public List<JClass> getClasses() {
            return classes;
        }

        public void setId(boolean value) {
            id = value;
        }

        /**
         * Return true iff this is strictly an id, meaning that there
         * are no collisions with type names.
         */
        public boolean isId() {
            return id && classes.size() == 0;
        }
    }
}
