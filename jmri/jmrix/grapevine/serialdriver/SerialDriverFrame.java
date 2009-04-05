// SerialDriverFrame.java

package jmri.jmrix.grapevine.serialdriver;

import javax.swing.*;

/**
 * Frame to control and connect Grapevine
 * @author    Bob Jacobsen   Copyright (C) 2001, 2006, 2007
 * @version   $Revision: 1.2 $
 */

public class SerialDriverFrame extends jmri.jmrix.SerialPortFrame {

    public SerialDriverFrame() {
        super("Open Grapevine Driver");
        adapter = new SerialDriverAdapter();
    }

    public void openPortButtonActionPerformed(java.awt.event.ActionEvent e) throws jmri.jmrix.SerialConfigException {
        if ((String) portBox.getSelectedItem() != null) {
            // connect to the port
            adapter.configureBaudRate((String)baudBox.getSelectedItem());
            adapter.configureOption1((String)opt1Box.getSelectedItem());
            String errCode = adapter.openPort((String) portBox.getSelectedItem(),"GrapevineSerial");

            if (errCode == null)	{
                adapter.configure();
                // hide this frame, since we're done
                setVisible(false);
            } else {
                JOptionPane.showMessageDialog(this,errCode);
            }
        } else {
            // not selected
            JOptionPane.showMessageDialog(this, "Please select a port name first");
        }
    }

    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SerialDriverFrame.class.getName());

}
