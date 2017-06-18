/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * The {@code WebServiceRefs} annotation allows
 * multiple web service references to be declared at the
 * class level.
 *
 * <p>
 * It can be used to inject both service and proxy
 * instances. These injected references are not thread safe.
 * If the references are accessed by multiple threads,
 * usual synchronization techniques can be used to
 * support multiple threads.
 *
 * <p>
 * There is no way to associate web service features with
 * the injected instances. If an instance needs to be
 * configured with web service features, use @WebServiceRef
 * to inject the resource along with its features.
 *
 * <p>
 * <b>Example</b>: The {@code StockQuoteProvider}
 * proxy instance, and the {@code StockQuoteService} service
 * instance are injected using @WebServiceRefs.
 *
 * <pre><code>
 *    {@literal @}WebServiceRefs({{@literal @}WebServiceRef(name="service/stockquoteservice", value=StockQuoteService.class),
 *                     {@literal @}WebServiceRef(name="service/stockquoteprovider", type=StockQuoteProvider.class, value=StockQuoteService.class})
 *    public class MyClient {
 *        void init() {
 *            Context ic = new InitialContext();
 *            StockQuoteService service = (StockQuoteService) ic.lookup("java:comp/env/service/stockquoteservice");
 *            StockQuoteProvider port = (StockQuoteProvider) ic.lookup("java:comp/env/service/stockquoteprovider");
 *            ...
 *       }
 *       ...
 *    }
 * </code></pre>
 *
 * @see WebServiceRef
 * @since 1.6, JAX-WS 2.0
 */

@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface WebServiceRefs {
   /**
    * Array used for multiple web service reference declarations.
    *
    * @return multiple web service reference declarations
    */
   WebServiceRef[] value();
}
