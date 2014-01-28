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

package com.sun.xml.internal.ws.spi.db;

import java.lang.reflect.Type;

//TODO SOAPVersion WebServiceFeatureList
import com.sun.xml.internal.bind.util.Which;

//TODO Packet AbstractMessageImpl
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;

//TODO DOMHeader DOMMessage SAAJMessage StatefulInstanceResolver
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;

//TODO MtomCodec
import com.sun.xml.internal.bind.v2.runtime.output.Encoded;

//TODO ExceptionBean
import com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper;

//TODO AbstractWrapperBeanGenerator
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.RuntimeInlineAnnotationReader;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

//TODO WSDLGenerator
import static com.sun.xml.internal.bind.v2.schemagen.Util.*;

import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
/**
 * BindingHelper
 *
 * @author shih-chang.chen@oracle.com
 */
public class BindingHelper {
    /**
     * Computes a Java identifier from a local name.
     *
     * <p>
     * This method faithfully implements the name mangling rule as specified in the JAXB spec.
     *
     * <p>
     * In JAXB, a collision with a Java reserved word (such as "return") never happens.
     * Accordingly, this method may return an identifier that collides with reserved words.
     *
     * <p>
     * Use <tt>JJavaName.isJavaIdentifier(String)</tt> to check for such collision.
     *
     * @return
     *      Typically, this method returns "nameLikeThis".
     */
    public static @NotNull String mangleNameToVariableName(@NotNull String localName) {
        return NameConverter.standard.toVariableName(localName);
    }

    /**
     * Computes a Java class name from a local name.
     *
     * <p>
     * This method faithfully implements the name mangling rule as specified in the JAXB spec.
     *
     * @return
     *      Typically, this method returns "NameLikeThis".
     */
    public static @NotNull String mangleNameToClassName(@NotNull String localName) {
        return NameConverter.standard.toClassName(localName);
    }

    /**
     * Computes a Java class name from a local name.
     *
     * <p>
     * This method faithfully implements the name mangling rule as specified in the JAXB spec.
     * This method works like {@link #mangleNameToClassName(String)} except that it looks
     * for "getClass" and returns something else.
     *
     * @return
     *      Typically, this method returns "NameLikeThis".
     */
    public static @NotNull String mangleNameToPropertyName(@NotNull String localName) {
        return NameConverter.standard.toPropertyName(localName);
    }

    /**
     * Gets the parameterization of the given base type.
     *
     * <p>
     * For example, given the following
     * <pre><xmp>
     * interface Foo<T> extends List<List<T>> {}
     * interface Bar extends Foo<String> {}
     * </xmp></pre>
     * This method works like this:
     * <pre><xmp>
     * getBaseClass( Bar, List ) = List<List<String>
     * getBaseClass( Bar, Foo  ) = Foo<String>
     * getBaseClass( Foo<? extends Number>, Collection ) = Collection<List<? extends Number>>
     * getBaseClass( ArrayList<? extends BigInteger>, List ) = List<? extends BigInteger>
     * </xmp></pre>
     *
     * @param type
     *      The type that derives from {@code baseType}
     * @param baseType
     *      The class whose parameterization we are interested in.
     * @return
     *      The use of {@code baseType} in {@code type}.
     *      or null if the type is not assignable to the base type.
     * @since 2.0 FCS
     */
    public static @Nullable Type getBaseType(@NotNull Type type, @NotNull Class baseType) {
        return Utils.REFLECTION_NAVIGATOR.getBaseClass(type,baseType);
    }

    public static <T> Class<T> erasure(Type t) {
        return (Class<T>) Utils.REFLECTION_NAVIGATOR.erasure(t);
    }
}
