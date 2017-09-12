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

TESTNAME = "kaleidoscope";
        
var WIDTH = 800;
var HEIGHT = 600;
var canvas = new Canvas(WIDTH, HEIGHT);
var context = canvas.graphicsContext2D;

var x,y;
var p_x,p_y;
var a=0;
var b=0;
var angle=Math.PI/180*8;
var color=0;
var limit1=Math.PI*1.5;
var limit2=Math.PI*1.79;
var c=new Array(6);
var d=new Array(6);
var r,e;
var fade;
var prv_x,prv_y,prv_x2,prv_y2;
var isFrameRendered = false;

function renderFrame() {
    if (!isFrameRendered) {
        a=0.2*angle;
        b=0.7*angle;
        r=0;
        fade=32;
        for(var i=0;i<6;i++)
            {
            c[i]=1.0/(i+1)/2;
            d[i]=1.0/(i+1)/2;
            }
        radius=Math.round((WIDTH+HEIGHT)/8);
        e=radius*0.2;
        p_x=Math.round(WIDTH/2);
        p_y=Math.round(HEIGHT/2);
        x=(radius*c[0])*Math.cos(a*d[1])+(radius*c[2])*Math.sin(a*d[3])+(radius*c[4])*Math.sin(a*d[5]);
        y=(radius*c[5])*Math.sin(a*d[4])+(radius*c[3])*Math.cos(a*d[2])+(radius*c[1])*Math.cos(a*d[0]);
        isFrameRendered = true;
    }
    anim();
}

function anim() {
    var a1=Math.cos(a*2);
    var a2=Math.cos(a*4);
    var a3=Math.cos(a);
    var a4=Math.sin(a);
    if(b>limit1&&b<limit2) {
        r+=radius*0.02*a1;
        prv_x=x;
        prv_y=y;
        x=prv_x2+r*a3;
        y=prv_y2+r*a4;
    } else {
        prv_x=x;
        prv_y=y;
        prv_x2=x;
        prv_y2=y;
        x=(radius*c[0])*Math.cos(a*d[1])+(radius*c[2])*Math.sin(a*d[3])+(radius*c[4])*Math.sin(a*d[5]);
        y=(radius*c[5])*Math.sin(a*d[4])+(radius*c[3])*Math.cos(a*d[2])+(radius*c[1])*Math.cos(a*d[0]);
    }
    var c3=16*Math.cos(a*10);
    var c1=Math.floor(56*Math.cos(a*angle*4)+c3);
    var c2=Math.floor(56*Math.sin(a*angle*4)-c3);
    context.lineCap=StrokeLineCap.ROUND;
    context.setStroke(Paint.valueOf('rgba('+(192+c1)+','+(192+c2)+','+(192-c1)+','+(0.01-0.005*-a1)+')'));
    context.lineWidth=e*1.4+e*0.8*a3;
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    context.lineWidth=e+e*0.8*a3;
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    context.setStroke(Paint.valueOf('rgba('+(192+c1)+','+(192+c2)+','+(192-c1)+','+(0.06-0.03*-a1)+')'));
    context.lineWidth=e*0.6+e*0.35*a3;
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    context.setStroke(Paint.valueOf('rgba(0,0,0,0.06)'));
    context.lineWidth=e*0.4+e*0.225*a3;
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    context.setStroke(Paint.valueOf('rgba('+(192+c1)+','+(192+c2)+','+(192-c1)+','+(0.1-0.075*-a1)+')'));
    context.lineWidth=e*0.2+e*0.1*a3;
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    context.setStroke(Paint.valueOf('rgba(255,255,255,0.4)'));
    context.lineWidth=e*(0.1-0.05*-a2);
    draw_line(p_x,p_y,prv_x,prv_y,x,y);
    a+=angle*Math.cos(b);
    b+=angle*0.1;
}

function draw_line(x,y,x1,y1,x2,y2) {
    context.beginPath();
    context.moveTo(x+x1,y+y1);
    context.lineTo(x+x2,y+y2);
    context.moveTo(x-x1,y+y1);
    context.lineTo(x-x2,y+y2);
    context.moveTo(x-x1,y-y1);
    context.lineTo(x-x2,y-y2);
    context.moveTo(x+x1,y-y1);
    context.lineTo(x+x2,y-y2);
    context.moveTo(x+y1,y+x1);
    context.lineTo(x+y2,y+x2);
    context.moveTo(x-y1,y+x1);
    context.lineTo(x-y2,y+x2);
    context.moveTo(x-y1,y-x1);
    context.lineTo(x-y2,y-x2);
    context.moveTo(x+y1,y-x1);
    context.lineTo(x+y2,y-x2);
    context.moveTo(x,y+x2);
    context.lineTo(x,y+x1);
    context.moveTo(x,y-x2);
    context.lineTo(x,y-x1);
    context.moveTo(x+x2,y);
    context.lineTo(x+x1,y);
    context.moveTo(x-x2,y);
    context.lineTo(x-x1,y);
    context.stroke();
    context.closePath();
}

var stack = new StackPane();
var pane = new BorderPane();
pane.setCenter(canvas);
stack.getChildren().add(pane);
$STAGE.scene = new Scene(stack);
var frame = 0;
var timer = new AnimationTimerExtend() {
    handle: function handle(now) {
        if (frame < 800) {
            renderFrame();
            frame++;
        } else {
            checkImageAndExit();
            timer.stop();
        }
    }
};
timer.start();
