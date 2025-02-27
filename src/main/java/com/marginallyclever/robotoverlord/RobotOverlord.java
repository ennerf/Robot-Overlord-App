package com.marginallyclever.robotoverlord;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import com.marginallyclever.convenience.log.Log;
import com.marginallyclever.convenience.log.LogPanel;
import com.marginallyclever.robotoverlord.components.CameraComponent;
import com.marginallyclever.robotoverlord.components.PoseComponent;
import com.marginallyclever.robotoverlord.demos.*;
import com.marginallyclever.robotoverlord.entities.SkyBoxEntity;
import com.marginallyclever.robotoverlord.entities.ViewCube;
import com.marginallyclever.robotoverlord.tools.move.MoveTool;
import com.marginallyclever.robotoverlord.swinginterface.*;
import com.marginallyclever.robotoverlord.swinginterface.actions.*;
import com.marginallyclever.robotoverlord.swinginterface.edits.SelectEdit;
import com.marginallyclever.robotoverlord.swinginterface.entitytreepanel.EntityTreePanel;
import com.marginallyclever.robotoverlord.swinginterface.entitytreepanel.EntityTreePanelEvent;
import com.marginallyclever.robotoverlord.swinginterface.translator.Translator;
import com.marginallyclever.util.PropertiesFileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.vecmath.Vector2d;
import java.awt.Component;
import java.awt.*;
import java.awt.event.*;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * {@code RobotOverlord} is the top-level controller of an application to educate robots.
 * It is built around good design patterns.
 * See <a href="https://github.com/MarginallyClever/Robot-Overlord-App">https://github.com/MarginallyClever/Robot-Overlord-App</a>
 *
 * @author Dan Royer
 */
public class RobotOverlord extends Entity {
	private static final Logger logger = LoggerFactory.getLogger(RobotOverlord.class);

	public static final String APP_TITLE = "Robot Overlord";
	public static final String APP_URL = "https://github.com/MarginallyClever/Robot-Overlord";
	private static final int FSAA_NUM_SAMPLES = 3;
	private static final int VERTICAL_SYNC_ON = 1;  // 1 on, 0 off
	private static final int DEFAULT_FRAMES_PER_SECOND = 30;
	private static final int PICK_BUFFER_SIZE = 256;


	private static final String KEY_WINDOW_WIDTH = "windowWidth";
	private static final String KEY_WINDOW_HEIGHT = "windowHeight";
	private static final String KEY_WINDOW_X = "windowX";
	private static final String KEY_WINDOW_Y = "windowY";
	private static final String KEY_IS_FULLSCREEN = "isFullscreen";

	public static final FileNameExtensionFilter FILE_FILTER = new FileNameExtensionFilter("RO files", "RO");

	// used for checking the application version with the GitHub release, for "there is a new version available!" notification
	public static final String VERSION = PropertiesFileHelper.getVersionPropertyValue();
	// settings
	private final Preferences prefs = Preferences.userRoot().node("Evil Overlord");  // Secretly evil?  Nice.
    //private RecentFiles recentFiles = new RecentFiles();
    
    private final Scene scene = new Scene();
	private transient final List<Entity> selectedEntities = new ArrayList<>();
	private transient Entity copiedEntities = new Entity();

	/**
	 * The list of actions registered in the editor.  This list is used for calls to
	 * {@link #updateActionEnableStatus()}.
	 */
	private final ArrayList<AbstractAction> actions = new ArrayList<>();

	private final MoveTool moveTool = new MoveTool();
	private transient final ViewCube viewCube = new ViewCube();
	
	// The main frame of the GUI
	private JFrame mainFrame; 
	private static JFrame logFrame;
    private JMenuBar mainMenu;
	private final JSplitPane splitLeftRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	private final JSplitPane rightFrameSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
	private final EntityTreePanel entityTree = new EntityTreePanel();
	private final ComponentPanel componentPanel = new ComponentPanel();
	
	private EntityRenameAction entityRenameAction;
	private EntityDeleteAction entityDeleteAction;

