/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import com.sun.tools.javac.util.*;
import java.util.Iterator;

/** A scope represents an area of visibility in a Java program. The
 *  Scope class is a container for symbols which provides
 *  efficient access to symbols given their names. Scopes are implemented
 *  as hash tables. Scopes can be nested; the next field of a scope points
 *  to its next outer scope. Nested scopes can share their hash tables.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Scope {

    /** The number of scopes that share this scope's hash table.
     */
    private int shared;

    /** Next enclosing scope (with whom this scope may share a hashtable)
     */
    public Scope next;

    /** The scope's owner.
     */
    public Symbol owner;

    /** A hash table for the scope's entries.
     */
    public Entry[] table;

    /** Mask for hash codes, always equal to (table.length - 1).
     */
    int hashMask;

    /** A linear list that also contains all entries in
     *  reverse order of appearance (i.e later entries are pushed on top).
     */
    public Entry elems;

    /** The number of elements in this scope.
     */
    public int nelems = 0;

    /** Every hash bucket is a list of Entry's which ends in sentinel.
     */
    private static final Entry sentinel = new Entry(null, null, null, null);

    /** The hash table's initial size.
     */
    private static final int INITIAL_SIZE = 0x10;

    /** A value for the empty scope.
     */
    public static final Scope emptyScope = new Scope(null, null, new Entry[]{});

    /** Construct a new scope, within scope next, with given owner, using
     *  given table. The table's length must be an exponent of 2.
     */
    Scope(Scope next, Symbol owner, Entry[] table) {
        this.next = next;
        assert emptyScope == null || owner != null;
        this.owner = owner;
        this.table = table;
        this.hashMask = table.length - 1;
        this.elems = null;
        this.nelems = 0;
        this.shared = 0;
    }

    /** Construct a new scope, within scope next, with given owner,
     *  using a fresh table of length INITIAL_SIZE.
     */
    public Scope(Symbol owner) {
        this(null, owner, new Entry[INITIAL_SIZE]);
        for (int i = 0; i < INITIAL_SIZE; i++) table[i] = sentinel;
    }

    /** Construct a fresh scope within this scope, with same owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    public Scope dup() {
        Scope result = new Scope(this, this.owner, this.table);
        shared++;
        // System.out.println("====> duping scope " + this.hashCode() + " owned by " + this.owner + " to " + result.hashCode());
        // new Error().printStackTrace(System.out);
        return result;
    }

    /** Construct a fresh scope within this scope, with new owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    public Scope dup(Symbol newOwner) {
        Scope result = new Scope(this, newOwner, this.table);
        shared++;
        // System.out.println("====> duping scope " + this.hashCode() + " owned by " + newOwner + " to " + result.hashCode());
        // new Error().printStackTrace(System.out);
        return result;
    }

    /** Construct a fresh scope within this scope, with same owner,
     *  with a new hash table, whose contents initially are those of
     *  the table of its outer scope.
     */
    public Scope dupUnshared() {
        return new Scope(this, this.owner, this.table.clone());
    }

    /** Remove all entries of this scope from its table, if shared
     *  with next.
     */
    public Scope leave() {
        assert shared == 0;
        if (table != next.table) return next;
        while (elems != null) {
            int hash = elems.sym.name.hashCode() & hashMask;
            Entry e = table[hash];
            assert e == elems : elems.sym;
            table[hash] = elems.shadowed;
            elems = elems.sibling;
        }
        assert next.shared > 0;
        next.shared--;
        // System.out.println("====> leaving scope " + this.hashCode() + " owned by " + this.owner + " to " + next.hashCode());
        // new Error().printStackTrace(System.out);
        return next;
    }

    /** Double size of hash table.
     */
    private void dble() {
        assert shared == 0;
        Entry[] oldtable = table;
        Entry[] newtable = new Entry[oldtable.length * 2];
        for (Scope s = this; s != null; s = s.next) {
            if (s.table == oldtable) {
                assert s == this || s.shared != 0;
                s.table = newtable;
                s.hashMask = newtable.length - 1;
            }
        }
        for (int i = 0; i < newtable.length; i++) newtable[i] = sentinel;
        for (int i = 0; i < oldtable.length; i++) copy(oldtable[i]);
    }

    /** Copy the given entry and all entries shadowed by it to table
     */
    private void copy(Entry e) {
        if (e.sym != null) {
            copy(e.shadowed);
            int hash = e.sym.name.hashCode() & hashMask;
            e.shadowed = table[hash];
            table[hash] = e;
        }
    }

    /** Enter symbol sym in this scope.
     */
    public void enter(Symbol sym) {
        assert shared == 0;
        enter(sym, this);
    }

    public void enter(Symbol sym, Scope s) {
        enter(sym, s, s);
    }

    /**
     * Enter symbol sym in this scope, but mark that it comes from
     * given scope `s' accessed through `origin'.  The last two
     * arguments are only used in import scopes.
     */
    public void enter(Symbol sym, Scope s, Scope origin) {
        assert shared == 0;
        // Temporarily disabled (bug 6460352):
        // if (nelems * 3 >= hashMask * 2) dble();
        int hash = sym.name.hashCode() & hashMask;
        Entry e = makeEntry(sym, table[hash], elems, s, origin);
        table[hash] = e;
        elems = e;
        nelems++;
    }

    Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
        return new Entry(sym, shadowed, sibling, scope);
    }

    /** Remove symbol from this scope.  Used when an inner class
     *  attribute tells us that the class isn't a package member.
     */
    public void remove(Symbol sym) {
        assert shared == 0;
        Entry e = lookup(sym.name);
        while (e.scope == this && e.sym != sym) e = e.next();
        if (e.scope == null) return;

        // remove e from table and shadowed list;
        Entry te = table[sym.name.hashCode() & hashMask];
        if (te == e)
            table[sym.name.hashCode() & hashMask] = e.shadowed;
        else while (true) {
            if (te.shadowed == e) {
                te.shadowed = e.shadowed;
                break;
            }
            te = te.shadowed;
        }

        // remove e from elems and sibling list
        te = elems;
        if (te == e)
            elems = e.sibling;
        else while (true) {
            if (te.sibling == e) {
                te.sibling = e.sibling;
                break;
            }
            te = te.sibling;
        }
    }

    /** Enter symbol sym in this scope if not already there.
     */
    public void enterIfAbsent(Symbol sym) {
        assert shared == 0;
        Entry e = lookup(sym.name);
        while (e.scope == this && e.sym.kind != sym.kind) e = e.next();
        if (e.scope != this) enter(sym);
    }

    /** Given a class, is there already a class with same fully
     *  qualified name in this (import) scope?
     */
    public boolean includes(Symbol c) {
        for (Scope.Entry e = lookup(c.name);
             e.scope == this;
             e = e.next()) {
            if (e.sym == c) return true;
        }
        return false;
    }

    /** Return the entry associated with given name, starting in
     *  this scope and proceeding outwards. If no entry was found,
     *  return the sentinel, which is characterized by having a null in
     *  both its scope and sym fields, whereas both fields are non-null
     *  for regular entries.
     */
    public Entry lookup(Name name) {
        Entry e = table[name.hashCode() & hashMask];
        while (e.scope != null && e.sym.name != name)
            e = e.shadowed;
        return e;
    }

    public Iterable<Symbol> getElements() {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                return new Iterator<Symbol>() {
                    private Scope currScope = Scope.this;
                    private Scope.Entry currEntry = elems;
                    {
                        update();
                    }

                    public boolean hasNext() {
                        return currEntry != null;
                    }

                    public Symbol next() {
                        Symbol sym = (currEntry == null ? null : currEntry.sym);
                        currEntry = currEntry.sibling;
                        update();
                        return sym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void update() {
                        while (currEntry == null && currScope.next != null) {
                            currScope = currScope.next;
                            currEntry = currScope.elems;
                        }
                    }
                };
            }
        };

    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Scope[");
        for (Scope s = this; s != null ; s = s.next) {
            if (s != this) result.append(" | ");
            for (Entry e = s.elems; e != null; e = e.sibling) {
                if (e != s.elems) result.append(", ");
                result.append(e.sym);
            }
        }
        result.append("]");
        return result.toString();
    }

    /** A class for scope entries.
     */
    public static class Entry {

        /** The referenced symbol.
         *  sym == null   iff   this == sentinel
         */
        public Symbol sym;

        /** An entry with the same hash code, or sentinel.
         */
        private Entry shadowed;

        /** Next entry in same scope.
         */
        public Entry sibling;

        /** The entry's scope.
         *  scope == null   iff   this == sentinel
         *  for an entry in an import scope, this is the scope
         *  where the entry came from (i.e. was imported from).
         */
        public Scope scope;

        public Entry(Symbol sym, Entry shadowed, Entry sibling, Scope scope) {
            this.sym = sym;
            this.shadowed = shadowed;
            this.sibling = sibling;
            this.scope = scope;
        }

        /** Return next entry with the same name as this entry, proceeding
         *  outwards if not found in this scope.
         */
        public Entry next() {
            Entry e = shadowed;
            while (e.scope != null && e.sym.name != sym.name)
                e = e.shadowed;
            return e;
        }

        public Scope getOrigin() {
            // The origin is only recorded for import scopes.  For all
            // other scope entries, the "enclosing" type is available
            // from other sources.  See Attr.visitSelect and
            // Attr.visitIdent.  Rather than throwing an assertion
            // error, we return scope which will be the same as origin
            // in many cases.
            return scope;
        }
    }

    public static class ImportScope extends Scope {

        public ImportScope(Symbol owner) {
            super(owner);
        }

        @Override
        Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
            return new ImportEntry(sym, shadowed, sibling, scope, origin);
        }

        public Entry lookup(Name name) {
            Entry e = table[name.hashCode() & hashMask];
            while (e.scope != null &&
                   (e.sym.name != name ||
                    /* Since an inner class will show up in package and
                     * import scopes until its inner class attribute has
                     * been processed, we have to weed it out here.  This
                     * is done by comparing the owners of the entry's
                     * scope and symbol fields.  The scope field's owner
                     * points to where the class originally was imported
                     * from.  The symbol field's owner points to where the
                     * class is situated now.  This can change when an
                     * inner class is read (see ClassReader.enterClass).
                     * By comparing the two fields we make sure that we do
                     * not accidentally import an inner class that started
                     * life as a flat class in a package. */
                    e.sym.owner != e.scope.owner))
                e = e.shadowed;
            return e;
        }

        static class ImportEntry extends Entry {
            private Scope origin;

            ImportEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
                super(sym, shadowed, sibling, scope);
                this.origin = origin;
            }
            public Entry next() {
                Entry e = super.shadowed;
                while (e.scope != null &&
                       (e.sym.name != sym.name ||
                        e.sym.owner != e.scope.owner)) // see lookup()
                    e = e.shadowed;
                return e;
            }

            @Override
            public Scope getOrigin() { return origin; }
        }
    }

    /** An empty scope, into which you can't place anything.  Used for
     *  the scope for a variable initializer.
     */
    public static class DelegatedScope extends Scope {
        Scope delegatee;
        public static final Entry[] emptyTable = new Entry[0];

        public DelegatedScope(Scope outer) {
            super(outer, outer.owner, emptyTable);
            delegatee = outer;
        }
        public Scope dup() {
            return new DelegatedScope(next);
        }
        public Scope dupUnshared() {
            return new DelegatedScope(next);
        }
        public Scope leave() {
            return next;
        }
        public void enter(Symbol sym) {
            // only anonymous classes could be put here
        }
        public void enter(Symbol sym, Scope s) {
            // only anonymous classes could be put here
        }
        public void remove(Symbol sym) {
            throw new AssertionError(sym);
        }
        public Entry lookup(Name name) {
            return delegatee.lookup(name);
        }
    }

    /** An error scope, for which the owner should be an error symbol. */
    public static class ErrorScope extends Scope {
        ErrorScope(Scope next, Symbol errSymbol, Entry[] table) {
            super(next, /*owner=*/errSymbol, table);
        }
        public ErrorScope(Symbol errSymbol) {
            super(errSymbol);
        }
        public Scope dup() {
            return new ErrorScope(this, owner, table);
        }
        public Scope dupUnshared() {
            return new ErrorScope(this, owner, table.clone());
        }
        public Entry lookup(Name name) {
            Entry e = super.lookup(name);
            if (e.scope == null)
                return new Entry(owner, null, null, null);
            else
                return e;
        }
    }
}
