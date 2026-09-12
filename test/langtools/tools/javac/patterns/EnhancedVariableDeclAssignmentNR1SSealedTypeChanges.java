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
 * @summary Verify variable declarations for the assignment case when sealed hierarchy changes after compilation.
 * @enablePreview
 * @compile EnhancedVariableDeclAssignmentNR1SSealedTypeChanges.java
 * @compile EnhancedVariableDeclAssignmentNR1SSealedTypeChanges2.java
 * @run main EnhancedVariableDeclAssignmentNR1SSealedTypeChanges
 */

import java.util.function.Consumer;

public class EnhancedVariableDeclAssignmentNR1SSealedTypeChanges {
    public static void main(String... args) throws Exception {
        new EnhancedVariableDeclAssignmentNR1SSealedTypeChanges().run();
    }

    void run() throws Exception {
        doRun(this::enhancedVariableDeclAssignmentNR1SStatement);
        doRun(this::enhancedVariableDeclAssignmentNR1SStatementNoComponentAccess);
    }

    <T> void doRun(Consumer<T> consumer) throws Exception {
        consumer.accept((T) new A(1, 2));

        try {
            consumer.accept((T) Class.forName("EnhancedVariableDeclAssignmentNR1SSealedTypeChangesClass")
                                     .getDeclaredConstructor()
                                     .newInstance());
            throw new AssertionError("Expected an exception, but none thrown.");
        } catch (Throwable ex) {
            validateCCE(ex);
        }
    }

    void validateCCE(Throwable t) {
        if (!(t instanceof ClassCastException)) {
            throw new AssertionError("Unexpected exception kind", t);
        }
    }

    void enhancedVariableDeclAssignmentNR1SStatement(EnhancedVariableDeclAssignmentNR1SSealedTypeChangesIntf obj) {
        A a = obj;
        if (a.x() + a.y() < 0) {
            throw new AssertionError("unreachable");
        }
    }

    void enhancedVariableDeclAssignmentNR1SStatementNoComponentAccess(EnhancedVariableDeclAssignmentNR1SSealedTypeChangesIntf obj) {
        A a = obj;
        if (obj == null) {
            throw new AssertionError("unreachable");
        }
    }

    record A(Integer x, Integer y) implements EnhancedVariableDeclAssignmentNR1SSealedTypeChangesIntf {}
}

sealed interface EnhancedVariableDeclAssignmentNR1SSealedTypeChangesIntf
        permits EnhancedVariableDeclAssignmentNR1SSealedTypeChanges.A {}