	private final FPSAnimator animator = new FPSAnimator(DEFAULT_FRAMES_PER_SECOND);
	private GLJPanel glCanvas;
	
	// should I check the state of the OpenGL stack size?  true=every frame, false=never
	private final boolean checkStackSize = false;
	
	// mouse steering controls
	private boolean isMouseIn=false;

	private final Viewport viewport = new Viewport();
	
    // timing for animations
    private long lastTime;
    private double frameDelay;
    private double frameLength;

	// click on screen to change which entity is selected
	private transient boolean pickNow = false;
	private transient Vector2d pickPoint = new Vector2d();

	private final SkyBoxEntity sky = new SkyBoxEntity();
	
 	private RobotOverlord() {
 		super();
 		setName("");
		this.addComponent(new PoseComponent());
 		 		
		if(GraphicsEnvironment.isHeadless()) {
			throw new RuntimeException("RobotOverlord cannot be run headless yet.");
		}

		Translator.start();
		SoundSystem.start();
		InputManager.start();

		buildMainFrame();
		buildMainMenu();
		createSimulationPanel();
		layoutComponents();
		startAnimationSystem();

		entityTree.addEntity(scene);
		scene.addSceneChangeListener(entityTree);

		addEntity(sky);
		addEntity(viewport);
		addEntity(scene);
		addEntity(moveTool);
		addEntity(viewCube);

		SceneNewAction action = new SceneNewAction("New Scene",this);
		action.resetScene();

		Log.message("** READY **");
    }

	private void createSimulationPanel() {
		createCanvas();
        addCanvasListeners();
        glCanvas.setMinimumSize(new Dimension(300,300));
	}

	private void createCanvas() {
        try {
            Log.message("...get default caps");
    		GLCapabilities caps = new GLCapabilities(GLProfile.getDefault());
            Log.message("...set caps");
    		caps.setBackgroundOpaque(true);
    		caps.setDoubleBuffered(true);
    		caps.setHardwareAccelerated(true);
            if(FSAA_NUM_SAMPLES>1) {
            	caps.setSampleBuffers(true);
                caps.setNumSamples(FSAA_NUM_SAMPLES);
            }
            Log.message("...create panel");
            glCanvas = new GLJPanel(caps);
    	} catch(GLException e) {
    		Log.error("Failed the first call to OpenGL.  Are your native drivers missing?");
    	}
	}

