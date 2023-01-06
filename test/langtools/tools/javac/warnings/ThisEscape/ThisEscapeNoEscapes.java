/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8194743
 * @summary Verify 'this' escape detection doesn't generate certain false positives
 */
public class ThisEscapeNoEscapes {

    public ThisEscapeNoEscapes() {
        this.noLeak1();                             // invoked method is private
        this.noLeak2();                             // invoked method is final
        ThisEscapeNoEscapes.noLeak3();              // invoked method is static
        this.noLeak4(this);                         // parameter is 'this' but it's not leaked
        this.noLeak5(new ThisEscapeNoEscapes(0));   // parameter is not 'this', so no leak
        this.noLeak6(null, this, null);             // method leaks 1st and 3rd parameters only
        this.noLeak7();                             // method does complicated stuff but doesn't leak
        Runnable r1 = () -> {                       // lambda does not leak 'this'
            if (System.out == System.err)
                throw new RuntimeException();
        };
        System.out.println(r1);                     // lambda does not leak 'this'
        Runnable r2 = () -> {                       // lambda leaks 'this' but is never used
            this.mightLeak1();
        };
        Runnable r3 = this::mightLeak1;             // reference leaks 'this' but is never used
    }

    public ThisEscapeNoEscapes(int x) {
    }

    public void mightLeak1() {
    }

    private void noLeak1() {
    }

    public final void noLeak2() {
    }

    public static void noLeak3() {
    }

    public static void noLeak4(ThisEscapeNoEscapes param) {
        param.noLeak1();
        param.noLeak2();
    }

    public final void noLeak5(ThisEscapeNoEscapes param) {
        param.mightLeak1();
    }

    public final void noLeak6(ThisEscapeNoEscapes param1,
        ThisEscapeNoEscapes param2, ThisEscapeNoEscapes param3) {
        if (param1 != null)
            param1.mightLeak1();
        if (param2 != null)
            param2.noLeak2();
        if (param3 != null)
            param3.mightLeak1();
    }

    public final void noLeak7() {
        ((ThisEscapeNoEscapes)(Object)this).noLeak2();
        final ThisEscapeNoEscapes obj1 = switch (new Object().hashCode()) {
            case 1, 2, 3 -> null;
            default -> new ThisEscapeNoEscapes(0);
        };
        obj1.mightLeak1();
    }

// PrivateClass

    private static class PrivateClass {

        PrivateClass() {
            this.cantLeak();                    // method is inside a private class
        }

        public void cantLeak() {
        }
    }

// FinalClass

    public static final class FinalClass extends ThisEscapeNoEscapes {

        public FinalClass() {
            this.mightLeak1();                  // class and therefore method is final
        }
    }

    public static void main(String[] args) {
        new ThisEscapeNoEscapes();
    }
}
