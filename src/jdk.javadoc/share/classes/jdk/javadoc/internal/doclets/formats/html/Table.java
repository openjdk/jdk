/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;

/**
 * An HTML container used to display summary tables for various kinds of elements
 * and other tabular data.
 * This class historically used to generate an HTML {@code <table>} element but has been
 * updated to render its content as a stream of {@code <div>} elements that rely on
 * <a href="https://www.w3.org/TR/css-grid-1/">CSS Grid Layout</a> for styling.
 * This provides for more flexible layout options, such as splitting up table rows on
 * small displays.
 *
 * <p>The table should be used in three phases:
 * <ol>
 * <li>Configuration: the overall characteristics of the table should be specified
 * <li>Population: the content for the cells in each row should be added
 * <li>Generation: the HTML content and any associated JavaScript can be accessed
 * </ol>
 *
 * Many methods return the current object, to facilitate fluent builder-style usage.
 *
 * A table may support filtered views, which can be selected by clicking on
 * one of a list of tabs above the table. If the table does not support filtered
 * views, the caption element is typically displayed as a single (inactive)
 * tab.   The filtered views use a {@link Predicate} to identify the
 * rows to be shown for each {@link #addTab(Content, Predicate) tab}. The
 * type parameter for the predicate is the type parameter {@code T} for the table.
 * The type parameter should be {@link Void} when the table is not configured
 * to use tabs.
 *
 * @param <T> the class or interface used to distinguish the rows to be displayed
 *            for each tab, or {@code Void} when a table does not contain tabs
 */
public class Table<T> extends Content {
    private final HtmlStyle tableStyle;
    private Content caption;
    private List<Tab<T>> tabs;
    private Set<Tab<T>> occurringTabs;
    private Content defaultTab;
    private boolean renderTabs = true;
    private TableHeader header;
    private List<HtmlStyle> columnStyles;
    private HtmlStyle gridStyle;
    private final List<Content> bodyRows;
    private HtmlId id;
    private boolean alwaysShowDefaultTab = false;

    /**
     * A record containing the data for a table tab.
     */
    record Tab<T>(Content label, Predicate<T> predicate, int index) {}

    /**
     * Creates a builder for an HTML element representing a table.
     *
     * @param tableStyle the style class for the top-level {@code <div>} element
     */
    public Table(HtmlStyle tableStyle) {
        this.tableStyle = tableStyle;
        bodyRows = new ArrayList<>();
    }

    /**
     * Sets the caption for the table.
     * This is ignored if the table is configured to provide tabs to select
     * different subsets of rows within the table.
     *
     * @param captionContent the caption
     * @return this object
     */
    public Table<T> setCaption(Content captionContent) {
        caption = getCaption(captionContent);
        return this;
    }

    /**
     * Adds a tab to the table.
     * Tabs provide a way to display subsets of rows, as determined by a
     * predicate for the tab, and an item associated with each row.
     * Tabs will appear left-to-right in the order they are added.
     *
     * @param label     the tab label
     * @param predicate the predicate
     * @return this object
     */
    public Table<T> addTab(Content label, Predicate<T> predicate) {
        if (tabs == null) {
            tabs = new ArrayList<>();         // preserves order that tabs are added
            occurringTabs = new HashSet<>();  // order not significant
        }
        // Use current size of tabs list as id so we have tab ids that are consistent
        // across tables with the same tabs but different content.
        tabs.add(new Tab<>(label, predicate, tabs.size() + 1));
        return this;
    }

    /**
     * Sets the label for the default tab, which displays all the rows in the table.
     * This tab will appear first in the left-to-right list of displayed tabs.
     *
     * @param label the default tab label
     * @return this object
     */
    public Table<T> setDefaultTab(Content label) {
        defaultTab = label;
        return this;
    }

    /**
     * Sets whether to display the default tab even if tabs are empty or only contain a single tab.
     * @param showDefaultTab true if default tab should always be shown
     * @return this object
     */
    public Table<T> setAlwaysShowDefaultTab(boolean showDefaultTab) {
        this.alwaysShowDefaultTab = showDefaultTab;
        return this;
    }

    /**
     * Allows to set whether tabs should be rendered for this table. Some pages use their
     * own controls to select table categories, in which case the tabs are omitted.
     *
     * @param renderTabs true if table tabs should be rendered
     * @return this object
     */
    public Table<T> setRenderTabs(boolean renderTabs) {
        this.renderTabs = renderTabs;
        return this;
    }

