/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class HtmlPage implements AutoCloseable {
    static final String STYLE_SHEET_FILENAME = "failure-handler-style.css";
    static final String SCRIPT_FILENAME = "failure-handler-script.js";

    private final PrintWriter writer;
    private final HtmlSection rootSection;

    /**
     * Constructs a {@code HtmlPage}
     *
     * @param dir          The directory into which the HTML file and related resources will be created
     * @param htmlFileName The HTML file name
     * @param append       if {@code true} then the content will be appended to the file represented
     *                     by the {@code htmlFileName}, else the {@code htmlFileName} will be overwritten
     *                     with the new content
     * @throws IllegalArgumentException if {@code dir} is not a directory or if the
     *                                  {@code htmlFileName} is {@linkplain String#isBlank() blank}
     * @throws IOException if there is an error constructing file resource(s) for this HTML page
     */
    public HtmlPage(final Path dir, final String htmlFileName, final boolean append)
            throws IOException {
        Objects.requireNonNull(dir, "directory cannot be null");
        Objects.requireNonNull(htmlFileName, "HTML file name cannot be null");
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
        if (htmlFileName.isBlank()) {
            throw new IllegalArgumentException("HTML file name cannot be blank");
        }
        final FileWriter fileWriter = new FileWriter(dir.resolve(htmlFileName).toFile(), append);
        this.writer = new PrintWriter(fileWriter, true);
        createScriptFile(dir);
        createStyleSheetFile(dir);
        rootSection = new HtmlSection(writer);
    }


    @Override
    public void close() {
        writer.close();
    }

    public HtmlSection getRootSection() {
        return rootSection;
    }

    private static void createStyleSheetFile(final Path destDir) throws IOException {
        final Path styleSheet = destDir.resolve(STYLE_SHEET_FILENAME);
        if (Files.exists(styleSheet)) {
            return;
        }
        final String content = """
                div { display:none;}
                """;
        Files.writeString(styleSheet, content);
    }

    private static void createScriptFile(final Path destDir) throws IOException {
        final Path script = destDir.resolve(SCRIPT_FILENAME);
        if (Files.exists(script)) {
            return;
        }
        final String content = """
                function doShow(e) {
                  while (e != null) {
                    if (e.tagName == 'DIV') {
                      e.style.display = 'block';
                    }
                    e = e.parentNode;
                  }
                }

                function showHandler(event) {
                  elementId = this.dataset.show;
                  elementToShow = document.getElementById(elementId);
                  doShow(elementToShow);
                }

                function toggleHandler(event) {
                  toggleElementId = this.dataset.toggle;
                  elementToToggle = document.getElementById(toggleElementId);
                  d = elementToToggle.style.display;
                  if (d == 'block') {
                    elementToToggle.style.display = 'none';
                  } else {
                    doShow(elementToToggle);
                  }
                }

                function bodyLoadHandler() {
                  const index = location.href.indexOf("#");
                  if (index != -1) {
                    doShow(document.getElementById(location.href.substring(index + 1)));
                  }
                  // elements that require the "toggleHandler" function to be registered
                  // as an event handler for the onclick event
                  const requiringToggleHandler = document.querySelectorAll("[data-toggle]");
                  for (const e of requiringToggleHandler) {
                    e.addEventListener("click", toggleHandler);
                  }
                  // elements that require the "showHandler" function to be registered
                  // as an event handler for the onclick event
                  const requiringShowHandler = document.querySelectorAll("[data-show]");
                  for (const e of requiringShowHandler) {
                    e.addEventListener("click", showHandler);
                  }
                }
                // register a onload event handler
                window.addEventListener("DOMContentLoaded", bodyLoadHandler);
                """;
        Files.writeString(script, content);
    }
}
