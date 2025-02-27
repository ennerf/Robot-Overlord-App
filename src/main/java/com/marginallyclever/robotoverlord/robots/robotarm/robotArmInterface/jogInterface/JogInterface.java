package com.marginallyclever.robotoverlord.robots.robotarm.robotArmInterface.jogInterface;

import com.marginallyclever.convenience.log.Log;
import com.marginallyclever.robotoverlord.components.RobotComponent;
import com.marginallyclever.robotoverlord.robots.Robot;

import javax.swing.*;
import javax.vecmath.Matrix4d;
import java.awt.*;
import java.io.Serial;

public class JogInterface extends JPanel {
	@Serial
	private static final long serialVersionUID = 1L;
	private final Robot myRobot;
	private final CartesianReportPanel eeReport, tcpReport;

	public JogInterface(Robot robot) {
		super();
		
		myRobot = robot;
		eeReport=new CartesianReportPanel(JogInterface.class.getSimpleName()+".EndEffector");
		tcpReport=new CartesianReportPanel(JogInterface.class.getSimpleName()+".ToolCenterPoint");
		
		this.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 1;
		c.gridheight = 1;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.NORTHWEST;

		this.add(new AngleReportPanel(myRobot), c);
		c.gridx++;
		c.weightx = 0;
		this.add(new AngleDrivePanel(myRobot), c);
		c.gridx--;
		c.gridy++;
		c.weightx = 1;
		this.add(eeReport, c);
		c.gridy++;
		this.add(tcpReport, c);
		c.gridy--;
		c.gridx++;
		c.gridheight=2;
		c.weightx = 0;
		this.add(new CartesianDrivePanel(myRobot), c);
		c.gridheight = 1;
		c.gridx--;
		c.gridy += 2;
		c.gridwidth = 2;
		c.weightx = 1;
		if(myRobot instanceof RobotComponent) {
			this.add(new JacobianReportPanel((RobotComponent)myRobot), c);
		}
		c.gridy++;
		c.weighty = 1;
		this.add(new JPanel(), c);

		myRobot.addPropertyChangeListener( (e)-> updateReports() );
		
		updateReports();
	}
	
	private void updateReports() {
		eeReport.updateReport((Matrix4d)myRobot.get(Robot.END_EFFECTOR));
		tcpReport.updateReport((Matrix4d)myRobot.get(Robot.TOOL_CENTER_POINT));
	}

	public static void main(String[] args) {
		Log.start();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch(Exception ignored) {}
		JFrame frame = new JFrame(JogInterface.class.getSimpleName());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new JogInterface(new RobotComponent()));
		frame.pack();
		frame.setVisible(true);
	}
}
