/*************************************************************************
 * Copyright 2009-2014 Eucalyptus Systems, Inc.
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
 ************************************************************************/
package com.eucalyptus.tokens;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nonnull;
import org.apache.log4j.Logger;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableField;
import com.eucalyptus.configurable.ConfigurableProperty;
import com.eucalyptus.configurable.ConfigurablePropertyException;
import com.eucalyptus.configurable.PropertyChangeListener;
import com.eucalyptus.configurable.PropertyChangeListeners;
import com.eucalyptus.util.Strings;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 *
 */
@SuppressWarnings( { "WeakerAccess", "unused" } )
@ConfigurableClass(root = "tokens", description = "Parameters controlling tokens service")
public class TokensServiceConfiguration {

  private static final Logger logger = Logger.getLogger( TokensServiceConfiguration.class );
  private static final Pattern NONE = Pattern.compile( "-" ); // invalid alias / algorithm

  private static final String DEFAULT_ROLE_ARN_ALIAS = "eucalyptus";
  private static final String DEFAULT_WEB_ID_SIG_ALGORITHMS = "RS512";
  private static final String DEFAULT_OIDC_DISCOVERY_CACHE = "maximumSize=20, expireAfterWrite=15m";

  @ConfigurableField(
      description = "Actions to enable (ignored if empty)",
      changeListener = TokensServicePropertyChangeListener.class )
  public static volatile String enabledActions = "";

  @ConfigurableField(
      description = "Actions to disable",
      changeListener = TokensServicePropertyChangeListener.class )
  public static volatile String disabledActions = "";

  @ConfigurableField(
      initial = DEFAULT_ROLE_ARN_ALIAS,
      description = "List of account aliases to allow in role ARNs",
      changeListener = TokensServiceRoleArnPropertyChangeListener.class )
  public static volatile String roleArnAliasWhitelist = DEFAULT_ROLE_ARN_ALIAS;

  @ConfigurableField(
      initial = "60",
      description = "Web identity token maximum time skew (seconds)",
      changeListener = PropertyChangeListeners.IsNonNegativeInteger.class )
  public static volatile Long webIdentityTokenSkew = 60L;

  @ConfigurableField(
      initial = DEFAULT_WEB_ID_SIG_ALGORITHMS,
      description = "List of JSON Web Signature algorithms to allow in web identity tokens",
      changeListener = TokensServiceWebIdentitySignatureAlgorithmPropertyChangeListener.class )
  public static volatile String webIdentitySignatureAlgorithmWhitelist = DEFAULT_WEB_ID_SIG_ALGORITHMS;

  @ConfigurableField(
      description = "OpenID Connect discovery cache configuration, for provider metadata",
      initial = DEFAULT_OIDC_DISCOVERY_CACHE,
      changeListener = PropertyChangeListeners.CacheSpecListener.class )
  public static volatile String webIdentityOidcDiscoveryCache = DEFAULT_OIDC_DISCOVERY_CACHE;

  @ConfigurableField(
      description = "OpenID Connect discovery cache refresh expiry (seconds)",
      initial = "60",
      changeListener = PropertyChangeListeners.IsNonNegativeInteger.class )
  public static volatile Long webIdentityOidcDiscoveryRefresh = 60L;

  private static final AtomicReference<Set<String>> enabledActionsSet =
      new AtomicReference<>( Collections.emptySet() );

  private static final AtomicReference<Set<String>> disabledActionsSet =
      new AtomicReference<>( Collections.emptySet() );

  private static final AtomicReference<Pattern> roleArnAliasPattern =
      new AtomicReference<>( Pattern.compile( DEFAULT_ROLE_ARN_ALIAS ) );

  private static final AtomicReference<Pattern> webIdSignatureAlgorithmPattern =
      new AtomicReference<>( Pattern.compile( DEFAULT_WEB_ID_SIG_ALGORITHMS ) );

  public static Set<String> getEnabledActions( ) {
    return enabledActionsSet.get( );
  }

  public static Set<String> getDisabledActions( ) {
    return disabledActionsSet.get( );
  }

  public static Pattern getRoleArnAliasPattern( ) {
    return roleArnAliasPattern.get( );
  }

