/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.util.Hashtable;
import java.io.PrintStream;
import java.util.Enumeration;

/**
 * A class to represent identifiers.<p>
 *
 * An identifier instance is very similar to a String. The difference
 * is that identifier can't be instanciated directly, instead they are
 * looked up in a hash table. This means that identifiers with the same
 * name map to the same identifier object. This makes comparisons of
 * identifiers much faster.<p>
 *
 * A lot of identifiers are qualified, that is they have '.'s in them.
 * Each qualified identifier is chopped up into the qualifier and the
 * name. The qualifier is cached in the value field.<p>
 *
 * Unqualified identifiers can have a type. This type is an integer that
 * can be used by a scanner as a token value. This value has to be set
 * using the setType method.<p>
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Arthur van Hoff
 */

public final
class Identifier implements Constants {
    /**
     * The hashtable of identifiers
     */
    static Hashtable hash = new Hashtable(3001, 0.5f);

    /**
     * The name of the identifier
     */
    String name;

    /**
     * The value of the identifier, for keywords this is an
     * instance of class Integer, for qualified names this is
     * another identifier (the qualifier).
     */
    Object value;

    /**
     * The Type which corresponds to this Identifier.  This is used as
     * cache for Type.tClass() and shouldn't be used outside of that
     * context.
     */
    Type typeObject = null;

    /**
     * The index of INNERCLASS_PREFIX in the name, or -1 if none.
     */
    private int ipos;

    /**
     * Construct an identifier. Don't call this directly,
     * use lookup instead.
     * @see Identifier.lookup
     */
    private Identifier(String name) {
        this.name = name;
        this.ipos = name.indexOf(INNERCLASS_PREFIX);
    }

    /**
     * Get the type of the identifier.
     */
    int getType() {
        return ((value != null) && (value instanceof Integer)) ?
                ((Integer)value).intValue() : IDENT;
    }

    /**
     * Set the type of the identifier.
     */
    void setType(int t) {
        value = t;
        //System.out.println("type(" + this + ")=" + t);
    }

    /**
     * Lookup an identifier.
     */
    public static synchronized Identifier lookup(String s) {
        //System.out.println("lookup(" + s + ")");
        Identifier id = (Identifier)hash.get(s);
        if (id == null) {
            hash.put(s, id = new Identifier(s));
        }
        return id;
    }

    /**
     * Lookup a qualified identifier.
     */
    public static Identifier lookup(Identifier q, Identifier n) {
        // lookup("", x) => x
        if (q == idNull)  return n;
        // lookup(lookupInner(c, ""), n) => lookupInner(c, lookup("", n))
        if (q.name.charAt(q.name.length()-1) == INNERCLASS_PREFIX)
            return lookup(q.name+n.name);
        Identifier id = lookup(q + "." + n);
        if (!n.isQualified() && !q.isInner())
            id.value = q;
        return id;
    }

    /**
     * Lookup an inner identifier.
     * (Note:  n can be idNull.)
     */
    public static Identifier lookupInner(Identifier c, Identifier n) {
        Identifier id;
        if (c.isInner()) {
            if (c.name.charAt(c.name.length()-1) == INNERCLASS_PREFIX)
                id = lookup(c.name+n);
            else
                id = lookup(c, n);
        } else {
            id = lookup(c + "." + INNERCLASS_PREFIX + n);
        }
        id.value = c.value;
        return id;
    }

    /**
     * Convert to a string.
     */
    public String toString() {
        return name;
    }

    /**
     * Check if the name is qualified (ie: it contains a '.').
     */
    public boolean isQualified() {
        if (value == null) {
            int idot = ipos;
            if (idot <= 0)
                idot = name.length();
            else
                idot -= 1;      // back up over previous dot
            int index = name.lastIndexOf('.', idot-1);
            value = (index < 0) ? idNull : Identifier.lookup(name.substring(0, index));
        }
        return (value instanceof Identifier) && (value != idNull);
    }

    /**
     * Return the qualifier. The null identifier is returned if
     * the name was not qualified.  The qualifier does not include
     * any inner part of the name.
     */
    public Identifier getQualifier() {
        return isQualified() ? (Identifier)value : idNull;
    }

