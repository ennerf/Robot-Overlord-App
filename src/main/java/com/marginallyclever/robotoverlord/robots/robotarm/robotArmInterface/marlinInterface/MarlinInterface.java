package com.marginallyclever.robotoverlord.robots.robotarm.robotArmInterface.marlinInterface;

import com.marginallyclever.communications.NetworkSession;
import com.marginallyclever.communications.NetworkSessionEvent;
import com.marginallyclever.communications.NetworkSessionListener;
import com.marginallyclever.convenience.StringHelper;
import com.marginallyclever.convenience.log.Log;
import com.marginallyclever.robotoverlord.components.RobotComponent;
import com.marginallyclever.robotoverlord.robots.Robot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class MarlinInterface extends JPanel {
	@Serial
	private static final long serialVersionUID = -6388563393882327725L;
	// number of commands we'll hold on to in case there's a resend.
	private static final int HISTORY_BUFFER_LIMIT = 250;
	// Marlin can buffer this many commands from serial, before processing.
	private static final int MARLIN_SEND_LIMIT = 20;
	// If nothing is heard for this many ms then send a ping to check if the connection is still live. 
	private static final int TIMEOUT_DELAY = 2000;
	// Marlin says this when a resend is needed, followed by the last well-received line number.
	private static final String STR_RESEND = "Resend: ";
	// Marlin sends this event when the robot is ready to receive more.
	private static final String STR_OK = "ok";
	// MarlinInterface sends this as an ActionEvent to let listeners know it can handle more input.
	public static final String IDLE = "idle";

	private final Robot myArm;
	private final TextInterfaceToNetworkSession chatInterface = new TextInterfaceToNetworkSession();
	private final List<MarlinCommand> myHistory = new ArrayList<>();

	private final JButton bESTOP = new JButton("EMERGENCY STOP");
	private final JButton bGetAngles = new JButton("M114");
	private final JButton bSetHome = new JButton("Set Home");
	private final JButton bGoHome = new JButton("Go Home");

	// the next line number I should send.  Marlin may say "please resend previous line x", which would change this.
	private int lineNumberToSend;
	// the last line number added to the queue.
	private int lineNumberAdded;
	// don't send more than this many at a time without acknowledgement.
	private int busyCount=MARLIN_SEND_LIMIT;
	
	private final Timer timeoutChecker = new Timer(10000,(e)->onTimeoutCheck());
	private long lastReceivedTime;

	public MarlinInterface(Robot arm) {
		super();

		myArm = arm;

		this.setLayout(new BorderLayout());
		this.add(getToolBar(), BorderLayout.PAGE_START);
		this.add(chatInterface, BorderLayout.CENTER);

		arm.addPropertyChangeListener(this::onRobotEvent);

		chatInterface.addActionListener((e) -> {
			switch (e.getID()) {
				case ChooseConnectionPanel.CONNECTION_OPENED -> {
					onConnect();
					notifyListeners(e);
				}
				case ChooseConnectionPanel.CONNECTION_CLOSED -> {
					onClose();
					updateButtonAccess();
					notifyListeners(e);
				}
			}
		});
	}

	public void addNetworkSessionListener(NetworkSessionListener a) {
		chatInterface.addNetworkSessionListener(a);
	}

	private void onRobotEvent(PropertyChangeEvent e)  {
		if(e.getPropertyName().contentEquals("ee")) sendGoto();
	}

	private void onConnect() {
		Log.message("MarlinInterface connected.");
		setupListener();
		lineNumberToSend=1;
		lineNumberAdded=0;
		myHistory.clear();
		updateButtonAccess();
		timeoutChecker.start();
		
		// you are at the position I say you are at.
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				updateButtonAccess();
				sendSetHome();
			}
		}, 1000 // 1s delay
		);
	}
	
	private void onClose() {
		Log.message("MarlinInterface disconnected.");
		timeoutChecker.stop();
	}
	
	private void onTimeoutCheck() {
		if(System.currentTimeMillis()-lastReceivedTime>TIMEOUT_DELAY) {
			chatInterface.sendCommand("M400");
		}
	}

	private void setupListener() {
		chatInterface.addNetworkSessionListener(this::onDataReceived);
	}
	
	private void onDataReceived(NetworkSessionEvent evt) {
		if(evt.flag == NetworkSessionEvent.DATA_AVAILABLE) {
			lastReceivedTime = System.currentTimeMillis();
			String message = ((String)evt.data).trim();
			if (message.startsWith("X:") && message.contains("Count")) {
				//Log.message("FOUND " + message);
				onHearM114(message);
			} else if(message.startsWith(STR_OK)) {
				onHearOK();
			} else if(message.contains(STR_RESEND)) {
				onHearResend(message);
			}
		}
	}

	private void onHearResend(String message) {
		String numberPart = message.substring(message.indexOf(STR_RESEND) + STR_RESEND.length());
		try {
			int n = Integer.parseInt(numberPart);
			if(n>lineNumberAdded-MarlinInterface.HISTORY_BUFFER_LIMIT) {
				// no problem.
				lineNumberToSend=n;
			}
			// else line is no longer in the buffer.  should not be possible!
		} catch(NumberFormatException e) {
			Log.message("Resend request for '"+message+"' failed: "+e.getMessage());
		}
	}

	private void onHearOK() {
		SwingUtilities.invokeLater(() -> {
			busyCount++;
			sendQueuedCommand();
			clearOldHistory();
			if(lineNumberToSend>=lineNumberAdded) {
				fireIdleNotice();
			}
		});
	}

	private void fireIdleNotice() {
		notifyListeners(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,MarlinInterface.IDLE));
	}

	private void clearOldHistory() {
		while(myHistory.size()>0 && myHistory.get(0).lineNumber<lineNumberAdded-HISTORY_BUFFER_LIMIT) {
			myHistory.remove(0);
		}
	}

	public void queueAndSendCommand(String str) {
		if(!chatInterface.getIsConnected()) return;
		if(str.trim().length()==0) return;
		
		lineNumberAdded++;
		String withLineNumber = "N"+lineNumberAdded+" "+str;
		String assembled = withLineNumber + generateChecksum(withLineNumber);
		myHistory.add(new MarlinCommand(lineNumberAdded,assembled));
		//Log.message("MarlinInterface queued '"+assembled+"'.  busyCount="+busyCount);
		if(busyCount>0) sendQueuedCommand();
	}

	private void sendQueuedCommand() {
		clearOldHistory();
		
		if(myHistory.size()==0) return;
		
		int smallest = Integer.MAX_VALUE;
		for( MarlinCommand mc : myHistory ) {
			if(smallest > mc.lineNumber) smallest = mc.lineNumber;
			if(mc.lineNumber == lineNumberToSend) {
				busyCount--;
				lineNumberToSend++;
				//Log.message("MarlinInterface sending '"+mc.command+"'.");
				chatInterface.sendCommand(mc.command);
				return;
			}
		}
		
		if(smallest>lineNumberToSend) {
			// history no longer contains the line?!
			Log.message("MarlinInterface did not find "+lineNumberToSend);
			for( MarlinCommand mc : myHistory ) {
				Log.message("..."+mc.lineNumber+": "+mc.command);
			}
		}
	}

	private String generateChecksum(String line) {
		byte checksum = 0;

		int i=line.length();
		while(i>0) checksum ^= (byte)line.charAt(--i);

		return "*" + Integer.toString(checksum);
	}

	public boolean getIsBusy() {
		return busyCount<=0;
	}
	
	// format is normally X:0.00 Y:270.00 Z:0.00 U:270.00 V:180.00 W:0.00 Count X:0 Y:0 Z:0 U:0 V:0 W:0
	// trim everything after and including "Count", then read the state data.
	private void onHearM114(String message) {
		try {
			message = message.substring(0, message.indexOf("Count"));
			String[] majorParts = message.split("\b");

			int count = (int)myArm.get(Robot.NUM_JOINTS);
			for (int i = 0; i < count; ++i) {
				myArm.set(Robot.ACTIVE_JOINT,i);
				double v = (double)myArm.get(Robot.JOINT_VALUE);
				for (String s : majorParts) {
					String[] minorParts = s.split(":");

					if (minorParts[0].contentEquals((String)myArm.get(Robot.JOINT_NAME))) {
						v = Double.parseDouble(minorParts[1]);
					}
				}
				myArm.set(Robot.JOINT_VALUE,v);
			}
		} catch (NumberFormatException e) {
			Log.error("M114: "+e.getMessage());
		}
	}

	private void sendGoto() {
		//Log.message("MarlinInterface.sendGoto()");
		StringBuilder action = new StringBuilder("G1");
		int count = (int)myArm.get(Robot.NUM_JOINTS);
		for (int i = 0; i < count; ++i) {
			myArm.set(Robot.ACTIVE_JOINT,i);
			action.append(" ").append(myArm.get(Robot.JOINT_NAME)).append(StringHelper.formatDouble((double) myArm.get(Robot.JOINT_VALUE)));
		}
		queueAndSendCommand(action.toString());
	}

	private JToolBar getToolBar() {
		JToolBar bar = new JToolBar();
		bar.setRollover(true);

		bESTOP.setFont(getFont().deriveFont(Font.BOLD));
		bESTOP.setForeground(Color.RED);

		bESTOP.addActionListener((e) -> sendESTOP() );
		bGetAngles.addActionListener((e) -> sendGetPosition() );
		bSetHome.addActionListener((e) -> sendSetHome() );
		bGoHome.addActionListener((e) -> sendGoHome() );

		bar.add(bESTOP);
		bar.addSeparator();
		bar.add(bGetAngles);
		bar.add(bSetHome);
		bar.add(bGoHome);
		
		updateButtonAccess();

		return bar;
	}

	private void sendESTOP() {
		chatInterface.sendCommand("M112");
		chatInterface.sendCommand("M112");
		chatInterface.sendCommand("M112");
	}

	private void sendGetPosition() {
		queueAndSendCommand("M114");
	}

	private void updateButtonAccess() {
		boolean isConnected = chatInterface.getIsConnected();

		bESTOP.setEnabled(isConnected);
		bGetAngles.setEnabled(isConnected);
		bSetHome.setEnabled(isConnected);
		bGoHome.setEnabled(isConnected);
	}

	private void sendSetHome() {
		StringBuilder action = new StringBuilder("G92");
		int count = (int)myArm.get(Robot.NUM_JOINTS);
		for (int i = 0; i < count; ++i) {
			myArm.set(Robot.ACTIVE_JOINT,i);
			String name = (String)myArm.get(Robot.JOINT_NAME);
			double angle = (double)myArm.get(Robot.JOINT_HOME);
			action.append(" ").append(name).append(StringHelper.formatDouble(angle));
		}
		queueAndSendCommand(action.toString());
		sendGoHome();
	}

	private void sendGoHome() {
		int count = (int)myArm.get(Robot.NUM_JOINTS);
		for (int i = 0; i < count; ++i) {
			myArm.set(Robot.ACTIVE_JOINT, i);
			double angle = (double)myArm.get(Robot.JOINT_HOME);
			myArm.set(Robot.JOINT_VALUE,angle);
		}
	}

	public void setNetworkSession(NetworkSession session) {
		chatInterface.setNetworkSession(session);
	}

	public void closeConnection() {
		this.chatInterface.closeConnection();
	}
	
	// OBSERVER PATTERN
	
	private final ArrayList<ActionListener> listeners = new ArrayList<>();

	public void addListener(ActionListener listener) {
		listeners.add(listener);
	}

	public void removeListener(ActionListener listener) {
		listeners.remove(listener);
	}
	
	private void notifyListeners(ActionEvent e) {
		for (ActionListener listener : listeners) listener.actionPerformed(e);
	}

	// TEST

	public static void main(String[] args) {
		Log.start();

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {}

		JFrame frame = new JFrame(MarlinInterface.class.getName());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new MarlinInterface(new RobotComponent()));
		frame.pack();
		frame.setVisible(true);
	}
}
