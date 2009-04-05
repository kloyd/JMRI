// SerialDriverAction.java

package jmri.jmrix.sprog.sprogCS;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

/**
 * Swing action to create and register a
 *       			SerialDriverFrame object
 *
 * @author			Andrew Crosland    Copyright (C) 2006
 * @version			$Revision: 1.3 $
 */
public class SerialDriverAction extends AbstractAction  {

	public SerialDriverAction(String s) { super(s);}

    public void actionPerformed(ActionEvent e) {
		// create a SerialDriverFrame
		SerialDriverFrame f = new SerialDriverFrame();
		try {
			f.initComponents();
			}
		catch (Exception ex) {
			log.error("starting SerialDriverFrame caught exception: "+ex.toString());
			}
		f.setVisible(true);
	};

   static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SerialDriverAction.class.getName());

}


/* @(#)SerialdriverAction.java */
