package au.org.ala.oidc

import au.org.ala.web.OidcClientProperties
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant
import com.nimbusds.oauth2.sdk.ParseException
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenErrorResponse
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser
import groovy.util.logging.Slf4j
import org.pac4j.core.exception.TechnicalException
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.credentials.OidcCredentials

@Slf4j
class TokenClient {

    private URI tokenEndpoint
    private String clientId
    private String secret

    private OidcConfiguration oidcConfiguration

    TokenClient(URI tokenEndpoint, String clientId, String secret) {
        this.secret = secret
        this.clientId = clientId
        this.tokenEndpoint = tokenEndpoint
    }

    def getTokens() {
        def tokenRequest = new TokenRequest(
                tokenEndpoint,
                new ClientID(clientId),
                new ClientCredentialsGrant(),
                Scope.parse(["openid", "users:read"]), // TODO
                null,
                null, // TODO
                [client_secret: [secret]]
        )
        return executeTokenRequest(tokenRequest) // save refresh tokens?
    }

    private OidcCredentials executeTokenRequest(TokenRequest request) throws IOException, ParseException {
        var tokenHttpRequest = request.toHTTPRequest()
        if (oidcConfiguration) {
            oidcConfiguration.configureHttpRequest(tokenHttpRequest)
        }

        def httpResponse = tokenHttpRequest.send()
        log.debug("Token response: status={}, content={}", httpResponse.getStatusCode(),
                httpResponse.getContent())

        def response = OIDCTokenResponseParser.parse(httpResponse)
        if (response instanceof TokenErrorResponse) {
            def errorObject = ((TokenErrorResponse) response).getErrorObject()
            throw new TechnicalException("Bad token response, error=" + errorObject.getCode() + "," +
                    " description=" + errorObject.getDescription())
        }
        log.debug("Token response successful")
        def tokenSuccessResponse = (OIDCTokenResponse) response

        def credentials = new OidcCredentials()
        def oidcTokens = tokenSuccessResponse.getOIDCTokens()
        credentials.accessToken = oidcTokens.accessToken
        credentials.refreshToken = oidcTokens.refreshToken
        if (oidcTokens.IDToken != null) {
            credentials.idToken = oidcTokens.IDToken
        }
        return credentials
    }
}
