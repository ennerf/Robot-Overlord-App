package com.marginallyclever.robotoverlord.robots.robotarm.robotarmtools;

import com.marginallyclever.convenience.memento.Memento;

@Deprecated
public class GripperMemento implements Memento {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public double gripperAngle;
	
	public GripperMemento(double angle) {
		gripperAngle=angle;
	}
}
