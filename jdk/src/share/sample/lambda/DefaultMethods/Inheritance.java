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
 * The sample illustrates rules to resolve conflicts between inheritance
 * candidates with <b>default methods</b>. There are two simple rules:
 * <ul>
 * <li>Class wins. If the superclass has a concrete or abstract declaration of
 * this method, then it is preferred over all defaults.</li>
 * <li>Subtype wins. If an interface extends another interface, and both provide
 * a default, then the more specific interface wins. </li>
 * </ul>
 */
public class Inheritance {

    /**
     * The behavior of an creature that can swim
     */
    public interface Swimable {

        /**
         * Return string representation of the swim action for a creature that
         * can swim
         *
         * @return string representation of the swim action for a creature
         * that can swim
         */
        default String swim() {
            return "I can swim.";
        }
    }

    /**
     * The abstract class that overrides {@link #swim} method
     */
    public abstract static class Fish implements Swimable {

        /**
         * Return string representation of the swim action for a fish
         *
         * @return string representation of the swim action for a fish
         */
        @Override
        public String swim() {
            return this.getClass().getSimpleName() + " swims under water";
        }
    }

    /**
     * This class is used for the illustration rule of 1. See the source code
     * of the {@link #main} method.
     * <pre>
     *      System.out.println(new Tuna().swim()); //"Tuna swims under water" output is suspected here
     * </pre>
     */
    public static class Tuna extends Fish implements Swimable {
    }

    /**
     * The behavior of an creature that can dive: the interface that overrides
     * {@link #swim} method (subtype of {@link Swimable})
     */
    public interface Diveable extends Swimable {

        /**
         * Return string representation of the swim action for a creature that
         * can dive
         *
         * @return string representation of the swim action for a creature
         * that can dive
         */
        @Override
        default String swim() {
            return "I can swim on the surface of the water.";
        }

        /**
         * Return string representation of the dive action for a creature that
         * can dive
         *
         * @return string representation of the dive action for a creature
         * that can dive
         */
        default String dive() {
            return "I can dive.";
        }
    }

    /**
     * This class is used for the illustration of rule 2. See the source code
     * of the {@link #main} method
     * <pre>
     *      //"I can swim on the surface of the water." output is suspected here
     *      System.out.println(new Duck().swim());
     * </pre>
     */
    public static class Duck implements Swimable, Diveable {
    }

    /**
     * Illustrate behavior of the classes: {@link Tuna} and {@link Duck}
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        // Illustrates rule 1. The Fish.swim() implementation wins
        //"Tuna swims under water" is output
        System.out.println(new Tuna().swim());

        // Illustrates rule 2. The Diveable.swim() implementation wins
        //"I can swim on the surface of the water." is output
        System.out.println(new Duck().swim());
    }
}
