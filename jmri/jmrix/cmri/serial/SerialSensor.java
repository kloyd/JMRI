// SerialSensor.java

package jmri.jmrix.cmri.serial;

import jmri.AbstractSensor;

/**
 * Extend jmri.AbstractSensor for C/MRI serial systems
 * <P>
 * @author	Bob Jacobsen Copyright (C) 2003
 * @version     $Revision: 1.6 $
 */
public class SerialSensor extends AbstractSensor {

    public SerialSensor(String systemName) {
        super(systemName);
        _knownState = UNKNOWN;
    }

    public SerialSensor(String systemName, String userName) {
        super(systemName, userName);
        _knownState = UNKNOWN;
    }

    /**
     * Request an update on status.
     * <P>
     * Since status is continually
     * being updated, this isn't active now.  Eventually, we may
     * want to have this move the related AIU to the top of the
     * polling queue.
     */
    public void requestUpdateFromLayout() {
    }

    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SerialSensor.class.getName());

}

/* @(#)SerialSensor.java */
