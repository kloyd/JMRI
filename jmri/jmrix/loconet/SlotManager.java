/* SlotManager.java */

package jmri.jmrix.loconet;

import jmri.CommandStation;
import jmri.ProgListener;
import jmri.Programmer;
import jmri.jmrix.AbstractProgrammer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Controls a collection of slots, acting as the
 * counter-part of a LocoNet command station.
 * <P>
 * A SlotListener can register to hear changes. By registering here, the SlotListener
 * is saying that it wants to be notified of a change in any slot.  Alternately,
 * the SlotListener can register with some specific slot, done via the LocoNetSlot
 * object itself.
 * <p>
 * Strictly speaking, functions 9 through 28 are not in the
 * actual slot, but it's convenient to imagine there's an 
 * "extended slot" and keep track of them here.  This is a 
 * partial implementation, though, because setting is still
 * done directly in {@link LocoNetThrottle}. In particular, 
 * if this slot has not been read from the command station,
 * the first message directly setting F9 through F28 will
 * not have a place to store information. Instead, it will
 * trigger a slot read, so the following messages
 * will be properly handled.
 * <P>
 * Some of the message formats used in this class are Copyright Digitrax, Inc.
 * and used with permission as part of the JMRI project.  That permission
 * does not extend to uses in other software products.  If you wish to
 * use this code, algorithm or these message formats outside of JMRI, please
 * contact Digitrax Inc for separate permission.
 * <P>
 * This Programmer implementation is single-user only. It's not clear whether
 * the command stations can have multiple programming requests outstanding
 * (e.g. service mode and ops mode, or two ops mode) at the same time, but this
 * code definitely can't.
 * <P>
 * @author	Bob Jacobsen  Copyright (C) 2001, 2003
 * @version     $Revision: 1.45 $
 */
public class SlotManager extends AbstractProgrammer implements LocoNetListener, CommandStation {