	private void addCanvasListeners() {
		glCanvas.addGLEventListener(new GLEventListener() {
			private final boolean glDebug=false;
			private final boolean glTrace=false;

		    @Override
		    public void init( GLAutoDrawable drawable ) {
		        GL gl = drawable.getGL();
		    	if(glDebug) gl = useGLDebugPipeline(gl);
		        if(glTrace) gl = useTracePipeline(gl);
		        
		    	GL2 gl2 = drawable.getGL().getGL2();
		    	
		    	// turn on vsync
		        gl2.setSwapInterval(VERTICAL_SYNC_ON);
		        
				// make things pretty
				gl2.glEnable(GL2.GL_NORMALIZE);
		    	gl2.glEnable(GL2.GL_LINE_SMOOTH);      
		        gl2.glEnable(GL2.GL_POLYGON_SMOOTH);
		        gl2.glHint(GL2.GL_POLYGON_SMOOTH_HINT, GL2.GL_NICEST);
		        // TODO add a settings toggle for this option, it really slows down older machines.
		        gl2.glEnable(GL2.GL_MULTISAMPLE);

				// Don't draw triangles facing away from camera
				gl2.glCullFace(GL2.GL_BACK);

		        int [] buf = new int[1];
		        int [] sbuf = new int[1];
		        gl2.glGetIntegerv(GL2.GL_SAMPLES, buf, 0);
		        gl2.glGetIntegerv(GL2.GL_SAMPLE_BUFFERS, sbuf, 0);

		        // depth testing and culling options
				gl2.glDepthFunc(GL2.GL_LESS);
				gl2.glEnable(GL2.GL_DEPTH_TEST);
				gl2.glDepthMask(true);
		        
		        // Scale normals using the scale of the transform matrix so that lighting is sane.
		        // This is more efficient than gl2.gleEnable(GL2.GL_NORMALIZE);
				//gl2.glEnable(GL2.GL_RESCALE_NORMAL);
				//gl2.glEnable(GL2.GL_NORMALIZE);
		        
				// default blending option for transparent materials
		        gl2.glEnable(GL2.GL_BLEND);
		        gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
				
		        // set the color to use when wiping the draw buffer
				gl2.glClearColor(0.85f,0.85f,0.85f,1.0f);
				
				// draw to the back buffer, so we can swap buffer later and avoid vertical sync tearing
		    	gl2.glDrawBuffer(GL2.GL_BACK);
		    }
			
		    @Override
		    public void reshape( GLAutoDrawable drawable, int x, int y, int width, int height ) {
		        // set up the projection matrix
		        viewport.setCanvasWidth(glCanvas.getSurfaceWidth());
		        viewport.setCanvasHeight(glCanvas.getSurfaceHeight());
		    }

			@Override
		    public void dispose( GLAutoDrawable drawable ) {}
			
		    @Override
		    public void display( GLAutoDrawable drawable ) {
		        long nowTime = System.currentTimeMillis();
		        long dt = nowTime - lastTime;
		    	lastTime = nowTime;
		    	updateStep(dt*0.001);  // to seconds
		    	
		    	GL2 gl2 = drawable.getGL().getGL2();
				if(checkStackSize) checkRenderStep(gl2);
				else renderStep(gl2);
		    	pickStep(gl2);
		    }
		});  // this class also listens to the glcanvas (messy!) 
		glCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// if they dragged the cursor around before releasing the mouse button, don't pick.
				if (e.getClickCount() == 2) {
					pickPoint.set(e.getX(),e.getY());
					pickNow=true;
				}
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				if(SwingUtilities.isLeftMouseButton(e)) {
					pickPoint.set(e.getX(),e.getY());
					viewport.pressed();
				}
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if(SwingUtilities.isLeftMouseButton(e)) {
					viewport.released();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
				isMouseIn=true;
				glCanvas.requestFocus();
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				isMouseIn=false;
			}
		});  // this class also listens to the mouse button clicks.
		glCanvas.addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseDragged(MouseEvent e) {
		        viewport.setCursor(e.getX(),e.getY());
			}
			
			@Override
			public void mouseMoved(MouseEvent e) {
		        viewport.setCursor(e.getX(),e.getY());
			}
		});  // this class also listens to the mouse movement.
	}
	
	public static void main(String[] argv) {
		logFrame = LogPanel.createFrame();
		Log.start();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch(Exception ignored) {}
		
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(RobotOverlord::new);
	}

	private JComponent buildEntityManagerPanel() {
        Log.message("buildEntityManagerPanel()");

		entityTree.addEntityTreePanelListener((e)-> {
			if (e.eventType == EntityTreePanelEvent.SELECT) {
				setSelectedEntities(e.subjects);
			}
		});

		entityTree.setPopupMenu(buildEntityTreePopupMenu());

		return entityTree;
	}

	private JPopupMenu buildEntityTreePopupMenu() {
		JPopupMenu popupMenu = new JPopupMenu();

		EntityAddChildAction EntityaddChildAction = new EntityAddChildAction(Translator.get("EntityAddChildAction.name"),this);
		EntityaddChildAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityAddChildAction.shortDescription"));

		for( AbstractAction action : actions ) {
			if(action instanceof EntityCopyAction || action instanceof EntityPasteAction) {
				popupMenu.add(action);
			}
		}

		entityRenameAction =new EntityRenameAction(Translator.get("EntityRenameAction.name"),this);
		entityRenameAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityRenameAction.shortDescription"));
		entityRenameAction.setEnabled(false);

		actions.add(EntityaddChildAction);
		actions.add(entityRenameAction);

		popupMenu.add(EntityaddChildAction);
		popupMenu.add(entityRenameAction);
		popupMenu.add(entityDeleteAction);

		popupMenu.add(new ComponentAddAction(this));

		return popupMenu;
	}

	private void layoutComponents() {
        Log.message("layoutComponents()");
        
		// the right hand stuff			        
		rightFrameSplitter.add(buildEntityManagerPanel());
		rightFrameSplitter.add(new JScrollPane(componentPanel));
		// make sure the master panel can't be squished.
        Dimension minimumSize = new Dimension(360,300);
        rightFrameSplitter.setMinimumSize(minimumSize);
        // if the window resizes, give top and bottom halves equal share of the real estate
		rightFrameSplitter.setResizeWeight(0.5);

        Log.message("build splitters");
        splitLeftRight.add(glCanvas);
        splitLeftRight.add(rightFrameSplitter);
        // if the window resizes, give left half as much real estate as it can get.
        splitLeftRight.setResizeWeight(1);

        mainFrame.add(splitLeftRight);
        
        mainFrame.setJMenuBar(mainMenu);
 	}

	private void buildMainFrame() {
		Log.message("buildMainFrame()");
		// start the main application frame - the largest visible rectangle on the screen with the minimize/maximize/close buttons.
        mainFrame = new JFrame( APP_TITLE + " " + VERSION ); 
    	mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.setLayout(new java.awt.BorderLayout());
        mainFrame.setExtendedState(mainFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        mainFrame.setVisible(true);
    	setWindowSizeAndPosition();
       
        mainFrame.addWindowListener(new WindowAdapter() {
            // when someone tries to close the app, confirm it.
        	@Override
        	public void windowClosing(WindowEvent e) {
        		confirmClose();
        		super.windowClosing(e);
        	}
        	
    		// switch back to this window
        	@Override
            public void windowActivated(WindowEvent e) {
        		super.windowActivated(e);
        		InputManager.focusGained();
        	}

    		// switch away to another window
        	@Override
            public void windowDeactivated(WindowEvent e) {
        		super.windowDeactivated(e);
        		InputManager.focusLost();
        	}
		});

		mainFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				saveWindowSizeAndPosition();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				saveWindowSizeAndPosition();
			}
		});
	}

	private void setWindowSizeAndPosition() {
		Log.message("Set window size and position");

    	Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		int windowW = prefs.getInt(KEY_WINDOW_WIDTH, dim.width);
		int windowH = prefs.getInt(KEY_WINDOW_HEIGHT, dim.height);
		int windowX = prefs.getInt(KEY_WINDOW_X, (dim.width - windowW)/2);
		int windowY = prefs.getInt(KEY_WINDOW_Y, (dim.height - windowH)/2);
		mainFrame.setBounds(windowX, windowY,windowW, windowH);
		boolean isFullscreen = prefs.getBoolean("isFullscreen",false);
		if(isFullscreen) {
			mainFrame.setExtendedState(mainFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		}
	}

	// remember window location for next time.
	private void saveWindowSizeAndPosition() {
		int state = mainFrame.getExtendedState();
		boolean isFullscreen = ((state & JFrame.MAXIMIZED_BOTH)!=0);
		prefs.putBoolean(KEY_IS_FULLSCREEN, isFullscreen);
		if(!isFullscreen) {
			Dimension frameSize = mainFrame.getSize();
			prefs.putInt(KEY_WINDOW_WIDTH, frameSize.width);
			prefs.putInt(KEY_WINDOW_HEIGHT, frameSize.height);
			Point p = mainFrame.getLocation();
			prefs.putInt(KEY_WINDOW_X, p.x);
			prefs.putInt(KEY_WINDOW_Y, p.y);
		}
	}

	public JFrame getMainFrame() {
		return mainFrame;
	}
		
	public Scene getScene() {
		return scene;
	}
	
	public List<Entity> getSelectedEntities() {
		return new ArrayList<>(selectedEntities);
	}

	private void buildMainMenu() {
		Log.message("buildMainMenu()");
		
		mainMenu = new JMenuBar();
		mainMenu.removeAll();
		mainMenu.add(createFileMenu());
		mainMenu.add(createDemoMenu());
		mainMenu.add(createEditMenu());
		mainMenu.add(createHelpMenu());
        mainMenu.updateUI();
	}

	private Component createFileMenu() {
		JMenu menu = new JMenu(APP_TITLE);

		SceneNewAction sceneNewAction = new SceneNewAction(Translator.get("SceneNewAction.name"),this);
		sceneNewAction.putValue(Action.SMALL_ICON,new UnicodeIcon("🌱"));
		sceneNewAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("SceneNewAction.shortDescription"));
		sceneNewAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK) );

		SceneLoadAction sceneLoadAction = new SceneLoadAction(Translator.get("SceneLoadAction.name"),this);
		sceneLoadAction.putValue(Action.SMALL_ICON,new UnicodeIcon("🗁"));
		sceneLoadAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("SceneLoadAction.shortDescription"));
		sceneLoadAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK) );

		SceneSaveAction sceneSaveAction = new SceneSaveAction(Translator.get("SceneSaveAction.name"),this);
		sceneSaveAction.putValue(Action.SMALL_ICON,new UnicodeIcon("💾"));
		sceneSaveAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("SceneSaveAction.shortDescription"));
		sceneSaveAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK) );

		menu.add(sceneNewAction);
		menu.add(sceneLoadAction);
		menu.add(sceneSaveAction);
		menu.add(new JSeparator());
		menu.add(new QuitAction(this));
		return menu;
	}

	private Component createDemoMenu() {
		JMenu menu = new JMenu("Demos");
		menu.add(new JMenuItem(new DemoAction(this,new ODEPhysicsDemo())));
		return menu;
	}

	private Component createEditMenu() {
		JMenu menu = new JMenu("Edit");
		menu.add(new JMenuItem(UndoSystem.getCommandUndo()));
		menu.add(new JMenuItem(UndoSystem.getCommandRedo()));
		menu.add(new JSeparator());

		EntityCopyAction entityCopyAction = new EntityCopyAction(Translator.get("EntityCopyAction.name"),this);
		entityCopyAction.putValue(Action.SMALL_ICON,new UnicodeIcon("📋"));
		entityCopyAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityCopyAction.shortDescription"));
		entityCopyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK) );

		EntityPasteAction entityPasteAction = new EntityPasteAction(Translator.get("EntityPasteAction.name"),this);
		entityPasteAction.putValue(Action.SMALL_ICON,new UnicodeIcon("📎"));
		entityPasteAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityPasteAction.shortDescription"));
		entityPasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK) );

		entityDeleteAction = new EntityDeleteAction(Translator.get("EntityDeleteAction.name"),this);
		entityDeleteAction.putValue(Action.SMALL_ICON,new UnicodeIcon("🗑"));
		entityDeleteAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityDeleteAction.shortDescription"));
		entityDeleteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0) );

		EntityCutAction entityCutAction = new EntityCutAction(Translator.get("EntityCutAction.name"), entityDeleteAction, entityCopyAction);
		entityCutAction.putValue(Action.SMALL_ICON,new UnicodeIcon("✂"));
		entityCutAction.putValue(Action.SHORT_DESCRIPTION, Translator.get("EntityCutAction.shortDescription"));
		entityCutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK) );

		menu.add(entityCopyAction);
		menu.add(entityPasteAction);
		menu.add(entityCutAction);
		menu.add(entityDeleteAction);

		actions.add(entityCopyAction);
		actions.add(entityPasteAction);
		actions.add(entityCutAction);
		actions.add(entityDeleteAction);

		return menu;
	}

	private Component createHelpMenu() {
		JMenu menu = new JMenu("Help");
		JMenuItem buttonViewLog = new JMenuItem("Show Log");
		buttonViewLog.addActionListener((e) -> showLogDialog() );
		menu.add(buttonViewLog);
		menu.add(new JMenuItem(new AboutControlsAction()));
		menu.add(new JMenuItem(new ForumsAction()));
		menu.add(new JMenuItem(new CheckForUpdateAction()));
		menu.add(new JMenuItem(new AboutAction()));
		return menu;
	}

	private void showLogDialog() {
		logFrame.setVisible(true);
	}

    private void updateSelectEntities() {
		if(entityRenameAction !=null) entityRenameAction.setEnabled(false);

		moveTool.setSubject(null);

		List<Entity> list = getSelectedEntities();
    	if( !list.isEmpty()) {
			if(list.size() == 1) {
				Entity firstEntity = list.get(0);
				if(firstEntity.findFirstComponent(PoseComponent.class) != null) {
					moveTool.setSubject(firstEntity);
				}
			}
    	}
		updateComponentPanel();
	}

	public void updateComponentPanel() {
		componentPanel.refreshContents(getSelectedEntities(),this);
	}

	public void confirmClose() {
        int result = JOptionPane.showConfirmDialog(
				mainFrame,
				Translator.get("RobotOverlord.quitConfirm"),
				Translator.get("RobotOverlord.quitTitle"),
				JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
        	mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        	// Run this on another thread than the AWT event queue to make sure the call to Animator.stop() completes before exiting
	        new Thread(() -> {
				stopAnimationSystem();
				mainFrame.dispose();
			}).start();
        }
	}
	
	/**
	 * Deep search for a child with this name.
	 * @param name the name to match
	 * @return the entity.  null if nothing found.
	 */
	public Entity findEntityWithName(String name) {
		ArrayList<Entity> list = new ArrayList<>();
		list.add(scene);
		while( !list.isEmpty() ) {
			Entity obj = list.remove(0);
			String objectName = obj.getName();
			if(name.equals(objectName)) return obj;
			list.addAll(obj.getEntities());
		}
		return null;
	}

    private GL useTracePipeline(GL gl) {
        try {
            return gl.getContext().setGL( GLPipelineFactory.create("com.jogamp.opengl.Trace", null, gl, new Object[] { System.err } ) );
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return gl;
	}

	private GL useGLDebugPipeline(GL gl) {
        Log.message("using GL debug pipeline");
        try {
			return gl.getContext().setGL( GLPipelineFactory.create("com.jogamp.opengl.Debug", null, gl, null) );
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return gl;
	}

	private void pickStep(GL2 gl2) {
        if(!pickNow) return;

		pickNow = false;

		CameraComponent cameraComponent = findFirstComponentRecursive(CameraComponent.class);
		if(cameraComponent==null) return;

		int pickName = findItemUnderCursor(gl2,cameraComponent);
		Entity next = scene.pickEntityWithName(pickName);
		UndoSystem.addEvent(this,new SelectEdit(this,getSelectedEntities(),next));
    }
    
    private void checkRenderStep(GL2 gl2) {
		IntBuffer stackDepth = IntBuffer.allocate(1);
		gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,stackDepth);
		logger.debug("stack depth start = "+stackDepth.get(0));

		renderStep(gl2);
		
		gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,stackDepth);
		logger.debug("stack depth end = "+stackDepth.get(0));
	}
	
    private void renderStep(GL2 gl2) {
		CameraComponent camera = scene.findFirstComponentRecursive(CameraComponent.class);
		if(camera==null) return;

        viewport.renderChosenProjection(gl2,camera);

		clearAll(gl2);
		sky.render(gl2);

        scene.render(gl2);

        // overlays
		moveTool.render(gl2);
		viewCube.render(gl2);
	}

	private void clearAll(GL2 gl2) {
		// Clear the screen and depth buffer
		//gl2.glClear(GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_COLOR_BUFFER_BIT);
		gl2.glClear(GL2.GL_DEPTH_BUFFER_BIT);
	}

	private void updateStep(double dt) {
    	frameDelay+=dt;
    	if(frameDelay>frameLength) {
   			frameDelay-=frameLength;
	    	InputManager.update(isMouseIn);
	    	update( frameLength );
    	}
	}

	private void startAnimationSystem() {
		logger.debug("setup the animation system");
        frameDelay=0;
        frameLength=1.0f/(float)DEFAULT_FRAMES_PER_SECOND;
        animator.add(glCanvas);
        // record the start time of the application, also the end of the core initialization process.
        lastTime = System.currentTimeMillis();
        // start the main application loop.  it will call display() repeatedly.
        animator.start();
	}

	private void stopAnimationSystem() {
		animator.stop();
	}

	/**
	 * Use glRenderMode(GL_SELECT) to ray pick the item under the cursor.
	 * See <a href="https://github.com/sgothel/jogl-demos/blob/master/src/demos/misc/Picking.java">1</a>
	 * and <a href="http://web.engr.oregonstate.edu/~mjb/cs553/Handouts/Picking/picking.pdf">2</a>
	 * @param gl2 the openGL render context
	 */
	private int findItemUnderCursor(GL2 gl2,CameraComponent cameraComponent) {
    	IntBuffer pickBuffer = Buffers.newDirectIntBuffer(PICK_BUFFER_SIZE);
        gl2.glSelectBuffer(PICK_BUFFER_SIZE, pickBuffer);

		gl2.glRenderMode( GL2.GL_SELECT );
		// wipe the select buffer
		gl2.glInitNames();

		viewport.renderPick(gl2,cameraComponent,pickPoint.x,pickPoint.y);
		
        gl2.glLoadName(0);
        // render in selection mode, without advancing time in the simulation.
        scene.render(gl2);

        gl2.glPopName();
        gl2.glFlush();
        
        // get the picking results and return the render mode to the default 
        int hits = gl2.glRenderMode( GL2.GL_RENDER );

        return getPickNameFromPickList(pickBuffer,hits,false);
    }
    
	private int getPickNameFromPickList(IntBuffer pickBuffer,int hits,boolean verbose) {
		if(verbose) logger.debug(hits+" PICKS @ "+pickPoint.x+","+pickPoint.y);

        float zMinBest = Float.MAX_VALUE;
    	int i, index=0, bestPick=0;
    	
    	for(i=0;i<hits;++i) {
    		if(verbose) describePickBuffer(pickBuffer,index);
    		
    		int nameCount=pickBuffer.get(index++);
    		float z1 = (float) (pickBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
    	    @SuppressWarnings("unused")
    		float z2 = (float) (pickBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;

			index+=nameCount;
			if(nameCount>0 && zMinBest > z1) {
    			zMinBest = z1;
    			bestPick = pickBuffer.get(index-1);
    		}
    	}
    	return bestPick;
    }
    
    private void describePickBuffer(IntBuffer pickBuffer, int index) {
		int nameCount=pickBuffer.get(index++);
		float z1 = (float) (pickBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
		float z2 = (float) (pickBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
		
		StringBuilder msg= new StringBuilder("  names=" + nameCount + " zMin=" + z1 + " zMax=" + z2 + ": ");
		String add="";
		int pickName;
		for(int j=0;j<nameCount;++j) {
			pickName = pickBuffer.get(index++);
			msg.append(add).append(pickName);
    		add=", ";
		}
		logger.debug(msg.toString());
	}

	public Viewport getViewport() {
		return viewport;
	}

	public CameraComponent getCamera() {
		return findFirstComponentRecursive(CameraComponent.class);
	}

	public void setSelectedEntity(Entity entity) {
		List<Entity> list = new ArrayList<>();
		if(entity!=null) list.add(entity);
		setSelectedEntities(list);
	}

	public void setSelectedEntities(List<Entity> list) {
		selectedEntities.clear();
		selectedEntities.addAll(list);
		entityTree.setSelection(list);
		updateSelectEntities();
		updateActionEnableStatus();
	}

	public void setCopiedEntities(Entity container) {
		copiedEntities=container;
		updateActionEnableStatus();
	}

	public Entity getCopiedEntities() {
		return copiedEntities;
	}

	/**
	 * All Actions have the tools to check for themselves if they are active.
	 */
	private void updateActionEnableStatus() {
		for(AbstractAction a : actions) {
			if(a instanceof EditorAction) {
				((EditorAction)a).updateEnableStatus();
			}
		}
	}
}
