package au.org.ala.web

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Strategy for implementing SSO.  Used by the SSO Interceptor to generalise authentication method
 */
interface SSOStrategy {

    /**
     * Authenticate a request with the SSO provider
     *
     * @param request The current request
     * @param response The current response
     * @param gateway Whether the request is allowed to callback without authenticating
     */
    boolean authenticate(HttpServletRequest request, HttpServletResponse response, boolean gateway)

}