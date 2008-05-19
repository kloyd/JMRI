// Receiver.java

package jmri.jmrix.rps;

import javax.vecmath.Point3d;

/**
 * Holds all the state information for a single receiver.
 *
 * @author	Bob Jacobsen  Copyright (C) 2008
 * @version	$Revision: 1.1 $
 */
public class Receiver {

    public Receiver(Point3d position) {
        this.position = position;
    }

    public void setPosition(Point3d position) {
        this.position = position;
    }

    public Point3d getPosition() {
        return position;
    }
    private Point3d position;
    
}

/* @(#)Receiver.java */
