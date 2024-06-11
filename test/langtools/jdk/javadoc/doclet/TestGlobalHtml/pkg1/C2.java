/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package pkg1;

import java.io.IOException;

/**
 * <div inert>
 *   <p>This content is inert and not interactable.</p>
 *   <a href="https://openjdk.org/" accesskey="O" title="OpenJDK's Website" tabindex="0">
 *     Visit OpenJDK's Website
 *   </a>
 * </div>
 *
 * <div>
 *   <p>This content is interactable.</p>
 *   <a href="https://openjdk.org/" accesskey="O" title="OpenJDK's Website" tabindex="0">
 *     Visit OpenJDK's Website
 *   </a>
 * </div>
 *
 * A class comment for testing.
 *
 * <p lang="en" accesskey="D" autocapitalize="none" draggable="true" spellcheck="false">
 * This class extends C1 and provides additional functionalities.
 * </p>
 *
 * <table class="plain">
 * <caption>Examples of methods in C2</caption>
 * <thead>
 *    <tr>
 *       <th scope="col">Method Name
 *       <th scope="col">Description
 * </thead>
 * <tbody>
 *    <tr>
 *       <td>performAction
 *       <td>Performs a specified action.
 *    <tr>
 *       <td>calculateResult
 *       <td>Calculates a result based on input.
 * </tbody>
 * </table>
 *
 * @since JDK 8
 */
public class C2 extends C1 {

    /**
     * Constructor with title and test flag.
     *
     * <p itemprop="constructor" itemtype="http://schema.org/Person">
     * Initializes a new instance of the C2 class with the specified title and test flag.
     * </p>
     *
     * @param title the title
     * @param test  boolean value
     */
    public C2(String title, boolean test) {
        super(title, test);
    }

    /**
     * Constructor with title only.
     *
     * <p lang="en"> Initializes a new instance of the C2 class with the specified title.</p>
     *
     * @param title the title
     */
    public C2(String title) {
        super(title);
    }

    /**
     * Perform a specified action.
     *
     * <p contenteditable="true" draggable="true" spellcheck="true" data-method="action">
     * This method performs an action and returns a status.
     * </p>
     *
     * <ul>
     *   <li>Item 1
     *   <li>Item 2
     *   <li>Item 3
     * </ul>
     *
     * @param action a string representing the action to be performed
     * @return a boolean indicating success or failure
     * @see #calculateResult(int, int)
     */
    public boolean performAction(String action) {
        return true;
    }

    /**
     * Calculates a result based on two integer inputs.
     *
     * <p enterkeyhint="done" data-method="calculation">
     * This method takes two integers, performs a calculation, and returns the result.
     * </p>
     *
     * <ol>
     *   <li>Step 1: Input validation
     *   <li>Step 2: Calculation
     *   <li>Step 3: Return result
     * </ol>
     *
     * @param a the first integer
     * @param b the second integer
     * @return the result of the calculation
     * @throws ArithmeticException if a division by zero occurs
     */
    public int calculateResult(int a, int b) throws ArithmeticException {
        return a + b;
    }

    /**
     * Reads the object from a stream.
     *
     * <p itemid="#readObject" tabindex="0" inputmode="text" data-method="deserializer">
     * This method deserializes an object from the input stream.
     * </p>
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void readObject() throws IOException {
        super.readObject();
    }
}
