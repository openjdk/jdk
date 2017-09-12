/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.*;
import java.rmi.activation.*;

public class Doctor
    extends Activatable
    implements Eliza, Retireable
{
    // reanimation constructor
    public Doctor(ActivationID id, MarshalledObject blah)
        throws RemoteException
    {
        super(id, 0);   // export self on port 0 (== assign randomly)
        System.out.println("Doctor constructed and exported");
    }

    private boolean asked = false;

    // implement Eliza.complain()
    public String complain(String plaint)
    {
        System.out.println("Doctor will see you now");
        if (this.asked) {
            return ("DO GO ON?");
        } else {
            this.asked = true;
            return ("TELL ME ABOUT YOUR MOTHER");
        }
    }

    // implement Retireable.retire()
    public void retire()
    {
        System.out.println("Doctor retiring");
        try {
            Activatable.inactive(this.getID());
            ActivationGroup.getSystem().unregisterObject(this.getID());
            (new HaraKiri()).start();

        } catch (UnknownObjectException uoe) {
            System.err.println("Exception in Activatable.inactive:");
            uoe.printStackTrace();

        } catch (ActivationException ae) {
            System.err.println("Exception in Activatable.inactive:");
            ae.printStackTrace();

        } catch (RemoteException re) {
            System.err.println("Exception in Activatable.inactive:");
            re.printStackTrace();
        }
    }

    private static class HaraKiri extends Thread
    {
        public HaraKiri() {
            super("Thread-of-Death");
        }

        public void run()
        {
            try {
                Thread.sleep(5000);
            } catch (Exception foo) {
            }
            System.exit(0);
        }
    }
}
