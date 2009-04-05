/**
 * ToolsMenu.java
 */

package jmri.jmrit.ussctc;

import javax.swing.JMenu;

/**
 * Create a "Tools" menu containing the USS CTC tools
 *
 * @author	Bob Jacobsen   Copyright 2007
 * @version     $Revision: 1.2 $
 */
public class ToolsMenu extends JMenu {
    public ToolsMenu(String name) {
        this();
        setText(name);
    }

    public ToolsMenu() {

        super();

        if (rb == null) rb = java.util.ResourceBundle.getBundle("jmri.jmrit.ussctc.UssCtcBundle");
        setText(rb.getString("MenuTools"));

        add(new OsIndicatorAction(rb.getString("MenuItemOsIndicator")));
        add(new FollowerAction(rb.getString("MenuItemFollower")));
    }

    static java.util.ResourceBundle rb = null;
    
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ToolsMenu.class.getName());
}


