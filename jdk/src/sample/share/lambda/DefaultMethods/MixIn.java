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

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * The example illustrates how to use the default method for mixin.
 * @see BuildType
 * @see Debuggable
 */
public class MixIn {

    /**
     * Implement this interface for a class that must be in debug print
     */
    public interface Debuggable {

        /**
         * Print the class name and all fields to a string. Uses reflection to
         * obtain and access fields of this object.
         *
         * @return the string formatted like the following: <pre>
         * State of the: &lt;Class Name&gt;
         * &lt;member name&gt; : &lt;value&gt;
         * ...
         * </pre>
         */
        default String toDebugString() {
            StringBuilder sb = new StringBuilder();
            sb.append("State of the: ").append(
                    this.getClass().getSimpleName()).append("\n");
            for (Class cls = this.getClass();
                    cls != null;
                    cls = cls.getSuperclass()) {
                for (Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        sb.append(f.getName()).append(" : ").
                                append(f.get(this)).append("\n");
                    } catch (IllegalAccessException e) {
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Sample exception class to demonstrate mixin. This enum inherits the
     * behavior of the {@link Debuggable}
     */
    public static enum BuildType implements Debuggable {

        BUILD(0, "-build"),
        PLAN(0, "-plan"),
        EXCLUDE(1, "-exclude"),
        TOTAL(2, "-total");

        private final int compareOrder;
        private final String pathSuffix;

        private BuildType(int compareOrder, String pathSuffix) {
            this.compareOrder = compareOrder;
            this.pathSuffix = pathSuffix;
        }

        public int getCompareOrder() {
            return compareOrder;
        }

        public String getPathSuffix() {
            return pathSuffix;
        }
    }

    /**
     * Illustrate the behavior of the MixClass
     *
     * @param args command-line arguments
     * @throws java.io.IOException internal demo error
     */
    public static void main(final String[] args) throws IOException {
        System.out.println(BuildType.BUILD.toDebugString());
    }
}
