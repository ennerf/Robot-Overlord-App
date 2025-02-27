package com.marginallyclever.robotoverlord.parameters;

import com.marginallyclever.robotoverlord.AbstractEntity;
import com.marginallyclever.robotoverlord.swinginterface.view.ViewPanel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serial;

/**
 * @author Dan Royer
 * @since 1.6.0
 *
 */
public class BooleanEntity extends AbstractEntity<Boolean> {
	@Serial
	private static final long serialVersionUID = 1393466347571351227L;

	public BooleanEntity() {
		super();
		setName("Boolean");
	}
	
	public BooleanEntity(String name, boolean b) {
		super();
		setName(name);
		set(b);
	}

	@Override
	public String toString() {
		return getName()+"="+t.toString();
	}
	
	public void toggle() {
		set(!get());
	}
	
	
	/**
	 * Explains to View in abstract terms the control interface for this entity.
	 * Derivatives of View implement concrete versions of that view. 
	 * @param view the panel to decorate
	 */
	@Override
	public void getView(ViewPanel view) {
		view.add(this);
	}

	@Override
	public JSONObject toJSON() {
		JSONObject jo = super.toJSON();
		jo.put("value",get());
		return jo;
	}

	@Override
	public void parseJSON(JSONObject jo) throws JSONException {
		super.parseJSON(jo);
		set(jo.getBoolean("value"));
	}
}
