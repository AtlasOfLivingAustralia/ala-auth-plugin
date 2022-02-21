package au.org.ala.web.config

import au.org.ala.web.CasClientProperties
import au.org.ala.web.CooperatingFilterWrapper
import au.org.ala.web.CoreAuthProperties
import au.org.ala.web.GrailsPac4jContextProvider
import au.org.ala.web.IAuthService
import au.org.ala.web.NotBotMatcher
import au.org.ala.web.OidcClientProperties
import au.org.ala.web.Pac4jAuthService
import au.org.ala.web.Pac4jContextProvider
import au.org.ala.web.Pac4jSSOStrategy
import au.org.ala.web.SSOStrategy
import au.org.ala.web.UserAgentFilterService
import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.pac4j.core.client.Clients
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.context.JEEContextFactory
import org.pac4j.core.context.WebContextFactory
import org.pac4j.core.context.session.JEESessionStore
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.http.url.DefaultUrlResolver
import org.pac4j.core.matching.matcher.HeaderMatcher
import org.pac4j.core.matching.matcher.PathMatcher
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.jee.filter.CallbackFilter
import org.pac4j.jee.filter.LogoutFilter
import org.pac4j.jee.filter.SecurityFilter
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.servlet.DispatcherType

import static org.pac4j.core.authorization.authorizer.IsAnonymousAuthorizer.isAnonymous
import static org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer.isAuthenticated
import static org.pac4j.core.authorization.authorizer.OrAuthorizer.or

@CompileStatic
@Configuration("authPac4jPluginConfiguration")
@EnableConfigurationProperties([CasClientProperties, OidcClientProperties, CoreAuthProperties])
@Slf4j
class AuthPac4jPluginConfig {

    static final String DEFAULT_CLIENT = "OidcClient"
    static final String PROMPT_NONE_CLIENT = "PromptNoneClient"
    
    static final String ALLOW_ALL = "allowAll"
    static final String IS_AUTHENTICATED = "isAuthenticated"

    static final String ALA_COOKIE_MATCHER = "alaCookieMatcher"
    static final String EXCLUDE_PATHS = "excludePaths"
    public static final String CALLBACK_URI = "/callback"

    @Autowired
    CasClientProperties casClientProperties
    @Autowired
    CoreAuthProperties coreAuthProperties
    @Autowired
    OidcClientProperties oidcClientProperties

