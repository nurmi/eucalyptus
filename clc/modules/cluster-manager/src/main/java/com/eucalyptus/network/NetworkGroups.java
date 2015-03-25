/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.network;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.hibernate.exception.ConstraintViolationException;
import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.principal.AccountFullName;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.auth.principal.UserFullName;
import com.eucalyptus.cluster.ClusterConfiguration;
import com.eucalyptus.compute.common.CloudMetadata;
import com.eucalyptus.compute.common.CloudMetadatas;
import com.eucalyptus.cloud.util.DuplicateMetadataException;
import com.eucalyptus.cloud.util.MetadataConstraintException;
import com.eucalyptus.cloud.util.MetadataException;
import com.eucalyptus.cloud.util.NoSuchMetadataException;
import com.eucalyptus.compute.common.IpPermissionType;
import com.eucalyptus.compute.common.SecurityGroupItemType;
import com.eucalyptus.compute.common.UserIdGroupPairType;
import com.eucalyptus.compute.identifier.ResourceIdentifiers;
import com.eucalyptus.compute.vpc.Vpc;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableField;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.PersistenceExceptions;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.entities.TransactionResource;
import com.eucalyptus.entities.Transactions;
import com.eucalyptus.network.config.NetworkConfigurations;
import com.eucalyptus.records.Logs;
import com.eucalyptus.tags.FilterSupport;
import com.eucalyptus.util.Callback;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.RestrictedTypes;
import com.eucalyptus.util.TypeMapper;
import com.eucalyptus.util.TypeMappers;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

@ConfigurableClass( root = "cloud.network",
                    description = "Default values used to bootstrap networking state discovery." )
public class NetworkGroups {
  private static final String DEFAULT_NETWORK_NAME          = "default";
  public static final Pattern VPC_GROUP_NAME_PATTERN       = Pattern.compile( "[a-zA-Z0-9 ._\\-:/()#,@\\[\\]+=&;{}!$*]{1,255}" );
  public static final Pattern VPC_GROUP_DESC_PATTERN       = Pattern.compile( "[a-zA-Z0-9 ._\\-:/()#,@\\[\\]+=&;{}!$*]{0,255}" );
  private static Logger       LOG                           = Logger.getLogger( NetworkGroups.class );

  @ConfigurableField( description = "Default max network index." )
  public static Long          GLOBAL_MAX_NETWORK_INDEX      = 4096l;
  @ConfigurableField( description = "Default min network index." )
  public static Long          GLOBAL_MIN_NETWORK_INDEX      = 2l;
  @ConfigurableField( description = "Default max vlan tag." )
  public static Integer       GLOBAL_MAX_NETWORK_TAG        = 4096;
  @ConfigurableField( description = "Default min vlan tag." )
  public static Integer       GLOBAL_MIN_NETWORK_TAG        = 1;
  @ConfigurableField( description = "Minutes before a pending tag allocation timesout and is released." )
  public static Integer       NETWORK_TAG_PENDING_TIMEOUT   = 35;
  @ConfigurableField( description = "Minutes before a pending index allocation timesout and is released." )
  public static Integer       NETWORK_INDEX_PENDING_TIMEOUT = 35;
  @ConfigurableField(
      description = "Network configuration document.",
      changeListener = NetworkConfigurations.NetworkConfigurationPropertyChangeListener.class )
  public static String        NETWORK_CONFIGURATION = "";
  @ConfigurableField( description = "Minimum interval between broadcasts of network information (seconds)." )
  public static Integer       MIN_BROADCAST_INTERVAL = 5;


  public static class NetworkRangeConfiguration {
    private Integer minNetworkTag   = GLOBAL_MIN_NETWORK_TAG;
    private Integer maxNetworkTag   = GLOBAL_MAX_NETWORK_TAG;
    private Long    minNetworkIndex = GLOBAL_MIN_NETWORK_INDEX;
    private Long    maxNetworkIndex = GLOBAL_MAX_NETWORK_INDEX;
    
    public Integer getMinNetworkTag( ) {
      return this.minNetworkTag;
    }
    
    public void setMinNetworkTag( final Integer minNetworkTag ) {
      
      this.minNetworkTag = minNetworkTag;
    }
    
    public Integer getMaxNetworkTag( ) {
      return this.maxNetworkTag;
    }
    
