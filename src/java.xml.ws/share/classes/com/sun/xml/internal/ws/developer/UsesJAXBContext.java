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

package com.sun.xml.internal.ws.developer;

import javax.xml.ws.spi.WebServiceFeatureAnnotation;
import javax.xml.bind.JAXBContext;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This feature instructs that the specified {@link JAXBContextFactory} be used for performing
 * data-binding for the SEI.
 *
 * <p>
 * For example,
 * <pre>
 * &#64;WebService
 * &#64;UsesJAXBContext(MyJAXBContextFactory.class)
 * public class HelloService {
 *   ...
 * }
 * </pre>
 *
 * <p>
 * If your {@link JAXBContextFactory} needs to carry some state from your calling application,
 * you can use {@link UsesJAXBContextFeature} to pass in an instance of {@link JAXBContextFactory},
 * instead of using this to specify the type.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1.5
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebServiceFeatureAnnotation(id=UsesJAXBContextFeature.ID,bean=UsesJAXBContextFeature.class)
public @interface UsesJAXBContext {
    /**
     * Designates the {@link JAXBContextFactory} to be used to create the {@link JAXBContext} object,
     * which in turn will be used by the JAX-WS runtime to marshal/unmarshal parameters and return
     * values to/from XML.
     */
    Class<? extends JAXBContextFactory> value();
}
