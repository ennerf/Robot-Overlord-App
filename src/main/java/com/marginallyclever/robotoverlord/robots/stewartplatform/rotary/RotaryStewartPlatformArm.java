package com.marginallyclever.robotoverlord.robots.stewartplatform.rotary;

import javax.vecmath.Point3d;

class RotaryStewartPlatformArm {
    // lowest point that the magnetic ball can travel.
    // they can only move up from this point.
    public Point3d pShoulder = new Point3d();
    // center of each magnetic ball at the end effector, before being transformed by ee.pose
    public Point3d pEE = new Point3d();
    // pEE after transform by ee.pose.  will be same coordinate system as base.
    public Point3d pEE2 = new Point3d();
    // point where arm is connected to slider after EE has moved.
    public Point3d pElbow = new Point3d();
    // value to remember to send to robot.
    public double angle;

    public RotaryStewartPlatformArm() {
        angle = 0;
    }
}
