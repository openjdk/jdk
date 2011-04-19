/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 7004029
 * @summary Basher for star-import scopes
 */

import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.file.JavacFileManager;
import static com.sun.tools.javac.code.Kinds.*;

public class StarImportTest {
    public static void main(String... args) throws Exception {
        new StarImportTest().run(args);
    }

    void run(String... args) throws Exception {
        int count = 1;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-seed") && (i + 1 < args.length))
                seed = Long.parseLong(args[++i]);
            else if(arg.equals("-tests") && (i + 1 < args.length))
                count = Integer.parseInt(args[++i]);
            else
                throw new Exception("unknown arg: " + arg);
        }

        rgen = new Random(seed);

        for (int i = 0; i < count; i++) {
            Test t = new Test();
            t.run();
        }

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    /**
     * Select a random element from an array of choices.
     */
    <T> T random(T... choices) {
        return choices[rgen.nextInt(choices.length)];
    }

    /**
     * Write a message to stderr.
     */
    void log(String msg) {
        System.err.println(msg);
    }

    /**
     * Write a message to stderr, and dump a scope.
     */
    void log(String msg, Scope s) {
        System.err.print(msg);
        System.err.print(": ");
        String sep = "(";
        for (Scope.Entry se = s.elems; se != null; se = se.sibling) {
            for (Scope.Entry e = se; e.sym != null; e = e.next()) {
                System.err.print(sep + e.sym.name + ":" + e.sym);
                sep = ",";
            }
            System.err.print(")");
            sep = ", (";
        }
        System.err.println();
    }

    /**
     * Write an error message to stderr.
     */
    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    Random rgen;
    long seed = 0;

    int errors;

    enum SetupKind { NAMES, PACKAGE, CLASS };
    static final int MAX_SETUP_COUNT = 50;
    static final int MAX_SETUP_NAME_COUNT = 20;
    static final int MAX_SETUP_PACKAGE_COUNT = 20;
    static final int MAX_SETUP_CLASS_COUNT = 20;

    /** Class to encapsulate a test run. */
    class Test {
        /** Run the test. */
        void run() throws Exception {
            log ("starting test");
            setup();
            createStarImportScope();
            test();
        }

        /**
         * Setup env by creating pseudo-random collection of names, packages and classes.
         */
        void setup() {
            log ("setup");
            context = new Context();
            JavacFileManager.preRegister(context); // required by ClassReader which is required by Symtab
            names = Names.instance(context);       // Name.Table impls tied to an instance of Names
            symtab = Symtab.instance(context);
            int setupCount = rgen.nextInt(MAX_SETUP_COUNT);
            for (int i = 0; i < setupCount; i++) {
                switch (random(SetupKind.values())) {
                    case NAMES:
                        setupNames();
                        break;
                    case PACKAGE:
                        setupPackage();
                        break;
                    case CLASS:
                        setupClass();
                        break;
                }
            }
        }

        /**
         * Set up a random number of names.
         */
        void setupNames() {
            int count = rgen.nextInt(MAX_SETUP_NAME_COUNT);
            log("setup: creating " + count + " new names");
            for (int i = 0; i < count; i++) {
                names.fromString("n" + (++nextNameSerial));
            }
        }

        /**
         * Set up a package containing a random number of member elements.
         */
        void setupPackage() {
            Name name = names.fromString("p" + (++nextPackageSerial));
            int count = rgen.nextInt(MAX_SETUP_PACKAGE_COUNT);
            log("setup: creating package " + name + " with " + count + " entries");
            PackageSymbol p = new PackageSymbol(name, symtab.rootPackage);
            p.members_field = new Scope(p);
            for (int i = 0; i < count; i++) {
                String outer = name + "c" + i;
                String suffix = random(null, "$Entry", "$Entry2");
                ClassSymbol c1 = createClass(names.fromString(outer), p);
//                log("setup: created " + c1);
                if (suffix != null) {
                    ClassSymbol c2 = createClass(names.fromString(outer + suffix), p);
//                    log("setup: created " + c2);
                }
            }
//            log("package " + p, p.members_field);
            packages.add(p);
            imports.add(p);
        }

        /**
         * Set up a class containing a random number of member elements.
         */
        void setupClass() {
            Name name = names.fromString("c" + (++nextClassSerial));
            int count = rgen.nextInt(MAX_SETUP_CLASS_COUNT);
            log("setup: creating class " + name + " with " + count + " entries");
            ClassSymbol c = createClass(name, symtab.unnamedPackage);
//            log("setup: created " + c);
            for (int i = 0; i < count; i++) {
                ClassSymbol ic = createClass(names.fromString("Entry" + i), c);
//                log("setup: created " + ic);
            }
            classes.add(c);
            imports.add(c);
        }

