/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
var intervalId;
var isScrolling = false;
var acceleration = 3;
var currentHunk = 0;
var numCallbacks = 0;

function getFrame(s) {
    for (var i = 0; i < parent.frames.length; i++) {
        var frame = parent.frames[i];
        if (frame.name === s) {
            return parent.frames[i];
        }
    }
}

function lastHunk() {
    return getFrame("newFrame").document.getElementById("eof").value;
}

function toHunk(frame, n) {
    frame.location.replace(frame.location.pathname + "#" + n);
    frame.scrollBy(0, -30);
    currentHunk = n;
}

function updateHunkDisplay(n) {
    var value = n;
    if (n == 0) {
        value = "BOF";
    } else if (n == lastHunk()) {
        value = "EOF";
    }
    document.getElementById("display").value = value;
}

function scrollToHunk(n) {
    updateHunkDisplay(n);
    toHunk(getFrame("oldFrame"), n)
    toHunk(getFrame("newFrame"), n)
}

function stopScrolling() {
    if (isScrolling) {
        clearInterval(intervalId);
        numCallbacks = 0;
        acceleration = 3;
    }
}

function scrollCallback() {
    var n = direction * acceleration;
    if (numCallbacks % 10 === 0) {
        acceleration *= 1.2;
    }
    getFrame("oldFrame").scrollBy(0, n);
    getFrame("newFrame").scrollBy(0, n);
    numCallbacks++;
}

function scrollUp() {
    isScrolling = true;
    direction = -1;
    intervalId = setInterval(scrollCallback, 10);
}

function scrollDown() {
    isScrolling = true;
    direction = 1;
    intervalId = setInterval(scrollCallback, 10);
}

function goToStart() {
    scrollToHunk(0);
}

function goToEnd() {
    scrollToHunk(lastHunk());
}

function goToPrevHunk() {
    var prev = currentHunk - 1;
    if (prev < 0) {
        prev = 0;
    }
    scrollToHunk(prev);
}

function goToNextHunk() {
    var next = currentHunk + 1;
    var last = lastHunk();
    if (next >= last) {
        next = last;
    }
    scrollToHunk(next);
}

function keydown(e) {
    // KeyboardEvent.which is deprecated but still supported by all major
    // browsers. Update this to e.g. KeyboardEvent.code once it gains broader
    // support.
    var key = String.fromCharCode(e.which);

    if (key === "k") {
        goToPrevHunk();
    } else if (key === "j" || key === " ") {
        goToNextHunk();
    }
}
