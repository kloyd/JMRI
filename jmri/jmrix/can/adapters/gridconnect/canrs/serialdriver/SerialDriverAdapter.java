// SerialDriverAdapter.java

package jmri.jmrix.can.adapters.gridconnect.canrs.serialdriver;

import jmri.jmrix.can.adapters.gridconnect.GcSerialDriverAdapter;

/**
 * Implements SerialPortAdapter for the CAN-RS.
 * <P>
 * This connects a MERG CAN-RS CAN adapter via a serial com port.
 * Normally controlled by the SerialDriverFrame class.
 * <P>
 *
 * @author			Andrew Crosland Copyright (C) 2008
 * @version			$Revision: 1.3 $
 */
public class SerialDriverAdapter extends GcSerialDriverAdapter  implements jmri.jmrix.SerialPortAdapter {

    static public SerialDriverAdapter instance() {
        if (mInstance == null) mInstance = new SerialDriverAdapter();
        return mInstance;
    }
    static SerialDriverAdapter mInstance = null;

    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SerialDriverAdapter.class.getName());

}