    public void setMaxNetworkTag( final Integer maxNetworkTag ) {
      this.maxNetworkTag = maxNetworkTag;
    }
    
    public Long getMaxNetworkIndex( ) {
      return this.maxNetworkIndex;
    }
    
    public void setMaxNetworkIndex( final Long maxNetworkIndex ) {
      this.maxNetworkIndex = maxNetworkIndex;
    }
    
    public Long getMinNetworkIndex( ) {
      return this.minNetworkIndex;
    }
    
    public void setMinNetworkIndex( final Long minNetworkIndex ) {
      this.minNetworkIndex = minNetworkIndex;
    }
    
    @Override
    public String toString( ) {
      StringBuilder builder = new StringBuilder( );
      builder.append( "NetworkRangeConfiguration:" );
      if ( this.minNetworkTag != null ) builder.append( "minNetworkTag=" ).append( this.minNetworkTag ).append( ":" );
      if ( this.maxNetworkTag != null ) builder.append( "maxNetworkTag=" ).append( this.maxNetworkTag ).append( ":" );
      if ( this.minNetworkIndex != null ) builder.append( "minNetworkIndex=" ).append( this.minNetworkIndex ).append( ":" );
      if ( this.maxNetworkIndex != null ) builder.append( "maxNetworkIndex=" ).append( this.maxNetworkIndex );
      return builder.toString( );
    }
    
  }

  static NetworkRangeConfiguration netConfig = new NetworkRangeConfiguration( );

