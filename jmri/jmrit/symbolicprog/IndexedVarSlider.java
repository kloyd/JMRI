// IndexedVarSlider.java
package jmri.jmrit.symbolicprog;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/* Extends a JSlider so that its color & value are consistent with
 * an underlying variable; we return one of these in IndexedVariableValue.getRep.
 *
 * @author    Howard G. Penny   Copyright (C) 2005
 * @version   $Revision: 1.2 $
 */
public class IndexedVarSlider extends JSlider implements ChangeListener {

    IndexedVariableValue _iVar;

    IndexedVarSlider(IndexedVariableValue iVar, int min, int max) {
        super(new DefaultBoundedRangeModel(min, 0, min, max));
        _iVar = iVar;
        // get the original color right
        setBackground(_iVar.getColor());
        // set the original value
        setValue(Integer.valueOf(_iVar.getValueString()).intValue());
        // listen for changes here
        addChangeListener(this);
        // listen for changes to associated variable
        _iVar.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                originalPropertyChanged(e);
            }
        });
    }

    public void stateChanged(ChangeEvent e) {
        // called for new values of a slider - set the variable value as needed
        // e.getSource() points to the JSlider object - find it in the list
        JSlider j = (JSlider) e.getSource();
        BoundedRangeModel r = j.getModel();

        _iVar.setIntValue(r.getValue());
        _iVar.setState(AbstractValue.EDITED);
    }

    void originalPropertyChanged(java.beans.PropertyChangeEvent e) {
        if (log.isDebugEnabled()) log.debug("IndexedVarSlider saw property change: "+e);
        // update this color from original state
        if (e.getPropertyName().equals("State")) {
            setBackground(_iVar.getColor());
        }
        if (e.getPropertyName().equals("Value")) {
            int newValue = Integer.valueOf(((JTextField)_iVar.getValue()).getText()).intValue();
            setValue(newValue);
        }
    }

    // initialize logging
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(IndexedVarSlider.class.getName());
}
