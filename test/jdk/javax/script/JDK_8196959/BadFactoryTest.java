/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8196959 8320712
 * @summary Verify that ScriptEngineManager can load BadFactory without throwing NPE
 * @build BadFactory BadFactoryTest
 * @run junit/othervm BadFactoryTest
 * @run junit/othervm -Djava.security.manager=allow BadFactoryTest
 */
public class BadFactoryTest {

    @Test
    public void scriptEngineManagerShouldLoadBadFactory() {
        // Check that ScriptEngineManager initializes even in the
        // presence of a ScriptEngineFactory returning nulls
        ScriptEngineManager m = new ScriptEngineManager();

        // Sanity check that ScriptEngineManager actually found the BadFactory
        Optional<ScriptEngineFactory> badFactory = m.getEngineFactories().stream()
                .filter(fac -> fac.getClass() == BadFactory.class)
                .findAny();
        assertTrue(badFactory.isPresent(), "BadFactory not found");
    }
}
