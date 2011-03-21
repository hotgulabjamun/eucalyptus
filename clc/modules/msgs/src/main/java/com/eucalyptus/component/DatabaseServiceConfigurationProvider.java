package com.eucalyptus.component;

import java.util.List;
import org.apache.log4j.Logger;
import com.eucalyptus.entities.EntityWrapper;
import com.eucalyptus.records.EventClass;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.util.LogUtil;
import com.google.common.collect.Lists;

public class DatabaseServiceConfigurationProvider<T extends ServiceConfiguration> implements ServiceConfigurationProvider<T> {

  /**
   * TODO: DOCUMENT DatabaseServiceConfigurationProvider.java
   * @param type
   * @return
   * @throws ServiceRegistrationException
   */
  public <T extends ServiceConfiguration> List<T> list( T type ) throws ServiceRegistrationException {
    EntityWrapper<T> db = EntityWrapper.get( type );
    List<T> existingHosts = null;
    try {
      T searchConfig = ( T ) type.getClass( ).newInstance( );
      existingHosts = db.query( searchConfig );
      db.rollback( );
    } catch ( Exception e ) {
      existingHosts = Lists.newArrayList( );
    }    
    return existingHosts;
  }
  
  @Override
  public <T extends ServiceConfiguration> T lookup( T type ) throws ServiceRegistrationException {
    EntityWrapper<T> db = EntityWrapper.get( type );
    T existingName = null;
    try {
      existingName = db.getUnique( type );
      db.rollback( );
      return existingName;
    } catch ( Exception e ) {
      throw new ServiceRegistrationException( "Component lookup failed for: " + LogUtil.dumpObject( type ) );
    }    
  }
      
  private static Logger LOG = Logger.getLogger( DatabaseServiceConfigurationProvider.class );
  
  @Override
  public <T extends ServiceConfiguration> T store( T t ) throws ServiceRegistrationException {
    EntityWrapper<T> db = EntityWrapper.get( t );
    try {
      db.add( t );
      t = db.getUnique( t );
      db.commit( );
      EventRecord.here( ServiceConfigurationProvider.class, EventClass.COMPONENT, EventType.COMPONENT_REGISTERED ).withDetails( t.getComponentId( ).name( ), t.getName( ), "host", t.getHostName( ) ).info( );
    } catch ( Exception e ) {
      db.rollback( );
      LOG.error( e, e );
      throw new ServiceRegistrationException( e );
    }
    return t;
  }

  @Override
  public <T extends ServiceConfiguration> T remove( T t ) throws ServiceRegistrationException {
    EntityWrapper<T> db = EntityWrapper.get( t );
    try {
      T searchConfig = ( T ) t.getClass( ).newInstance( );
      searchConfig.setName( t.getName( ) );
      T exists = db.getUnique( searchConfig );
      db.delete( exists );
      db.commit( );
      EventRecord.here( ServiceConfigurationProvider.class, EventClass.COMPONENT, EventType.COMPONENT_DEREGISTERED ).withDetails( t.getComponentId( ).name( ), t.getName( ), "host", t.getHostName( )  ).info( );
    } catch ( Exception e ) {
      db.rollback( );
      LOG.error( e, e );
      throw new ServiceRegistrationException( e );
    }
    return t;
  }

}
