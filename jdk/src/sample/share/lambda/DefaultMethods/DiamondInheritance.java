/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * This sample diamond interface inheritance with <b>default methods</b>.
 * If there's not already a unique method implementation to inherit,
 * you must provide it. The inheritance diagram is similar to the following:
 * <pre>
 *                   Animal
 *                    /   \
 *                 Horse   Bird
 *                    \   /
 *                   Pegasus
 * </pre>
 *
 * Both {@link Horse} and {@link Bird} interfaces implements the <code>go</code>
 * method. The {@link Pegasus} class have to overrides the
 * <code>go</code> method.
 *
 * The new syntax of super-call is used here:
 * <pre>
 *     &lt;interface_name&gt;.super.&lt;method&gt;(...);
 *     For example:  Horse.super.go();
 * </pre> So, Pegasus moves like a horse.
 */
public class DiamondInheritance {

    /**
     * Base interface to illustrate the diamond inheritance.
     *
     * @see DiamondInheritance
     */
    public interface Animal {

        /**
         * Return string representation of the "go" action for concrete animal
         *
         * @return string representation of the "go" action for concrete animal
         */
        String go();
    }

    /**
     * Interface to illustrate the diamond inheritance.
     *
     * @see DiamondInheritance
     */
    public interface Horse extends Animal {

        /**
         * Return string representation of the "go" action for horse
         *
         * @return string representation of the "go" action for horse
         */
        @Override
        default String go() {
            return this.getClass().getSimpleName() + " walks on four legs";
        }
    }

    /**
     * Interface to illustrate the diamond inheritance.
     *
     * @see DiamondInheritance
     */
    public interface Bird extends Animal {

        /**
         * Return string representation of the "go" action for bird
         *
         * @return string representation of the "go" action for bird
         */
        @Override
        default String go() {
            return this.getClass().getSimpleName() + " walks on two legs";
        }

        /**
         * Return string representation of the "fly" action for bird
         *
         * @return string representation of the "fly" action for bird
         */
        default String fly() {
            return "I can fly";
        }
    }

    /**
     * Class to illustrate the diamond inheritance. Pegasus must mix horse and
     * bird behavior.
     *
     * @see DiamondInheritance
     */
    public static class Pegasus implements Horse, Bird {

        /**
         * Return string representation of the "go" action for the fictitious
         * creature Pegasus
         *
         * @return string representation of the "go" action for the fictitious
         * creature Pegasus
         */
        @Override
        public String go() {
            return Horse.super.go();
        }
    }

    /**
     * Illustrate the behavior of the {@link Pegasus} class
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        System.out.println(new Pegasus().go());
    }
}