    public SlotManager() {
        // error if more than one constructed?
        if (self != null)
            log.debug("Creating too many SlotManager objects");

        // need a longer LONG_TIMEOUT for Fleischman command stations
        LONG_TIMEOUT=180000;
        
        // initialize slot array
        for (int i=0; i<=127; i++) _slots[i] = new LocoNetSlot(i);

        // register this as the default, register as the Programmer
        self = this;
        jmri.InstanceManager.setProgrammerManager(new LnProgrammerManager(this));

        // listen to the LocoNet
        LnTrafficController.instance().addLocoNetListener(~0, this);

          // We will scan the slot table every 10 s for in-use slots that are stale
        staleSlotCheckTimer = new javax.swing.Timer(10000, new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
              checkStaleSlots();
            }
          }
        );

      staleSlotCheckTimer.setRepeats(true);
      staleSlotCheckTimer.setInitialDelay( 30000 );
      staleSlotCheckTimer.start();
    }

    /**
     * Send a DCC packet to the rails. This implements the CommandStation interface.
     * @param packet
     */
    public void sendPacket(byte[] packet, int repeats) {
        if (repeats>7) log.error("Too many repeats!");
        if (packet.length<=1) log.error("Invalid DCC packet length: "+packet.length);
        if (packet.length>6) log.error("Only 6-byte packets accepted: "+packet.length);

        LocoNetMessage m = new LocoNetMessage(11);
        m.setElement(0,0xED);
        m.setElement(1,0x0B);
        m.setElement(2,0x7F);
        // the incoming packet includes a check byte that's not included in LocoNet packet
        int length = packet.length-1;
        
        m.setElement(3, (repeats&0x7)+16*(length&0x7));

        int highBits = 0;
        if (length>=1 && ((packet[0]&0x80) != 0)) highBits |= 0x01;
        if (length>=2 && ((packet[1]&0x80) != 0)) highBits |= 0x02;
        if (length>=3 && ((packet[2]&0x80) != 0)) highBits |= 0x04;
        if (length>=4 && ((packet[3]&0x80) != 0)) highBits |= 0x08;
        if (length>=5 && ((packet[4]&0x80) != 0)) highBits |= 0x10;
        m.setElement(4,highBits);

        m.setElement(5,0);
        m.setElement(6,0);
        m.setElement(7,0);
        m.setElement(8,0);
        m.setElement(9,0);
        for (int i=0; i<packet.length-1; i++) m.setElement(5+i, packet[i]&0x7F);

        LnTrafficController.instance().sendLocoNetMessage(m);
    }

    /**
     * Information on slot state is stored in an array of LocoNetSlot objects.
     * This is declared final because we never need to modify the array itself,
     * just its contents.
     */
    final private LocoNetSlot _slots[] = new LocoNetSlot[128];

    /**
     * Access the information in a specific slot.  Note that this is a
     * mutable access, so that the information in the LocoNetSlot object
     * can be changed.
     * @param i  Specific slot, counted starting from zero.
     * @return   The Slot object
     */
    public LocoNetSlot slot(int i) {return _slots[i];}

    /**
     * Obtain a slot for a particular loco address.
     * <P>This requires access to the command station, even if the
     * locomotive address appears in the current contents of the slot
     * array, to ensure that our local image is up-to-date.
     * <P>
     * This method sends an info request.  When the echo of this is
     * returned from the LocoNet, the
     * next slot-read is recognized as the response.
     * <P>
     * The object that's looking for this information must provide
     * a SlotListener to notify when the slot ID becomes available.
     * <P>
     * The SlotListener is not subscribed for slot notifications; it can
     * do that later if it wants.  We don't currently think that's a race
     * condition.
     * @param i  Specific slot, counted starting from zero.
     * @param l  The SlotListener to notify of the answer.
     */
    public void slotFromLocoAddress(int i, SlotListener l) {
        // store connection between this address and listener for later
        mLocoAddrHash.put(new Integer(i), l);

        // send info request
        LocoNetMessage m = new LocoNetMessage(4);
        m.setOpCode(LnConstants.OPC_LOCO_ADR);  // OPC_LOCO_ADR
        m.setElement(1, (i/128)&0x7F);
        m.setElement(2, i&0x7F);
        LnTrafficController.instance().sendLocoNetMessage(m);
    }

    /**
     * method to scan the slot array looking for slots that are in-use but have
     * not had any updates in over 90s and issue a read slot request to update
     * their state as the command station may have purged or stopped updating
     * the slot without telling us via a LocoNet message.
     *
     * This is intended to be called from the staleSlotCheckTimer
     */

    javax.swing.Timer staleSlotCheckTimer = null ;

    private void checkStaleSlots() {
      long staleTimeout = System.currentTimeMillis() - 90000;
      LocoNetSlot slot;

        // We will just check the normal loco slots 1 to 120
      for (int i = 1; i <= 120; i++) {
        slot = _slots[i];
        if ( (slot.slotStatus() == LnConstants.LOCO_IN_USE) &&
            (slot.getLastUpdateTime() <= staleTimeout))
          sendReadSlot(i);
      }
    }

    /**
     * Provide a mapping between locomotive addresses and the
     * SlotListener that's interested in them
     */
    Hashtable mLocoAddrHash = new Hashtable();

    /**
     * method to find the existing SlotManager object, if need be creating one
     */
    static public final SlotManager instance() {
        if (self == null) self = new SlotManager();
        return self;
    }
    static private SlotManager self = null;

    // data members to hold contact with the slot listeners
    final private Vector slotListeners = new Vector();

    public synchronized void addSlotListener(SlotListener l) {
        // add only if not already registered
        if (!slotListeners.contains(l)) {
            slotListeners.addElement(l);
        }
    }

    public synchronized void removeSlotListener(SlotListener l) {
        if (slotListeners.contains(l)) {
            slotListeners.removeElement(l);
        }
    }

    /**
     * Trigger the notification of all SlotListeners.
     * @param s The changed slot to notify.
     */
    protected void notify(LocoNetSlot s) {
        // make a copy of the listener vector to synchronized not needed for transmit
        Vector v;
        synchronized(this)
            {
                v = (Vector) slotListeners.clone();
            }
        if (log.isDebugEnabled()) log.debug("notify "+v.size()
                                            +" SlotListeners about slot "
                                            +s.getSlot());
        // forward to all listeners
        int cnt = v.size();
        for (int i=0; i < cnt; i++) {
            SlotListener client = (SlotListener) v.elementAt(i);
            client.notifyChangedSlot(s);
        }
    }

    /**
     * Stores the opcode of the previously-processed message for context.
     * This is needed to know whether a SLOT DATA READ is a response to
     * a REQ LOCO ADDR, for example.
     */
    int lastMessage = -1;

    /**
     * Listen to the LocoNet. This is just a steering routine, which invokes
     * others for the various processing steps.
     * @param m incoming message
     */
    public void message(LocoNetMessage m) {
        // slot specific message?
        int i = findSlotFromMessage(m);
        if (i != -1) {
            forwardMessageToSlot(m,i);
            respondToAddrRequest(m,i);
            programmerOpMessage(m,i);
        }

        // see if extended function message
        if (isExtFunctionMessage(m)) {
            // yes, get address
            int addr = getDirectFunctionAddress(m);
            // find slot(s) containing this address
            // and route message to them
            boolean found = false;
            for (int j = 0; j < 120; j++) {
                LocoNetSlot slot = slot(j);
                if ( slot == null ) continue;
                if ( (slot.locoAddr() != addr) 
                    || (slot.slotStatus() == LnConstants.LOCO_FREE) ) continue;
                // found!
                slot.functionMessage(getDirectDccPacket(m));
                found = true;
            }
            if (!found) {
                // rats! Slot not loaded since program start.  Request it be 
                // reloaded for later, but that'll be too late
                // for this one.
                LocoNetMessage mo = new LocoNetMessage(4);
                mo.setOpCode(LnConstants.OPC_LOCO_ADR);  // OPC_LOCO_ADR
                mo.setElement(1, (addr/128)&0x7F);
                mo.setElement(2, addr&0x7F);
                LnTrafficController.instance().sendLocoNetMessage(mo);             
            }
        }
        
        // save this message for context next time
        // unless it is a OPC_GPBUSY AJS 28-Mar-03
        if( m.getOpCode() != LnConstants.OPC_GPBUSY )
            lastMessage = m.getOpCode();
    }

    /**
     * If this is a direct function command, return -1, 
     * otherwise return DCC address word
     */
    int getDirectFunctionAddress(LocoNetMessage m) {
        if (m.getElement(0) != 0xED) return -1;
        if (m.getElement(1) != 0x0B) return -1;
        if (m.getElement(2) != 0x7F) return -1;
        // Direct packet, check length
        if ( (m.getElement(3)&0x70) < 0x20) return -1;
        int addr = -1;
        // check long address
        if ( (m.getElement(5)&0x40) != 0) {
            addr = (m.getElement(5)&0x3F)*256 + (m.getElement(6)&0xFF);
            if ( (m.getElement(4)&0x02) != 0 ) addr+=128;  // and high bit
        } else {
            addr = (m.getElement(5)&0xFF);
            if ( (m.getElement(4)&0x01) != 0 ) addr+=128;  // and high bit
        }
        return addr;
    }
    
    /* if this is a direct DCC packet, return as one long
     * else return -1. Packet does not include
     * address bytes.
     */
    int getDirectDccPacket(LocoNetMessage m) {
        if (m.getElement(0) != 0xED) return -1;
        if (m.getElement(1) != 0x0B) return -1;
        if (m.getElement(2) != 0x7F) return -1;
        // Direct packet, check length
        if ( (m.getElement(3)&0x70) < 0x20) return -1;
        int result = 0;
        int n = (m.getElement(3)&0xF0)/16;
        int start;
        int high = m.getElement(4);
        // check long or short address
        if ( (m.getElement(5)&0x40) != 0) {
            start = 7;
            high = high>>2;
            n = n-2;
        } else {
            start = 6;
            high = high>>1;
            n = n-1;
        }
        // get result
        for (int i = 0; i<n; i++) {
            result = result*256+(m.getElement(start+i)&0x7F);
            if ((high & 0x01) !=0 ) result += 128;
            high = high >> 1;
        }
        return result;
    }

    /** 
     * True if the message is an external DCC packet
     * request for F9-F28
     */
    boolean isExtFunctionMessage(LocoNetMessage m) {
        int pkt = getDirectDccPacket(m);
        if (pkt<0) return false;
        // check F9-12
        if ( (pkt&0xFFFFFF0) == 0xA0) return true;
        // check F13-28
        if ( (pkt&0xFFFFFE00) == 0xDE00) return true;
        return false;
    }
    
    /**
     * FInd the slot number that a message references
     */
    public int findSlotFromMessage(LocoNetMessage m) {

        int i = -1;  // find the slot index in the message and store here

        // decode the specific message type and hence slot number
        switch (m.getOpCode()) {
        case LnConstants.OPC_WR_SL_DATA:
        case LnConstants.OPC_SL_RD_DATA:
            i = m.getElement(2);
            break;

        case LnConstants.OPC_LOCO_DIRF:
        case LnConstants.OPC_LOCO_SND:
        case LnConstants.OPC_LOCO_SPD:
        case LnConstants.OPC_SLOT_STAT1:
            i = m.getElement(1);
            break;

        case LnConstants.OPC_MOVE_SLOTS:  // handle the follow-on message when it comes
            return i; // need to cope with that!!

        case LnConstants.OPC_LONG_ACK:
            // handle if reply to slot. There's no slot number in the LACK, unfortunately.
            // If this is a LACK to a Slot op, and progState is command pending,
            // assume its for us...
            if (log.isDebugEnabled())
                log.debug("LACK in state "+progState+" message: "+m.toString());
            if ( (m.getElement(1)&0xEF) == 0x6F && progState == 1 ) {
                // in programming state
                // check status byte
                if ((m.getElement(2) == 1) // task accepted
                    || (m.getElement(2) == 0x7F)) {
                    // 'not implemented' (op on main)
                    // but BDL16 and other devices can eventually reply, so
                    // move to commandExecuting state
                    log.debug("LACK accepted, next state 2");
                    if ( (_progRead || _progConfirm) && mServiceMode)
                        startLongTimer();
                    else
                        startShortTimer();
                    progState = 2;
                    return i;
                }
                else if (m.getElement(2) == 0) { // task aborted as busy
                    // move to not programming state
                    progState = 0;
                    // notify user ProgListener
                    stopTimer();
                    notifyProgListenerLack(jmri.ProgListener.ProgrammerBusy);
                    return i;
                }
                else if (m.getElement(2) == 0x40) { // task accepted blind
                    // move to not programming state
                    progState = 0;
                    // notify user ProgListener
                    stopTimer();
                    // have to send this in a little while to 
                    // allow command station time to execute
                    int delay = 100; // milliseconds
                    javax.swing.Timer timer = new javax.swing.Timer(delay, new java.awt.event.ActionListener() {
                            public void actionPerformed(java.awt.event.ActionEvent e) {
                                notifyProgListenerEnd(-1, 0); // no value (e.g. -1), no error status (e.g.0)
                            }
                        });
                    timer.stop();
                    timer.setInitialDelay(delay);
                    timer.setRepeats(false);
                    timer.start();
                    return i;
                }
                else { // not sure how to cope, so complain
                    log.warn("unexpected LACK reply code "+m.getElement(2));
                    // move to not programming state
                    progState = 0;
                    // notify user ProgListener
                    stopTimer();
                    notifyProgListenerLack(jmri.ProgListener.UnknownError);
                    return i;
                }
            }
            else return i;

        default:
				// nothing here for us
            return i;
        }
        // break gets to here
        return i;
    }

    public void forwardMessageToSlot(LocoNetMessage m, int i) {

        // if here, i holds the slot number, and we expect to be able to parse
        // and have the slot handle the message
        if (i>=_slots.length || i<0) log.error("Received slot number "+i+
                                               " is greater than array length "+_slots.length+" Message was "
                                               + m.toString());
        try {
            _slots[i].setSlot(m);
        }
        catch (LocoNetException e) {
            // must not have been interesting, or at least routed right
            log.error("slot rejected LocoNetMessage"+m);
            return;
        }
        // notify listeners that slot may have changed
        notify(_slots[i]);
    }

    protected void respondToAddrRequest(LocoNetMessage m, int i) {
        // if the last was a OPC_LOCO_ADR and this was OPC_SL_RD_DATA
        if ( (lastMessage==0xBF) && (m.getOpCode()==0xE7)) {
            // yes, see if request exists
            // note that slot has already been told, so
            // slot i has the address of this request
            int addr = _slots[i].locoAddr();
            if (log.isDebugEnabled()) log.debug("LOCO_ADR resp of slot "+i+" loco "+addr);
            SlotListener l = (SlotListener)mLocoAddrHash.get(new Integer(addr));
            if (l!=null) {
                // only notify once per request
                mLocoAddrHash.remove(new Integer(addr));
                // and send the notification
                if (log.isDebugEnabled()) log.debug("notify listener");
                l.notifyChangedSlot(_slots[i]);
            } else {
                if (log.isDebugEnabled()) log.debug("no request for this");
            }
        }
    }

    protected void programmerOpMessage(LocoNetMessage m, int i) {

        // start checking for programming operations in slot 124
        if (i == 124) {
            // here its an operation on the programmer slot
            if (log.isDebugEnabled())
                log.debug("Message "+m.getOpCodeHex()
                          +" for slot 124 in state "+progState);
            switch (progState) {
            case 0:   // notProgramming
                break;
            case 1:   // commandPending
                // we just sit here waiting for a LACK, handled above
                break;
            case 2:   // commandExecuting
                // waiting for slot read, is it present?
                if (m.getOpCode() == LnConstants.OPC_SL_RD_DATA) {
                    // yes, this is the end
                    // move to not programming state
                    stopTimer();
                    progState = 0;

                    // parse out value returned
                    int value = -1;
                    int status = 0;
                    if (_progConfirm ) {
                        // read command, get value; check if OK
                        value = _slots[i].cvval();
                        if (value != _confirmVal) status = status | jmri.ProgListener.ConfirmFailed;
                    }
                    if (_progRead ) {
                        // read command, get value
                        value = _slots[i].cvval();
                    }
                    // parse out status
                    if ( (_slots[i].pcmd() & LnConstants.PSTAT_NO_DECODER ) != 0 )
                        status = (status | jmri.ProgListener.NoLocoDetected);
                    if ( (_slots[i].pcmd() & LnConstants.PSTAT_WRITE_FAIL ) != 0 )
                        status = (status | jmri.ProgListener.NoAck);
                    if ( (_slots[i].pcmd() & LnConstants.PSTAT_READ_FAIL ) != 0 )
                        status = (status | jmri.ProgListener.NoAck);
                    if ( (_slots[i].pcmd() & LnConstants.PSTAT_USER_ABORTED ) != 0 )
                        status = (status | jmri.ProgListener.UserAborted);

                    // and send the notification
                    notifyProgListenerEnd(value, status);
                }
                break;
            default:  // error!
                log.error("unexpected programming state "+progState);
                break;
            }
        }
    }

    // members for handling the programmer interface

    /**
     * Set the mode of the Programmer implementation.
     * @param mode A mode constant from the Programmer interface
     */
    public void setMode(int mode) {
        if (mode == jmri.Programmer.DIRECTBITMODE)
            mode = jmri.Programmer.DIRECTBYTEMODE;
        if (mode != _mode) {
            notifyPropertyChange("Mode", _mode, mode);
            _mode = mode;
        }
    }

    /**
     * Get the current mode of the Programmer implementation.
     * @return A mode constant from the Programmer interface
     */
    public int getMode() { return _mode; }

    /**
     * Records the current mode of the Programmer implementation.
    */
    protected int _mode = Programmer.PAGEMODE;

    /**
     * Trigger notification of PropertyChangeListeners. The only bound
     * property is Mode from the Programmer interface. It is not clear
     * why this is not in AbstractProgrammer...
     * @param name Changed property
     * @param oldval
     * @param newval
     */
    protected void notifyPropertyChange(String name, int oldval, int newval) {
        // make a copy of the listener vector to synchronized not needed for transmit
        Vector v;
        synchronized(this)
            {
                v = (Vector) propListeners.clone();
            }
        if (log.isDebugEnabled()) log.debug("notify "+v.size()
                                            +"listeners of property change name: "
                                            +name+" oldval: "+oldval+" newval: "+newval);
        // forward to all listeners
        int cnt = v.size();
        for (int i=0; i < cnt; i++) {
            PropertyChangeListener client = (PropertyChangeListener) v.elementAt(i);
            client.propertyChange(new PropertyChangeEvent(this, name, new Integer(oldval), new Integer(newval)));
        }
    }

    /**
     * Remember whether the attached command station has powered
     * off the main track after programming
     */
    private boolean mProgPowersOff = false;

    /**
     * Determine whether this Programmer implementation powers off the
     * main track after a service track programming operation.
     * This is entirely determined by
     * the attached command station, not the code here, so it
     * refers to the mProgPowersOff member variable which is recording
     * the known state of that.
     * @return True if main track off after service operation
     */
    public boolean getProgPowersOff() { return mProgPowersOff; }

    /**
     * Configure whether this Programmer owers off the
     * main track after a service track programming operation.<P>
     * This is not part of the Programmer interface, but is used
     * as part of the startup sequence for the LocoNet objects.
     *
     * @param pProgPowersOff True if power is off afterward
     */
    public void setProgPowersOff(boolean pProgPowersOff) {
        log.debug("set progPowersOff to "+pProgPowersOff);
        mProgPowersOff = pProgPowersOff;
    }

    /**
     * Remember whether the attached command station can read from
     * Decoders.
     */
    private boolean mCanRead = true;

    /**
     * Determine whether this Programmer implementation is capable of
     * reading decoder contents. This is entirely determined by
     * the attached command station, not the code here, so it
     * refers to the mCanRead member variable which is recording
     * the known state of that.
     * @return True if reads are possible
     */
    public boolean getCanRead() { return mCanRead; }

    /**
     * Configure whether this Programmer implementation is capable of
     * reading decoder contents. <P>
     * This is not part of the Programmer interface, but is used
     * as part of the startup sequence for the LocoNet objects.
     *
     * @param pCanRead True if reads are possible
     */
    public void setCanRead(boolean pCanRead) {
        log.debug("set canRead to "+pCanRead);
        mCanRead = pCanRead;
    }

    /**
     * Set the command station type
     */
    public void setCommandStationType(String value){
        commandStationType = value;
    }
    /**
     * Get the command station type
     */
    public String getCommandStationType(){
        return commandStationType;
    }
    protected String commandStationType = "<unknown>";
    
    /**
     * Determine is a mode is available for this Programmer implementation
     * @param mode A mode constant from the Programmer interface
     * @return True if paged or register mode
     */
    public boolean hasMode(int mode) {
        if ( mode == Programmer.PAGEMODE ||
             mode == Programmer.ADDRESSMODE ||
             mode == Programmer.DIRECTBYTEMODE ||
             mode == Programmer.REGISTERMODE ) {
            log.debug("hasMode request on mode "+mode+" returns true");
            return true;
        }
        log.debug("hasMode returns false on mode "+mode);
        return false;
    }

    /**
     * Internal routine to handle a timeout
     */
    synchronized protected void timeout() {
        if (log.isDebugEnabled()) log.debug("timeout fires in state "+progState);
        if (progState != 0) {
            // we're programming, time to stop
            if (log.isDebugEnabled()) log.debug("timeout while programming");
            // perhaps no communications present? Fail back to end of programming
            progState = 0;
            // and send the notification; error code depends on state
            if (progState == 2 && !mServiceMode) // ops mode command executing,
                // so did talk to command station at first
                notifyProgListenerEnd(_slots[124].cvval(), jmri.ProgListener.NoAck);
            else // all others
                notifyProgListenerEnd(_slots[124].cvval(), jmri.ProgListener.FailedTimeout);
        }
    }

    int progState = 0;
    // 1 is commandPending
    // 2 is commandExecuting
    // 0 is notProgramming
    boolean _progRead = false;
    boolean _progConfirm = false;
    int _confirmVal;
    boolean mServiceMode = true;

    public void writeCVOpsMode(int CV, int val, jmri.ProgListener p,
                               int addr, boolean longAddr) throws jmri.ProgrammerException {
        lopsa = addr&0x7f;
        hopsa = (addr/128)&0x7f;
        mServiceMode = false;
        doWrite(CV, val, p, 0x67);  // ops mode byte write, with feedback
    }
    public void writeCV(int CV, int val, jmri.ProgListener p) throws jmri.ProgrammerException {
        lopsa = 0;
        hopsa = 0;
        mServiceMode = true;
        // parse the programming command
        int pcmd = 0x43;       // LPE imples 0x40, but 0x43 is observed
        if (getMode() == jmri.Programmer.PAGEMODE) pcmd = pcmd | 0x20;
        else if (getMode() == jmri.Programmer.DIRECTBYTEMODE) pcmd = pcmd | 0x28;
        else if (getMode() == jmri.Programmer.REGISTERMODE
                 || getMode() == jmri.Programmer.ADDRESSMODE) pcmd = pcmd | 0x10;
        else throw new jmri.ProgrammerException("mode not supported");

        doWrite(CV, val, p, pcmd);
    }
    public void doWrite(int CV, int val, jmri.ProgListener p, int pcmd) throws jmri.ProgrammerException {
        if (log.isDebugEnabled()) log.debug("writeCV: "+CV);
        stopPowerTimer();  // still programming, so no longer waiting for power off

        useProgrammer(p);
        _progRead = false;
        _progConfirm = false;
        // set commandPending state
        progState = 1;

        // format and send message
        startShortTimer();
        LnTrafficController.instance().sendLocoNetMessage(progTaskStart(pcmd, val, CV, true));
    }

    public void confirmCVOpsMode(int CV, int val, jmri.ProgListener p,
                               int addr, boolean longAddr) throws jmri.ProgrammerException {
        lopsa = addr&0x7f;
        hopsa = (addr/128)&0x7f;
        mServiceMode = false;
        doConfirm(CV, val, p, 0x2F);  // although LPE implies 0x2C, 0x2F is observed
    }
    public void confirmCV(int CV, int val, jmri.ProgListener p) throws jmri.ProgrammerException {
        lopsa = 0;
        hopsa = 0;
        mServiceMode = true;
        // parse the programming command
        int pcmd = 0x03;       // LPE imples 0x00, but 0x03 is observed
        if (getMode() == jmri.Programmer.PAGEMODE) pcmd = pcmd | 0x20;
        else if (getMode() == jmri.Programmer.DIRECTBYTEMODE) pcmd = pcmd | 0x28;
        else if (getMode() == jmri.Programmer.REGISTERMODE
                 || getMode() == jmri.Programmer.ADDRESSMODE) pcmd = pcmd | 0x10;
        else throw new jmri.ProgrammerException("mode not supported");

        doConfirm(CV, val, p, pcmd);
    }

    public void doConfirm(int CV, int val, ProgListener p,
                          int pcmd) throws jmri.ProgrammerException {
        if (log.isDebugEnabled()) log.debug("confirmCV: "+CV);
        stopPowerTimer();  // still programming, so no longer waiting for power off

        useProgrammer(p);
        _progRead = false;
        _progConfirm = true;
        _confirmVal = val;

        // set commandPending state
        progState = 1;

        // format and send message
        startShortTimer();
        LnTrafficController.instance().sendLocoNetMessage(progTaskStart(pcmd, -1, CV, false));
    }

    int hopsa; // high address for CV read/write
    int lopsa; // low address for CV read/write
    /**
     * Invoked by LnOpsModeProgrammer to start an ops-mode
     * read operation.
     * @param CV Which CV to read
     * @param p Who to notify on complete
     * @param addr Address of the locomotive
     * @param longAddr true if a long address, false if short address
     * @throws ProgrammerException
     */
    public void readCVOpsMode(int CV, jmri.ProgListener p, int addr, boolean longAddr) throws jmri.ProgrammerException {
        lopsa = addr&0x7f;
        hopsa = (addr/128)&0x7f;
        mServiceMode = false;
        doRead(CV, p, 0x2F);  // although LPE implies 0x2C, 0x2F is observed
    }
    public void readCV(int CV, jmri.ProgListener p) throws jmri.ProgrammerException {
        lopsa = 0;
        hopsa = 0;
        mServiceMode = true;
        // parse the programming command
        int pcmd = 0x03;       // LPE imples 0x00, but 0x03 is observed
        if (getMode() == jmri.Programmer.PAGEMODE) pcmd = pcmd | 0x20;
        else if (getMode() == jmri.Programmer.DIRECTBYTEMODE) pcmd = pcmd | 0x28;
        else if (getMode() == jmri.Programmer.REGISTERMODE
                 || getMode() == jmri.Programmer.ADDRESSMODE) pcmd = pcmd | 0x10;
        else throw new jmri.ProgrammerException("mode not supported");

        doRead(CV, p, pcmd);
    }
    void doRead(int CV, jmri.ProgListener p, int progByte) throws jmri.ProgrammerException {
        if (log.isDebugEnabled()) log.debug("readCV: "+CV);
        stopPowerTimer();  // still programming, so no longer waiting for power off

        useProgrammer(p);
        _progRead = true;
        _progConfirm = false;
        // set commandPending state
        progState = 1;

        // format and send message
        startShortTimer();
        LnTrafficController.instance().sendLocoNetMessage(progTaskStart(progByte, -1, CV, false));
    }

    private jmri.ProgListener _usingProgrammer = null;

    // internal method to remember who's using the programmer
    protected void useProgrammer(jmri.ProgListener p) throws jmri.ProgrammerException {
        // test for only one!
        if (_usingProgrammer != null && _usingProgrammer != p) {
            if (log.isInfoEnabled()) log.info("programmer already in use by "+_usingProgrammer);
            throw new jmri.ProgrammerException("programmer in use");
        }
        else {
            _usingProgrammer = p;
            return;
        }
    }

    /**
     * Internal method to create the LocoNetMessage for programmer task start
     */
    protected LocoNetMessage progTaskStart(int pcmd, int val, int cvnum, boolean write) throws jmri.ProgrammerException {

        int addr = cvnum-1;    // cvnum is in human readable form; addr is what's sent over loconet

        LocoNetMessage m = new LocoNetMessage(14);

        m.setOpCode(LnConstants.OPC_WR_SL_DATA);
        m.setElement(1, 0x0E);
        m.setElement(2, LnConstants.PRG_SLOT);

        m.setElement(3, pcmd);

        // set zero, then HOPSA, LOPSA, TRK
        m.setElement(4, 0);
        m.setElement(5, hopsa);
        m.setElement(6, lopsa);
        m.setElement(7, 0);  // TRK was 0, then 7 for PR2, now back to zero

        // store address in CVH, CVL. Note CVH format is truely wierd...
        m.setElement(8, (addr&0x300)/16 + (addr&0x80)/128 + (val&0x80)/128*2 );
        m.setElement(9,addr & 0x7F);

        // store low bits of CV value
        m.setElement(10, val&0x7F);

        // throttle ID
        m.setElement(11, 0x7F);
        m.setElement(12, 0x7F);
        return m;
    }

    /**
     * internal method to notify of the final result
     * @param value The cv value to be returned
     * @param status The error code, if any
     */
    protected void notifyProgListenerEnd(int value, int status) {
        // (re)start power timer
        restartPowerTimer();
        // and send the reply
        ProgListener p = _usingProgrammer;
        _usingProgrammer = null;
        if (p!=null) sendProgrammingReply(p, value, status);
    }

    /**
     * Internal method to notify of the LACK result.
     * This is a separate routine from nPLRead in case we need to handle something later
     * @param status The error code, if any
     */
    protected void notifyProgListenerLack(int status) {
        // (re)start power timer
        restartPowerTimer();
        // and send the reply
        sendProgrammingReply(_usingProgrammer, -1, status);
        _usingProgrammer = null;
    }

    /**
     * Internal routine to forward a programing reply.
     * This is delayed to prevent overruns of the 
     * command station.
     * @param value the value to return
     * @param status The error code, if any
     */
    protected void sendProgrammingReply(ProgListener p, int value, int status) {
        int delay = 20;  // value in service mode
        if (!mServiceMode) delay=100;  // value in ops mode
        
        NotifyDelay r = new NotifyDelay(delay, p, value, status);
        r.start();
    }
    
    class NotifyDelay extends Thread {
        int delay;
        ProgListener p;
        int value;
        int status;
        NotifyDelay(int delay, ProgListener p, int value, int status) {
            this.delay = delay;
            this.p = p;
            this.value = value;
            this.status = status;
        }
        public void run() {
            synchronized (this) {
                try {
                    wait(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // retain if needed later
                }
            }
            // to avoid problems, we defer this to Swing thread
            NotifyExec r = new NotifyExec(p, value, status);
            javax.swing.SwingUtilities.invokeLater(r);
        }
    }
    
    class NotifyExec implements Runnable {
        ProgListener p;
        int value;
        int status;
        NotifyExec(ProgListener p, int value, int status) {
            this.p = p;
            this.value = value;
            this.status = status;
        }
        public void run() {
            p.programmingOpReply(value, status);
        }
    }
    /**
     * Internal routine to stop power timer, as another programming
     * operation has happened
     */
    protected void stopPowerTimer() {
        if (mPowerTimer!=null) mPowerTimer.stop();
    }

    /**
     * Internal routine to handle timer restart if needed to restore
     * power.  This is only needed in service mode.
     */
    protected void restartPowerTimer() {
        if (mProgPowersOff && mServiceMode) {
            if (mPowerTimer==null) {
                mPowerTimer = new javax.swing.Timer(2000, new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            doPowerOn();
                        }
                    });
            }
            mPowerTimer.stop();
            mPowerTimer.setInitialDelay(2000);
            mPowerTimer.setRepeats(false);
            mPowerTimer.start();
        }
    }

    /**
     * Internal routine to handle a timeout & turn power off
     */
    synchronized protected void doPowerOn() {
        if (progState == 0) {
            // we're not programming, time to power on
            if (log.isDebugEnabled()) log.debug("timeout: turn power on");
            try {
                jmri.InstanceManager.powerManagerInstance().setPower(jmri.PowerManager.ON);
            } catch (jmri.JmriException e) {
                log.error("exception during power on at end of programming: "+e);
            }
        }
    }

    javax.swing.Timer mPowerTimer = null;

    /**
     * Start the process of checking each slot for contents.
     * <P>
     * This is not invoked by this class, but can be invoked
     * from elsewhere to start the process of scanning all
     * slots to update their contents.
     */
    public void update() {
        nextReadSlot = 0;
        readNextSlot();
    }

    /**
     * Send a message requesting the data from a particular slot.
     * @param slot Slot number
     */
    public void sendReadSlot(int slot) {
        LocoNetMessage m = new LocoNetMessage(4);
        m.setOpCode(LnConstants.OPC_RQ_SL_DATA);
        m.setElement(1, slot&0x7F);
        m.setElement(2, 0);
        LnTrafficController.instance().sendLocoNetMessage(m);
    }

    protected int nextReadSlot = 0;
    synchronized protected void readNextSlot() {
        // send info request
        sendReadSlot(nextReadSlot++);

        // schedule next read if needed
        if (nextReadSlot < 127) {
            javax.swing.Timer t = new javax.swing.Timer(500, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    readNextSlot();
                }
            });
            t.setRepeats(false);
            t.start();
        }
    }

    /**
     * Provide a snapshot of the slots in use
     */
    public int getInUseCount() {
        int result = 0;
        for (int i = 0; i<=120; i++) {
            if (slot(i).slotStatus() == LnConstants.LOCO_IN_USE ) result++;
        }
        return result;
    }
    
    // initialize logging
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SlotManager.class.getName());
}


/* @(#)SlotManager.java */
