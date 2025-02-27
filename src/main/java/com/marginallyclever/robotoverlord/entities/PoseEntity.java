package com.marginallyclever.robotoverlord.entities;

import com.jogamp.opengl.GL2;
import com.marginallyclever.convenience.MatrixHelper;
import com.marginallyclever.convenience.OpenGLHelper;
import com.marginallyclever.convenience.PrimitiveSolids;
import com.marginallyclever.robotoverlord.Entity;
import com.marginallyclever.robotoverlord.Removable;
import com.marginallyclever.robotoverlord.Scene;
import com.marginallyclever.robotoverlord.swinginterface.UndoSystem;
import com.marginallyclever.robotoverlord.swinginterface.edits.PoseMoveEdit;
import com.marginallyclever.robotoverlord.swinginterface.view.ViewPanel;
import com.marginallyclever.robotoverlord.parameters.BooleanEntity;
import com.marginallyclever.robotoverlord.parameters.DoubleEntity;
import com.marginallyclever.robotoverlord.parameters.IntEntity;
import com.marginallyclever.robotoverlord.parameters.Vector3dEntity;

import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * A object in the world with a position and orientation (collectively, a "pose")
 * @author Dan Royer
 *
 */
@Deprecated
public class PoseEntity extends Entity implements Removable {
	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = -7250407040741008778L;

	// axis names
	static public final String [] AXIS_LABELS = new String[] { "X","Y","Z","Xr","Yr","Zr"};

	// unique ids for all objects in the world.  
	// zero is reserved to indicate no object.
	static private int pickNameCounter=1;

	// my unique id
	private transient int pickName;	

	// pose relative to my parent.
	protected Matrix4d myPose = new Matrix4d();

	// which axis do we want to move?
	private IntEntity axisChoice = new IntEntity("Jog direction",0);
	// how fast do we want to move?
	private DoubleEntity axisAmount = new DoubleEntity("Jog speed",0);
	
	// draw collidable Cuboid(s)?
	public BooleanEntity showBoundingBox = new BooleanEntity("Show Bounding Box",false);
	// show star at local origin?
	public BooleanEntity showLocalOrigin = new BooleanEntity("Show Local Origin",false);
	// show connection to all children?
	public BooleanEntity showLineage = new BooleanEntity("Show Lineage",false);


	public PoseEntity() {
		this(PoseEntity.class.getSimpleName());
	}

	public PoseEntity(String name) {
		super(name);
		
		pickName = pickNameCounter++;
		
		showBoundingBox.addPropertyChangeListener(this);
		showLocalOrigin.addPropertyChangeListener(this);
		showLineage.addPropertyChangeListener(this);
		axisChoice.addPropertyChangeListener(this);
		
		myPose.setIdentity();
	}
	
	public void set(PoseEntity b) {
		super.set(b);
		myPose.set(b.myPose);
	}

	public int getPickName() {
		return pickName;
	}

	/**
	 * Render this Entity to the display
	 * @param gl2
	 */
	@Override
	public void render(GL2 gl2) {
		gl2.glPushMatrix();
			MatrixHelper.applyMatrix(gl2, myPose);

			// helpful info
			if(showLocalOrigin.get()) PrimitiveSolids.drawStar(gl2,10);
			if(showLineage.get()) drawLineage(gl2);

			super.render(gl2);
			
		gl2.glPopMatrix();
	}
	
	public void drawLineage(GL2 gl2) {
		boolean isTex = OpenGLHelper.disableTextureStart(gl2);
		int depthWasOn = OpenGLHelper.drawAtopEverythingStart(gl2);
		boolean lightWasOn = OpenGLHelper.disableLightingStart(gl2);

		gl2.glColor4d(1,1,1,1);
		gl2.glBegin(GL2.GL_LINES);
		// connection to children
		for(Entity e : entities) {
			if(e instanceof PoseEntity) {					
				Vector3d p = ((PoseEntity)e).getPosition();
				gl2.glVertex3d(0, 0, 0);
				gl2.glVertex3d(p.x,p.y,p.z);
			}
		}
		gl2.glEnd();

		OpenGLHelper.drawAtopEverythingEnd(gl2, depthWasOn);
		OpenGLHelper.disableLightingEnd(gl2, lightWasOn);
		OpenGLHelper.disableTextureEnd(gl2,isTex);
	}

	public Vector3d getPosition() {
		Vector3d trans = new Vector3d();
		myPose.get(trans);
		return trans;
	}

	public void setPosition(Vector3d pos) {
		Matrix4d m = new Matrix4d(myPose);
		m.setTranslation(pos);
		setPose(m);
	}

	public void moveTowards(Matrix4d newWorldPose) {
		setPoseWorld(newWorldPose);
	}
	
	/**
	 * 
	 * @param arg0 fills the vector3 with one possible combination of radian rotations.
	 */
	public void getRotation(Vector3d arg0) {
		Matrix3d temp = new Matrix3d();
		myPose.get(temp);
		arg0.set(MatrixHelper.matrixToEuler(temp));
	}

