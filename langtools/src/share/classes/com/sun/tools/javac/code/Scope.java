/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import com.sun.tools.javac.util.*;

/** A scope represents an area of visibility in a Java program. The
 *  Scope class is a container for symbols which provides
 *  efficient access to symbols given their names. Scopes are implemented
 *  as hash tables with "open addressing" and "double hashing".
 *  Scopes can be nested; the next field of a scope points
 *  to its next outer scope. Nested scopes can share their hash tables.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
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
    Entry[] table;

    /** Mask for hash codes, always equal to (table.length - 1).
     */
    int hashMask;

    /** A linear list that also contains all entries in
     *  reverse order of appearance (i.e later entries are pushed on top).
     */
    public Entry elems;

    /** The number of elements in this scope.
     * This includes deleted elements, whose value is the sentinel.
     */
    int nelems = 0;

    /** A list of scopes to be notified if items are to be removed from this scope.
     */
    List<ScopeListener> listeners = List.nil();

    /** Use as a "not-found" result for lookup.
     * Also used to mark deleted entries in the table.
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
    private Scope(Scope next, Symbol owner, Entry[] table) {
        this.next = next;
        Assert.check(emptyScope == null || owner != null);
        this.owner = owner;
        this.table = table;
        this.hashMask = table.length - 1;
    }

    /** Convenience constructor used for dup and dupUnshared. */
    private Scope(Scope next, Symbol owner, Entry[] table, int nelems) {
        this(next, owner, table);
        this.nelems = nelems;
    }

    /** Construct a new scope, within scope next, with given owner,
     *  using a fresh table of length INITIAL_SIZE.
     */
    public Scope(Symbol owner) {
        this(null, owner, new Entry[INITIAL_SIZE]);
    }

    /** Construct a fresh scope within this scope, with same owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    public Scope dup() {
        return dup(this.owner);
    }

    /** Construct a fresh scope within this scope, with new owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    public Scope dup(Symbol newOwner) {
        Scope result = new Scope(this, newOwner, this.table, this.nelems);
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
        return new Scope(this, this.owner, this.table.clone(), this.nelems);
    }

    /** Remove all entries of this scope from its table, if shared
     *  with next.
     */
    public Scope leave() {
        Assert.check(shared == 0);
        if (table != next.table) return next;
        while (elems != null) {
            int hash = getIndex(elems.sym.name);
            Entry e = table[hash];
            Assert.check(e == elems, elems.sym);
            table[hash] = elems.shadowed;
            elems = elems.sibling;
        }
        Assert.check(next.shared > 0);
        next.shared--;
        next.nelems = nelems;
        // System.out.println("====> leaving scope " + this.hashCode() + " owned by " + this.owner + " to " + next.hashCode());
        // new Error().printStackTrace(System.out);
        return next;
    }

    /** Double size of hash table.
     */
    private void dble() {
        Assert.check(shared == 0);
        Entry[] oldtable = table;
        Entry[] newtable = new Entry[oldtable.length * 2];
        for (Scope s = this; s != null; s = s.next) {
            if (s.table == oldtable) {
                Assert.check(s == this || s.shared != 0);
                s.table = newtable;
                s.hashMask = newtable.length - 1;
            }
        }
        int n = 0;
        for (int i = oldtable.length; --i >= 0; ) {
            Entry e = oldtable[i];
            if (e != null && e != sentinel) {
                table[getIndex(e.sym.name)] = e;
                n++;
            }
        }
        // We don't need to update nelems for shared inherited scopes,
        // since that gets handled by leave().
        nelems = n;
    }

    /** Enter symbol sym in this scope.
     */
    public void enter(Symbol sym) {
        Assert.check(shared == 0);
        enter(sym, this);
    }

    public void enter(Symbol sym, Scope s) {
        enter(sym, s, s, false);
    }

    /**
     * Enter symbol sym in this scope, but mark that it comes from
     * given scope `s' accessed through `origin'.  The last two
     * arguments are only used in import scopes.
     */
    public void enter(Symbol sym, Scope s, Scope origin, boolean staticallyImported) {
        Assert.check(shared == 0);
        if (nelems * 3 >= hashMask * 2)
            dble();
        int hash = getIndex(sym.name);
        Entry old = table[hash];
        if (old == null) {
            old = sentinel;
            nelems++;
        }
        Entry e = makeEntry(sym, old, elems, s, origin, staticallyImported);
        table[hash] = e;
        elems = e;

        //notify listeners
        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolAdded(sym, this);
        }
    }

    Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin, boolean staticallyImported) {
        return new Entry(sym, shadowed, sibling, scope);
    }


    public interface ScopeListener {
        public void symbolAdded(Symbol sym, Scope s);
        public void symbolRemoved(Symbol sym, Scope s);
    }

    public void addScopeListener(ScopeListener sl) {
        listeners = listeners.prepend(sl);
    }

    /** Remove symbol from this scope.  Used when an inner class
     *  attribute tells us that the class isn't a package member.
     */
    public void remove(Symbol sym) {
        Assert.check(shared == 0);
        Entry e = lookup(sym.name);
        if (e.scope == null) return;

        // remove e from table and shadowed list;
        int i = getIndex(sym.name);
        Entry te = table[i];
        if (te == e)
            table[i] = e.shadowed;
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

        //notify listeners
        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolRemoved(sym, this);
        }
    }

    /** Enter symbol sym in this scope if not already there.
     */
    public void enterIfAbsent(Symbol sym) {
        Assert.check(shared == 0);
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

    static final Filter<Symbol> noFilter = new Filter<Symbol>() {
        public boolean accepts(Symbol s) {
            return true;
        }
    };

    /** Return the entry associated with given name, starting in
     *  this scope and proceeding outwards. If no entry was found,
     *  return the sentinel, which is characterized by having a null in
     *  both its scope and sym fields, whereas both fields are non-null
     *  for regular entries.
     */
    public Entry lookup(Name name) {
        return lookup(name, noFilter);
    }

    public Entry lookup(Name name, Filter<Symbol> sf) {
        Entry e = table[getIndex(name)];
        if (e == null || e == sentinel)
            return sentinel;
        while (e.scope != null && (e.sym.name != name || !sf.accepts(e.sym)))
            e = e.shadowed;
        return e;
    }

    /*void dump (java.io.PrintStream out) {
        out.println(this);
        for (int l=0; l < table.length; l++) {
            Entry le = table[l];
            out.print("#"+l+": ");
            if (le==sentinel) out.println("sentinel");
            else if(le == null) out.println("null");
            else out.println(""+le+" s:"+le.sym);
        }
    }*/

    /** Look for slot in the table.
     *  We use open addressing with double hashing.
     */
    int getIndex (Name name) {
        int h = name.hashCode();
        int i = h & hashMask;
        // The expression below is always odd, so it is guaranteed
        // to be mutually prime with table.length, a power of 2.
        int x = hashMask - ((h + (h >> 16)) << 1);
        int d = -1; // Index of a deleted item.
        for (;;) {
            Entry e = table[i];
            if (e == null)
                return d >= 0 ? d : i;
            if (e == sentinel) {
                // We have to keep searching even if we see a deleted item.
                // However, remember the index in case we fail to find the name.
                if (d < 0)
                    d = i;
            } else if (e.sym.name == name)
                return i;
            i = (i + x) & hashMask;
        }
    }

    public boolean anyMatch(Filter<Symbol> sf) {
        return getElements(sf).iterator().hasNext();
    }

    public Iterable<Symbol> getElements() {
        return getElements(noFilter);
    }

    public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
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
                        if (currEntry != null) {
                            currEntry = currEntry.sibling;
                        }
                        update();
                        return sym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void update() {
                        skipToNextMatchingEntry();
                        while (currEntry == null && currScope.next != null) {
                            currScope = currScope.next;
                            currEntry = currScope.elems;
                            skipToNextMatchingEntry();
                        }
                    }

                    void skipToNextMatchingEntry() {
                        while (currEntry != null && !sf.accepts(currEntry.sym)) {
                            currEntry = currEntry.sibling;
                        }
                    }
                };
            }
        };
    }

    public Iterable<Symbol> getElementsByName(Name name) {
        return getElementsByName(name, noFilter);
    }

    public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                 return new Iterator<Symbol>() {
                    Scope.Entry currentEntry = lookup(name, sf);

                    public boolean hasNext() {
                        return currentEntry.scope != null;
                    }
                    public Symbol next() {
                        Scope.Entry prevEntry = currentEntry;
                        currentEntry = currentEntry.next(sf);
                        return prevEntry.sym;
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
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
            return shadowed;
        }

        public Entry next(Filter<Symbol> sf) {
            if (shadowed.sym == null || sf.accepts(shadowed.sym)) return shadowed;
            else return shadowed.next(sf);
        }

        public boolean isStaticallyImported() {
            return false;
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
        Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope,
                final Scope origin, final boolean staticallyImported) {
            return new Entry(sym, shadowed, sibling, scope) {
                @Override
                public Scope getOrigin() {
                    return origin;
                }

                @Override
                public boolean isStaticallyImported() {
                    return staticallyImported;
                }
            };
        }
    }

    public static class StarImportScope extends ImportScope implements ScopeListener {

        public StarImportScope(Symbol owner) {
            super(owner);
        }

        public void importAll (Scope fromScope) {
            for (Scope.Entry e = fromScope.elems; e != null; e = e.sibling) {
                if (e.sym.kind == Kinds.TYP && !includes(e.sym))
                    enter(e.sym, fromScope);
            }
            // Register to be notified when imported items are removed
            fromScope.addScopeListener(this);
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            remove(sym);
        }
        public void symbolAdded(Symbol sym, Scope s) { }
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

    /** A class scope adds capabilities to keep track of changes in related
     *  class scopes - this allows client to realize whether a class scope
     *  has changed, either directly (because a new member has been added/removed
     *  to this scope) or indirectly (i.e. because a new member has been
     *  added/removed into a supertype scope)
     */
    public static class CompoundScope extends Scope implements ScopeListener {

        public static final Entry[] emptyTable = new Entry[0];

        private List<Scope> subScopes = List.nil();
        private int mark = 0;

        public CompoundScope(Symbol owner) {
            super(null, owner, emptyTable);
        }

        public void addSubScope(Scope that) {
           if (that != null) {
                subScopes = subScopes.prepend(that);
                that.addScopeListener(this);
                mark++;
                for (ScopeListener sl : listeners) {
                    sl.symbolAdded(null, this); //propagate upwards in case of nested CompoundScopes
                }
           }
         }

        public void symbolAdded(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolAdded(sym, s);
            }
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolRemoved(sym, s);
            }
        }

        public int getMark() {
            return mark;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("CompoundScope{");
            String sep = "";
            for (Scope s : subScopes) {
                buf.append(sep);
                buf.append(s);
                sep = ",";
            }
            buf.append("}");
            return buf.toString();
        }

        @Override
        public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElements(sf).iterator();
                        }
                    };
                }
            };
        }

        @Override
        public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElementsByName(name, sf).iterator();
                        }
                    };
                }
            };
        }

        abstract class CompoundScopeIterator implements Iterator<Symbol> {

            private Iterator<Symbol> currentIterator;
            private List<Scope> scopesToScan;

            public CompoundScopeIterator(List<Scope> scopesToScan) {
                this.scopesToScan = scopesToScan;
                update();
            }

            abstract Iterator<Symbol> nextIterator(Scope s);

            public boolean hasNext() {
                return currentIterator != null;
            }

            public Symbol next() {
                Symbol sym = currentIterator.next();
                if (!currentIterator.hasNext()) {
                    update();
                }
                return sym;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private void update() {
                while (scopesToScan.nonEmpty()) {
                    currentIterator = nextIterator(scopesToScan.head);
                    scopesToScan = scopesToScan.tail;
                    if (currentIterator.hasNext()) return;
                }
                currentIterator = null;
            }
        }

        @Override
        public Entry lookup(Name name, Filter<Symbol> sf) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope dup(Symbol newOwner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enter(Symbol sym, Scope s, Scope origin, boolean staticallyImported) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Symbol sym) {
            throw new UnsupportedOperationException();
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
