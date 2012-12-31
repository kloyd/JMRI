// SimpleClockFrame.java

package jmri.jmrit.simpleclock;

import jmri.Timebase;
import jmri.InstanceManager;
import jmri.util.JmriJFrame;

import java.awt.*;

import java.text.DecimalFormat;
import java.util.Date;

import javax.swing.*;
import javax.swing.border.Border;

/**
 * Frame for user configuration of Simple Timebase
 *
 * The current implementation (2007) handles the internal clock and one hardware clock
 *
 * @author	Dave Duchamp   Copyright (C) 2004, 2007
 * @version	$Revision$
 */
public class SimpleClockFrame extends JmriJFrame
	implements java.beans.PropertyChangeListener {

    private Timebase clock;
	private String hardwareName = null;
	//private boolean synchronize = true;
	//private boolean correct = true;
	private boolean changed = false;
    protected boolean showTime = false;
    DecimalFormat threeDigits = new DecimalFormat("0.000");	// 3 digit precision for speedup factor

    protected javax.swing.JComboBox timeSourceBox = null;
	protected javax.swing.JComboBox clockStartBox = null;
	
	protected javax.swing.JCheckBox synchronizeCheckBox = null;
	protected javax.swing.JCheckBox correctCheckBox = null;
	protected javax.swing.JCheckBox displayCheckBox = null;
	protected javax.swing.JCheckBox showStartupCheckBox = null;
	protected javax.swing.JCheckBox startStoppedCheckBox = null;
	protected javax.swing.JCheckBox startSetTimeCheckBox = null;

    protected javax.swing.JTextField factorField = new javax.swing.JTextField(5);
    protected javax.swing.JTextField hoursField = new javax.swing.JTextField(2);
    protected javax.swing.JTextField minutesField = new javax.swing.JTextField(2);
    protected javax.swing.JTextField startHoursField = new javax.swing.JTextField(2);
    protected javax.swing.JTextField startMinutesField = new javax.swing.JTextField(2);

    protected javax.swing.JButton setRateButton = new javax.swing.JButton(Bundle.getString("ButtonSet"));
    protected javax.swing.JButton setTimeButton = new javax.swing.JButton(Bundle.getString("ButtonSet"));
    protected javax.swing.JButton startButton = new javax.swing.JButton(Bundle.getString("ButtonStart"));
    protected javax.swing.JButton stopButton = new javax.swing.JButton(Bundle.getString("ButtonStop"));
    protected javax.swing.JButton setStartTimeButton = new javax.swing.JButton(
														Bundle.getString("ButtonSet"));

    protected javax.swing.JLabel clockStatus = new javax.swing.JLabel();
    protected javax.swing.JLabel timeLabel = new javax.swing.JLabel();
	
	private int internalSourceIndex = 0;
	private int hardwareSourceIndex = 1;
	
	private int startNone = 0;
	private int startNixieClock = 1;
	private int startAnalogClock = 2;
	private int startLcdClock = 3;

    /**
     * Constructor method
     */
    public SimpleClockFrame() {
    	super();
    }

    /**
     *  Initialize the config window
     */
    @SuppressWarnings("deprecation")
    public void initComponents() throws Exception {
        setTitle(Bundle.getString("SimpleClockWindowTitle"));

        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // Determine current state of the clock
        clock = InstanceManager.timebaseInstance();
        if (clock==null) {
            // could not initialize clock
            log.error("Could not obtain a timebase instance.");
            setVisible(false);
            dispose();
            throw new jmri.JmriException("Could not obtain a timebase instance");
        }
		if (!clock.getIsInitialized()) {
			// if clocks have not been initialized at start up, do so now
			clock.initializeHardwareClock();
		}

        // Set up time source choice
        JPanel panel11 = new JPanel();
        panel11.add(new JLabel(Bundle.getString("TimeSource")+" "));
        timeSourceBox = new JComboBox();
        panel11.add(timeSourceBox);
        timeSourceBox.addItem(Bundle.getString("ComputerClock"));
		hardwareName = InstanceManager.clockControlInstance().getHardwareClockName();
		if (hardwareName!=null) timeSourceBox.addItem(hardwareName);
        timeSourceBox.setToolTipText(Bundle.getString("TipTimeSource"));
		timeSourceBox.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    setTimeSourceChanged();
                }
            });
        contentPane.add(panel11);
		if (hardwareName!=null) {
			if (clock.getInternalMaster()) timeSourceBox.setSelectedIndex(internalSourceIndex);
			else timeSourceBox.setSelectedIndex(hardwareSourceIndex);
			JPanel panel11x = new JPanel();
			synchronizeCheckBox = new JCheckBox(Bundle.getString("Synchronize")+" "+
							hardwareName);
			synchronizeCheckBox.setToolTipText(Bundle.getString("TipSynchronize"));
			synchronizeCheckBox.setSelected(clock.getSynchronize());
			synchronizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    synchronizeChanged();
                }
            });
			panel11x.add(synchronizeCheckBox);
			contentPane.add(panel11x);
			if (InstanceManager.clockControlInstance().canCorrectHardwareClock()) {
				JPanel panel11y = new JPanel();
				correctCheckBox = new JCheckBox(Bundle.getString("Correct"));
				correctCheckBox.setToolTipText(Bundle.getString("TipCorrect"));
				correctCheckBox.setSelected(clock.getCorrectHardware());
				correctCheckBox.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
						correctChanged();
					}
				});
				panel11y.add(correctCheckBox);
				contentPane.add(panel11y);
			}
			if (InstanceManager.clockControlInstance().canSet12Or24HourClock()) {
				JPanel panel11z = new JPanel();
				displayCheckBox = new JCheckBox(Bundle.getString("Display12Hour"));
				displayCheckBox.setToolTipText(Bundle.getString("TipDisplay"));
				displayCheckBox.setSelected(clock.use12HourDisplay());
				displayCheckBox.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
						displayChanged();
					}
				});
				panel11z.add(displayCheckBox);
				contentPane.add(panel11z);
			}
		}			

        // Set up speed up factor
        JPanel panel12 = new JPanel();
        panel12.add(new JLabel(Bundle.getString("SpeedUpFactor")+" "));
        panel12.add(factorField);
        factorField.setText(threeDigits.format(clock.userGetRate()));
        factorField.setToolTipText(Bundle.getString("TipFactorField"));
        panel12.add(new JLabel(":1 "));
        setRateButton.setToolTipText(Bundle.getString("TipSetRateButton"));
        setRateButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    setRateButtonActionPerformed();
                }
            });
        panel12.add(setRateButton);
        contentPane.add(panel12);

        // Set up time setup information
        JPanel panel2 = new JPanel();
        panel2.add(new JLabel(Bundle.getString("NewTime")+" "));
        panel2.add(hoursField);
        hoursField.setText("00");
        hoursField.setToolTipText(Bundle.getString("TipHoursField"));
        panel2.add(new JLabel(":"));
        panel2.add(minutesField);
        minutesField.setText("00");
        minutesField.setToolTipText(Bundle.getString("TipMinutesField"));
        setTimeButton.setToolTipText(Bundle.getString("TipSetTimeButton"));
        setTimeButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    setTimeButtonActionPerformed();
                }
            });
        panel2.add(setTimeButton);
        contentPane.add(panel2);
		
		// Set up startup options panel
		JPanel panel6 = new JPanel();
        panel6.setLayout(new BoxLayout(panel6, BoxLayout.Y_AXIS));
        JPanel panel61 = new JPanel();
		startStoppedCheckBox = new JCheckBox(Bundle.getString("StartStopped"));
		startStoppedCheckBox.setToolTipText(Bundle.getString("TipStartStopped"));
		startStoppedCheckBox.setSelected(clock.getStartStopped());
		startStoppedCheckBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				startStoppedChanged();
			}
		});
		panel61.add(startStoppedCheckBox);
		panel6.add(panel61);
        JPanel panel62 = new JPanel();
		startSetTimeCheckBox = new JCheckBox(Bundle.getString("StartSetTime"));
		startSetTimeCheckBox.setToolTipText(Bundle.getString("TipStartSetTime"));
		startSetTimeCheckBox.setSelected(clock.getStartSetTime());
		startSetTimeCheckBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				startSetTimeChanged();
			}
		});
		panel62.add(startSetTimeCheckBox);
		Date tem = clock.getStartTime();
		startHoursField.setText(""+tem.getHours());
		startHoursField.setToolTipText(Bundle.getString("TipStartHours"));
		panel62.add(startHoursField);
        panel62.add(new JLabel(":"));
		startMinutesField.setText(""+tem.getMinutes());
		startMinutesField.setToolTipText(Bundle.getString("TipStartMinutes"));
		panel62.add(startMinutesField);
        setStartTimeButton.setToolTipText(Bundle.getString("TipSetStartTimeButton"));
        setStartTimeButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				startSetTimeChanged();
			}
		});
        panel62.add(setStartTimeButton);
		panel6.add(panel62);
		JPanel panel63 = new JPanel();
        panel63.add(new JLabel(Bundle.getString("StartClock")+" "));
		clockStartBox = new JComboBox();
        panel63.add(clockStartBox);
        clockStartBox.addItem(Bundle.getString("None"));
        clockStartBox.addItem(Bundle.getString("MenuItemNixieClock"));
        clockStartBox.addItem(Bundle.getString("MenuItemAnalogClock"));
        clockStartBox.addItem(Bundle.getString("MenuItemLcdClock"));
		clockStartBox.setSelectedIndex(startNone);
		if (clock.getStartClockOption()==Timebase.NIXIE_CLOCK)
			clockStartBox.setSelectedIndex(startNixieClock);
		else if (clock.getStartClockOption()==Timebase.ANALOG_CLOCK)
			clockStartBox.setSelectedIndex(startAnalogClock);
		else if (clock.getStartClockOption()==Timebase.LCD_CLOCK)
			clockStartBox.setSelectedIndex(startLcdClock);
        clockStartBox.setToolTipText(Bundle.getString("TipClockStartOption"));
		clockStartBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				setClockStartChanged();
			}
		});
		panel6.add(panel63);
		
        Border panel6Border = BorderFactory.createEtchedBorder();
        Border panel6Titled = BorderFactory.createTitledBorder(panel6Border,
                                                Bundle.getString("BoxLabelStartUp"));
        panel6.setBorder(panel6Titled);
        contentPane.add(panel6);		

        // Set up clock information panel
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
        JPanel panel31 = new JPanel();

        panel31.add(clockStatus);
        panel3.add(panel31);
        JPanel panel32 = new JPanel();
        panel32.add(new JLabel(Bundle.getString("CurrentTime")+" "));
        setTimeLabel();
        panel32.add(timeLabel);
        panel3.add(panel32);
        Border panel3Border = BorderFactory.createEtchedBorder();
        Border panel3Titled = BorderFactory.createTitledBorder(panel3Border,
                                                Bundle.getString("BoxLabelClockState"));
        panel3.setBorder(panel3Titled);
        contentPane.add(panel3);

        // Set up Start and Stop buttons
        JPanel panel4 = new JPanel();
        startButton.setToolTipText(Bundle.getString("TipStartButton"));
        startButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    startButtonActionPerformed();
                }
            });
        panel4.add(startButton);
        stopButton.setToolTipText(Bundle.getString("TipStopButton"));
        stopButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    stopButtonActionPerformed();
                }
            });
        panel4.add(stopButton);
        contentPane.add(panel4);

		// update contents for current status
		updateRunningButton();

        // add help menu to window
    	addHelpMenu("package.jmri.jmrit.simpleclock.SimpleClockFrame", true);

        // pack for display
        pack();

        // listen for changes to the timebase parameters
        clock.addPropertyChangeListener(this);

        // request callback to update time
        clock.addMinuteChangeListener( new java.beans.PropertyChangeListener() {
                    public void propertyChange(java.beans.PropertyChangeEvent e) {
                        updateTime();
                    }
                });

        return;
    }

    /**
     * Method to adjust to rate changes
     */
    void updateRate() {
        factorField.setText(threeDigits.format(clock.userGetRate()));
		changed = true;
	}

    /**
     * Method to adjust to running state changes
     */
    void updateRunningButton() {
    	boolean running = clock.getRun();
        if (running) {
            clockStatus.setText(Bundle.getString("ClockRunning"));
            startButton.setVisible(false);
            stopButton.setVisible(true);
        }
        else {
            clockStatus.setText(Bundle.getString("ClockStopped"));
            startButton.setVisible(true);
            stopButton.setVisible(false);
        }
        clockStatus.setVisible(true);
	}

    /**
     * Method to handle Set Rate button
     */
    public void setRateButtonActionPerformed() {
        double rate = 1.0;
        try {
            rate = Double.valueOf(factorField.getText()).doubleValue();
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("ParseRateError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when parsing Rate Field: "+e);
            return;
        }
		if (rate < 0.0) {
            JOptionPane.showMessageDialog(this,Bundle.getString("NegativeRateError"),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
			factorField.setText(threeDigits.format(clock.userGetRate()));
			return;
		}
		if (InstanceManager.clockControlInstance().requiresIntegerRate()) {
			double frac = rate-(int)rate;
			if (frac > 0.001) {
				JOptionPane.showMessageDialog(this,Bundle.getString("NonIntegerError"),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
				factorField.setText(threeDigits.format(clock.userGetRate()));
				return;
			}
		}
        try {
            clock.userSetRate(rate);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("SetRateError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when setting timebase rate: "+e);
        }
		changed = true;
    }
	
	/** 
	 * Method to handle time source change
	 */
	private void setTimeSourceChanged() {
		int index = timeSourceBox.getSelectedIndex();
		int oldIndex = internalSourceIndex;
		if (!clock.getInternalMaster()) oldIndex = hardwareSourceIndex;
		// return if nothing changed
		if ( oldIndex == index ) return;
		// change the time source master
		if (index == internalSourceIndex) clock.setInternalMaster(true,true);
		else clock.setInternalMaster(false,true);
		changed = true;
	}
	
	/** 
	 * Method to handle synchronize check box change
	 */
	private void synchronizeChanged() {
		clock.setSynchronize(synchronizeCheckBox.isSelected(),true);
		changed = true;
	}
	
	/** 
	 * Method to handle correct check box change
	 */
	private void correctChanged() {
		clock.setCorrectHardware(correctCheckBox.isSelected(),true);
		changed = true;
	}
 
	/** 
	 * Method to handle 12-hour display check box change
	 */
	private void displayChanged() {
		clock.set12HourDisplay(displayCheckBox.isSelected(),true);
		changed = true;
	}

    /**
     * Method to handle Set Time button
     */
    @SuppressWarnings("deprecation")
    public void setTimeButtonActionPerformed() {
        int hours = 0;
        int minutes = 0;
        // get hours, reporting errors if any
        try {
            hours = Integer.parseInt(hoursField.getText());
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when parsing hours Field: "+e);
            return;
        }
        if ( (hours<0) || (hours>23) ) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursRangeError")),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            return;
        }
        // get minutes, reporting errors if any
        try {
            minutes = Integer.parseInt(minutesField.getText());
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when parsing hours Field: "+e);
            return;
        }
        if ( (minutes<0) || (minutes>59) ) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("MinutesRangeError")),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            return;
        }
        // set time of the fast clock
        long mSecPerHour = 3600000;
        long mSecPerMinute = 60000;
        Date tem = clock.getTime();
        int cHours = tem.getHours();
        long cNumMSec = tem.getTime();
        long nNumMSec = ((cNumMSec/mSecPerHour)*mSecPerHour) - (cHours*mSecPerHour) +
                    (hours*mSecPerHour) + (minutes*mSecPerMinute);
        clock.userSetTime(new Date(nNumMSec));
        showTime = true;
		updateTime();
    }
	
	/** 
	 * Method to handle start stopped check box change
	 */
	private void startStoppedChanged() {
		clock.setStartStopped(startStoppedCheckBox.isSelected());
		changed = true;
	}
	
	/** 
	 * Method to handle start set time check box change
	 */
	@SuppressWarnings("deprecation")
	private void startSetTimeChanged() {
        int hours = 0;
        int minutes = 0;
        // get hours, reporting errors if any
        try {
            hours = Integer.parseInt(startHoursField.getText());
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when parsing hours Field: "+e);
            return;
        }
        if ( (hours<0) || (hours>23) ) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursRangeError")),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            return;
        }
        // get minutes, reporting errors if any
        try {
            minutes = Integer.parseInt(startMinutesField.getText());
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("HoursError")+"\n"+e),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            log.error("Exception when parsing hours Field: "+e);
            return;
        }
        if ( (minutes<0) || (minutes>59) ) {
            JOptionPane.showMessageDialog(this,(Bundle.getString("MinutesRangeError")),
                    Bundle.getString("ErrorTitle"),JOptionPane.ERROR_MESSAGE);
            return;
        }
        // set time of the fast clock
        long mSecPerHour = 3600000;
        long mSecPerMinute = 60000;
        Date tem = clock.getTime();
        int cHours = tem.getHours();
        long cNumMSec = tem.getTime();
        long nNumMSec = ((cNumMSec/mSecPerHour)*mSecPerHour) - (cHours*mSecPerHour) +
                    (hours*mSecPerHour) + (minutes*mSecPerMinute);	
		clock.setStartSetTime(startSetTimeCheckBox.isSelected(),new Date(nNumMSec));
		changed = true;
	}
	
	/** 
	 * Method to handle start clock combo box change
	 */
	private void setClockStartChanged() {
		int sel = Timebase.NONE;
		if (clockStartBox.getSelectedIndex()==startNixieClock) 
			sel = Timebase.NIXIE_CLOCK;
		else if (clockStartBox.getSelectedIndex()==startAnalogClock)
			sel = Timebase.ANALOG_CLOCK;
		else if (clockStartBox.getSelectedIndex()==startLcdClock)
			sel = Timebase.LCD_CLOCK;
		clock.setStartClockOption(sel);
		changed = true;
	}

    /**
     * Method to handle Start Clock button
     */
    public void startButtonActionPerformed() {
        clock.setRun(true);
    }

    /**
     * Method to handle Stop Clock button
     */
    public void stopButtonActionPerformed() {
        clock.setRun(false);
    }

    /**
     * Method to update clock state information
     */
    void updateTime() {
        if (clock.getRun() || showTime) {
            showTime = false;
            setTimeLabel();
            timeLabel.setVisible(true);
        }
    }

    /**
     * Method to set the current Timebase time into timeLabel
     */
    @SuppressWarnings("deprecation")
    void setTimeLabel() {
        // Get time
        Date now = clock.getTime();
        int hours = now.getHours();
        int minutes = now.getMinutes();
        // Format and display the time
        timeLabel.setText(" "+(hours/10)+(hours-(hours/10)*10)+":"+
                                (minutes/10)+(minutes-(minutes/10)*10));
    }

    /**
     * Handle a change to clock properties
     */
    public void propertyChange(java.beans.PropertyChangeEvent e) {
		updateRunningButton();
		updateRate();
    }

    /**
     * Method to handle window closing event
     */
    public void windowClosing(java.awt.event.WindowEvent e) {
		if (changed) {
			// remind to save		
			javax.swing.JOptionPane.showMessageDialog(null,
					Bundle.getString("Reminder1")+"\n"+Bundle.getString("Reminder2"),
						Bundle.getString("ReminderTitle"),
							javax.swing.JOptionPane.INFORMATION_MESSAGE);
			changed = false;
		}	
        setVisible(false);
        super.windowClosing(e);
    }

    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SimpleClockFrame.class.getName());
}

/* @(#)SimpleClockFrame.java */
