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

 /* Test helloworldjsr */

/*
 * @test
 * @library /test/lib
 * @summary Check that hello world with JSR and RET runs the same after instructions are replaced.
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          java.base/jdk.internal.vm
 * @compile helloworldjsr.jasm
 * @run main/othervm -Xverify:all helloworldjsrTest
 */

import java.lang.reflect.Method;
import java.io.File;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.internal.vm.Preverifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

/**
 * Modular test for jasm files that print on execution and do not throw errors
 */
public class testPatch {
    public static void test(String fileName, String expectedOutput) throws Throwable {
		Class<?> newClass;
		try {
        	newClass = Class.forName(fileName);
			Method m = newClass.getMethod("test");
			m.invoke(newClass.newInstance());
			byte[] newClassBytes = Preverifier.patch(
				new String[]{newClass.getProtectionDomain()
				.getCodeSource().getLocation().getPath() + fileName}
			);
			try {
        		Path tmpDir;
        		if (!Files.exists(Path.of("/tmp/preverifier/"))) {
        			tmpDir = Files.createDirectory(Path.of("/tmp/preverifier/"));	
        			try {
        				ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
							"/tmp/preverifier/" + fileName);
        				OutputAnalyzer output = new OutputAnalyzer(pb.start());
        				output.shouldContain(expectedOutput);
        			} catch (Exception e) {
            			System.out.println("Class not found");
        			}
        		}
        		else {
        			tmpDir = Path.of("/tmp/preverifier/");
        		}
        		Path tmpFile = Path.of(tmpDir.toString() + fileName + ".class");
        		Files.write(tmpFile, newClassBytes, 
					StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        	} catch (IOException e) {
            	throw new Error("Cannot write file", e);
        	}
		} catch (Exception e) {
			System.out.println("Class not found");
		}
    }
}