	/**
	 * Convert Euler rotations to a matrix.
	 * See also https://www.learnopencv.com/rotation-matrix-to-euler-angles/
	 * Eulers are using the ZYX convention.
	 * @param arg0 Vector3d with three radian rotation values
	 */
	public void setRotation(Vector3d arg0) {
		Matrix4d m4 = new Matrix4d();
		Matrix3d m3 = MatrixHelper.eulerToMatrix(arg0);
		m4.set(m3);
		m4.setTranslation(getPosition());
		setPose(m4);
	}
	
	public void setRotation(Matrix3d arg0) {
		Matrix4d m = new Matrix4d();
		m.set(arg0);
		m.setTranslation(getPosition());
		setPose(m);
	}
	
	public void getRotation(Matrix4d arg0) {
		arg0.set(myPose);
		arg0.setTranslation(new Vector3d(0,0,0));
	}
	
	/**
	 * @return {@link Matrix4d} of the local pose
	 */
	public Matrix4d getPose() {
		return new Matrix4d(myPose);
	}

	/**
	 * Set the local pose (relative to my parent)
	 * Automatically updates the cumulative pose.
	 * @param arg0 the local pose
	 */
	public void setPose(Matrix4d arg0) {
		Matrix4d oldValue = new Matrix4d(myPose);
		myPose.set(arg0);
		notifyPropertyChangeListeners(new PropertyChangeEvent(this,"pose",oldValue,arg0));
	}

	
	@Override
	public void update(double dt) {
		super.update(dt);
		
		if(axisAmount.get()!=0) {
			double aa = axisAmount.get();
			int ac = axisChoice.get();
			double aaOverTime = aa*dt;

			Matrix4d target = getPose();
			
			Vector3d p = new Vector3d(target.m03,target.m13,target.m23);
			target.setTranslation(new Vector3d(0,0,0));
			Matrix4d r = new Matrix4d();
			r.setIdentity();

			switch (ac) {
				case 0 -> p.x += aaOverTime;
				case 1 -> p.y += aaOverTime;
				case 2 -> p.z += aaOverTime;
				case 3 -> r.rotX(Math.toRadians(aaOverTime));
				case 4 -> r.rotY(Math.toRadians(aaOverTime));
				case 5 -> r.rotZ(Math.toRadians(aaOverTime));
				default -> {}
			}
			target.mul(r);
			target.setTranslation(p);
			setPose(target);
			
			// which will cause a propertyChange event
			axisAmount.set(aa);
		}
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		super.propertyChange(evt);
		Object o = evt.getSource();

		if(o != axisAmount && axisAmount!=null) {
			axisAmount.set(0.0);
		}
		
		if(o==showBoundingBox) setShowBoundingBox((boolean)showBoundingBox.get());
		if(o==showLocalOrigin) setShowLocalOrigin((boolean)showLocalOrigin.get());
		if(o==showLineage) setShowLineage((boolean)showLineage.get());
	}
	
	/**
	 * Climb through the entity tree, to the root.
	 * Then work from root forward, finding all parents that are a {@link PoseEntity}, to build the world pose matrix.
	 * @return {@link Matrix4d} of the world pose
	 */
	public Matrix4d getPoseWorld() {
		Entity parent = getParent();
		
		if(parent instanceof PoseEntity) {
			Matrix4d m = ((PoseEntity)parent).getPoseWorld();
			m.mul(myPose);
			return m;
		} else {
			return getPose();
		}
	}
	
	/**
	 * Set the pose and poseWorld of this item
	 * @param m
	 */
	public void setPoseWorld(Matrix4d m) {
		if(parent != null && parent instanceof PoseEntity) {
			// I have a parent that is posed in the world.  I only hold onto relative pose information,
			// so remove my parent's world pose from m to get the correct relative pose.
			PoseEntity pep = (PoseEntity)parent;
			Matrix4d newPose = pep.getPoseWorld();
			newPose.invert();
			newPose.mul(m);
			// set the new relative pose.
			setPose(newPose);
		} else {
			setPose(m);
		}
	}
	
	public Scene getWorld() {
		Entity p = parent;
		while (p != null) {
			if (p instanceof Scene) {
				return (Scene) p;
			}
			p=p.getParent();
		}
		return null;
	}
	
	/**
	 * Build a matrix as close to *from* as possible, with the Z axis parallel to the nearest world axis.
	 * @param from the matrix we are comparing to the world.
	 * @return 
	 */
	public Matrix4d findMajorAxisTarget(Matrix4d from) {		
		// find Z axis to major value		
		Vector3d vz = MatrixHelper.getZAxis(from);
		
		double zx = Math.abs(vz.x);
		double zy = Math.abs(vz.y);
		double zz = Math.abs(vz.z);
		
		Vector3d nx=new Vector3d();
		Vector3d ny=new Vector3d();
		Vector3d nz=new Vector3d(); 
		if(zx>zy) {
			if(zx>zz) nz.x = Math.signum(vz.x);  //zx wins
			else      nz.z = Math.signum(vz.z);  //zz wins
		} else {
			if(zy>zz) nz.y = Math.signum(vz.y);  //zy wins
			else      nz.z = Math.signum(vz.z);  //zz wins
		}
		
		// snap X to major value
		nx.cross(MatrixHelper.getYAxis(from),nz);
		// make Y orthogonal to X and Z
		ny.cross(nz, nx);
		// build the new matrix.  Make sure m33=1 and position data is copied in.
		Matrix4d m = new Matrix4d(from);
		MatrixHelper.setXAxis(m,nx);
		MatrixHelper.setYAxis(m,ny);
		MatrixHelper.setZAxis(m,nz);
		return m;
	}
	
