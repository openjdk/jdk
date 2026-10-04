/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify enhanced variable declarations when sealed hierarchy changes after compilation.
 * @enablePreview
 * @compile EnhancedVariableDeclSealedTypeChanges.java
 * @compile EnhancedVariableDeclSealedTypeChanges2.java
 * @run main EnhancedVariableDeclSealedTypeChanges
 */

import java.util.function.Consumer;

public class EnhancedVariableDeclSealedTypeChanges {
    public static void main(String... args) throws Exception {
        new EnhancedVariableDeclSealedTypeChanges().run();
    }

    void run() throws Exception {
        doRun(this::enhancedVariableDeclStatement);
        doRun(this::enhancedVariableDeclStatementNoComponentAccess);
    }

    <T> void doRun(Consumer<T> consumer) throws Exception {
        consumer.accept((T) new A(1, 2));

        try {
            consumer.accept((T) Class.forName("EnhancedVariableDeclSealedTypeChangesClass")
                                     .getDeclaredConstructor()
                                     .newInstance());
            throw new AssertionError("Expected an exception, but none thrown.");
        } catch (Throwable ex) {
            validateMatchException(ex);
        }
    }

    void validateMatchException(Throwable t) {
        if (!(t instanceof MatchException)) {
            throw new AssertionError("Unexpected exception kind", t);
        }
    }

    void enhancedVariableDeclStatement(EnhancedVariableDeclSealedTypeChangesIntf obj) {
        A(Integer x, Integer y) = obj;
        if (x + y < 0) {
            throw new AssertionError("unreachable");
        }
    }

    void enhancedVariableDeclStatementNoComponentAccess(EnhancedVariableDeclSealedTypeChangesIntf obj) {
        A(var x, var y) = obj;
        if (obj == null) {
            throw new AssertionError("unreachable");
        }
    }

    record A(Integer x, Integer y) implements EnhancedVariableDeclSealedTypeChangesIntf {}
}

sealed interface EnhancedVariableDeclSealedTypeChangesIntf
        permits EnhancedVariableDeclSealedTypeChanges.A {}
