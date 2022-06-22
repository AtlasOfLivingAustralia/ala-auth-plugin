package au.org.ala.web.config

import au.org.ala.oidc.TokenClient
import au.org.ala.userdetails.UserDetailsClient
import au.org.ala.web.CasClientProperties
import au.org.ala.web.OidcClientProperties
import au.org.ala.web.UserAgentFilterService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Rfc3339DateJsonAdapter
import grails.core.GrailsApplication
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.pac4j.core.client.DirectClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.regex.Pattern

import static java.util.concurrent.TimeUnit.MILLISECONDS

@CompileStatic
@Configuration("authGenericPluginConfiguration")
@EnableConfigurationProperties(CasClientProperties)
@Slf4j
class AuthGenericPluginConfig {

    @Autowired
    GrailsApplication grailsApplication

    @ConditionalOnMissingBean(name = "userDetailsHttpClient")
    @Bean(name = ["defaultUserDetailsHttpClient", "userDetailsHttpClient"])
    OkHttpClient userDetailsHttpClient(@Qualifier('userDetailsBearerTokenInterceptor') @Autowired(required = false) Interceptor userDetailsBearerTokenInterceptor) {
        Integer readTimeout = grailsApplication.config['userDetails']['readTimeout'] as Integer
        def client = new OkHttpClient.Builder().tap {
            readTimeout(readTimeout, MILLISECONDS)
            if (userDetailsBearerTokenInterceptor) {
                addInterceptor(userDetailsBearerTokenInterceptor)
            }
        }.build()
        return client
    }

    @ConditionalOnMissingBean(name = "userDetailsMoshi")
    @Bean(name = ["defaultUserDetailsMoshi", "userDetailsMoshi"])
    Moshi userDetailsMoshi() {
        new Moshi.Builder().add(Date, new Rfc3339DateJsonAdapter().nullSafe()).build()
    }


    @Bean("userDetailsClient")
    UserDetailsClient userDetailsClient(@Qualifier("userDetailsHttpClient") OkHttpClient userDetailsHttpClient,
                                        @Qualifier('userDetailsMoshi') Moshi moshi) {
        String baseUrl = grailsApplication.config["userDetails"]["url"]
        new UserDetailsClient.Builder(userDetailsHttpClient, baseUrl).moshi(moshi).build()
    }

    @ConditionalOnMissingBean(name = "crawlerPatterns")
    @Bean
    @CompileDynamic
    List<Pattern> crawlerPatterns() {
        List crawlerUserAgents = new JsonSlurper().parse(this.class.classLoader.getResource('crawler-user-agents.json'))
        return crawlerUserAgents*.pattern.collect { Pattern.compile(it) }
    }

    @Bean
    UserAgentFilterService userAgentFilterService() {
        return new UserAgentFilterService('', crawlerPatterns())
    }
}
