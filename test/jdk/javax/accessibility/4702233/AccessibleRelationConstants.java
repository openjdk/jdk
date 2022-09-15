/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @summary Constant for testing public fields in AccessibleRelation.
 */
public interface AccessibleRelationConstants {

    /**
     * Fully-qualified name of the class.
     */
    String CLASS_NAME = "javax.accessibility.AccessibleRelation";

    /**
     * Public fields values in AccessibleRelation class.
     */
    String[][] FIELDS = new String[][] { { "CHILD_NODE_OF", "childNodeOf" },
        { "CHILD_NODE_OF_PROPERTY", "childNodeOfProperty" },
        { "CONTROLLED_BY", "controlledBy" },
        { "CONTROLLED_BY_PROPERTY", "controlledByProperty" },
        { "CONTROLLER_FOR", "controllerFor" },
        { "CONTROLLER_FOR_PROPERTY", "controllerForProperty" },
        { "EMBEDDED_BY", "embeddedBy" },
        { "EMBEDDED_BY_PROPERTY", "embeddedByProperty" },
        { "EMBEDS", "embeds" }, { "EMBEDS_PROPERTY", "embedsProperty" },
        { "FLOWS_FROM", "flowsFrom" },
        { "FLOWS_FROM_PROPERTY", "flowsFromProperty" },
        { "FLOWS_TO", "flowsTo" }, { "FLOWS_TO_PROPERTY", "flowsToProperty" },
        { "LABELED_BY", "labeledBy" },
        { "LABELED_BY_PROPERTY", "labeledByProperty" },
        { "LABEL_FOR", "labelFor" },
        { "LABEL_FOR_PROPERTY", "labelForProperty" },
        { "MEMBER_OF", "memberOf" },
        { "MEMBER_OF_PROPERTY", "memberOfProperty" },
        { "PARENT_WINDOW_OF", "parentWindowOf" },
        { "PARENT_WINDOW_OF_PROPERTY", "parentWindowOfProperty" },
        { "SUBWINDOW_OF", "subwindowOf" },
        { "SUBWINDOW_OF_PROPERTY", "subwindowOfProperty" }, };

    /**
     * Old(removed) fields in AccessibleRelation class.
     */
    String[] OLD_FIELDS = new String[] {};
}

