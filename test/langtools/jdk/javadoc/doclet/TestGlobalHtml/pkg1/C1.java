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


/**
 * <div inert>
 * <p> This content is inert and not interactable.</p>
 * <a href="https://openjdk.org/" title="OpenJDK's Website" tabindex="0">
 * Visit OpenJDK's Website!
 * </a>
 * </div>
 *
 * <div>
 *   <p autocapitalize="on">This content is interactable.</p>
 *   <a href="https://openjdk.org/" title="OpenJDK's Website" tabindex="0">
 *     Visit OpenJDK's Website!
 *   </a>
 * </div>
 *
 *
 * <div dir="ltr" lang="en">
 *   <p itemprop="description">This is used in a jtreg test to check that global HTML tags are allowed</p>
 *   <ul spellcheck="true">
 *     <li>Class C</li>
 *     <li>Has a default constructor</li>
 *   </ul>
 * </div>
 *
 * <p contenteditable="true" inputmode="text">Here is a description of the class and methods:</p>
 *
 * <ol draggable="true" tabindex="0">
 *   <li><p accesskey="1" data-element-type="constructor" title="Class Details">Has a default constructor</p></li>
 *   <li><p accesskey="2" data-element-type="toString" title="Methods Summary">Overrides toString method</p></li>
 *   <li><p accesskey="3" data-element-type="other" title="Usage Example">Is used for testing</p></li>
 * </ol>
 *
 * <div itemscope>
 *   <p itemprop="name">C1</p>
 *   <p itemprop="description">C1</p>
 * </div>
 */
public class C1 {

    /**
     * <p lang="en" accesskey="D" autocapitalize="on" draggable="true" spellcheck="false">
     * Default constructor for the {@code C1} class. (this content is draggable!) </p>
     * <div lang="en" contenteditable="true">
     *   <p itemprop="creator">Author: try editing this content!</p>
     *   <p title="Creation Date">Created on: June 14 2024</p>
     * </div>
     */
    public C1() {
    }

    /**
     * A method in C1
     *
     * <p lang="en" inputmode="numeric">simple method.</p>
     *
     * <div itemprop="method" itemscope>
     *   <p itemprop="name">method m</p>
     *   <p itemprop="description">the method m does nothing</p>
     * </div>
     */
    public void m() {
    }

    /**
     * A toString Override.
     *
     * <p dir="ltr" spellcheck="true">returns a String Object.</p>
     *
     * <div itemprop="method" itemscope>
     *   <p itemprop="name">toString</p>
     * </div>
     *
     * @return a string.
     */
    @Override
    public String toString() {
        return "C1";
    }
}
