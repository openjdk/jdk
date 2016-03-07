/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8087104
 * @summary Make sure that clone() method is not called from DateFormatSymbols constructor.
 */
import java.text.DateFormatSymbols;

public class DFSymbolsCloneTest extends DateFormatSymbols {

    private Foo foo;

    public DFSymbolsCloneTest(Foo fooObj) {
        if (fooObj == null) {
            this.foo = new Foo();
        } else {
            this.foo = fooObj;
        }
    }

    @Override
    public Object clone() {
        DFSymbolsCloneTest dfsclone = (DFSymbolsCloneTest) super.clone();
        if (this.foo == null) {
            throw new RuntimeException("Clone method should not be called from "
                    + " Superclass(DateFormatSymbols) Constructor...");
        } else {
            dfsclone.foo = (Foo) this.foo.clone();
        }
        return dfsclone;
    }

    public static void main(String[] args) {
        DFSymbolsCloneTest dfsctest = new DFSymbolsCloneTest(new Foo());
    }
}

class Foo {

    public Foo() {
    }

    @Override
    protected Object clone() {
        return new Foo();
    }

}