  public static synchronized void updateNetworkRangeConfiguration( ) {
    final AtomicReference<NetworkRangeConfiguration> equalityCheck = new AtomicReference<NetworkRangeConfiguration>( null );
    try {
      Transactions.each( new ClusterConfiguration( ), new Callback<ClusterConfiguration>( ) {

        @Override
        public void fire( final ClusterConfiguration input ) {
          NetworkRangeConfiguration comparisonConfig = new NetworkRangeConfiguration( );
          comparisonConfig.setMinNetworkTag( input.getMinNetworkTag( ) );
          comparisonConfig.setMaxNetworkTag( input.getMaxNetworkTag( ) );
          comparisonConfig.setMinNetworkIndex( input.getMinNetworkIndex( ) );
          comparisonConfig.setMaxNetworkIndex( input.getMaxNetworkIndex( ) );
          Logs.extreme( ).debug( "Updating cluster config: " + input.getName( ) + " " + comparisonConfig.toString( ) );
          if ( equalityCheck.compareAndSet( null, comparisonConfig ) ) {
            Logs.extreme( ).debug( "Initialized cluster config check: " + equalityCheck.get( ) );
          } else {
            NetworkRangeConfiguration currentConfig = equalityCheck.get( );
            List<String> errors = Lists.newArrayList( );
            if ( !currentConfig.getMinNetworkTag( ).equals( comparisonConfig.getMinNetworkTag( ) ) ) {
              errors.add( input.getName( ) + " network config mismatch: min vlan tag " + currentConfig.getMinNetworkTag( ) + " != " + comparisonConfig.getMinNetworkTag( ) );
            } else if ( !currentConfig.getMaxNetworkTag( ).equals( comparisonConfig.getMaxNetworkTag( ) ) ) {
              errors.add( input.getName( ) + " network config mismatch: max vlan tag " + currentConfig.getMaxNetworkTag( ) + " != " + comparisonConfig.getMaxNetworkTag( ) );
            } else if ( !currentConfig.getMinNetworkIndex( ).equals( comparisonConfig.getMinNetworkIndex( ) ) ) {
              errors.add( input.getName( ) + " network config mismatch: min net index " + currentConfig.getMinNetworkIndex( ) + " != " + comparisonConfig.getMinNetworkIndex( ) );
            } else if ( !currentConfig.getMaxNetworkIndex( ).equals( comparisonConfig.getMaxNetworkIndex( ) ) ) {
              errors.add( input.getName( ) + " network config mismatch: max net index " + currentConfig.getMaxNetworkIndex( ) + " != " + comparisonConfig.getMaxNetworkIndex( ) );
            }
          }
        }
      } );
    } catch ( RuntimeException ex ) {
      Logs.extreme( ).error( ex, ex );
      throw ex;
    } catch ( TransactionException ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }

    netConfig = new NetworkRangeConfiguration( );
    try {
      Transactions.each( new ClusterConfiguration( ), new Callback<ClusterConfiguration>( ) {

        @Override
        public void fire( final ClusterConfiguration input ) {
          netConfig.setMinNetworkTag( Ints.max( netConfig.getMinNetworkTag(), input.getMinNetworkTag() ) );
          netConfig.setMaxNetworkTag( Ints.min( netConfig.getMaxNetworkTag( ), input.getMaxNetworkTag( ) ) );
          netConfig.setMinNetworkIndex( Longs.max( netConfig.getMinNetworkIndex(), input.getMinNetworkIndex() ) );
          netConfig.setMaxNetworkIndex( Longs.min( netConfig.getMaxNetworkIndex( ), input.getMaxNetworkIndex( ) ) );
        }
      } );
      Logs.extreme( ).debug( "Updated network configuration: " + netConfig.toString( ) );
    } catch ( final TransactionException ex ) {
      Logs.extreme( ).error( ex, ex );
    }
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      final List<NetworkGroup> ret = Entities.query( new NetworkGroup( ) );
      for ( NetworkGroup group : ret ) {
        ExtantNetwork exNet = group.getExtantNetwork( );
        if ( exNet != null && ( exNet.getTag( ) > netConfig.getMaxNetworkTag( ) || exNet.getTag( ) < netConfig.getMinNetworkTag( ) ) ) {
          if ( exNet.teardown( ) ) {
            Entities.delete( exNet );
            group.clearExtantNetwork( );
          }
        }
      }
      db.commit( );
    } catch ( final Exception ex ) {
      Logs.extreme( ).error( ex, ex );
      LOG.error( ex );
    }
  }

  public static List<Long> networkIndexInterval( ) {
    final List<Long> interval = Lists.newArrayList( );
    for ( Long i = NetworkGroups.networkingConfiguration( ).getMinNetworkIndex( ); i < NetworkGroups.networkingConfiguration( ).getMaxNetworkIndex( ); i++ ) {
      interval.add( i );
    }
    return interval;
  }
  
  public static List<Integer> networkTagInterval( ) {
    final List<Integer> interval = Lists.newArrayList( );
    for ( Integer i = NetworkGroups.networkingConfiguration( ).getMinNetworkTag( ); i < NetworkGroups.networkingConfiguration( ).getMaxNetworkTag( ); i++ ) {
      interval.add( i );
    }
    return interval;
  }
  
  public static synchronized NetworkRangeConfiguration networkingConfiguration( ) {
    return netConfig;
  }
  
  public static NetworkGroup delete( final String groupId ) throws MetadataException {
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      final NetworkGroup ret = Entities.uniqueResult( NetworkGroup.withGroupId( null, groupId ) );
      Entities.delete( ret );
      db.commit( );
      return ret;
    } catch ( final ConstraintViolationException ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new MetadataConstraintException( "Failed to delete security group: " + groupId + " because of: "
                                                + Exceptions.causeString( ex ), ex );
    } catch ( final Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchMetadataException( "Failed to find security group: " + groupId, ex );
    }
  }
  
  public static NetworkGroup lookupByNaturalId( final String uuid ) throws NoSuchMetadataException {
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      NetworkGroup entity = Entities.uniqueResult( NetworkGroup.withNaturalId( uuid ) );
      db.commit( );
      return entity;
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchMetadataException( "Failed to find security group: " + uuid, ex );
    }
  }
  
  public static NetworkGroup lookupByGroupId( final String groupId ) throws NoSuchMetadataException {
    return lookupByGroupId( null, groupId );
  }

  public static NetworkGroup lookupByGroupId( @Nullable final OwnerFullName ownerFullName,
                                              final String groupId ) throws NoSuchMetadataException {
      try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
        NetworkGroup entity = Entities.uniqueResult( NetworkGroup.withGroupId(ownerFullName, groupId) );
        db.commit( );
        return entity;
      } catch ( Exception ex ) {
        Logs.exhaust( ).error( ex, ex );
        throw new NoSuchMetadataException( "Failed to find security group: " + groupId, ex );
      }
    }
  
  public static NetworkGroup lookup( final OwnerFullName ownerFullName, final String groupName ) throws MetadataException {
    if ( defaultNetworkName( ).equals( groupName ) ) {
      createDefault( ownerFullName );
    }
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      NetworkGroup ret = Entities.uniqueResult( NetworkGroup.named( ownerFullName, groupName ) );
      db.commit( );
      return ret;
    } catch ( final Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchMetadataException( "Failed to find security group: " + groupName + " for " + ownerFullName, ex );
    }
  }

  public static NetworkGroup lookupDefault( final OwnerFullName ownerFullName,
                                            final String vpcId ) throws MetadataException {
    return lookup( ownerFullName, vpcId, defaultNetworkName( ) );
  }

  public static NetworkGroup lookup( final OwnerFullName ownerFullName,
                                     final String vpcId,
                                     final String name ) throws MetadataException {
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      return Entities.uniqueResult( ownerFullName == null && vpcId != null ?
              NetworkGroup.namedForVpc( vpcId, name ) :
              NetworkGroup.withUniqueName( ownerFullName, vpcId, name )
      );
    } catch ( final Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchMetadataException( "Failed to find security group: " + name +", for vpc: " + vpcId + " for " + ownerFullName, ex );
    }
  }

  public static void periodicCleanup( ) {
    updateNetworkRangeConfiguration( );
    releaseUnusedExtantNetworks( );
  }

  private static void releaseUnusedExtantNetworks( ) {
    try {
      final List<NetworkGroup> groups = NetworkGroups.lookupUnusedExtant( );
      for ( NetworkGroup net : groups ) {
        try ( final TransactionResource tx = Entities.distinctTransactionFor( NetworkGroup.class ) ) {
          net = Entities.merge( net );
          if ( net.hasExtantNetwork( ) && !net.getExtantNetwork( ).inUse( ) ) {
            final ExtantNetwork exNet = net.getExtantNetwork( );
            if ( exNet.teardown( ) ) {
              Entities.delete( exNet );
              net.clearExtantNetwork( );
            }
          }
          tx.commit( );
        } catch ( final Exception ex ) {
          LOG.debug( ex );
          Logs.extreme( ).error( ex, ex );
        }
      }
    } catch ( MetadataException ex ) {
      LOG.error( ex );
    }
  }

  private static List<NetworkGroup> lookupUnusedExtant( ) throws MetadataException {
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      return Entities.query(
          NetworkGroup.withOwner( null ),
          true,
          Restrictions.and(
              Restrictions.lt( "extantNetwork.lastUpdateTimestamp", new Date( System.currentTimeMillis( ) - TimeUnit.MINUTES.toMillis( 1 ) ) ),
              Restrictions.isNotNull( "extantNetwork.tag" ),
              Restrictions.isEmpty( "extantNetwork.indexes" )
          ),
          ImmutableMap.of(
              "extantNetwork", "extantNetwork"
          )
      );
    } catch ( final Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchMetadataException( "Error looking up extant groups", ex );
    }
  }

  public static Function<NetworkGroup,String> groupId() {
    return FilterFunctions.GROUP_ID;
  }

  public static Function<NetworkGroup,String> vpcId() {
    return FilterFunctions.VPC_ID;
  }

  static void createDefault( final OwnerFullName ownerFullName ) throws MetadataException {
    try ( final TransactionResource tx = Entities.transactionFor( Vpc.class ) ) {
      if ( Iterables.tryFind(
          Entities.query( Vpc.exampleDefault( ownerFullName.getAccountNumber( ) ) ),
          Predicates.alwaysTrue()
      ).isPresent( ) ) {
        return; // skip default security group creation when there is a default VPC
      }
    }

    try {
      try {
        NetworkGroup net = Transactions.find( NetworkGroup.named( AccountFullName.getInstance( ownerFullName.getAccountNumber() ), DEFAULT_NETWORK_NAME ) );
        if ( net == null ) {
          create( ownerFullName, DEFAULT_NETWORK_NAME, "default group" );
        }
      } catch ( NoSuchElementException | TransactionException ex ) {
        try {
          create( ownerFullName, DEFAULT_NETWORK_NAME, "default group" );
        } catch ( ConstraintViolationException ex1 ) {}
      }
    } catch ( DuplicateMetadataException ex ) {}
  }
  
  public static String defaultNetworkName( ) {
    return DEFAULT_NETWORK_NAME;
  }

  public static NetworkGroup create( final OwnerFullName ownerFullName,
                                     final String groupName,
                                     final String groupDescription ) throws MetadataException {
    return create( ownerFullName, null, groupName, groupDescription );
  }

  public static NetworkGroup create( final OwnerFullName ownerFullName,
                                     final Vpc vpc,
                                     final String groupName,
                                     final String groupDescription ) throws MetadataException {
    UserFullName userFullName = null;
    if ( ownerFullName instanceof UserFullName ) {
      userFullName = ( UserFullName ) ownerFullName;
    } else {
      try {
        User admin = Accounts.lookupAccountById( ownerFullName.getAccountNumber( ) ).lookupAdmin();
        userFullName = UserFullName.getInstance( admin );
      } catch ( Exception ex ) {
        LOG.error( ex, ex );
        throw new NoSuchMetadataException( "Failed to create group because owning user could not be identified.", ex );
      }
    }

    final String resourceDesc = groupName + ( vpc != null ? " in " + vpc.getDisplayName( ) : "" ) +
        " for " + userFullName.toString( );
    try ( final TransactionResource db = Entities.transactionFor( NetworkGroup.class ) ) {
      try {
        Entities.uniqueResult( NetworkGroup.withUniqueName(
            userFullName.asAccountFullName( ),
            CloudMetadatas.toDisplayName().apply( vpc ),
            groupName ) );
        throw new DuplicateMetadataException( "Failed to create group: " + resourceDesc );
      } catch ( final NoSuchElementException ex ) {
        final NetworkGroup entity = Entities.persist( NetworkGroup.create( userFullName, vpc, ResourceIdentifiers.generateString( NetworkGroup.ID_PREFIX ), groupName, groupDescription ) );
        db.commit();
        return entity;
      }
    } catch ( final ConstraintViolationException ex ) {
      Logs.exhaust( ).error( ex );
      throw new DuplicateMetadataException( "Failed to create group: " + resourceDesc, ex );
    } catch ( final Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new MetadataException( "Failed to create group: " + resourceDesc, PersistenceExceptions.transform( ex ) );
    }
  }

  /**
   * Resolve Group Names / Identifiers for the given permissions.
   *
   * <p>Caller must have open transaction.</p>
   *
   * @param permissions - The permissions to update
   * @param defaultUserId - The account number to use when not specified
   * @param vpcId - The identifier for the VPC if the a VPC security group
   * @param revoke - True if resolving for a revoke operation
   * @throws MetadataException If an error occurs
   */
  public static void resolvePermissions( final Iterable<IpPermissionType> permissions,
                                         final String defaultUserId,
                                         @Nullable final String vpcId,
                                         final boolean revoke ) throws MetadataException {
    for ( final IpPermissionType ipPermission : permissions ) {
      if ( ipPermission.getGroups() != null ) for ( final UserIdGroupPairType groupInfo : ipPermission.getGroups() ) {
        if ( !Strings.isNullOrEmpty( groupInfo.getSourceGroupId( ) ) ) {
          try{
            final NetworkGroup networkGroup = NetworkGroups.lookupByGroupId( groupInfo.getSourceGroupId() );
            if ( vpcId != null && !vpcId.equals( networkGroup.getVpcId( ) ) ) {
              throw new NoSuchMetadataException( "Group ("+groupInfo.getSourceGroupId()+") not found." );
            }
            groupInfo.setSourceUserId( networkGroup.getOwnerAccountNumber() );
            groupInfo.setSourceGroupName( networkGroup.getDisplayName() );
          }catch(final NoSuchMetadataException ex){
            if(!revoke)
              throw ex;
          }
        } else if ( Strings.isNullOrEmpty( groupInfo.getSourceGroupName( ) ) ) {
          throw new MetadataException( "Group ID or Group Name required." );
        } else {
          try{
            final AccountFullName accountFullName = AccountFullName.getInstance(
                Objects.firstNonNull( Strings.emptyToNull( groupInfo.getSourceUserId() ), defaultUserId ) );
            final NetworkGroup networkGroup = vpcId == null ?
                NetworkGroups.lookup( accountFullName, groupInfo.getSourceGroupName( ) ) :
                NetworkGroups.lookup( accountFullName, vpcId, groupInfo.getSourceGroupName( ) );
            groupInfo.setSourceGroupId( networkGroup.getGroupId( ) );
          }catch(final NoSuchMetadataException ex){
            if(!revoke)
              throw ex;
          }
        }
      }
    }
  }

  public static void flushRules( ) {
    NetworkInfoBroadcaster.requestNetworkInfoBroadcast( );
  }

  @TypeMapper
  public enum NetworkPeerAsUserIdGroupPairType implements Function<NetworkPeer, UserIdGroupPairType> {
    INSTANCE;
    
    @Override
    public UserIdGroupPairType apply( final NetworkPeer peer ) {
      return new UserIdGroupPairType(
          peer.getUserQueryKey( ),
          peer.getGroupName( ),
          peer.getGroupId( ) );
    }
  }
  
  @TypeMapper
  public enum NetworkRuleAsIpPerm implements Function<NetworkRule, IpPermissionType> {
    INSTANCE;
    
    @Override
    public IpPermissionType apply( final NetworkRule rule ) {
      final IpPermissionType ipPerm = new IpPermissionType( rule.getDisplayProtocol( ), rule.getLowPort( ), rule.getHighPort( ) );
      final Iterable<UserIdGroupPairType> peers = Iterables.transform( rule.getNetworkPeers( ),
                                                                       TypeMappers.lookup( NetworkPeer.class, UserIdGroupPairType.class ) );
      Iterables.addAll( ipPerm.getGroups( ), peers );
      ipPerm.setCidrIpRanges( rule.getIpRanges( ) );
      return ipPerm;
    }
  }
  
  @TypeMapper
  public enum NetworkGroupAsSecurityGroupItem implements Function<NetworkGroup, SecurityGroupItemType> {
    INSTANCE;
    @Override
    public SecurityGroupItemType apply( final NetworkGroup input ) {
      try ( final TransactionResource tx = Entities.transactionFor( NetworkGroup.class ) ) {
        final NetworkGroup netGroup = Entities.merge( input );
        final SecurityGroupItemType groupInfo = new SecurityGroupItemType( netGroup.getOwnerAccountNumber( ),
                                                                           netGroup.getGroupId( ),
                                                                           netGroup.getDisplayName( ),
                                                                           netGroup.getDescription( ),
                                                                           netGroup.getVpcId( ) );
        final Iterable<IpPermissionType> ipPerms = Iterables.transform(
            netGroup.getIngressNetworkRules( ),
            TypeMappers.lookup( NetworkRule.class, IpPermissionType.class ) );
        Iterables.addAll( groupInfo.getIpPermissions( ), ipPerms );

        final Iterable<IpPermissionType> ipPermsEgress = Iterables.transform(
            netGroup.getEgressNetworkRules( ),
            TypeMappers.lookup( NetworkRule.class, IpPermissionType.class ) );
        Iterables.addAll( groupInfo.getIpPermissionsEgress( ), ipPermsEgress );

        return groupInfo;
      }
    }
  }
  
  public enum IpPermissionTypeExtractNetworkPeers implements Function<IpPermissionType, Collection<NetworkPeer>> {
    INSTANCE;
    
    @Override
    public Collection<NetworkPeer> apply( IpPermissionType ipPerm ) {
      final Collection<NetworkPeer> networkPeers = Lists.newArrayList();
      for ( UserIdGroupPairType peerInfo : ipPerm.getGroups( ) ) {
        networkPeers.add( new NetworkPeer( peerInfo.getSourceUserId(), peerInfo.getSourceGroupName(), peerInfo.getSourceGroupId() ) );
      }
      return networkPeers;
    }
  }

  public enum IpPermissionTypeAsNetworkRule implements Function<IpPermissionType, List<NetworkRule>> {
    CLASSIC( false ),
    VPC( true );

    private final boolean anyProtocolAllowed;

    private IpPermissionTypeAsNetworkRule( boolean anyProtocolAllowed ) {
      this.anyProtocolAllowed = anyProtocolAllowed;
    }
    
    /**
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Nonnull
    @Override
    public List<NetworkRule> apply( IpPermissionType ipPerm ) {
      List<NetworkRule> ruleList = new ArrayList<NetworkRule>( );
      if ( !ipPerm.getGroups( ).isEmpty( ) ) {
        if ( ipPerm.getFromPort()!=null && ipPerm.getFromPort( ) == 0 && ipPerm.getToPort( ) != null && ipPerm.getToPort( ) == 0 ) {
          ipPerm.setToPort( 65535 );
        }
        List<String> empty = Lists.newArrayList( );
        //:: fixes handling of under-specified named-network rules sent by some clients :://
        if ( ipPerm.getIpProtocol( ) == null ) {
          NetworkRule rule = NetworkRule.create( NetworkRule.Protocol.tcp, ipPerm.getFromPort( ), ipPerm.getToPort( ),
                                                 IpPermissionTypeExtractNetworkPeers.INSTANCE.apply( ipPerm ), empty );
          ruleList.add( rule );
          NetworkRule rule1 = NetworkRule.create( NetworkRule.Protocol.udp, ipPerm.getFromPort( ), ipPerm.getToPort( ),
                                                  IpPermissionTypeExtractNetworkPeers.INSTANCE.apply( ipPerm ), empty );
          ruleList.add( rule1 );
          NetworkRule rule2 = NetworkRule.create( NetworkRule.Protocol.tcp, -1, -1,
                                                  IpPermissionTypeExtractNetworkPeers.INSTANCE.apply( ipPerm ), empty );
          ruleList.add( rule2 );
        } else {
          NetworkRule rule = NetworkRule.create( ipPerm.getIpProtocol( ), anyProtocolAllowed, ipPerm.getFromPort( ), ipPerm.getToPort( ),
                                                 IpPermissionTypeExtractNetworkPeers.INSTANCE.apply( ipPerm ), empty );
          ruleList.add( rule );
        }
      } else if ( !ipPerm.getCidrIpRanges().isEmpty( ) ) {
        List<String> ipRanges = Lists.newArrayList( );
        for ( String range : ipPerm.getCidrIpRanges() ) {
          String[] rangeParts = range.split( "/" );
          try {
            if ( rangeParts.length != 2 ) throw new IllegalArgumentException( );
            if ( Integer.parseInt( rangeParts[1] ) > 32 || Integer.parseInt( rangeParts[1] ) < 0 ) throw new IllegalArgumentException( );
            if ( InetAddresses.forString( rangeParts[0] ) instanceof Inet4Address ) {
              ipRanges.add( range );
            }
          } catch ( IllegalArgumentException e ) {
            throw new IllegalArgumentException( "Invalid IP range: '"+range+"'" );
          }
        }
        NetworkRule rule = NetworkRule.create( ipPerm.getIpProtocol( ), anyProtocolAllowed, ipPerm.getFromPort( ), ipPerm.getToPort( ),
                                               IpPermissionTypeExtractNetworkPeers.INSTANCE.apply( ipPerm ), ipRanges );
        ruleList.add( rule );
      } else {
        throw new IllegalArgumentException( "Invalid Ip Permissions:  must specify either a source cidr or user" );
      }
      return ruleList;
    }
  }

  static List<NetworkRule> ipPermissionAsNetworkRules( final IpPermissionType ipPermission,
                                                       final boolean vpc ) {
    return ipPermissionsAsNetworkRules( Collections.singletonList( ipPermission ), vpc );
  }

  static List<NetworkRule> ipPermissionsAsNetworkRules( final List<IpPermissionType> ipPermissions,
                                                        final boolean vpc ) {
    final List<NetworkRule> ruleList = Lists.newArrayList( );
    for ( final IpPermissionType ipPerm : ipPermissions ) {
      ruleList.addAll(
          ( vpc ? IpPermissionTypeAsNetworkRule.VPC : IpPermissionTypeAsNetworkRule.CLASSIC ).apply( ipPerm )
      );
    }
    return ruleList;
  }

  @RestrictedTypes.Resolver( NetworkGroup.class )
  public enum Lookup implements Function<String, NetworkGroup> {
    INSTANCE;

    @Override
    public NetworkGroup apply( final String identifier ) {
      try {
        return NetworkGroups.lookupByGroupId( identifier );
      } catch ( NoSuchMetadataException e ) {
        throw Exceptions.toUndeclared( e );
      }
    }
  }


  public static class NetworkGroupFilterSupport extends FilterSupport<NetworkGroup> {
    public NetworkGroupFilterSupport() {
      super( builderFor( NetworkGroup.class )
          .withTagFiltering( NetworkGroupTag.class, "networkGroup" )
          .withStringProperty( "description", FilterFunctions.DESCRIPTION )
          .withStringProperty( "group-id", FilterFunctions.GROUP_ID )
          .withStringProperty( "group-name", CloudMetadatas.toDisplayName() )
          .withStringSetProperty( "ip-permission.cidr", FilterSetFunctions.PERMISSION_CIDR )
          .withStringSetProperty( "ip-permission.from-port", FilterSetFunctions.PERMISSION_FROM_PORT )
          .withStringSetProperty( "ip-permission.group-id", FilterSetFunctions.PERMISSION_GROUP_ID )
          .withStringSetProperty( "ip-permission.group-name", FilterSetFunctions.PERMISSION_GROUP )
          .withStringSetProperty( "ip-permission.protocol", FilterSetFunctions.PERMISSION_PROTOCOL )
          .withStringSetProperty( "ip-permission.to-port", FilterSetFunctions.PERMISSION_TO_PORT )
          .withStringSetProperty( "ip-permission.user-id", FilterSetFunctions.PERMISSION_ACCOUNT_ID )
          .withStringProperty( "owner-id", FilterFunctions.ACCOUNT_ID )
          .withStringProperty( "vpc-id", FilterFunctions.VPC_ID )
          .withPersistenceAlias( "networkRules", "networkRules" )
          .withPersistenceFilter( "description" )
          .withPersistenceFilter( "group-id", "groupId" )
          .withPersistenceFilter( "group-name", "displayName" )
          .withPersistenceFilter( "ip-permission.from-port", "networkRules.lowPort", PersistenceFilter.Type.Integer )
          .withPersistenceFilter( "ip-permission.protocol", "networkRules.protocol", Enums.valueOfFunction( NetworkRule.Protocol.class ) )
          .withPersistenceFilter( "ip-permission.to-port", "networkRules.highPort", PersistenceFilter.Type.Integer )
          .withPersistenceFilter( "owner-id", "ownerAccountNumber" )
          .withPersistenceFilter( "vpc-id", "vpcId" )
      );
    }
  }

  private enum FilterFunctions implements Function<NetworkGroup,String> {
    ACCOUNT_ID {
      @Override
      public String apply( final NetworkGroup group ) {
        return group.getOwnerAccountNumber();
      }
    },
    DESCRIPTION {
      @Override
      public String apply( final NetworkGroup group ) {
        return group.getDescription();
      }
    },
    GROUP_ID {
      @Override
      public String apply( final NetworkGroup group ) {
        return group.getGroupId();
      }
    },
    VPC_ID {
      @Override
      public String apply( final NetworkGroup group ) {
        return group.getVpcId( );
      }
    }
  }

  private enum FilterSetFunctions implements Function<NetworkGroup,Set<String>> {
    PERMISSION_CIDR {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          result.addAll( rule.getIpRanges() );
        }
        return result;
      }
    },
    PERMISSION_FROM_PORT {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          result.addAll( Optional.fromNullable( rule.getLowPort() ).transform( Functions.toStringFunction() ).asSet() );
        }
        return result;
      }
    },
    PERMISSION_GROUP {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          for ( final NetworkPeer peer : rule.getNetworkPeers() ) {
            if ( peer.getGroupName() != null ) result.add( peer.getGroupName() );
          }
        }
        return result;
      }
    },
    PERMISSION_GROUP_ID {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          for ( final NetworkPeer peer : rule.getNetworkPeers() ) {
            if ( peer.getGroupId() != null ) result.add( peer.getGroupId() );
          }
        }
        return result;
      }
    },
    PERMISSION_PROTOCOL {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          result.add( rule.getDisplayProtocol( ) );
        }
        return result;
      }
    },
    PERMISSION_TO_PORT {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          result.addAll( Optional.fromNullable( rule.getHighPort() ).transform( Functions.toStringFunction() ).asSet() );
        }
        return result;
      }
    },
    PERMISSION_ACCOUNT_ID {
      @Override
      public Set<String> apply( final NetworkGroup group ) {
        final Set<String> result = Sets.newHashSet();
        for ( final NetworkRule rule : group.getNetworkRules() ) {
          for ( final NetworkPeer peer : rule.getNetworkPeers() ) {
            if ( peer.getUserQueryKey() != null ) result.add( peer.getUserQueryKey() );
          }
        }
        return result;
      }
    }
  }

  @RestrictedTypes.QuantityMetricFunction( CloudMetadata.NetworkGroupMetadata.class )
  public enum CountNetworkGroups implements Function<OwnerFullName, Long> {
    INSTANCE;

    @Override
    public Long apply( @Nullable final OwnerFullName input ) {
      try ( final TransactionResource tx = Entities.transactionFor( NetworkGroup.class ) ) {
        return Entities.count( NetworkGroup.withOwner( input ) ); //TODO:STEVE: Quota for regular vs VPC security groups
      }
    }
  }
}
