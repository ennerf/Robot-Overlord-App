package com.marginallyclever.robotoverlord.swinginterface.entitytreepanel;

import com.marginallyclever.robotoverlord.Entity;

import java.util.List;

public class EntityTreePanelEvent {
	// something in the panel was added.
	public static final int ADD=1;
	// something in the panel was removed.
	public static final int REMOVE=2;
	// something in the panel was renamed.
	public static final int RENAME=3;
	// something in the panel was selected.  usually preceeded by an UNSELECT.
	public static final int SELECT=4;
	// something in the panel was unselected.  usually followed by a SELECT.
	public static final int UNSELECT=5;

	public int eventType;
	public EntityTreePanel panel;
	public List<Entity> subjects;
	
	public EntityTreePanelEvent(int eventType,EntityTreePanel panel,List<Entity> subject) {
		this.eventType=eventType;
		this.panel=panel;
		this.subjects=subject;
	}
}
