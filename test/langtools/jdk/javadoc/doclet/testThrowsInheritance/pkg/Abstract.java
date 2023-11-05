/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
public abstract class Abstract {
    /**
     * @throws java.lang.NullPointerException this should appear
     * @throws java.lang.IndexOutOfBoundsException this <em>should not for bug-compatibility</em>
     */
    abstract void method() throws NullPointerException;

    // NOTE: Not sure why this test suggests that IndexOutOfBoundsException
    // should not appear due to compatibility with some buggy behavior.
    //
    // Here's the expected behavior: documentation for an exception X is never
    // inherited by an overrider unless it "pulls" it by either (or both)
    // of these:
    //
    //   * tag:
    //       @throws X {@inheritDoc}
    //   * clause:
    //       throws ..., X,...
    //
    // Neither of those are applicable here. Even taking into account
    // mechanisms such as the one introduced in 4947455, neither of
    // NullPointerException and IndexOutOfBoundsException is a subclass
    // of the other.
    //
    // So, IndexOutOfBoundsException should not appear in Extender.
}
