/*
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

/*
 * Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */

package javax.jws;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Associates the Web Service with an externally defined handler chain.  This annotation is typically used in scenarios
 * where embedding the handler configuration directly in the Java source is not appropriate; for example, where the
 * handler configuration needs to be shared across multiple Web Services, or where the handler chain consists of
 * handlers for multiple transports.
 *
 * It is an error to combine this annotation with the @SOAPMessageHandlers annotation.
 *
 * @author Copyright (c) 2004 by BEA Systems, Inc. All Rights Reserved.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface HandlerChain {

    /**
     * Location of the handler chain file.
     * <p>
     * The location supports 2 formats:
     * <ol>
     * <li>An absolute java.net.URL in externalForm (ex: http://myhandlers.foo.com/handlerfile1.xml).
     * <li>A relative path from the source file or class file (ex: bar/handlerfile1.xml).
     * </ol>
     */
    String file();

    /**
     * Name of the handler chain in the configuration file
     *
     * @deprecated As of JSR-181 2.0 with no replacement.
     */
    @Deprecated String name() default "";
};
