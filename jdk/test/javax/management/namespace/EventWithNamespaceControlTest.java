/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 *
 * @test EventWithNamespaceControlTest.java
 * @summary Check -Djmx.remote.use.event.service=true and
 *                -Djmx.remote.delegate.event.service
 * @author Daniel Fuchs
 * @bug 5072476 5108776
 * @run clean EventWithNamespaceTest EventWithNamespaceControlTest
 *            Wombat WombatMBean JMXRemoteTargetNamespace
 *            NamespaceController NamespaceControllerMBean
 * @compile -XDignore.symbol.file=true EventWithNamespaceTest.java
              EventWithNamespaceControlTest.java
 *            Wombat.java WombatMBean.java JMXRemoteTargetNamespace.java
 *            NamespaceController.java NamespaceControllerMBean.java
 * @run main/othervm -Djmx.remote.use.event.service=true EventWithNamespaceControlTest
 * @run main/othervm EventWithNamespaceControlTest
 * @run main/othervm -Djmx.remote.delegate.event.service=false EventWithNamespaceControlTest java.lang.UnsupportedOperationException
 */

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.management.RuntimeOperationsException;

/**
 *
 * @author Sun Microsystems, Inc.
 */
public class EventWithNamespaceControlTest extends EventWithNamespaceTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(EventWithNamespaceControlTest.class.getName());

    /** Creates a new instance of EventWithNamespaceTest */
    public EventWithNamespaceControlTest() {
    }



    public static void main(String[] args) {
        final  EventWithNamespaceControlTest test =
                new EventWithNamespaceControlTest();
        if (args.length == 0) {
            test.run(args);
            System.out.println("Test successfully passed");
        } else {
            try {
                test.run(args);
                throw new RuntimeException("Test should have failed.");
            } catch (RuntimeOperationsException x) {
                if (! args[0].equals(x.getCause().getClass().getName())) {
                    System.err.println("Unexpected wrapped exception: "+
                            x.getCause());
                    throw x;
                } else {
                    System.out.println("Got expected exception: "+x.getCause());
                }
            }
        }
    }

    @Override
    public Map<String, ?> getServerMap() {
        Map<String, ?> retValue = Collections.emptyMap();
        return retValue;
    }

}
