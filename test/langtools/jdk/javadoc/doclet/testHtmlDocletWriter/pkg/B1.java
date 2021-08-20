/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

package pkg;

/**
 *<a href="{@docRoot}/pkg/A1.html#functions">Various functions</a>in this Class.
 *<a id="functions">various functions</a>
 *<ul>
 *<li>function1</li>
 *<li>function2</li>
 *<li>function3</li>
 *</ul>
 *<a id="methods">special methods</a>
 *<ul>
 *<li>method1</li>
 *<li>method2</li>
 *<li>method3</li>
 *</ul>
 */
public class B1 implements java.io.Serializable {
	/**
	 *fields.
	 */
	protected StaticInnerB1 field2;

	/**
	 *<a href="{@docRoot}/pkg/B1.html#functions">Creates an instance which has various functions.</a>
	 */
	public B1(){
	}

	/**
	 *This is a<a href="#methods">special methods</a>.
	 *@param p1 arg1
	 */
	public void method1(A1 p1){
	}

	/**
	 *Use the InnerA1 class for the first parameter.
	 *@param p1 class InnerA1
	 */
	public void method2(A1.InnerA1 p1){
	}

	/**
	 *Use the InnerB1 class for the first parameter.
	 *@param p1 class InnerB1
	 */
	public void method3(InnerB1 p1){
	}

	/**
	 *Use the InnerB2 class for the first parameter.
	 *@param p1 class InnerB2
	 */
	public void method4(InnerB2 p1){
	}

	/**
	 *Use the InnerB1 class for the first parameter.
	 *@param p1 class InnerB1
	 */
	public void method5(StaticInnerB1 p1){
	}

	/**
	 *Innerclass of class B1.
	 */
	public class InnerB1 extends Object {
	}

	/**
	 *Innerclass of class B1.
	 */
	class InnerB2 extends Object {
	}

	/**
	 * static Innerclass of class B1.
	 */
	public static class StaticInnerB1 extends Object {
	}
}