        /**
         * Create a star-import scope and a model therof, from the packages and
         * classes created by setupPackages and setupClasses.
         * @throws Exception for fatal errors, such as from reflection
         */
        void createStarImportScope() throws Exception {
            log ("createStarImportScope");
            PackageSymbol pkg = new PackageSymbol(names.fromString("pkg"), symtab.rootPackage);

            // if StarImportScope exists, use it, otherwise, for testing legacy code,
            // fall back on ImportScope
            Method importAll;
            try {
                Class<?> c = Class.forName("com.sun.tools.javac.code.Scope$StarImportScope");
                Constructor ctor = c.getDeclaredConstructor(new Class[] { Symbol.class });
                importAll = c.getDeclaredMethod("importAll", new Class[] { Scope.class });
                starImportScope = (Scope) ctor.newInstance(new Object[] { pkg });
            } catch (ClassNotFoundException e) {
                starImportScope = new ImportScope(pkg);
                importAll = null;
            }
            starImportModel = new Model();

            for (Symbol imp: imports) {
                Scope members = imp.members();
                if (importAll != null) {
//                    log("importAll", members);
                    importAll.invoke(starImportScope, members);
                } else {
                    Scope fromScope = members;
                    Scope toScope = starImportScope;
                    // The following lines are taken from MemberEnter.importAll,
                    // before the use of StarImportScope.importAll.
                    for (Scope.Entry e = fromScope.elems; e != null; e = e.sibling) {
                        if (e.sym.kind == TYP && !toScope.includes(e.sym))
                            toScope.enter(e.sym, fromScope);
                    }
                }

                for (Scope.Entry e = members.elems; e != null; e = e.sibling) {
                    starImportModel.enter(e.sym);
                }
            }

//            log("star-import scope", starImportScope);
            starImportModel.check(starImportScope);
        }

        /**
         * The core of the test. In a random order, move nested classes from
         * the package in which they created to the class which should own them.
         */
        void test() {
            log ("test");
            List<ClassSymbol> nestedClasses = new LinkedList<ClassSymbol>();
            for (PackageSymbol p: packages) {
                for (Scope.Entry se = p.members_field.elems; se != null; se = se.sibling) {
                    if (se.sym.name.toString().contains("$"))
                        nestedClasses.add((ClassSymbol) se.sym);
                }
            }

            for (int i = nestedClasses.size(); i > 0; i--) {
                // select a random nested class to move from package to class
                ClassSymbol sym = nestedClasses.remove(rgen.nextInt(i));
                log("adjusting class " + sym);

                // remove from star import model
                starImportModel.remove(sym);

                String s = sym.name.toString();
                int dollar = s.indexOf("$");

                // owner should be a package
                assert (sym.owner.kind == PCK);

                // determine new owner
                Name outerName = names.fromString(s.substring(0, dollar));
//                log(sym + " owner: " + sym.owner, sym.owner.members());
                Scope.Entry outerEntry = sym.owner.members().lookup(outerName);
                ClassSymbol outer = (ClassSymbol) outerEntry.sym;
//                log("outer: " + outerName + " " + outer);

                // remove from package
                sym.owner.members().remove(sym);

                // rename and insert into class
                sym.name = names.fromString(s.substring(dollar + 1));
                outer.members().enter(sym);
                sym.owner = outer;

                // verify
                starImportModel.check(starImportScope);
            }
        }

        ClassSymbol createClass(Name name, Symbol owner) {
            ClassSymbol sym = new ClassSymbol(0, name, owner);
            sym.members_field = new Scope(sym);
            if (owner != symtab.unnamedPackage)
                owner.members().enter(sym);
            return sym;
        }

        Context context;
        Symtab symtab;
        Names names;
        int nextNameSerial;
        List<PackageSymbol> packages = new ArrayList<PackageSymbol>();
        int nextPackageSerial;
        List<ClassSymbol> classes = new ArrayList<ClassSymbol>();
        List<Symbol> imports = new ArrayList<Symbol>();
        int nextClassSerial;

        Scope starImportScope;
        Model starImportModel;
    }

    class Model {
        private Map<Name, Set<Symbol>> map = new HashMap<Name, Set<Symbol>>();
        private Set<Symbol> bogus = new HashSet<Symbol>();

        void enter(Symbol sym) {
            Set<Symbol> syms = map.get(sym.name);
            if (syms == null)
                map.put(sym.name, syms = new LinkedHashSet<Symbol>());
            syms.add(sym);
        }

        void remove(Symbol sym) {
            Set<Symbol> syms = map.get(sym.name);
            if (syms == null)
                error("no entries for " + sym.name + " found in reference model");
            else {
                boolean ok = syms.remove(sym);
                if (ok) {
//                        log(sym.name + "(" + sym + ") removed from reference model");
                } else {
                    error(sym.name + " not found in reference model");
                }
                if (syms.isEmpty())
                    map.remove(sym.name);
            }
        }

        /**
         * Check the contents of a scope
         */
        void check(Scope scope) {
            // First, check all entries in scope are in map
            int bogusCount = 0;
            for (Scope.Entry se = scope.elems; se != null; se = se.sibling) {
                Symbol sym = se.sym;
                if (sym.owner != se.scope.owner) {
                    if (bogus.contains(sym)) {
                        bogusCount++;
                    } else {
                        log("Warning: " + sym.name + ":" + sym + " appears to be bogus");
                        bogus.add(sym);
                    }
                } else {
                    Set<Symbol> syms = map.get(sym.name);
                    if (syms == null) {
                        error("check: no entries found for " + sym.name + ":" + sym + " in reference map");
                    } else  if (!syms.contains(sym)) {
                        error("check: symbol " + sym.name + ":" + sym + " not found in reference map");
                    }
                }
            }
            if (bogusCount > 0) {
                log("Warning: " + bogusCount + " other bogus entries previously reported");
            }

            // Second, check all entries in map are in scope
            for (Map.Entry<Name,Set<Symbol>> me: map.entrySet()) {
                Name name = me.getKey();
                Scope.Entry se = scope.lookup(name);
                assert (se != null);
                if (se.sym == null) {
                    error("check: no entries found for " + name + " in scope");
                    continue;
                }
            nextSym:
                for (Symbol sym: me.getValue()) {
                    for (Scope.Entry e = se; e.sym != null; e = e.next()) {
                        if (sym == e.sym)
                            continue nextSym;
                    }
                    error("check: symbol " + sym + " not found in scope");
                }
            }
        }
    }
}
