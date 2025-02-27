package com.marginallyclever.robotoverlord.swinginterface.view;

import com.marginallyclever.robotoverlord.swinginterface.UndoSystem;
import com.marginallyclever.robotoverlord.swinginterface.edits.StringEdit;
import com.marginallyclever.robotoverlord.swinginterface.translator.Translator;
import com.marginallyclever.robotoverlord.parameters.StringEntity;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.undo.AbstractUndoableEdit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Panel to alter a file parameter.
 * @author Dan Royer
 *
 */
public class ViewElementFilename extends ViewElement implements ActionListener {
	private static String lastPath=System.getProperty("user.dir");
	private final JTextField field = new JTextField(15);
	private final ArrayList<FileFilter> filters = new ArrayList<FileFilter>();
	private final StringEntity e;
	
	public ViewElementFilename(final StringEntity e) {
		super();
		this.e=e;
		
		//this.setBorder(BorderFactory.createLineBorder(Color.RED));

		field.setEditable(false);
		field.setText(e.get());
		field.setMargin(new Insets(1,0,1,0));
		//pathAndFileName.setBorder(BorderFactory.createLoweredBevelBorder());
		
		JLabel label=new JLabel(e.getName(),JLabel.LEADING);
		label.setLabelFor(field);

		JButton choose = new JButton("...");
		choose.addActionListener(this);
		choose.setMargin(new Insets(0, 5, 0, 5));
		choose.addFocusListener(this);
		
		this.setLayout(new GridBagLayout());

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.weightx=0;
		gbc.gridy=0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		//gbc.gridheight = GridBagConstraints.REMAINDER;
		gbc.insets.right=5;
		this.add(label,gbc);
		gbc.weightx=1;
		gbc.insets.left=0;
		gbc.insets.right=0;
		this.add(field,gbc);
		gbc.weightx=0;
		this.add(choose,gbc);
		
		e.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				field.setText(e.get());
			}
		});
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		JFileChooser chooser = new JFileChooser();
		if(filters.size()==0) return;  // @TODO: fail!
		if(filters.size()==1) chooser.setFileFilter(filters.get(0));
		else {
			Iterator<FileFilter> i = filters.iterator();
			while(i.hasNext()) {
				chooser.addChoosableFileFilter(i.next());
			}
		}
		if(lastPath!=null) chooser.setCurrentDirectory(new File(lastPath));
		int returnVal = chooser.showDialog(SwingUtilities.getWindowAncestor(this), Translator.get("Select"));
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			String newFilename = chooser.getSelectedFile().getAbsolutePath();
			lastPath = chooser.getSelectedFile().getParent();

			AbstractUndoableEdit event = new StringEdit(e, newFilename);
			UndoSystem.addEvent(this,event);
		}
	}

	public void setFileFilter(FileFilter arg0) {
		filters.clear();
		filters.add(arg0);
	}
	
	public void addFileFilter(FileFilter arg0) {
		filters.add(arg0);
	}
	
	/**
	 * Plural form of {@link #addFileFilter}.
	 * @param arg0 {@link ArrayList} of {@link FileFilter}.
	 */
	public void addFileFilters(ArrayList<FileFilter> arg0) {
		filters.addAll(arg0);
	}


	@Override
	public void setReadOnly(boolean arg0) {
		field.setEnabled(!arg0);
	}
}
