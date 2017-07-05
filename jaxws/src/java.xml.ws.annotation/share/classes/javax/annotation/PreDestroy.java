/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.annotation;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * The PreDestroy annotation is used on methods as a callback notification to
 * signal that the instance is in the process of being removed by the
 * container. The method annotated with PreDestroy is typically used to
 * release resources that it has been holding. This annotation MUST be
 * supported by all container managed objects that support PostConstruct
 * except the application client container in Java EE 5. The method on which
 * the PreDestroy annotation is applied MUST fulfill all of the following
 * criteria:
 * <ul>
 * <li>The method MUST NOT have any parameters except in the case of
 * interceptors in which case it takes an InvocationContext object as
 * defined by the Interceptors specification.</li>
 * <li>The method defined on an interceptor class MUST HAVE one of the
 * following signatures:
 * <p>
 * void &#060;METHOD&#062;(InvocationContext)
 * <p>
 * Object &#060;METHOD&#062;(InvocationContext) throws Exception
 * <p>
 * <i>Note: A PreDestroy interceptor method must not throw application
 * exceptions, but it may be declared to throw checked exceptions including
 * the java.lang.Exception if the same interceptor method interposes on
 * business or timeout methods in addition to lifecycle events. If a
 * PreDestroy interceptor method returns a value, it is ignored by
 * the container.</i>
 * </li>
 * <li>The method defined on a non-interceptor class MUST HAVE the
 * following signature:
 * <p>
 * void &#060;METHOD&#062;()
 * </li>
 * <li>The method on which PreDestroy is applied MAY be public, protected,
 * package private or private.</li>
 * <li>The method MUST NOT be static.</li>
 * <li>The method MAY be final.</li>
 * <li>If the method throws an unchecked exception it is ignored except in the
 * case of EJBs where the EJB can handle exceptions.</li>
 * </ul>
 *
 * @see javax.annotation.PostConstruct
 * @see javax.annotation.Resource
 * @since 1.6, Common Annotations 1.0
 */

@Documented
@Retention (RUNTIME)
@Target(METHOD)
public @interface PreDestroy {
}
