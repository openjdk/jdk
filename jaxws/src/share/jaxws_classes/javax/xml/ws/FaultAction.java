/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>FaultAction</code> annotation is used inside an {@link Action}
 * annotation to allow an explicit association of a WS-Addressing
 * <code>Action</code> message addressing property with the <code>fault</code>
 * messages of the WSDL operation mapped from the exception class.
 * <p>
 * The <code>wsam:Action</code> attribute value in the <code>fault</code>
 * message in the generated WSDL operation mapped for <code>className</code>
 * class is equal to the corresponding value in the <code>FaultAction</code>.
 * For the exact computation of <code>wsam:Action</code> values for the
 * fault messages, refer to the algorithm in the JAX-WS specification.
 *
 * <p>
 * <b>Example 1</b>: Specify explicit values for <code>Action</code> message addressing
 * property for the <code>input</code>, <code>output</code> and <code>fault</code> message
 * if the Java method throws only one service specific exception.
 *
 * <pre>
 * &#64;WebService(targetNamespace="http://example.com/numbers")
 * public class AddNumbersImpl {
 *     &#64;Action(
 *         fault = {
 *             <b>&#64;FaultAction(className=AddNumbersException.class, value="http://example.com/faultAction")</b>
 *         })
 *     public int addNumbers(int number1, int number2)
 *         throws AddNumbersException {
 *         return number1 + number2;
 *     }
 * }
 * </pre>
 *
 * The generated WSDL looks like:
 *
 * <pre>
 *   &lt;definitions targetNamespace="http://example.com/numbers" ...>
 *     ...
 *     &lt;portType name="AddNumbersPortType">
 *       &lt;operation name="AddNumbers">
 *         ...
 *         &lt;fault message="tns:AddNumbersException" name="AddNumbersException"
 *           <b>wsam:Action="http://example.com/faultAction"</b>/>
 *       &lt;/operation>
 *     &lt;/portType>
 *     ...
 *   &lt;/definitions>
 * </pre>
 *
 * <p>
 * Example 2: Here is an example that shows if the explicit value for <code>Action</code>
 * message addressing property for the service specific exception is not present.
 *
 * <pre>
 * &#64;WebService(targetNamespace="http://example.com/numbers")
 * public class AddNumbersImpl {
 *     public int addNumbers(int number1, int number2)
 *         throws AddNumbersException {
 *         return number1 + number2;
 *     }
 * }
 * </pre>
 *
 * The generated WSDL looks like:
 *
 * <pre>
 *   &lt;definitions targetNamespace="http://example.com/numbers" ...>
 *     ...
 *     &lt;portType name="AddNumbersPortType">
 *       &lt;operation name="AddNumbers">
 *         ...
 *         &lt;fault message="tns:addNumbersFault" name="InvalidNumbers"
 *           <b>wsam:Action="http://example.com/numbers/AddNumbersPortType/AddNumbers/Fault/AddNumbersException"</b>/>
 *       &lt;/operation>
 *     &lt;/portType>
 *     ...
 *   &lt;/definitions>
 * </pre>
 *
 * <p>
 * Example 3: Here is an example that shows how to specify explicit values for <code>Action</code>
 * message addressing property if the Java method throws more than one service specific exception.
 *
 * <pre>
 * &#64;WebService(targetNamespace="http://example.com/numbers")
 * public class AddNumbersImpl {
 *     &#64;Action(
 *         fault = {
 *             <b>&#64;FaultAction(className=AddNumbersException.class, value="http://example.com/addFaultAction"),
 *             &#64;FaultAction(className=TooBigNumbersException.class, value="http://example.com/toobigFaultAction")</b>
 *         })
 *     public int addNumbers(int number1, int number2)
 *         throws AddNumbersException, TooBigNumbersException {
 *         return number1 + number2;
 *     }
 * }
 * </pre>
 *
 * The generated WSDL looks like:
 *
 * <pre>
 *   &lt;definitions targetNamespace="http://example.com/numbers" ...>
 *     ...
 *     &lt;portType name="AddNumbersPortType">
 *       &lt;operation name="AddNumbers">
 *         ...
 *         &lt;fault message="tns:addNumbersFault" name="AddNumbersException"
 *           <b>wsam:Action="http://example.com/addFaultAction"</b>/>
 *         &lt;fault message="tns:tooBigNumbersFault" name="TooBigNumbersException"
 *           <b>wsam:Action="http://example.com/toobigFaultAction"</b>/>
 *       &lt;/operation>
 *     &lt;/portType>
 *     ...
 *   &lt;/definitions>
 * </pre>
 *
 * @since JAX-WS 2.1
 */

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FaultAction {
    /**
     * Name of the exception class
     */
    Class<? extends Exception> className();

    /**
     * Value of WS-Addressing <code>Action</code> message addressing property for the exception
     */
    String value() default "";
}
