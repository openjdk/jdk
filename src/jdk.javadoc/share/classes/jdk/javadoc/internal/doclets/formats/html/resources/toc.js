/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

const LABEL_ALPHA  = "${doclet.lexical}";
const LABEL_SOURCE = "${doclet.sort_to_source}";

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("toc-lexical-order-toggle");
  const toc = document.querySelector("nav.toc ol.toc-list");
  if (!btn || !toc) return;
  const img = btn.querySelector("img");

  const sections = Array.from(toc.children);

  const nestedMap = new Map();
  sections.forEach(section => {
    const subList = section.querySelector(":scope > ol.toc-list");
    if (subList) {
      nestedMap.set(subList, Array.from(subList.children));
    }
  });

  function reorder(mode) {
    toc.textContent = "";
    sections.forEach(li => toc.appendChild(li));

    nestedMap.forEach((originalChildren, subList) => {
      subList.textContent = "";
      if (mode === "alpha") {
        originalChildren
          .slice()
          .sort((a, b) =>
            a.textContent.trim()
             .localeCompare(b.textContent.trim(), undefined, {
               numeric: true,
               sensitivity: "base"
             })
          )
          .forEach(child => subList.appendChild(child));
      } else {
        originalChildren.forEach(child => subList.appendChild(child));
      }
    });

    const nextLabel = (mode === "alpha") ? LABEL_SOURCE : LABEL_ALPHA;
    btn.setAttribute("aria-label", nextLabel);
    if (img) img.alt = nextLabel;
    localStorage.setItem("toc-order-mode", mode);
  }

  reorder(localStorage.getItem("toc-order-mode") || "source");

  btn.addEventListener("click", () => {
    const newMode = (btn.getAttribute("aria-label") === LABEL_ALPHA)
                  ? "alpha"
                  : "source";
    reorder(newMode);
  });
});
