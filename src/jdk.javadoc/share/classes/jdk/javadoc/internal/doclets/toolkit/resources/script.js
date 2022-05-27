/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

var moduleSearchIndex;
var packageSearchIndex;
var typeSearchIndex;
var memberSearchIndex;
var tagSearchIndex;
function loadScripts(doc, tag) {
    createElem(doc, tag, 'search.js');

    createElem(doc, tag, 'module-search-index.js');
    createElem(doc, tag, 'package-search-index.js');
    createElem(doc, tag, 'type-search-index.js');
    createElem(doc, tag, 'member-search-index.js');
    createElem(doc, tag, 'tag-search-index.js');
}

function createElem(doc, tag, path) {
    var script = doc.createElement(tag);
    var scriptElement = doc.getElementsByTagName(tag)[0];
    script.src = pathtoroot + path;
    scriptElement.parentNode.insertBefore(script, scriptElement);
}

function show(tableId, selected, columns) {
    if (tableId !== selected) {
        document.querySelectorAll('div.' + tableId + ':not(.' + selected + ')')
            .forEach(function(elem) {
                elem.style.display = 'none';
            });
    }
    document.querySelectorAll('div.' + selected)
        .forEach(function(elem, index) {
            elem.style.display = '';
            var isEvenRow = index % (columns * 2) < columns;
            elem.classList.remove(isEvenRow ? oddRowColor : evenRowColor);
            elem.classList.add(isEvenRow ? evenRowColor : oddRowColor);
        });
    updateTabs(tableId, selected);
}

function updateTabs(tableId, selected) {
    document.querySelector('div#' + tableId +' .summary-table')
        .setAttribute('aria-labelledby', selected);
    document.querySelectorAll('button[id^="' + tableId + '"]')
        .forEach(function(tab, index) {
            if (selected === tab.id || (tableId === selected && index === 0)) {
                tab.className = activeTableTab;
                tab.setAttribute('aria-selected', true);
                tab.setAttribute('tabindex',0);
            } else {
                tab.className = tableTab;
                tab.setAttribute('aria-selected', false);
                tab.setAttribute('tabindex',-1);
            }
        });
}

function switchTab(e) {
    var selected = document.querySelector('[aria-selected=true]');
    if (selected) {
        if ((e.keyCode === 37 || e.keyCode === 38) && selected.previousSibling) {
            // left or up arrow key pressed: move focus to previous tab
            selected.previousSibling.click();
            selected.previousSibling.focus();
            e.preventDefault();
        } else if ((e.keyCode === 39 || e.keyCode === 40) && selected.nextSibling) {
            // right or down arrow key pressed: move focus to next tab
            selected.nextSibling.click();
            selected.nextSibling.focus();
            e.preventDefault();
        }
    }
}

var updateSearchResults = function() {};

function indexFilesLoaded() {
    return moduleSearchIndex
        && packageSearchIndex
        && typeSearchIndex
        && memberSearchIndex
        && tagSearchIndex;
}
// Copy the contents of the local snippet to the clipboard
function copySnippet(button) {
    copyToClipboard(button.nextElementSibling.innerText);
    switchCopyLabel(button.firstElementChild, button.parentElement);
}
// Copy the link to the adjacent header to the clipboard
function copyUrl(button) {
    var id;
    var header = button.parentElement;
    if (header.hasAttribute("id")) {
        id = header.getAttribute("id");
    } else if (header.parentElement.tagName === 'SECTION' && header.parentElement.hasAttribute("id")) {
        id = header.parentElement.getAttribute("id");
    } else if (header.firstElementChild && header.firstElementChild.tagName === "A"
                                        && header.firstElementChild.hasAttribute("id")) {
        id = header.firstElementChild.getAttribute("id");
    }
    var url = document.location.href;
    if (url.indexOf("#") > -1) {
        url = url.substring(0, url.indexOf("#"));
    }
    copyToClipboard(url + "#" + id);
    switchCopyLabel(button.lastElementChild, button.parentElement);
}
function copyToClipboard(content) {
    var textarea = document.createElement("textarea");
    textarea.style.height = 0;
    document.body.appendChild(textarea);
    textarea.value = content;
    textarea.select();
    document.execCommand("copy");
    document.body.removeChild(textarea);
}
function switchCopyLabel(span, parent) {
    var copied = span.getAttribute("data-copied");
    if (span.innerHTML !== copied) {
        var initialLabel = span.innerHTML;
        span.innerHTML = copied;
        parent.onmouseleave = parent.ontouchend = function() {
            span.innerHTML = initialLabel;
        };
    }
}
// Workaround for scroll position not being included in browser history (8249133)
document.addEventListener("DOMContentLoaded", function(e) {
    var contentDiv = document.querySelector("div.flex-content");
    window.addEventListener("popstate", function(e) {
        if (e.state !== null) {
            contentDiv.scrollTop = e.state;
        }
    });
    window.addEventListener("hashchange", function(e) {
        history.replaceState(contentDiv.scrollTop, document.title);
    });
    var timeoutId;
    contentDiv.addEventListener("scroll", function(e) {
        if (timeoutId) {
            clearTimeout(timeoutId);
        }
        timeoutId = setTimeout(function() {
            history.replaceState(contentDiv.scrollTop, document.title);
        }, 100);
    });
    if (!location.hash) {
        history.replaceState(contentDiv.scrollTop, document.title);
    }
});
