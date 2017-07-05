/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.generics.reflectiveObjects;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.tree.FieldTypeSignature;
import sun.reflect.generics.visitor.Reifier;

/**
 * Implementation of <tt>java.lang.reflect.TypeVariable</tt> interface
 * for core reflection.
 */
public class TypeVariableImpl<D extends GenericDeclaration>
    extends LazyReflectiveObjectGenerator implements TypeVariable<D> {
    D genericDeclaration;
    private String name;
    // upper bounds - evaluated lazily
    private Type[] bounds;

    // The ASTs for the bounds. We are required to evaluate the bounds
    // lazily, so we store these at least until we are first asked
    // for the bounds. This also neatly solves the
    // problem with F-bounds - you can't reify them before the formal
    // is defined.
    private FieldTypeSignature[] boundASTs;

    // constructor is private to enforce access through static factory
    private TypeVariableImpl(D decl, String n, FieldTypeSignature[] bs,
                             GenericsFactory f) {
        super(f);
        genericDeclaration = decl;
        name = n;
        boundASTs = bs;
    }

    // Accessors

    // accessor for ASTs for bounds. Must not be called after
    // bounds have been evaluated, because we might throw the ASTs
    // away (but that is not thread-safe, is it?)
    private FieldTypeSignature[] getBoundASTs() {
        // check that bounds were not evaluated yet
        assert(bounds == null);
        return boundASTs;
    }

    /**
     * Factory method.
     * @param decl - the reflective object that declared the type variable
     * that this method should create
     * @param name - the name of the type variable to be returned
     * @param bs - an array of ASTs representing the bounds for the type
     * variable to be created
     * @param f - a factory that can be used to manufacture reflective
     * objects that represent the bounds of this type variable
     * @return A type variable with name, bounds, declaration and factory
     * specified
     */
    public static <T extends GenericDeclaration>
                             TypeVariableImpl<T> make(T decl, String name,
                                                      FieldTypeSignature[] bs,
                                                      GenericsFactory f) {
        return new TypeVariableImpl<T>(decl, name, bs, f);
    }


    /**
     * Returns an array of <tt>Type</tt> objects representing the
     * upper bound(s) of this type variable.  Note that if no upper bound is
     * explicitly declared, the upper bound is <tt>Object</tt>.
     *
     * <p>For each upper bound B:
     * <ul>
     *  <li>if B is a parameterized type or a type variable, it is created,
     *  (see {@link #ParameterizedType} for the details of the creation
     *  process for parameterized types).
     *  <li>Otherwise, B is resolved.
     * </ul>
     *
     * @throws <tt>TypeNotPresentException</tt>  if any of the
     *     bounds refers to a non-existent type declaration
     * @throws <tt>MalformedParameterizedTypeException</tt> if any of the
     *     bounds refer to a parameterized type that cannot be instantiated
     *     for any reason
     * @return an array of Types representing the upper bound(s) of this
     *     type variable
    */
    public Type[] getBounds() {
        // lazily initialize bounds if necessary
        if (bounds == null) {
            FieldTypeSignature[] fts = getBoundASTs(); // get AST
            // allocate result array; note that
            // keeping ts and bounds separate helps with threads
            Type[] ts = new Type[fts.length];
            // iterate over bound trees, reifying each in turn
            for ( int j = 0; j  < fts.length; j++) {
                Reifier r = getReifier();
                fts[j].accept(r);
                ts[j] = r.getResult();
            }
            // cache result
            bounds = ts;
            // could throw away bound ASTs here; thread safety?
        }
        return bounds.clone(); // return cached bounds
    }

    /**
     * Returns the <tt>GenericDeclaration</tt>  object representing the
     * generic declaration that declared this type variable.
     *
     * @return the generic declaration that declared this type variable.
     *
     * @since 1.5
     */
    public D getGenericDeclaration(){
        return genericDeclaration;
    }


    /**
     * Returns the name of this type variable, as it occurs in the source code.
     *
     * @return the name of this type variable, as it appears in the source code
     */
    public String getName()   { return name; }

    public String toString() {return getName();}

    @Override
    public boolean equals(Object o) {
        if (o instanceof TypeVariable) {
            TypeVariable that = (TypeVariable) o;

            GenericDeclaration thatDecl = that.getGenericDeclaration();
            String thatName = that.getName();

            return
                (genericDeclaration == null ?
                 thatDecl == null :
                 genericDeclaration.equals(thatDecl)) &&
                (name == null ?
                 thatName == null :
                 name.equals(thatName));

        } else
            return false;
    }

    @Override
    public int hashCode() {
        return genericDeclaration.hashCode() ^ name.hashCode();
    }
}
