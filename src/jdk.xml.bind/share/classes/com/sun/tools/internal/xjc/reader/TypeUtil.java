/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.ErrorReceiver;

import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

/**
 * Type-related utility methods.
 *
 * @author
 *    <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public class TypeUtil {


    /**
     * Computes the common base type of two types.
     *
     * @param types
     *      set of {@link JType} objects.
     */
    public static JType getCommonBaseType( JCodeModel codeModel, Collection<? extends JType> types ) {
        return getCommonBaseType( codeModel, types.toArray(new JType[types.size()]) );
    }

    /**
     * Computes the common base type of types.
     *
     * TODO: this is a very interesting problem. Since one type has possibly
     * multiple base types, it's not an easy problem.
     * The current implementation is very naive.
     *
     * To make the result deterministic across differente JVMs, we have to
     * use a Set whose ordering is deterministic.
     */
    public static JType getCommonBaseType(JCodeModel codeModel, JType... t) {
        // first, eliminate duplicates.
        Set<JType> uniqueTypes = new TreeSet<JType>(typeComparator);
        for (JType type : t)
            uniqueTypes.add(type);

        // if this yields only one type. return now.
        // this is the only case where we can return a primitive type
        // from this method
        if (uniqueTypes.size() == 1)
            return uniqueTypes.iterator().next();

        // assertion failed. nullType can be used only under a very special circumstance
        assert !uniqueTypes.isEmpty();

        // the null type doesn't need to be taken into account.
        uniqueTypes.remove(codeModel.NULL);

        // box all the types and compute the intersection of all types
        Set<JClass> s = null;

        for (JType type : uniqueTypes) {
            JClass cls = type.boxify();

            if (s == null)
                s = getAssignableTypes(cls);
            else
                s.retainAll(getAssignableTypes(cls));
        }

        // any JClass can be casted to Object, so make sure it's always there
        s.add( codeModel.ref(Object.class));

        // refine 's' by removing "lower" types.
        // for example, if we have both java.lang.Object and
        // java.io.InputStream, then we don't want to use java.lang.Object.

        JClass[] raw = s.toArray(new JClass[s.size()]);
        s.clear();

        for (int i = 0; i < raw.length; i++) { // for each raw[i]
            int j;
            for (j = 0; j < raw.length; j++) { // see if raw[j] "includes" raw[i]
                if (i == j)
                    continue;

                if (raw[i].isAssignableFrom(raw[j]))
                    break; // raw[j] is derived from raw[i], hence j includes i.
            }

            if (j == raw.length)
                // no other type inclueds raw[i]. remember this value.
                s.add(raw[i]);
        }

        assert !s.isEmpty(); // since at least java.lang.Object has to be there

        // we now pick the candidate for the return type
        JClass result = pickOne(s);

        // finally, sometimes this method is used to compute the base type of types like
        // JAXBElement<A>, JAXBElement<B>, and JAXBElement<C>.
        // for those inputs, at this point result=JAXBElement.
        //
        // here, we'll try to figure out the parameterization
        // so that we can return JAXBElement<? extends D> instead of just "JAXBElement".
        if(result.isParameterized())
            return result;

        // for each uniqueType we store the list of base type parameterization
        List<List<JClass>> parameters = new ArrayList<List<JClass>>(uniqueTypes.size());
        int paramLen = -1;

        for (JType type : uniqueTypes) {
            JClass cls = type.boxify();
            JClass bp = cls.getBaseClass(result);
            // if there's no parameterization in the base type,
            // we won't do any better than <?>. Thus no point in trying to figure out the parameterization.
            // just return the base type.
            if(bp.equals(result))
                return result;

            assert bp.isParameterized();
            List<JClass> tp = bp.getTypeParameters();
            parameters.add(tp);

            assert paramLen==-1 || paramLen==tp.size();
                // since 'bp' always is a parameterized version of 'result', it should always
                // have the same number of parameters.
            paramLen = tp.size();
        }

        List<JClass> paramResult = new ArrayList<JClass>();
        List<JClass> argList = new ArrayList<JClass>(parameters.size());
        // for each type parameter compute the common base type
        for( int i=0; i<paramLen; i++ ) {
            argList.clear();
            for (List<JClass> list : parameters)
                argList.add(list.get(i));

            // compute the lower bound.
            JClass bound = (JClass)getCommonBaseType(codeModel,argList);
            boolean allSame = true;
            for (JClass a : argList)
                allSame &= a.equals(bound);
            if(!allSame)
                bound = bound.wildcard();

            paramResult.add(bound);
        }

        return result.narrow(paramResult);
    }

    private static JClass pickOne(Set<JClass> s) {
        // we may have more than one candidates at this point.
        // any user-defined generated types should have
        // precedence over system-defined existing types.
        //
        // so try to return such a type if any.
        for (JClass c : s)
            if (c instanceof JDefinedClass)
                return c;

        // we can do more if we like. for example,
        // we can avoid types in the RI runtime.
        // but for now, just return the first one.
        return s.iterator().next();
    }

    private static Set<JClass> getAssignableTypes( JClass t ) {
        Set<JClass> r = new TreeSet<JClass>(typeComparator);
        getAssignableTypes(t,r);
        return r;
    }

    /**
     * Returns the set of all classes/interfaces that a given type
     * implements/extends, including itself.
     *
     * For example, if you pass java.io.FilterInputStream, then the returned
     * set will contain java.lang.Object, java.lang.InputStream, and
     * java.lang.FilterInputStream.
     */
    private static void getAssignableTypes( JClass t, Set<JClass> s ) {
        if(!s.add(t))
            return;

        // add its raw type
        s.add(t.erasure());

        // if this type is added for the first time,
        // recursively process the super class.
        JClass _super = t._extends();
        if(_super!=null)
            getAssignableTypes(_super,s);

        // recursively process all implemented interfaces
        Iterator<JClass> itr = t._implements();
        while(itr.hasNext())
            getAssignableTypes(itr.next(),s);
    }

    /**
     * Obtains a {@link JType} object for the string representation
     * of a type.
     */
    public static JType getType( JCodeModel codeModel,
        String typeName, ErrorReceiver errorHandler, Locator errorSource ) {

        try {
            return codeModel.parseType(typeName);
        } catch( ClassNotFoundException ee ) {

            // make it a warning
            errorHandler.warning( new SAXParseException(
                Messages.ERR_CLASS_NOT_FOUND.format(typeName)
                ,errorSource));

            // recover by assuming that it's a class that derives from Object
            return codeModel.directClass(typeName);
        }
    }

    /**
     * Compares {@link JType} objects by their names.
     */
    private static final Comparator<JType> typeComparator = new Comparator<JType>() {
        public int compare(JType t1, JType t2) {
            return t1.fullName().compareTo(t2.fullName());
        }
    };
}
