/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Testing JavaFX canvas run by Nashorn.
 *
 * @test/nocompare
 * @run
 * @fork
 */
 
TESTNAME = "spread";

var WIDTH = 800;
var HEIGHT = 600;
var canvas = new Canvas(WIDTH, HEIGHT);
var context = canvas.graphicsContext2D;

/* "Spread" tech demo of canvas by Tom Theisen
 *
 * This will animate a sequence of branch structures in a canvas element.
 * Each frame, a new direction is calculated, similar to the last frame.
 */

var start_width = 20;           // starting width of each branch
var frame_time = 30;            // milliseconds per frame
var straighten_factor = 0.95;   // value from 0 to 1, factor applied to direction_offset every frame
var curviness = 0.2;            // amount of random direction change each frame

var color_speed = 0.03;     // speed at which colors change when cycling is enabled
var branch_shrink = 0.95;   // factor by which branches shrink every frame
var min_width = 1;          // minimum WIDTH for branch, after which they are discontinued
var branch_opacity = 0.4;   // opacity of lines drawn
var branch_count = 3;       // branch count per tree
var branch_bud_size = 0.5;  // ratio of original branch size at which branch will split
var branch_bud_angle = 1;   // angle offset for split branch;

var paper;                  // reference to graphics context
var branches = Object();    // linked list of active branches
var color_styles = [];      // pre-computed list of colors as styles. format: (r,g,b,a)    
var direction_offset = 0;   // current direction offset in radians.  this is applied to all branches.
var frame = 0;              // frame counter
var timespent = 0;          // total time spent so far, used to calculate average frame render duration
var frameratespan;          // html span element for updating performance number

// preferences object, contains an attribute for each user setting
var prefs = {
    wrap: true,             // causes branches reaching edge of viewable area to appear on opposite side
    fade: false,             // fade existing graphics on each frame
    cycle: true,            // gradually change colors each frame
    new_branch_frames: 20    // number of frames elapsed between each auto-generated tree
};

// create tree at the specified position with number of branches
function create_tree(branches, start_width, position, branch_count) {
    var angle_offset = Math.PI * 2 / branch_count;
    for (var i = 0; i < branch_count; ++i) {
        branch_add(branches, new Branch(position, angle_offset * i, start_width));
    }
}

// add branch to collection
function branch_add(branches, branch) {
    branch.next = branches.next;
    branches.next = branch;
}

// get the coordinates for the position of a new tree
// use the center of the canvas
function get_new_tree_center(width, height) {
    return {
        x: 0.5 * width, 
        y: 0.5 * height 
    };
}

// Branch constructor
// position has x and y properties
// direction is in radians
function Branch(position, direction, width) {
    this.x = position.x;
    this.y = position.y;
    this.width = width;
    this.original_width = width;
    this.direction = direction;
}

// update position, direction and width of a particular branch
function branch_update(branches, branch, paper) {
    paper.beginPath();
    paper.lineWidth = branch.width;
    paper.moveTo(branch.x, branch.y);
    
    branch.width *= branch_shrink;
    branch.direction += direction_offset;
    branch.x += Math.cos(branch.direction) * branch.width;
    branch.y += Math.sin(branch.direction) * branch.width;
    
    paper.lineTo(branch.x, branch.y);
    paper.stroke();
    
    if (prefs.wrap) wrap_branch(branch, WIDTH, HEIGHT);

    if (branch.width < branch.original_width * branch_bud_size) {
        branch.original_width *= branch_bud_size;
        branch_add(branches, new Branch(branch, branch.direction + 1, branch.original_width));
    }
}

function draw_frame() {
    if (prefs.fade) {
        paper.fillRect(0, 0, WIDTH, HEIGHT);
    }

    if (prefs.cycle) {
        paper.setStroke(Paint.valueOf(color_styles[frame % color_styles.length]));
    }

    if (frame++ % prefs.new_branch_frames == 0) {
        create_tree(branches, start_width, get_new_tree_center(WIDTH, HEIGHT), branch_count);
    }
    
    direction_offset += (0.35 + (frame % 200) * 0.0015) * curviness - curviness / 2;
    direction_offset *= straighten_factor;
    
    var branch = branches;
    var prev_branch = branches;
    while (branch = branch.next) {
        branch_update(branches, branch, paper);
        
        if (branch.width < min_width) {
            // remove branch from list
            prev_branch.next = branch.next;
        }
        
        prev_branch = branch;
    }
}

// constrain branch position to visible area by "wrapping" from edge to edge
function wrap_branch(branch, WIDTH, HEIGHT) {
    branch.x = positive_mod(branch.x, WIDTH);
    branch.y = positive_mod(branch.y, HEIGHT);
}

// for a < 0, b > 0, javascript returns a negative number for a % b
// this is a variant of the % operator that adds b to the result in this case
function positive_mod(a, b) {
    // ECMA 262 11.5.3: Applying the % Operator 
    // remainder operator does not convert operands to integers,
    // although negative results are possible

    return ((a % b) + b) % b;
}

// pre-compute color styles that will be used for color cycling
function populate_colors(color_speed, color_styles, branch_opacity) {
    // used in calculation of RGB values
    var two_thirds_pi = Math.PI * 2 / 3;
    var four_thirds_pi = Math.PI * 4 / 3;
    var two_pi = Math.PI * 2;

    // hue does represent hue, but not in the conventional HSL scheme
    for(var hue = 0; hue < two_pi; hue += color_speed) {
        var r = Math.floor(Math.sin(hue) * 128 + 128);
        var g = Math.floor(Math.sin(hue + two_thirds_pi) * 128 + 128);
        var b = Math.floor(Math.sin(hue + four_thirds_pi) * 128 + 128);
        color = "rgba(" + [r, g, b, branch_opacity].join() + ")";

        color_styles.push(color);
    }
}

// apply initial settings to canvas object
function setup_canvas() {
    paper = canvas.graphicsContext2D;
    paper.setFill(Paint.valueOf('rgb(0, 0, 0)'));
    paper.fillRect(0, 0, WIDTH, HEIGHT);
    paper.setFill(Paint.valueOf("rgba(0, 0, 0, 0.005)"));
    paper.setStroke(Paint.valueOf("rgba(128, 128, 64, " + String(branch_opacity) + ")"));
}

populate_colors(color_speed, color_styles, branch_opacity);
setup_canvas();

var stack = new StackPane();
var pane = new BorderPane();
pane.setCenter(canvas);
stack.getChildren().add(pane);
$STAGE.scene = new Scene(stack);
var timer = new AnimationTimerExtend() {
    handle: function handle(now) {
        if (frame < 200) {
            draw_frame();
        } else {
            checkImageAndExit();
            timer.stop();
        }
    }
};
timer.start();