    /**
     * Return the unqualified name.
     * In the case of an inner name, the unqualified name
     * will itself contain components.
     */
    public Identifier getName() {
        return isQualified() ?
            Identifier.lookup(name.substring(((Identifier)value).name.length() + 1)) : this;
    }

    /** A space character, which precedes the first inner class
     *  name in a qualified name, and thus marks the qualification
     *  as involving inner classes, instead of merely packages.<p>
     *  Ex:  <tt>java.util.Vector. Enumerator</tt>.
     */
    public static final char INNERCLASS_PREFIX = ' ';

    /* Explanation:
     * Since much of the compiler's low-level name resolution code
     * operates in terms of Identifier objects.  This includes the
     * code which walks around the file system and reports what
     * classes are where.  It is important to get nesting information
     * right as early as possible, since it affects the spelling of
     * signatures.  Thus, the low-level import and resolve code must
     * be able Identifier type must be able to report the nesting
     * of types, which implied that that information must be carried
     * by Identifiers--or that the low-level interfaces be significantly
     * changed.
     */

    /**
     * Check if the name is inner (ie: it contains a ' ').
     */
    public boolean isInner() {
        return (ipos > 0);
    }

    /**
     * Return the class name, without its qualifier,
     * and with any nesting flattened into a new qualfication structure.
     * If the original identifier is inner,
     * the result will be qualified, and can be further
     * decomposed by means of <tt>getQualifier</tt> and <tt>getName</tt>.
     * <p>
     * For example:
     * <pre>
     * Identifier id = Identifier.lookup("pkg.Foo. Bar");
     * id.getName().name      =>  "Foo. Bar"
     * id.getFlatName().name  =>  "Foo.Bar"
     * </pre>
     */
    public Identifier getFlatName() {
        if (isQualified()) {
            return getName().getFlatName();
        }
        if (ipos > 0 && name.charAt(ipos-1) == '.') {
            if (ipos+1 == name.length()) {
                // last component is idNull
                return Identifier.lookup(name.substring(0,ipos-1));
            }
            String n = name.substring(ipos+1);
            String t = name.substring(0,ipos);
            return Identifier.lookup(t+n);
        }
        // Not inner.  Just return the same as getName()
        return this;
    }

    public Identifier getTopName() {
        if (!isInner())  return this;
        return Identifier.lookup(getQualifier(), getFlatName().getHead());
    }

    /**
     * Yet another way to slice qualified identifiers:
     * The head of an identifier is its first qualifier component,
     * and the tail is the rest of them.
     */
    public Identifier getHead() {
        Identifier id = this;
        while (id.isQualified())
            id = id.getQualifier();
        return id;
    }

    /**
     * @see getHead
     */
    public Identifier getTail() {
        Identifier id = getHead();
        if (id == this)
            return idNull;
        else
            return Identifier.lookup(name.substring(id.name.length() + 1));
    }

    // Unfortunately, the current structure of the compiler requires
    // that the resolveName() family of methods (which appear in
    // Environment.java, Context.java, and ClassDefinition.java) raise
    // no exceptions and emit no errors.  When we are in resolveName()
    // and we find a method that is ambiguous, we need to
    // unambiguously mark it as such, so that later stages of the
    // compiler realize that they should give an ambig.class rather than
    // a class.not.found error.  To mark it we add a special prefix
    // which cannot occur in the program source.  The routines below
    // are used to check, add, and remove this prefix.
    // (part of solution for 4059855).

    /**
     * A special prefix to add to ambiguous names.
     */
    private static final String ambigPrefix = "<<ambiguous>>";

    /**
     * Determine whether an Identifier has been marked as ambiguous.
     */
    public boolean hasAmbigPrefix() {
        return (name.startsWith(ambigPrefix));
    }

    /**
     * Add ambigPrefix to `this' to make a new Identifier marked as
     * ambiguous.  It is important that this new Identifier not refer
     * to an existing class.
     */
    public Identifier addAmbigPrefix() {
        return Identifier.lookup(ambigPrefix + name);
    }

    /**
     * Remove the ambigPrefix from `this' to get the original identifier.
     */
    public Identifier removeAmbigPrefix() {
        if (hasAmbigPrefix()) {
            return Identifier.lookup(name.substring(ambigPrefix.length()));
        } else {
            return this;
        }
    }
}
