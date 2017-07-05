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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * The code sample illustrates changes in the reflection API linked
 * <b>default methods</b>. Since Java SE 8, a new method is added into the class
 * <b><code>java.lang.reflect.Method</code></b>, with which you can reflectively
 * determine whether or not a default method provided by an interface
 * (<b><code>Method.isDefault()</code></b>).
 */
public class Reflection {

    /**
     * Base interface to illustrate the new reflection API.
     *
     * @see Dog
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

        /**
         * Return string representation of the sleep action for Animal
         *
         * @return string representation of the sleep action for Animal
         */
        default String sleep() {
            return this.getClass().getSimpleName()
                    + " sleeps like an ordinary animal";
        }

        /**
         * Return string representation of the go action for Animal
         *
         * @return string representation of the go action for Animal
         */
        String go();
    }

    /**
     * Dog class to illustrate the new reflection API. You can see that:
     * <ul>
     * <li> the {@link #go} and {@link #sleep} methods are not default.
     * {@link #go} is not the default implementation and the {@link #sleep}
     * method implementation wins as subtype (according with {@link Inheritance}
     * rule. 2) </li>
     * <li> the {@link #eat} is a simple default method that is not overridden
     * in this class.
     * </li>
     * </ul>
     */
    public static class Dog implements Animal {

        /**
         * Return string representation of the go action for Dog
         *
         * @return string representation of the go action for Dog
         */
        @Override
        public String go() {
            return "Dog walks on four legs";
        }

        /**
         * Return string representation of the sleep action for Dog
         *
         * @return string representation of the sleep action for Dog
         */
        @Override
        public String sleep() {
            return "Dog sleeps";
        }
    }

    /**
     * Illustrate the usage of the method java.lang.reflect.Method.isDefault()
     *
     * @param args command-line arguments
     * @throws NoSuchMethodException internal demo error
     */
    public static void main(final String[] args) throws NoSuchMethodException {
        Dog dog = new Dog();
        Stream.of(Dog.class.getMethod("eat"), Dog.class.getMethod("go"), Dog.class.getMethod("sleep"))
                .forEach((m) -> {
                    System.out.println("Method name:   " + m.getName());
                    System.out.println("    isDefault: " + m.isDefault());
                    System.out.print("    invoke:    ");
                    try {
                        m.invoke(dog);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    }
                    System.out.println();
                });
    }
}