	/**
	 * Build a matrix as close to *from* as possible, with the Z axis parallel to the nearest world axis and then the other two axies
	 * pointing along their nearest world axies.
	 * @param from the matrix we are comparing to the world.
	 * @return 
	 */
	public Matrix4d findMinorAxisTarget(Matrix4d from) {
		Matrix4d m = findMajorAxisTarget(from);
		// find X axis to major value		
		Vector3d vx = MatrixHelper.getXAxis(m);
		Vector3d vz = MatrixHelper.getZAxis(m);
		
		double xx = Math.abs(vx.x);
		double xy = Math.abs(vx.y);
		double xz = Math.abs(vx.z);
		
		Vector3d nx=new Vector3d();
		Vector3d ny=new Vector3d();
		if(xx>xy) {
			if(xx>xz) nx.x = Math.signum(vx.x);  //xx wins
			else      nx.z = Math.signum(vx.z);  //xz wins
		} else {
			if(xy>xz) nx.y = Math.signum(vx.y);  //xy wins
			else      nx.z = Math.signum(vx.z);  //xz wins
		}
		
		// make Y orthogonal to X and Z
		ny.cross(vz, nx);
		// snap X to major value
		nx.cross(ny,vz);
		
		// build the new matrix.  Make sure m33=1 and position data is copied in.
		Matrix4d m2 = new Matrix4d(from);
		MatrixHelper.setXAxis(m2,nx);
		MatrixHelper.setYAxis(m2,ny);
		MatrixHelper.setZAxis(m2,vz);
		return m2;
	}
	
	public void snapZToMajorAxis() {
		Matrix4d poseWorld = getPoseWorld();
		Matrix4d m = findMajorAxisTarget(poseWorld);
		if(m!=null) {
			UndoSystem.addEvent(this,new PoseMoveEdit(this,m));
		}
	}
	
	public void snapXToMajorAxis() {
		Matrix4d poseWorld = getPoseWorld();
		Matrix4d m = findMinorAxisTarget(poseWorld);
		if(m!=null) {
			UndoSystem.addEvent(this,new PoseMoveEdit(this,m));
		}
	}

	// recursively set for all children
	public void setShowBoundingBox(boolean arg0) {
		for( Entity c : getEntities() ) {
			if(c instanceof PoseEntity) {
				((PoseEntity)c).setShowBoundingBox(arg0);
			}
		}
		showBoundingBox.set(arg0);
	}
	
	// recursively set for all children
	public void setShowLocalOrigin(boolean arg0) {
		for( Entity c : getEntities() ) {
			if(c instanceof PoseEntity) {
				((PoseEntity)c).setShowLocalOrigin(arg0);
			}
		}
		showLocalOrigin.set(arg0);
	}

	// recursively set for all children
	public void setShowLineage(boolean arg0) {
		for( Entity c : getEntities() ) {
			if(c instanceof PoseEntity) {
				((PoseEntity)c).setShowLineage(arg0);
			}
		}
		showLineage.set(arg0);
	}
	
	@Override
	public void getView(ViewPanel view) {
		view.pushStack("Pose",true);
		
		view.addComboBox(axisChoice, AXIS_LABELS);
		view.addRange(axisAmount, 5, -5);
		
		view.addButton("Snap Z to major axis").addActionEventListener((e)->snapZToMajorAxis());
		view.addButton("Snap X to major axis").addActionEventListener((e)->snapXToMajorAxis());

		view.add(showBoundingBox);
		view.add(showLocalOrigin);
		view.add(showLineage);
		
		//view.addStaticText("Pick name="+getPickName());
		//	pose.getView(view);
		view.popStack();
		view.pushStack("PoseEntity",true);
		Matrix4d poseWorld = getPoseWorld();
		view.add(new Vector3dEntity("Position",MatrixHelper.getPosition(poseWorld)));
		//	poseWorld.getView(view);
		view.popStack();
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		PoseEntity b = (PoseEntity)super.clone();
		b.myPose = (Matrix4d)myPose.clone();
		return b;
	}
	
	@Override
	public String toString() {
		String s = super.toString()
				+","+ myPose.toString();
		return s;
	}

	@Override
	public void beingRemoved() {}
	
    private void writeObject(ObjectOutputStream stream) throws IOException {
    	stream.defaultWriteObject();
    }
    private void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
    	stream.defaultReadObject();
    	pickName = pickNameCounter++;
    }
}
