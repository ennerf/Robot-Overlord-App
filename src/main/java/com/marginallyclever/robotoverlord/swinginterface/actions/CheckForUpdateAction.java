package com.marginallyclever.robotoverlord.swinginterface.actions;

import com.marginallyclever.convenience.log.Log;
import com.marginallyclever.robotoverlord.RobotOverlord;
import com.marginallyclever.robotoverlord.swinginterface.translator.Translator;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks online for a new version of Robot Overlord. This action is not undoable.
 * @author Dan Royer
 *
 */
public class CheckForUpdateAction extends AbstractAction implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public CheckForUpdateAction() {
		super(Translator.get("Check for update"));
        putValue(SHORT_DESCRIPTION, Translator.get("Check if you are using the latest version of this app"));
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		String updateURL = "https://github.com/MarginallyClever/Robot-Overlord-App/releases/latest";
		try {
			URL github = new URL(updateURL);
			HttpURLConnection conn = (HttpURLConnection) github.openConnection();
			conn.setInstanceFollowRedirects(false);  //you still need to handle redirect manully.
			HttpURLConnection.setFollowRedirects(false);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

			String inputLine;
			if ((inputLine = in.readLine()) != null) {
				// parse the URL in the text-only redirect
				String matchStart = "<a href=\"";
				String matchEnd = "\">";
				int start = inputLine.indexOf(matchStart);
				int end = inputLine.indexOf(matchEnd);
				if (start != -1 && end != -1) {
					inputLine = inputLine.substring(start + matchStart.length(), end);
					// parse the last part of the redirect URL, which contains the release tag (which is the VERSION)
					inputLine = inputLine.substring(inputLine.lastIndexOf("/") + 1);

					Log.message("last release: " + inputLine);
					Log.message("your VERSION: " + RobotOverlord.VERSION);
					//Log.message(inputLine.compareTo(VERSION));

					if (inputLine.compareTo(RobotOverlord.VERSION) > 0) {
						JOptionPane.showMessageDialog(null, "A new version of this software is available.  The latest version is "+inputLine+"\n"
								+"Please visit http://www.marginallyclever.com/ to get the new hotness.");
					} else {
						JOptionPane.showMessageDialog(null, "This version is up to date.");
					}
				}
			} else {
				throw new Exception();
			}
			in.close();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Sorry, I failed.  Please visit "+updateURL+" to check yourself.");
		}
	}
}
