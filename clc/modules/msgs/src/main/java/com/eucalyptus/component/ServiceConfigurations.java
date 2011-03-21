package com.eucalyptus.component;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import javax.persistence.PersistenceException;
import org.apache.log4j.Logger;
import com.eucalyptus.config.ComponentConfiguration;
import com.eucalyptus.config.LocalConfiguration;
import com.eucalyptus.config.RemoteConfiguration;
import com.eucalyptus.entities.EntityWrapper;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.Internets;

public class ServiceConfigurations {
  private static Logger                       LOG       = Logger.getLogger( ServiceConfigurations.class );
  private static ServiceConfigurationProvider singleton = new DatabaseServiceConfigurationProvider( );
  
  public static ServiceConfigurationProvider getInstance( ) {
    return singleton;
  }
  
  public static <T extends ServiceConfiguration> List<T> getConfigurations( Class<T> type ) throws PersistenceException {
    if( !ComponentConfiguration.class.isAssignableFrom( type ) ) {
      throw new PersistenceException( "Unknown configuration type passed: " + type.getCanonicalName( ) );
    }
    EntityWrapper<T> db = EntityWrapper.get( type );
    List<T> componentList;
    try {
      componentList = db.query( type.newInstance( ) );
      db.commit( );
      return componentList;
    } catch ( PersistenceException ex ) {
      LOG.error( ex , ex );
      db.rollback( );
      throw ex;
    } catch ( Throwable ex ) {
      LOG.error( ex, ex );
      db.rollback( );
      throw new PersistenceException( ex );
    }
  }

  public static <T extends ServiceConfiguration> List<T> getPartitionConfigurations( Class<T> type, String partition ) throws PersistenceException, NoSuchElementException {
    if( !ComponentConfiguration.class.isAssignableFrom( type ) ) {
      throw new PersistenceException( "Unknown configuration type passed: " + type.getCanonicalName( ) );
    }
    EntityWrapper<T> db = EntityWrapper.get( type );
    List<T> componentList;
    try {
      T conf = type.newInstance( );
      conf.setPartition( partition );
      componentList = db.query( conf );
      if( componentList.isEmpty( ) ) {
        throw new NoSuchElementException( "Failed to lookup registration for " + type.getSimpleName( ) + " in partition: " + partition ); 
      }
      db.commit( );
      return componentList;
    } catch ( NoSuchElementException ex ) {
      db.rollback( );
      throw ex;
    } catch ( PersistenceException ex ) {
      LOG.error( ex , ex );
      db.rollback( );
      throw ex;
    } catch ( Throwable ex ) {
      LOG.error( ex, ex );
      db.rollback( );
      throw new PersistenceException( ex );
    }
  }

  public static <T extends ServiceConfiguration> T getConfiguration( Class<T> type, String uniqueName ) throws PersistenceException, NoSuchElementException {
    if( !ComponentConfiguration.class.isAssignableFrom( type ) ) {
      throw new PersistenceException( "Unknown configuration type passed: " + type.getCanonicalName( ) );
    }
    EntityWrapper<T> db = EntityWrapper.get( type );
    try {
      T conf = type.newInstance( );
      conf.setName( uniqueName );
      T configuration = db.getUnique( conf );
      db.commit( );
      return configuration;
    } catch ( EucalyptusCloudException ex ) {
      LOG.trace( ex );
      db.rollback( );
      throw new NoSuchElementException( ex.getMessage( ) );
    } catch ( PersistenceException ex ) {
      LOG.error( ex, ex );
      db.rollback( );
      throw ex;
    } catch ( Throwable ex ) {
      LOG.error( ex, ex );
      db.rollback( );
      throw new PersistenceException( ex );
    }
  }

  public static ServiceConfiguration uriToServiceConfiguration( Component component, URI uri ) {
    String partition = "bootstrap".equals( component.getName( ) ) ? component.getName( ) : "eucalyptus"; 
    String name = component.getName( );
    try {      
      if( uri.getScheme( ).matches( ".*vm.*" ) || ( uri.getHost( ) != null && Internets.testLocal( uri.getHost( ) ) ) ) {
        return new LocalConfiguration( component.getComponentId( ), partition, name, uri );      
      } else {
        return new RemoteConfiguration( component.getComponentId( ), partition, name, uri );      
      }
    } catch ( Throwable t ) {
      return new LocalConfiguration( component.getComponentId( ), partition, name, uri );      
    }
  }
  
}
