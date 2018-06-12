/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
<p style="font-style: italic; font-size:larger">
<b>Note:</b> The declarations in this package have been superseded by those
in the package {@link jdk.javadoc.doclet}.
For more information, see the <i>Migration Guide</i> in the documentation for that package.
</p>

The Doclet API (also called the Javadoc API) provides a mechanism
for clients to inspect the source-level structure of programs and
libraries, including javadoc comments embedded in the source.
This is useful for documentation, program checking, automatic
code generation and many other tools.
<p>

Doclets are invoked by javadoc and use this API to write out
program information to files.  For example, the standard doclet is called
by default and writes out documentation to HTML files.
<p>

The invocation is defined by the abstract {@link com.sun.javadoc.Doclet} class
-- the entry point is the {@link com.sun.javadoc.Doclet#start(RootDoc) start} method:
<pre>
    public static boolean <b>start</b>(RootDoc root)
</pre>
The {@link com.sun.javadoc.RootDoc} instance holds the root of the program structure
information. From this root all other program structure
information can be extracted.
<p>

<a id="terminology"></a>
<h3>Terminology</h3>

<a id="included"></a>
When calling javadoc, you pass in package names and source file names --
these are called the <em>specified</em> packages and classes.
You also pass in Javadoc options; the <em>access control</em> Javadoc options
({@code -public}, {@code -protected}, {@code -package},
and {@code -private}) filter program elements, producing a
result set, called the <em>included</em> set, or "documented" set.
(The unfiltered set is also available through
{@link com.sun.javadoc.PackageDoc#allClasses(boolean) allClasses(false)}.)
<p>

<a id="class"></a>
Throughout this API, the term <em>class</em> is normally a
shorthand for "class or interface", as in: {@link com.sun.javadoc.ClassDoc},
{@link com.sun.javadoc.PackageDoc#allClasses() allClasses()}, and
{@link com.sun.javadoc.PackageDoc#findClass(String) findClass(String)}.
In only a couple of other places, it means "class, as opposed to interface",
as in:  {@link com.sun.javadoc.Doc#isClass()}.
In the second sense, this API calls out four kinds of classes:
{@linkplain com.sun.javadoc.Doc#isOrdinaryClass() ordinary classes},
{@linkplain com.sun.javadoc.Doc#isEnum() enums},
{@linkplain com.sun.javadoc.Doc#isError() errors} and
{@linkplain com.sun.javadoc.Doc#isException() exceptions}.
Throughout the API, the detailed description of each program element
describes explicitly which meaning is being used.
<p>

<a id="qualified"></a>
A <em>qualified</em> class or interface name is one that has its package
name prepended to it, such as {@code java.lang.String}.  A non-qualified
name has no package name, such as {@code String}.
<p>

<a id="example"></a>
<h3>Example</h3>

The following is an example doclet that
displays information in the {@code @param} tags of the processed
classes:
<pre>
import com.sun.javadoc.*;

public class ListParams extends <span style="color:#E00000" >Doclet</span> {

    public static boolean start(<span style="color:#E00000" >RootDoc</span> root) {
        <span style="color:#E00000" >ClassDoc</span>[] classes = root.<span style="color:#E00000" >classes</span>();
        for (int i = 0; i &lt; classes.length; ++i) {
            <span style="color:#E00000" >ClassDoc</span> cd = classes[i];
            printMembers(cd.<span style="color:#E00000" >constructors</span>());
            printMembers(cd.<span style="color:#E00000" >methods</span>());
        }
        return true;
    }

    static void printMembers(<span style="color:#E00000" >ExecutableMemberDoc</span>[] mems) {
        for (int i = 0; i &lt; mems.length; ++i) {
            <span style="color:#E00000" >ParamTag</span>[] params = mems[i].<span style="color:#E00000" >paramTags</span>();
            System.out.println(mems[i].<span style="color:#E00000" >qualifiedName</span>());
            for (int j = 0; j &lt; params.length; ++j) {
                System.out.println("   " + params[j].<span style="color:#E00000" >parameterName</span>()
                    + " - " + params[j].<span style="color:#E00000" >parameterComment</span>());
            }
        }
    }
}
</pre>
Interfaces and methods from the Javadoc API are marked in
<span style="color:#E00000" >red</span>.
{@link com.sun.javadoc.Doclet Doclet} is an abstract class that specifies
the invocation interface for doclets,
{@link com.sun.javadoc.Doclet Doclet} holds class or interface information,
{@link com.sun.javadoc.ExecutableMemberDoc} is a
superinterface of {@link com.sun.javadoc.MethodDoc} and
{@link com.sun.javadoc.ConstructorDoc},
and {@link com.sun.javadoc.ParamTag} holds information
from "{@code @param}" tags.
<p>
This doclet when invoked with a command line like:
<pre>
    javadoc -doclet ListParams -sourcepath &lt;source-location&gt; java.util
</pre>
producing output like:
<pre>
    ...
    java.util.ArrayList.add
       index - index at which the specified element is to be inserted.
       element - element to be inserted.
    java.util.ArrayList.remove
       index - the index of the element to removed.
    ...

</pre>
@see com.sun.javadoc.Doclet
@see com.sun.javadoc.RootDoc
*/
package com.sun.javadoc;
