/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

function split_string(s) {
    return s.split(/(\s+)/).filter(function(e) {return e.trim().length > 0;});
}

function colorize(selector, color) {
    var f = new ColorFilter("");
    f.addRule(new ColorFilter.ColorRule(selector, color));
    f.apply(graph);
}

invisibleConnection = Connection.ConnectionStyle.INVISIBLE;

function styleOutputConnections(selector, color, style) {
    var f = new ConnectionFilter("");
    f.addRule(new ConnectionFilter.ConnectionStyleRule(selector, color, style));
    f.apply(graph);
}

function warn(propertyToMatch, regexp, propertyToShow) {
    var f = new WarningFilter("", "[" + propertyToShow + "]");
    f.addRule(new WarningFilter.WarningRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(propertyToMatch, regexp))));
    f.apply(graph);
}

function removeSelfLoops() {
    var f = new RemoveSelfLoopsFilter("");
    f.apply(graph);
}

function remove(selector) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveFilter.RemoveRule(selector));
    f.apply(graph);
}

function removeIncludingOrphans(property, regexp) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveFilter.RemoveRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp)), true));
    f.apply(graph);
}

function split(selector, propertyNames) {
    if (propertyNames == undefined) {
        propertyNames = [];
    }
    new SplitFilter("", selector, propertyNames).apply(graph);
}

function combine(first, second, propertyNames) {
    if (propertyNames == undefined) {
        propertyNames = [];
    }
    var f = new CombineFilter("");
    f.addRule(new CombineFilter.CombineRule(first, second, false, propertyNames));
    f.apply(graph);
}

function removeInputs(property, regexp, from, to) {
    var f = new RemoveInputsFilter("");
    if(from == undefined && to == undefined) {
        f.addRule(new RemoveInputsFilter.RemoveInputsRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp))));
    } else if(to == undefined) {
        f.addRule(new RemoveInputsFilter.RemoveInputsRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp)), from));
    } else {
        f.addRule(new RemoveInputsFilter.RemoveInputsRule(new MatcherSelector(new Properties.RegexpPropertyMatcher(property, regexp)), from, to));
    }
    f.apply(graph);
}

function removeUnconnectedSlots(inputs, outputs) {
    var f = new UnconnectedSlotFilter(inputs, outputs);
    f.apply(graph);
}

function colorizeGradient(property, min, max) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.apply(graph);
}

function colorizeGradientWithMode(property, min, max, mode) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.setMode(mode);
    f.apply(graph);
}

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

function editSameProperty(selector, propertyName, editFunction) {
    var f = new EditPropertyFilter("", selector, propertyName, propertyName, editFunction);
    f.apply(graph);
}

function editProperty(selector, inputPropertyName, outputPropertyName, editFunction) {
    var f = new EditPropertyFilter("", selector, inputPropertyName, outputPropertyName, editFunction);
    f.apply(graph);
}

function removeInputs(selector1, selector2) {
    var f = new RemoveInputsFilter("");
    f.addRule(new RemoveInputsFilter.RemoveInputsRule(selector1, selector2));
    f.apply(graph);
}

function removeEmptySlots(selector) {
    new RemoveEmptySlotsFilter("", selector).apply(graph);
}

function removeBlock(selector) {
    var f = new RemoveBlockFilter("");
    f.addRule(new RemoveBlockFilter.RemoveBlockRule(selector));
    f.apply(graph);
}

function or(selectors) {
    return new OrSelector(selectors);
}

function and(selectors) {
    return new AndSelector(selectors);
}

function not(selector) {
    return new InvertSelector(selector);
}

function successorOf(selector) {
    return new SuccessorSelector(selector);
}

function hasAnyNode(selector) {
    return new AnySelector(selector);
}

function matches(property, value) {
    return new MatcherSelector(new Properties.RegexpPropertyMatcher(property, value));
}
