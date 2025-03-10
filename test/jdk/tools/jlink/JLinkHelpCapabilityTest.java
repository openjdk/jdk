/*
 * Copyright (c) 2024, Red Hat, Inc.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;

import jdk.tools.jlink.internal.LinkableRuntimeImage;

/*
 * @test
 * @summary Test jlink --help for capability output
 * @modules jdk.jlink/jdk.tools.jlink.internal
 * @requires vm.compMode != "Xcomp"
 * @run main/othervm -Duser.language=en JLinkHelpCapabilityTest
 */
public class JLinkHelpCapabilityTest {
    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );

    public static void main(String[] args) throws Exception {
        boolean runtimeLinkCap = LinkableRuntimeImage.isLinkableRuntime();
        String capabilities = String.format("Linking from run-time image %s",
                                            runtimeLinkCap ? "enabled" : "disabled");
        {
            // Verify capability in --help output
            StringWriter writer = new StringWriter();
            PrintWriter pw = new PrintWriter(writer);
            JLINK_TOOL.run(pw, pw, "--help");
            String output = writer.toString().trim();
            String lines[] = output.split("\n");
            String capabilitiesMsg = null;
            boolean seenCap = false;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("Capabilities:")) {
                    seenCap = true;
                    continue; // skip 'Capabilities:'
                }
                if (!seenCap) {
                    continue;
                } else {
                    // Line after capabilities is the message we care about
                    capabilitiesMsg = lines[i].trim();
                    break;
                }
            }
            System.out.println("DEBUG: Capabilities:");
            System.out.println("DEBUG:   " + capabilitiesMsg);
            if (!capabilities.equals(capabilitiesMsg)) {
                System.err.println(output);
                throw new AssertionError("'--help': Capabilities mismatch. Expected: '" +
                                         capabilities +"' but got '" + capabilitiesMsg + "'");
            }
        }
    }
}
