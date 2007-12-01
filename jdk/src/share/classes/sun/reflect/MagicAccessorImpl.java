/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect;

/** <P> MagicAccessorImpl (named for parity with FieldAccessorImpl and
    others, not because it actually implements an interface) is a
    marker class in the hierarchy. All subclasses of this class are
    "magically" granted access by the VM to otherwise inaccessible
    fields and methods of other classes. It is used to hold the code
    for dynamically-generated FieldAccessorImpl and MethodAccessorImpl
    subclasses. (Use of the word "unsafe" was avoided in this class's
    name to avoid confusion with {@link sun.misc.Unsafe}.) </P>

    <P> The bug fix for 4486457 also necessitated disabling
    verification for this class and all subclasses, as opposed to just
    SerializationConstructorAccessorImpl and subclasses, to avoid
    having to indicate to the VM which of these dynamically-generated
    stub classes were known to be able to pass the verifier. </P>

    <P> Do not change the name of this class without also changing the
    VM's code. </P> */

class MagicAccessorImpl {
}
