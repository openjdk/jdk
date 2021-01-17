/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8213766
 * @summary Assertion error in TypeAnnotations$TypeAnnotationPositions.resolveFrame
 * @run compile T8213766.java
 */

import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import static java.lang.annotation.ElementType.TYPE_USE;

public abstract class T8213766 {
    @Target({ TYPE_USE })
    public @interface TestAnnotation { }

    public abstract void get(final Function<Consumer<Runnable>, Object> function);

    public void buggyMethod() {
        get(consumer -> {
            new Runnable() {
                @Override
                public void run() {
                    consumer.accept(() -> {
                        final List<@TestAnnotation Object> buggyDeclaration;
                    });
                }
            };
            return null;
        });
    }
}
