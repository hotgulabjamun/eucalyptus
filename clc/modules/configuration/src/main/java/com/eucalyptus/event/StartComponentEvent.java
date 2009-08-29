package com.eucalyptus.event;

import com.eucalyptus.bootstrap.Component;
import com.eucalyptus.config.ComponentConfiguration;
import com.eucalyptus.config.LocalConfiguration;

public class StartComponentEvent extends ComponentEvent {

  public static StartComponentEvent getLocal( Component c ) {
    return new StartComponentEvent( new LocalConfiguration(c), c, true );
  }
  public static StartComponentEvent getRemote( ComponentConfiguration config ) {
    return new StartComponentEvent( config, config.getComponent( ), false );
  }
  
  private StartComponentEvent( ComponentConfiguration configuration, Component component, boolean local ) {
    super( configuration, component, local );
  }

}