    @Autowired
    GrailsApplication grailsApplication

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    IAuthService delegateService(Config config, Pac4jContextProvider pac4jContextProvider, SessionStore sessionStore) {
        new Pac4jAuthService(config, pac4jContextProvider, sessionStore)
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    OidcConfiguration oidcConfiguration() {
        OidcConfiguration config = generateBaseOidcClient()
        return config
    }

    private OidcConfiguration generateBaseOidcClient() {
        OidcConfiguration config = new OidcConfiguration()
        config.setClientId(oidcClientProperties.clientId)
        config.setSecret(oidcClientProperties.secret)
        config.setDiscoveryURI(oidcClientProperties.discoveryUri)
        config.setScope(oidcClientProperties.scope)
        config.setWithState(oidcClientProperties.withState)
        config.customParams.putAll(oidcClientProperties.customParams)
        if (oidcClientProperties.clientAuthenticationMethod) {
            config.setClientAuthenticationMethodAsString(oidcClientProperties.clientAuthenticationMethod)
        }
        // select display mode: page, popup, touch, and wap
//        config.addCustomParam("display", "popup");
        // select prompt mode: none, consent, select_account
//        config.addCustomParam("prompt", "none");
        config
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    OidcClient oidcClient(OidcConfiguration oidcConfiguration) {
        def client = new OidcClient(oidcConfiguration)
        client.setUrlResolver(new DefaultUrlResolver(true))
        client.setName(DEFAULT_CLIENT)
        client
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    OidcClient oidcPromptNoneClient() {
        def config = generateBaseOidcClient()
        // select prompt mode: none, consent, select_account
        config.addCustomParam("prompt", "none")
        def client = new OidcClient(config)
        client.setUrlResolver(new DefaultUrlResolver(true))
        client.setName(PROMPT_NONE_CLIENT)
        return client
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    Pac4jContextProvider pac4jContextProvider(Config config) {
        new GrailsPac4jContextProvider(config)
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    SessionStore sessionStore() {
        JEESessionStore.INSTANCE
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    WebContextFactory webContextFactory() {
        JEEContextFactory.INSTANCE
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    Config pac4jConfig(OidcClient oidcClient, SessionStore sessionStore, WebContextFactory webContextFactory, UserAgentFilterService userAgentFilterService) {
        Clients clients = new Clients(CALLBACK_URI, oidcClient, new AnonymousClient())
        Config config = new Config(clients)
        //config.addAuthorizer()
//        config.addMatcher("", new Matcher()))
        config.sessionStore = sessionStore
        config.webContextFactory = webContextFactory
        config.addAuthorizer(IS_AUTHENTICATED, isAuthenticated())
        config.addAuthorizer(ALLOW_ALL, or(isAuthenticated(), isAnonymous()))
        config.addMatcher(ALA_COOKIE_MATCHER, new HeaderMatcher(coreAuthProperties.authCookieName ?: casClientProperties.authCookieName,".*"))
        config.addMatcher("notBotMatcher", new NotBotMatcher(userAgentFilterService))
        def excludeMatcher = new PathMatcher()
        (coreAuthProperties.uriExclusionFilterPattern + casClientProperties.uriExclusionFilterPattern).each {
            if (!it.startsWith("^")) {
                it = '^' + it
            }
            if (!it.endsWith('$')) {
                it += '$'
            }
            excludeMatcher.excludeRegex(it)
        }
        config.addMatcher(EXCLUDE_PATHS, excludeMatcher)
        config
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    FilterRegistrationBean pac4jLogoutFilter(Config pac4jConfig) {
        final name = 'Pac4j Logout Filter'
        def frb = new FilterRegistrationBean()
        frb.name = name
        LogoutFilter logoutFilter = new LogoutFilter(pac4jConfig, "/logout")
        frb.filter = logoutFilter
        frb.dispatcherTypes = EnumSet.of(DispatcherType.REQUEST)
        frb.order = AuthPluginConfig.filterOrder()
        frb.urlPatterns = [ '/logout' ]
        frb.enabled = true
        frb.asyncSupported = true
        logFilter(name, frb)
        return frb
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    FilterRegistrationBean pac4jCallbackFilter(Config pac4jConfig) {
        final name = 'Pac4j Callback Filter'
        def frb = new FilterRegistrationBean()
        frb.name = name
        CallbackFilter callbackFilter = new CallbackFilter(pac4jConfig, CALLBACK_URI)
        frb.filter = callbackFilter
        frb.dispatcherTypes = EnumSet.of(DispatcherType.REQUEST)
        frb.order = AuthPluginConfig.filterOrder()
        frb.urlPatterns = [ CALLBACK_URI ]
        frb.enabled = true
        frb.asyncSupported = true
        logFilter(name, frb)
        return frb
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    FilterRegistrationBean pac4jUriFilter(Config pac4jConfig) {

        // This filter will apply the uriFiltersPattern
        final name = 'Pac4j Security Filter'
        def frb = new FilterRegistrationBean()
        frb.name = name
        SecurityFilter securityFilter = new SecurityFilter(pac4jConfig,
                toStringParam(AnonymousClient.class.name, DEFAULT_CLIENT),
                IS_AUTHENTICATED, EXCLUDE_PATHS)
        frb.filter = securityFilter
        frb.dispatcherTypes = EnumSet.of(DispatcherType.REQUEST)
        frb.order = AuthPluginConfig.filterOrder() + 1
        frb.urlPatterns = coreAuthProperties.uriFilterPattern ?: casClientProperties.uriFilterPattern
        frb.enabled = !frb.urlPatterns.empty
        frb.asyncSupported = true
        logFilter(name, frb)
        return frb
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    FilterRegistrationBean pac4jAuthOnlyIfFilter(Config pac4jConfig) {

        // This filter will apply the uriFiltersPattern
        final name = 'Pac4j Optional Security Filter'
        def frb = new FilterRegistrationBean()
        frb.name = name
        SecurityFilter securityFilter = new SecurityFilter(pac4jConfig,
                toStringParam(AnonymousClient.class.name, DEFAULT_CLIENT),
                ALLOW_ALL, toStringParam(ALA_COOKIE_MATCHER, EXCLUDE_PATHS))
        frb.filter = new CooperatingFilterWrapper(securityFilter, AuthPluginConfig.AUTH_FILTER_KEY)
        frb.dispatcherTypes = EnumSet.of(DispatcherType.REQUEST)
        frb.order = AuthPluginConfig.filterOrder() + 2
        frb.urlPatterns = coreAuthProperties.optionalFilterPattern +
                casClientProperties.authenticateOnlyIfCookieFilterPattern +
                casClientProperties.authenticateOnlyIfLoggedInFilterPattern +
                casClientProperties.authenticateOnlyIfLoggedInPattern
        frb.enabled = !frb.urlPatterns.empty
        frb.asyncSupported = true
        logFilter(name, frb)
        return frb
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    FilterRegistrationBean pac4jPromptNoneFilter(Config pac4jConfig) {

        // This filter will apply the uriFiltersPattern
        final name = 'Pac4j Prompt None Security Filter'
        def frb = new FilterRegistrationBean()
        frb.name = name
        SecurityFilter securityFilter = new SecurityFilter(pac4jConfig,
                toStringParam(AnonymousClient.class.name, PROMPT_NONE_CLIENT),
                ALLOW_ALL, EXCLUDE_PATHS)
        frb.filter = new CooperatingFilterWrapper(securityFilter, AuthPluginConfig.AUTH_FILTER_KEY)
        frb.dispatcherTypes = EnumSet.of(DispatcherType.REQUEST)
        frb.order = AuthPluginConfig.filterOrder() + 3
        frb.urlPatterns = casClientProperties.gatewayFilterPattern +
                casClientProperties.gatewayIfCookieFilterPattern
        frb.enabled = !frb.urlPatterns.empty
        frb.asyncSupported = true
        logFilter(name, frb)
        return frb
    }

    private static void logFilter(String name, FilterRegistrationBean frb) {
        if (frb.enabled) {
            log.debug('{} enabled with type: {}', name, frb.filter)
            log.debug('{} enabled with params: {}', name, frb.initParameters)
            log.debug('{} enabled for paths: {}', name, frb.urlPatterns)
        } else {
            log.debug('{} disabled', name)
        }
    }

    @ConditionalOnProperty(prefix= 'security.oidc', name='enabled', matchIfMissing = false)
    @Bean
    SSOStrategy ssoStrategy(Config config) {
        new Pac4jSSOStrategy(config, null,
                toStringParam(AnonymousClient.class.name, DEFAULT_CLIENT),
                toStringParam(AnonymousClient.class.name, PROMPT_NONE_CLIENT),
                IS_AUTHENTICATED, ALLOW_ALL,
                "")
    }

    private static String toStringParam(String... params) {
        params.join(Pac4jConstants.ELEMENT_SEPARATOR)
    }
}
