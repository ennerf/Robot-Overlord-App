package com.marginallyclever.robotoverlord;

import com.marginallyclever.convenience.Cuboid;

import java.util.ArrayList;

/**
 * Objects that can collide with other objects in the world.
 * @author Dan Royer
 * @since 2021-02-24
 *
 */
public abstract interface Collidable {
	/**
	 * @return a list of {@link Cuboid} relative to the world.
	 */
	public ArrayList<Cuboid> getCuboidList();
}
