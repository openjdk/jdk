/*
* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that parsing a hidden class with max constant pool entries
 *          doesn't crash the VM when adding an entry for the hidden class name.
 *          Instead throws ClassFormatError.
 * @bug 8364360
 * @modules java.base/jdk.internal.access
 *          java.base/jdk.internal.reflect
 * @library /testlibrary/asm
 * @run main HiddenClassesTest
 */

import java.lang.invoke.MethodHandles;

import org.objectweb.asm.ClassWriter;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V17;

public class HiddenClassesTest {

    public static void main(String[] args) throws Exception {
        var cw = new ClassWriter(0);
        cw.visit(V17, ACC_PUBLIC, "Hidden", null, "java/lang/Object", null);
        // This magic number causes a constant pool index overflow with this asm generated class.
        int i = 0;
        while (i < 65535-1) {
            i = cw.newUTF8(Integer.toString(i));
        }
        try {
            MethodHandles.lookup().defineHiddenClass(cw.toByteArray(), false);
            throw new RuntimeException("Test Failed: ClassFormatError expected.");
        } catch (ClassFormatError cfe) {
            String message = cfe.getMessage();
            if (message == null || !message.contains("Overflow in constant pool size for hidden class")) {
                throw new RuntimeException("Test Failed: wrong ClassFormatError " + message);
            }
            System.out.println("ClassFormatError thrown as expected. Message: " + cfe.getMessage());
        }
    }
}
