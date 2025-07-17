/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

 /**
 *
 * @author Thomas Wuerthinger
 */

// Split a string by whitespace, collapsing repeated ones.
function split_string(s) {
    return s.split(/(\s+)/).filter(function(e) {return e.trim().length > 0;});
}

// Select the node union in a list of selectors.
function or(selectors) {
    return new OrSelector(selectors);
}

// Select the node intersection in a list of selectors.
function and(selectors) {
    return new AndSelector(selectors);
}

// Select the nodes that are not selected by a given selector.
function not(selector) {
    return new InvertSelector(selector);
}

// Select the nodes that succeed those given by a selector.
function successorOf(selector) {
    return new SuccessorSelector(selector);
}

// Select the blocks where at least one node is selected by the given selector.
function hasAnyNode(selector) {
    return new AnySelector(selector);
}

// Select the nodes for which the given property is defined.
function hasProperty(property) {
    return new MatcherSelector(new Properties.InvertPropertyMatcher(new Properties.RegexpPropertyMatcher(property, "")));
}

// Select the nodes whose given property matches a given regular expression.
function matches(property, regexp) {
    return new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp));
}

// Select the nodes for which the given property is defined.
function hasProperty(property) {
    return new MatcherSelector(new Properties.InvertPropertyMatcher(new Properties.RegexpPropertyMatcher(property, "")));
}

// Color the selected nodes.
function colorize(selector, color) {
    var f = new ColorFilter("");
    f.addRule(new ColorFilter.ColorRule(selector, color));
    f.apply(graph);
}

// Invisible connection style (used to hide edges).
invisibleConnection = Connection.ConnectionStyle.INVISIBLE;

// Apply a given style (e.g. invisible) and color to the out edges of the
// selected nodes.
function styleOutputConnections(selector, color, style) {
    var f = new ConnectionFilter("");
    f.addRule(new ConnectionFilter.ConnectionStyleRule(selector, color, style));
    f.apply(graph);
}

// Display a warning with the contents of a given property ('propertyToShow') in
// the nodes whose given property ('propertyToMatch') matches a regular
// expression.
function warn(propertyToMatch, regexp, propertyToShow) {
    var f = new WarningFilter("", "[" + propertyToShow + "]");
    f.addRule(new WarningFilter.WarningRule(matches(propertyToMatch, regexp)));
    f.apply(graph);
}

// Remove edges with the same source and destination node.
function removeSelfLoops() {
    var f = new RemoveSelfLoopsFilter("");
    f.apply(graph);
}

// Remove the selected nodes.
function remove(selector) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveFilter.RemoveRule(selector));
    f.apply(graph);
}

// Remove the selected nodes and nodes that become orphan after the removal.
function removeIncludingOrphans(property, regexp) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveFilter.RemoveRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp)), true));
    f.apply(graph);
}

// Inline the selected nodes into their successors and display the first found
// property in the given property list in the resulting input slots.
function split(selector, propertyNames) {
    if (propertyNames == undefined) {
        propertyNames = [];
    }
    new SplitFilter("", selector, propertyNames).apply(graph);
}

// Combine the selected (second) nodes into their selected (first) predecessors
// and display the first found property in the given property list in the
// resulting output slots.
function combine(first, second, propertyNames) {
    if (propertyNames == undefined) {
        propertyNames = [];
    }
    var f = new CombineFilter("");
    f.addRule(new CombineFilter.CombineRule(first, second, false, propertyNames));
    f.apply(graph);
}

// Remove (input and/or output) slots without connecting edges.
function removeUnconnectedSlots(inputs, outputs) {
    var f = new UnconnectedSlotFilter(inputs, outputs);
    f.apply(graph);
}

// Color nodes using a gradient based on the given property and min/max values.
function colorizeGradient(property, min, max) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.apply(graph);
}

// Color nodes using a gradient based on the given property, min/max values, and
// mode ("LINEAR" or "LOGARITHMIC").
function colorizeGradientWithMode(property, min, max, mode) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.setMode(mode);
    f.apply(graph);
}

// Color nodes using a custom gradient based on the given property, min/max
// values, mode ("LINEAR" or "LOGARITHMIC"), list of colors, list of fractions,
// and number of shades.
function colorizeGradientCustom(property, min, max, mode, colors, fractions, nshades) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.setMode(mode);
    f.setColors(colors);
    f.setFractions(fractions);
    f.setShadeCount(nshades);
    f.apply(graph);
}

// Pre-defined colors for coloring filters.
var black = Color.black;
var blue = Color.blue;
var cyan = Color.cyan;
var darkGray = Color.darkGray;
var gray = Color.gray;
var green = Color.green;
var lightGray = Color.lightGray;
var magenta = Color.magenta;
var orange = Color.orange;
var pink = Color.pink
var red = Color.red;
var yellow = Color.yellow;
var white = Color.white;

// Update the value of the given property in the selected nodes according to a
// function that takes as input the old property value and returns the new
// property value.
function editSameProperty(selector, propertyName, editFunction) {
    var f = new EditPropertyFilter("", selector, [propertyName], propertyName, editFunction);
    f.apply(graph);
}

// Update the value of the given property ('outputPropertyName') in the selected
// nodes according to a function that takes as input the values of multiple
// properties ('inputPropertyNames') and returns the new property value.
function editProperty(selector, inputPropertyNames, outputPropertyName, editFunction) {
    var f = new EditPropertyFilter("", selector, inputPropertyNames, outputPropertyName, editFunction);
    f.apply(graph);
}

// Remove edges that go from the selected slots into the selected nodes.
function removeInputs(nodeSelector, slotSelector) {
    var f = new RemoveInputsFilter("");
    f.addRule(new RemoveInputsFilter.RemoveInputsRule(nodeSelector, slotSelector));
    f.apply(graph);
}

// Remove empty slots in the selected nodes, condensing all inputs as a result.
function removeEmptySlots(selector) {
    new RemoveEmptySlotsFilter("", selector).apply(graph);
}

// Remove the selected block.
function removeBlock(selector) {
    var f = new RemoveBlockFilter("");
    f.addRule(new RemoveBlockFilter.RemoveBlockRule(selector));
    f.apply(graph);
}

// Color the selected live ranges.
function colorizeLiveRange(selector, color) {
    var f = new ColorLiveRangeFilter("");
    f.addRule(new ColorLiveRangeFilter.ColorRule(selector, color));
    f.apply(graph);
}

// Select the live ranges whose given property matches a given regular expression.
function matchesLiveRange(property, regexp) {
    return new LiveRangeMatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp));
}