    /**
     * Sets the header for the table.
     *
     * <p>Notes:
     * <ul>
     * <li>The column styles are not currently applied to the header, but probably should, eventually
     * </ul>
     *
     * @param header the header
     * @return this object
     */
    public Table<T> setHeader(TableHeader header) {
        this.header = header;
        return this;
    }

    /**
     * Sets the styles for be used for the cells in each row.
     *
     * <p>Note:
     * <ul>
     * <li>The column styles are not currently applied to the header, but probably should, eventually
     * </ul>
     *
     * @param styles the styles
     * @return this object
     */
    public Table<T> setColumnStyles(HtmlStyle... styles) {
        return setColumnStyles(Arrays.asList(styles));
    }

    /**
     * Sets the styles for be used for the cells in each row.
     *
     * <p>Note:
     * <ul>
     * <li>The column styles are not currently applied to the header, but probably should, eventually
     * </ul>
     *
     * @param styles the styles
     * @return this object
     */
    public Table<T> setColumnStyles(List<HtmlStyle> styles) {
        columnStyles = styles;
        return this;
    }

    /**
     * Sets the style for the table's grid which controls allocation of space among table columns.
     * The style should contain a {@code display: grid;} property and its number of columns must
     * match the number of column styles and content passed to other methods in this class.
     *
     * @param gridStyle the grid style
     * @return this object
     */
    public Table<T> setGridStyle(HtmlStyle gridStyle) {
        this.gridStyle = gridStyle;
        return this;
    }

    /**
     * Sets the id attribute of the table.
     * This is required if the table has tabs, in which case a subsidiary id
     * will be generated for the tabpanel. This subsidiary id is required for
     * the ARIA support.
     *
     * @param id the id
     * @return this object
     */
    public Table<T> setId(HtmlId id) {
        this.id = id;
        return this;
    }

    /**
     * Adds a row of data to the table.
     * Each item of content should be suitable for use as the content of a
     * {@code <th>} or {@code <td>} cell.
     * This method should not be used when the table has tabs: use a method
     * that takes an {@code Element} parameter instead.
     *
     * @param contents the contents for the row
     */
    public void addRow(Content... contents) {
        addRow(null, Arrays.asList(contents));
    }

    /**
     * Adds a row of data to the table.
     * Each item of content should be suitable for use as the content of a
     * {@code <th>} or {@code <td> cell}.
     * This method should not be used when the table has tabs: use a method
     * that takes an {@code item} parameter instead.
     *
     * @param contents the contents for the row
     */
    public void addRow(List<Content> contents) {
        addRow(null, contents);
    }

    /**
     * Adds a row of data to the table.
     * Each item of content should be suitable for use as the content of a
     * {@code <th>} or {@code <td>} cell.
     *
     * If tabs have been added to the table, the specified item will be used
     * to determine whether the row should be displayed when any particular tab
     * is selected, using the predicate specified when the tab was
     * {@link #addTab(Content, Predicate) added}.
     *
     * @param item the item
     * @param contents the contents for the row
     * @throws NullPointerException if tabs have previously been added to the table
     *      and {@code item} is null
     */
    public void addRow(T item, Content... contents) {
        addRow(item, Arrays.asList(contents));
    }

    /**
     * Adds a row of data to the table.
     * Each item of content should be suitable for use as the content of a
     * {@code <div>} cell.
     *
     * If tabs have been added to the table, the specified item will be used
     * to determine whether the row should be displayed when any particular tab
     * is selected, using the predicate specified when the tab was
     * {@link #addTab(Content, Predicate) added}.
     *
     * @param item the item
     * @param contents the contents for the row
     * @throws NullPointerException if tabs have previously been added to the table
     *      and {@code item} is null
     */
    public void addRow(T item, List<Content> contents) {
        if (tabs != null && item == null) {
            throw new NullPointerException();
        }
        if (contents.size() != columnStyles.size()) {
            throw new IllegalArgumentException("row content size does not match number of columns");
        }

        Content row = new ContentBuilder();

        int rowIndex = bodyRows.size();
        HtmlStyle rowStyle = rowIndex % 2 == 0 ? HtmlStyle.evenRowColor : HtmlStyle.oddRowColor;

        List<String> tabClasses = new ArrayList<>();
        if (tabs != null) {
            // Construct a series of values to add to the HTML 'class' attribute for the cells of
            // this row, such that there is a default value and a value corresponding to each tab
            // whose predicate matches the item. The values correspond to the equivalent ids.
            // The values are used to determine the cells to make visible when a tab is selected.
            tabClasses.add(id.name());
            for (var tab : tabs) {
                if (tab.predicate().test(item)) {
                    occurringTabs.add(tab);
                    tabClasses.add(HtmlIds.forTab(id, tab.index()).name());
                }
            }
        }
        int colIndex = 0;
        for (Content c : contents) {
            HtmlStyle cellStyle = columnStyles.get(colIndex);
            // Always add content to make sure the cell isn't dropped
            var cell = HtmlTree.DIV(cellStyle).addUnchecked(c.isEmpty() ? Text.EMPTY : c);
            cell.addStyle(rowStyle);

            for (String tabClass : tabClasses) {
                cell.addStyle(tabClass);
            }
            row.add(cell);
            colIndex++;
        }
        bodyRows.add(row);
    }

