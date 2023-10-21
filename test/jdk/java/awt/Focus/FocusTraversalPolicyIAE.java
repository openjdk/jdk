/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 6225100
  @summary FocusTraversalPolicy.getInitialComponent does not work as expected
  @run main FocusTraversalPolicyIAE
*/

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;

public class FocusTraversalPolicyIAE {
    public static void main(String[] args) {
        CustomFocusTraversalPolicy cftp = new CustomFocusTraversalPolicy();
        try {
            cftp.getInitialComponent(null);
            throw new RuntimeException("Test failed. No exceptions thrown.");
        } catch (IllegalArgumentException iae) {
            System.out.println("Test passed.");
        } catch (NullPointerException npe) {
            throw new RuntimeException("Test failed. Unexpected NPE thrown: " + npe);
        } catch (Exception e) {
            throw new RuntimeException("Test failed. Unexpected exception thrown: " + e);
        }
    }
}

class CustomFocusTraversalPolicy extends FocusTraversalPolicy {
    public Component getComponentAfter(Container focusCycleRoot,
                                       Component aComponent) {
        return null;
    }

    public Component getComponentBefore(Container focusCycleRoot,
                                        Component aComponent) {
        return null;
    }

    public Component getDefaultComponent(Container focusCycleRoot) {
        return null;
    }

    public Component getFirstComponent(Container focusCycleRoot) {
        return null;
    }

    public Component getLastComponent(Container focusCycleRoot) {
        return null;
    }
}
