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

package jdk.test.failurehandler;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Verifies that repeated and non-contiguous command names produce unique
 * section ids in the generated HTML, so every occurrence's output is
 * reachable (JDK-8337680).
 */
public class HtmlSectionTest {

    private static String generate(String... commands) throws Exception {
        Path dir = Files.createTempDirectory("HtmlSectionTest");
        HtmlPage page = new HtmlPage(dir, "processes.html", false);
        HtmlSection root = page.getRootSection();
        int run = 0;
        for (String command : commands) {
            HtmlSection s = root.createChildren(command.split("\\."));
            s.getWriter().println("MARKER-" + (++run));
        }
        root.close();
        page.close();
        return Files.readString(dir.resolve("processes.html"));
    }

    private static List<String> divIds(String html) {
        List<String> ids = new ArrayList<>();
        Matcher m = Pattern.compile("div id='([^']*)'").matcher(html);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static void assertUniqueIds(String html) {
        List<String> ids = divIds(html);
        Assert.assertEquals("duplicate section ids in: " + ids,
                ids.size(), new HashSet<>(ids).size());
    }

    // The id of the section whose opening tag most closely precedes the given
    // text; output is written right after its section is created, so this is
    // the section containing it.
    private static String sectionIdOf(String html, String text) {
        int at = html.indexOf(text);
        Assert.assertTrue("missing " + text, at >= 0);
        Matcher m = Pattern.compile("div id='([^']*)'").matcher(html.substring(0, at));
        String id = null;
        while (m.find()) {
            id = m.group(1);
        }
        Assert.assertNotNull("no section before " + text, id);
        return id;
    }

    @Test
    public void repeatedCommand() throws Exception {
        // JDK-8337680, first case: the same command run twice, separated by
        // another command.
        String html = generate("jhsdb.jstack.live.mixed",
                               "jinfo",
                               "jhsdb.jstack.live.mixed");
        assertUniqueIds(html);
        for (int i = 1; i <= 3; i++) {
            Assert.assertTrue("missing output of run " + i,
                    html.contains("MARKER-" + i));
        }
        // each occurrence's output is in its own section
        Assert.assertEquals("jhsdb.jstack.live.mixed", sectionIdOf(html, "MARKER-1"));
        Assert.assertNotEquals(sectionIdOf(html, "MARKER-1"), sectionIdOf(html, "MARKER-3"));
    }

    @Test
    public void tripleRepetition() throws Exception {
        String html = generate("jinfo", "jcmd", "jinfo", "jstack", "jinfo");
        assertUniqueIds(html);
        Assert.assertNotEquals(sectionIdOf(html, "MARKER-1"), sectionIdOf(html, "MARKER-3"));
        Assert.assertNotEquals(sectionIdOf(html, "MARKER-3"), sectionIdOf(html, "MARKER-5"));
        Assert.assertNotEquals(sectionIdOf(html, "MARKER-1"), sectionIdOf(html, "MARKER-5"));
    }

    @Test
    public void suffixCollisionWithRealName() throws Exception {
        // a command literally named "jcmd-2" must not collide with the
        // generated suffix for a repeated "jcmd"
        String html = generate("jcmd-2", "jcmd", "jinfo", "jcmd");
        assertUniqueIds(html);
        Assert.assertNotEquals(sectionIdOf(html, "MARKER-2"), sectionIdOf(html, "MARKER-4"));
    }

    @Test
    public void adjacentIdenticalCommandsShareSection() throws Exception {
        // adjacent identical commands reuse the open section (existing
        // behavior): one section, both outputs in it
        String html = generate("jinfo", "jinfo");
        assertUniqueIds(html);
        Assert.assertEquals(sectionIdOf(html, "MARKER-1"), sectionIdOf(html, "MARKER-2"));
    }

    @Test
    public void nonContiguousPrefix() throws Exception {
        // JDK-8337680, second case: the same top-level tool name used again
        // after an intervening command.
        String html = generate("jcmd.compiler.codecache",
                               "jinfo",
                               "jcmd.compiler.codelist");
        assertUniqueIds(html);
        for (int i = 1; i <= 3; i++) {
            Assert.assertTrue("missing output of run " + i,
                    html.contains("MARKER-" + i));
        }
    }

    @Test
    public void adjacentPrefixSharingPreserved() throws Exception {
        // Adjacent commands with a common prefix share the open sections;
        // their ids must remain the plain name path, without suffixes.
        String html = generate("jcmd.compiler.codecache",
                               "jcmd.compiler.codelist");
        List<String> ids = divIds(html);
        Assert.assertTrue(ids.contains("jcmd"));
        Assert.assertTrue(ids.contains("jcmd.compiler"));
        Assert.assertTrue(ids.contains("jcmd.compiler.codecache"));
        Assert.assertTrue(ids.contains("jcmd.compiler.codelist"));
        assertUniqueIds(html);
    }
}