    /**
     * Returns whether the table is empty.
     * The table is empty if it has no (body) rows.
     *
     * @return true if the table has no rows
     */
    @Override
    public boolean isEmpty() {
        return bodyRows.isEmpty();
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        return toContent().write(out, newline, atNewline);
    }

    /**
     * Returns the HTML for the table.
     *
     * @return the HTML
     */
    private Content toContent() {
        Content main;
        if (id != null) {
            main = new HtmlTree(TagName.DIV).setId(id);
        } else {
            main = new ContentBuilder();
        }
        // If no grid style is set use on of the default styles
        if (gridStyle == null) {
            gridStyle = switch (columnStyles.size()) {
                case 2 -> HtmlStyle.twoColumnSummary;
                case 3 -> HtmlStyle.threeColumnSummary;
                case 4 -> HtmlStyle.fourColumnSummary;
                default -> throw new IllegalStateException();
            };
        }

        var table = HtmlTree.DIV(tableStyle).addStyle(gridStyle);
        if ((tabs == null || occurringTabs.size() == 1) && !alwaysShowDefaultTab) {
            if (tabs == null) {
                main.add(caption);
            } else {
                main.add(getCaption(occurringTabs.iterator().next().label()));
            }
            table.add(getTableBody());
            main.add(table);
        } else {
            var tablist = HtmlTree.DIV(HtmlStyle.tableTabs)
                    .put(HtmlAttr.ROLE, "tablist")
                    .put(HtmlAttr.ARIA_ORIENTATION, "horizontal");

            HtmlId defaultTabId = HtmlIds.forTab(id, 0);
            if (renderTabs) {
                tablist.add(createTab(defaultTabId, HtmlStyle.activeTableTab, true, defaultTab));
            } else {
                tablist.add(getCaption(defaultTab));
            }
            if (renderTabs) {
                for (var tab : tabs) {
                    if (occurringTabs.contains(tab)) {
                        tablist.add(createTab(HtmlIds.forTab(id, tab.index()), HtmlStyle.tableTab, false, tab.label()));
                    }
                }
            }
            if (id == null) {
                throw new IllegalStateException("no id set for table");
            }
            var tabpanel = new HtmlTree(TagName.DIV)
                    .setId(HtmlIds.forTabPanel(id))
                    .put(HtmlAttr.ROLE, "tabpanel")
                    .put(HtmlAttr.ARIA_LABELLEDBY, defaultTabId.name());
            table.add(getTableBody());
            tabpanel.add(table);
            main.add(tablist);
            main.add(tabpanel);
        }
        return main;
    }

    private HtmlTree createTab(HtmlId tabId, HtmlStyle style, boolean defaultTab, Content tabLabel) {
        var tab = new HtmlTree(TagName.BUTTON)
                .setId(tabId)
                .put(HtmlAttr.ROLE, "tab")
                .put(HtmlAttr.ARIA_SELECTED, defaultTab ? "true" : "false")
                .put(HtmlAttr.ARIA_CONTROLS, HtmlIds.forTabPanel(id).name())
                .put(HtmlAttr.TABINDEX, defaultTab ? "0" : "-1")
                .put(HtmlAttr.ONKEYDOWN, "switchTab(event)")
                .put(HtmlAttr.ONCLICK, "show('" + id.name() + "', '" + (defaultTab ? id : tabId).name()
                        + "', " + columnStyles.size() + ")")
                .setStyle(style);
        tab.add(tabLabel);
        return tab;
    }

    private Content getTableBody() {
        ContentBuilder tableContent = new ContentBuilder();
        tableContent.add(header);
        bodyRows.forEach(tableContent::add);
        return tableContent;
    }

    private HtmlTree getCaption(Content title) {
        return HtmlTree.DIV(HtmlStyle.caption, HtmlTree.SPAN(title));
    }
}
