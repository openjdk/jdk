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
 * The sample illustrates the simplest use case of the <b>default methods</b>.
 */
public class SimplestUsage {

    /**
     * The Animal interface provides the default implementation
     * of the {@link #eat} method.
     */
    public interface Animal {

        /**
         * Return string representation of the eat action for Animal
         *
         * @return string representation of the eat action for Animal
         */
        default String eat() {
            return this.getClass().getSimpleName()
                    + " eats like an ordinary animal";
        }
    }

    /**
     * The Dog class doesn't have its own implementation of the {@link #eat}
     * method and uses the default implementation.
     */
    public static class Dog implements Animal {
    }

    /**
     * The Mosquito class implements {@link #eat} method, its own implementation
     * overrides the default implementation.
     *
     */
    public static class Mosquito implements Animal {

        /**
         * Return string representation of the eat action for Mosquito
         *
         * @return string representation of the eat action for Mosquito
         */
        @Override
        public String eat() {
            return "Mosquito consumes blood";
        }
    }

    /**
     * Illustrate behavior of the classes: {@link Dog} and {@link Mosquito}
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // "Dog eats like an ordinary animal" is output
        System.out.println(new Dog().eat());

        // "Mosquito consumes blood" is output
        System.out.println(new Mosquito().eat());
    }
}
