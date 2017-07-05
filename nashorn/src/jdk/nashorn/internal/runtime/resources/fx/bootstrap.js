/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

// Check for fx presence.
if (typeof javafx.application.Application != "function") {
    print("JavaFX is not available.");
    exit(1);
}

// Extend the javafx.application.Application class overriding init, start and stop.
com.sun.javafx.application.LauncherImpl.launchApplication((Java.extend(javafx.application.Application, {
    // Overridden javafx.application.Application.init();
    init: function() {
        // Java FX packages and classes must be defined here because
        // they may not be viable until launch time due to clinit ordering.
    },

    // Overridden javafx.application.Application.start(Stage stage);
    start: function(stage) {
        // Set up stage global.
        $STAGE = stage;

        // Load user FX scripts.
        for each (var script in $SCRIPTS) {
            load(script);
        }

        // Call the global init function if present.
        if ($GLOBAL.init) {
            init();
        }

        // Call the global start function if present.  Otherwise show the stage.
        if ($GLOBAL.start) {
            start(stage);
        } else {
            stage.show();
        }
    },

    // Overridden javafx.application.Application.stop();
    stop: function() {
        // Call the global stop function if present.
        if ($GLOBAL.stop) {
            stop();
        }
    }

    // No arguments passed to application (handled thru $ARG.)
})).class, new (Java.type("java.lang.String[]"))(0));
