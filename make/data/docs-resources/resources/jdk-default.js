/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

const THEME = "theme";
const THEME_LIGHT = "theme-light";
const THEME_DARK = "theme-dark";
const THEME_OS = "theme-os";
const UNDERLINE_LINKS = "underline-links";

const linkIconHref = new URL("link.svg", document.currentScript.src).href;
const linkIcon = "Link icon";
const linkToSection = "Link to this section";
const selectTheme = "Select Theme";
const lightThemeLabel = "Light";
const darkThemeLabel = "Dark";
const systemThemeLabel = "System Setting";
const underlineLinksLabel = "Underline links in text";

/*
 * Theme support
 */
function initTheme() {
    const template = document.createElement("template");
    template.innerHTML =
        `<li style="text-transform:none;">
            <button id="theme-button" aria-label="${selectTheme}" title="${selectTheme}"></button>
            <div id="theme-panel">
                <div class="panel-heading">${selectTheme}</div>
                <div>
                    <label for="theme-light"><input type="radio" id="theme-light" name="theme" value="theme-light"><span>${lightThemeLabel}</span></label>
                    <label for="theme-dark"><input type="radio" id="theme-dark" name="theme" value="theme-dark"><span>${darkThemeLabel}</span></label>
                    <label for="theme-os"><input type="radio" id="theme-os" name="theme" value="theme-os"><span>${systemThemeLabel}</span></label>
                </div>
                <div>
                    <label for="underline-links"><input type="checkbox" id="underline-links"><span>${underlineLinksLabel}</span></label>
                </div>
                <button id="theme-panel-close-button"></button>
            </div>
        </li>`;

    const themeUI = template.content.firstElementChild;
    const themeButton = themeUI.querySelector("button#theme-button");
    const themePanel = themeUI.querySelector("div#theme-panel");
    var themePanelVisible = false;
    const osDarkTheme = window.matchMedia("(prefers-color-scheme: dark)");
    let [currentTheme, underlineLinks] = getTheme();
    setTheme(currentTheme, underlineLinks);

    function getTheme() {
        return [
            localStorage.getItem(THEME) ?? THEME_LIGHT,
            localStorage.getItem(UNDERLINE_LINKS)
        ];
    }
    function setTheme(theme, underlineLinks) {
        if (theme === THEME_OS) {
            theme = osDarkTheme.matches ? THEME_DARK : THEME_LIGHT;
        }
        document.documentElement.setAttribute("data-theme", theme);
        if (underlineLinks) {
            document.documentElement.setAttribute("data-underline-links", underlineLinks);
        } else {
            themePanel.querySelector("input#underline-links").checked = (theme === THEME_DARK);
        }
    }
    osDarkTheme.addEventListener("change", e => {
        if (currentTheme === THEME_OS) {
            setTheme(currentTheme, underlineLinks);
        }
    });
    themePanel.querySelectorAll("input").forEach(input => {
        if (input.id === currentTheme) {
            input.checked = true;
        } else if (input.id === UNDERLINE_LINKS) {
            input.checked = (underlineLinks === "on" ||
                (!underlineLinks && document.documentElement.getAttribute("data-theme") === THEME_DARK));
            input.addEventListener("change", e => {
                underlineLinks = e.target.checked ? "on" : "off";
                localStorage.setItem(UNDERLINE_LINKS, underlineLinks);
                setTheme(currentTheme, underlineLinks);
            });
        }
        if (input.name === "theme") {
            input.addEventListener("change", e => {
                currentTheme = e.target.value;
                localStorage.setItem(THEME, e.target.value);
                setTheme(currentTheme, underlineLinks);
            });
        }
    });

    themeButton.addEventListener("click", e => {
        if (!themePanelVisible) {
            let {x, y} = themeButton.getBoundingClientRect();
            let expanded = x < y;
            themePanel.style.display = "block";
            if (document.documentElement.clientHeight - themePanel.offsetHeight < y) {
                themePanel.style.top = "";
                themePanel.style.bottom = "4px";
            } else {
                themePanel.style.top = y + (expanded ? 0 : 36) + "px";
                themePanel.style.bottom = "";
            }
            const desiredLeft = x + (expanded ? 36 : 0);
            const maxLeft = document.documentElement.clientWidth - themePanel.offsetWidth - 4;
            themePanel.style.left = Math.max(4, Math.min(desiredLeft, maxLeft)) + "px";

            themeButton.setAttribute("aria-expanded", "true");
            themePanelVisible = true;
            e.stopPropagation();
        }
    });

    function closeThemePanel() {
        if (themePanelVisible) {
            themePanel.style.removeProperty("display");
            themeButton.setAttribute("aria-expanded", "false");
            themePanelVisible = false;
        }
    }
    themeUI.querySelector("button#theme-panel-close-button")?.addEventListener("click", e => {
        closeThemePanel();
    });
    themePanel.addEventListener("focusout", e => {
        if (e.relatedTarget && !themePanel.contains(e.relatedTarget) && !themeButton.contains(e.relatedTarget)) {
            closeThemePanel();
        }
    });
    document.body.addEventListener("click", (e) => {
        if (e.target && !themePanel.contains(e.target)) {
            closeThemePanel();
        }
    });
    document.body.addEventListener("keydown", (e) => {
        if (e.key === "Escape" || e.key === "Enter") {
            closeThemePanel();
        }
    });
    document.currentScript.parentElement.appendChild(themeUI);
}

document.addEventListener("DOMContentLoaded", function(e) {

    document.querySelectorAll("h1, h2, h3, h4, h5, h6")
        .forEach((hdr, idx) => {
            // Create anchor links for headers with an associated id attribute
            const id = hdr.id
                || hdr.querySelector("a")?.id
                || (hdr.parentElement?.firstElementChild === hdr && hdr.parentElement.id);
            if (id) {
                const template = document.createElement('template');
                template.innerHTML = ` <a href='#${encodeURI(id)}' class='anchor-link' 
                        aria-label='${linkToSection}'><img src='${linkIconHref}' alt='${linkIcon}'
                        width='16' height='16'></a>`;
                hdr.append(...template.content.childNodes);
            }
        });

});
