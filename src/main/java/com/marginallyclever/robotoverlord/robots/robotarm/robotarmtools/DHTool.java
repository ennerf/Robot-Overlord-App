package com.marginallyclever.robotoverlord.robots.robotarm.robotarmtools;

import com.jogamp.opengl.GL2;
import com.marginallyclever.convenience.MatrixHelper;
import com.marginallyclever.convenience.PrimitiveSolids;
import com.marginallyclever.convenience.memento.MementoOriginator;
import com.marginallyclever.robotoverlord.Entity;

import javax.vecmath.Matrix4d;

/**
 * DHTool has a DHLink equivalence.
 * In this way it can perform transforms and have sub-links.
 * @author Dan Royer
 *
 */
@Deprecated
public abstract class DHTool extends Entity implements MementoOriginator {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3140513593165370783L;
	// tool tip convenience used in kinematics
	protected Entity toolTipOffset = new Entity();
	
	public DHTool() {
		super();
		setName("DHTool");
		addEntity(toolTipOffset);
	}
	
	public void set(DHTool b) {
		super.set(b);
		setName(b.getName());
		b.toolTipOffset.set(toolTipOffset);
	}

	/**
	 * use the keyState to control the tool.
	 * @return true if the robot's pose has been affected.
	 */
	public boolean directDrive() {
		return false;		
	}

	public String getCommand() {
		return "";
	}
	
	public void sendCommand(String str) {}
	
	public void interpolate(double dt) {}

}
