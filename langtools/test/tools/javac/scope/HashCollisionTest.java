/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Ensure Scope impl can cope with hash collisions
 */

import java.lang.reflect.*;
import java.io.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.file.JavacFileManager;
import static com.sun.tools.javac.code.Kinds.*;

public class HashCollisionTest {
    public static void main(String... args) throws Exception {
        new HashCollisionTest().run();
    }

    void run() throws Exception {
        // set up basic environment for test
        Context context = new Context();
        JavacFileManager.preRegister(context); // required by ClassReader which is required by Symtab
        names = Names.instance(context);       // Name.Table impls tied to an instance of Names
        symtab = Symtab.instance(context);
        scopeCounter = ScopeCounter.instance(context);

        // determine hashMask for an empty scope
        Scope emptyScope = new Scope(symtab.unnamedPackage); // any owner will do
        Field sHashMask = Scope.class.getDeclaredField("hashMask");
        sHashMask.setAccessible(true);
        scopeHashMask = sHashMask.getInt(emptyScope);
        log("scopeHashMask: " + scopeHashMask);

        // 1. determine the Name.hashCode of "Entry", and therefore the index of
        // Entry in an empty scope.  i.e. name.hashCode() & Scope.hashMask
        Name entry = names.fromString("Entry");

        // 2. create names of the form *$Entry until we find a name with a
        // hashcode which yields the same index as Entry in an empty scope.
        // Since Name.hashCode is a function of position (and not content) it
        // should work to create successively longer names until one with the
        // desired characteristics is found.
        Name outerName;
        Name innerName;
        StringBuilder sb = new StringBuilder("C");
        int i = 0;
        do {
            sb.append(Integer.toString(i % 10));
            innerName = names.fromString(sb + "$Entry");
        } while (!clash(entry, innerName) && (++i) < MAX_TRIES);

        if (clash(entry, innerName)) {
            log("Detected expected hash collision for " + entry + " and " + innerName
                    + " after " + i + " tries");
        } else {
            throw new Exception("No potential collision found after " + i + " tries");
        }

        outerName = names.fromString(sb.toString());

        /*
         * Now we can set up the scenario.
         */

        // 3. Create a nested class named Entry
        ClassSymbol cc = createClass(names.fromString("C"), symtab.unnamedPackage);
        ClassSymbol ce = createClass(entry, cc);

        // 4. Create a package containing a nested class using the name from 2
        PackageSymbol p = new PackageSymbol(names.fromString("p"), symtab.rootPackage);
        p.members_field = new Scope(p);
        ClassSymbol inner = createClass(innerName, p);
        // we'll need this later when we "rename" cn
        ClassSymbol outer = createClass(outerName, p);

        // 5. Create a star-import scope
        log ("createStarImportScope");

        // if StarImportScope exists, use it, otherwise, for testing legacy code,
        // fall back on ImportScope
        Scope starImportScope;
        Method importAll;
        PackageSymbol pkg = new PackageSymbol(names.fromString("pkg"), symtab.rootPackage);
        try {
            Class<?> c = Class.forName("com.sun.tools.javac.code.Scope$StarImportScope");
            Constructor ctor = c.getDeclaredConstructor(new Class[] { Symbol.class });
            importAll = c.getDeclaredMethod("importAll", new Class[] { Scope.class });
            starImportScope = (Scope) ctor.newInstance(new Object[] { pkg });
        } catch (ClassNotFoundException e) {
            starImportScope = new ImportScope(pkg);
            importAll = null;
        }

        dump("initial", starImportScope);

        // 6. Insert the contents of the package from 4.
        Scope p_members = p.members();
        if (importAll != null) {
            importAll.invoke(starImportScope, p_members);
        } else {
            Scope fromScope = p_members;
            Scope toScope = starImportScope;
            // The following lines are taken from MemberEnter.importAll,
            // before the use of StarImportScope.importAll.
            for (Scope.Entry e = fromScope.elems; e != null; e = e.sibling) {
                if (e.sym.kind == TYP && !toScope.includes(e.sym))
                    toScope.enter(e.sym, fromScope);
            }
        }

        dump("imported p", starImportScope);

        // 7. Insert the class from 3.
        starImportScope.enter(ce, cc.members_field);
        dump("imported ce", starImportScope);

        /*
         * Set the trap.
         */

        // 8. Rename the nested class to Entry. so that there is a bogus entry in the star-import scope
        p.members_field.remove(inner);
        inner.name = entry;
        inner.owner = outer;
        outer.members_field.enter(inner);

        // 9. Lookup Entry
        Scope.Entry e = starImportScope.lookup(entry);
        dump("final", starImportScope);

        if (e.sym == null)
            throw new Exception("symbol not found: " + entry);
    }

    /*
     * Check for a (probable) hash collision in an empty scope.
     */
    boolean clash(Name n1, Name n2) {
        log(n1 + " hc:" + n1.hashCode() + " v:" + (n1.hashCode() & scopeHashMask) + ", " +
                n2 + " hc:" + n2.hashCode() + " v:" + (n2.hashCode() & scopeHashMask));
        return (n1.hashCode() & scopeHashMask) == (n2.hashCode() & scopeHashMask);
    }

    /**
     * Create a class symbol, init the members scope, and add it to owner's scope.
     */
    ClassSymbol createClass(Name name, Symbol owner) {
        ClassSymbol sym = new ClassSymbol(0, name, owner);
        sym.members_field = new ClassScope(sym, scopeCounter);
        if (owner != symtab.unnamedPackage)
            owner.members().enter(sym);
        return sym;
    }

    /**
     * Dump the contents of a scope to System.err.
     */
    void dump(String label, Scope s) throws Exception {
        dump(label, s, System.err);
    }

    /**
     * Dump the contents of a scope to a stream.
     */
    void dump(String label, Scope s, PrintStream out) throws Exception {
        out.println(label);
        Field sTable = Scope.class.getDeclaredField("table");
        sTable.setAccessible(true);

        out.println("owner:" + s.owner);
        Scope.Entry[] table = (Scope.Entry[]) sTable.get(s);
        for (int i = 0; i < table.length; i++) {
            if (i > 0)
                out.print(", ");
            out.print(i + ":" + toString(table[i], table, false));
        }
        out.println();
    }

    /**
     * Create a string showing the contents of an entry, using the table
     * to help identify cross-references to other entries in the table.
     * @param e the entry to be shown
     * @param table the table containing the other entries
     */
    String toString(Scope.Entry e, Scope.Entry[] table, boolean ref) {
        if (e == null)
            return "null";
        if (e.sym == null)
            return "sent"; // sentinel
        if (ref) {
            int index = indexOf(table, e);
            if (index != -1)
                return String.valueOf(index);
        }
        return "(" + e.sym.name + ":" + e.sym
                + ",shdw:" + toString(e.next(), table, true)
                + ",sibl:" + toString(e.sibling, table, true)
                + ((e.sym.owner != e.scope.owner)
                    ? (",BOGUS[" + e.sym.owner + "," + e.scope.owner + "]")
                    : "")
                + ")";
    }

    <T> int indexOf(T[] array, T item) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == item)
                return i;
        }
        return -1;
    }

    /**
     * Write a message to stderr.
     */
    void log(String msg) {
        System.err.println(msg);
    }

    int MAX_TRIES = 100; // max tries to find a hash clash before giving up.
    int scopeHashMask;

    Names names;
    Symtab symtab;
    ScopeCounter scopeCounter;
}
