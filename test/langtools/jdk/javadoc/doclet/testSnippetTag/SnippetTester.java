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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.ObjIntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class SnippetTester extends JavadocTester {

    protected final ToolBox tb = new ToolBox();

    protected void checkOrder(Output output, String... strings) {
        new OutputChecker(output).setExpectOrdered(true).check(strings);
    }

    /*
     * When checking for errors, it is important not to confuse one error with
     * another. This method checks that there are no crashes (which are also
     * errors) by checking for stack traces. We never expect crashes.
     */
    protected void checkNoCrashes() {
        checking("check crashes");
        Matcher matcher = Pattern.compile("\\s*at.*\\(.*\\.java:\\d+\\)")
                .matcher(getOutput(Output.STDERR));
        if (!matcher.find()) {
            passed("");
        } else {
            failed("Looks like a stacktrace: " + matcher.group());
        }
    }

    /*
     * This is a convenience method to iterate through a list.
     * Unlike List.forEach, this method provides the consumer not only with an
     * element but also that element's index.
     *
     * See JDK-8184707.
     */
    protected static <T> void forEachNumbered(List<T> list, ObjIntConsumer<? super T> action) {
        for (var iterator = list.listIterator(); iterator.hasNext(); ) {
            action.accept(iterator.next(), iterator.previousIndex());
        }
    }

    // TODO:
    //   Explore the toolbox.ToolBox.writeFile and toolbox.ToolBox.writeJavaFiles methods:
    //   see if any of them could be used instead of this one
    protected static void addSnippetFile(Path srcDir, String packageName, String fileName, String content)
            throws UncheckedIOException
    {
        String[] components = packageName.split("\\.");
        Path snippetFiles = Path.of(components[0], Arrays.copyOfRange(components, 1, components.length)).resolve("snippet-files");
        try {
            Path p = Files.createDirectories(srcDir.resolve(snippetFiles));
            Files.writeString(p.resolve(fileName), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected void checkOutputEither(Output out, String first, String... other) {
        var strings = Stream.concat(Stream.of(first), Stream.of(other))
                .toArray(String[]::new);
        new OutputChecker(out).checkAnyOf(strings);
    }

    protected String getSnippetHtmlRepresentation(String pathToHtmlFile,
                                                  String content) {
        return getSnippetHtmlRepresentation(pathToHtmlFile, content, Optional.empty(), Optional.empty());
    }

    protected String getSnippetHtmlRepresentation(String pathToHtmlFile,
                                                  String content,
                                                  Optional<String> lang) {
        return getSnippetHtmlRepresentation(pathToHtmlFile, content, lang, Optional.empty());
    }

    protected String getSnippetHtmlRepresentation(String pathToHtmlFile,
                                                  String content,
                                                  Optional<String> lang,
                                                  Optional<String> id) {
        // the further away from the root, the further to reach to common resources
        int nComponents = (int) pathToHtmlFile.chars().filter(c -> c == '/').count();
        var svgString = "../".repeat(nComponents) + "copy.svg";
        var idString = id.isEmpty() ? "" : " id=\"%s\"".formatted(id.get());
        var langString = lang.isEmpty() ? "" : " class=\"language-%s\"".formatted(lang.get());
        return """
                <div class="snippet-container"><button class="snippet-copy" onclick="copySnippet(this)">\
                <span data-copied="Copied!">Copy</span><img src="%s" alt="Copy"></button>
                <pre class="snippet"%s><code%s>%s</code></pre>
                </div>""".formatted(svgString, idString, langString, content);
    }
}