  public static long getWebIdentityTokenTimeSkew( ) { return MoreObjects.firstNonNull( webIdentityTokenSkew, 0L ); };

  public static Pattern getWebIdSignatureAlgorithmPattern( ) {
    return webIdSignatureAlgorithmPattern.get( );
  }

  @SuppressWarnings( "StaticPseudoFunctionalStyleMethod" )
  public static final class TokensServicePropertyChangeListener implements PropertyChangeListener {
    @SuppressWarnings( "unchecked" )
    @Override
    public void fireChange( final ConfigurableProperty configurableProperty,
                            final Object newValue ) throws ConfigurablePropertyException {
      try {
        final Splitter splitter =
            Splitter.on( CharMatcher.WHITESPACE.or( CharMatcher.anyOf( ",;|" ) ) ).trimResults( ).omitEmptyStrings( );
        final String fieldName = configurableProperty.getField().getName() + "Set";
        final Field field = TokensServiceConfiguration.class.getDeclaredField( fieldName );
        field.setAccessible( true );
        final Set<String> value =
            ImmutableSet.copyOf( Iterables.transform( splitter.split( String.valueOf( newValue ) ), Strings.lower( ) ) );
        logger.info( "Tokens service configuration updated " + configurableProperty.getDisplayName( ) + ": " + value );
        ((AtomicReference<Set<String>>)field.get( null )).set( value );
      } catch ( NoSuchFieldException | IllegalAccessException  e ) {
        logger.error( "Error updating token service configuration for " + configurableProperty.getDisplayName( ), e );
      }
    }
  }

  @SuppressWarnings( "StaticPseudoFunctionalStyleMethod" )
  public static final class TokensServiceRoleArnPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty configurableProperty,
                            final Object newValue ) throws ConfigurablePropertyException {
      setPatternFromPropertyValueList( roleArnAliasPattern, configurableProperty, newValue, "[0-9\\p{javaLowerCase}*-]+"  );
    }
  }

  @SuppressWarnings( "StaticPseudoFunctionalStyleMethod" )
  public static final class TokensServiceWebIdentitySignatureAlgorithmPropertyChangeListener
      implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty configurableProperty,
                            final Object newValue ) throws ConfigurablePropertyException {
      setPatternFromPropertyValueList( webIdSignatureAlgorithmPattern, configurableProperty, newValue, "[\\w*-]+"  );
    }
  }

  private static void setPatternFromPropertyValueList(
      final AtomicReference<Pattern> reference,
      final ConfigurableProperty configurableProperty,
      final Object newValue,
      final String itemRegex
      ) throws ConfigurablePropertyException {
    try {
      final String strValue = com.google.common.base.Strings.emptyToNull( Objects.toString( newValue, "" ) );
      if ( strValue != null ) {
        reference.set( buildPatternFromWildcardList( strValue, itemRegex ) );
      } else {
        reference.set( NONE );
      }
    } catch ( final PatternSyntaxException e ) {
      logger.error( "Error updating token service configuration for " + configurableProperty.getDisplayName( ), e );
      reference.set( NONE );
    }
  }

  private static Pattern buildPatternFromWildcardList(
      @Nonnull final String strValue,
      @Nonnull final String itemRegex
  ) throws ConfigurablePropertyException {
    final Splitter splitter =
        Splitter.on( CharMatcher.WHITESPACE.or( CharMatcher.anyOf( ",;|" ) ) ).trimResults( ).omitEmptyStrings( );
    final StringBuilder builder = new StringBuilder( );
    builder.append( "(-|" );
    for ( final String aliasWildcard : splitter.split( strValue ) ) {
      builder.append( toPattern( aliasWildcard, itemRegex ) );
      builder.append( '|' );
    }
    builder.append( "-)" );
    return Pattern.compile( builder.toString( ) );
  }

  private static String toPattern( final String aliasWildcard, final String itemRegex ) throws ConfigurablePropertyException {
    if ( !aliasWildcard.matches( itemRegex ) ) {
      throw new ConfigurablePropertyException( "Invalid alias : " + aliasWildcard );
    }
    return aliasWildcard.replace( "*", ".*" );
  }
}
