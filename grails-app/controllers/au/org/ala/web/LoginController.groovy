package au.org.ala.web

import grails.web.mapping.LinkGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

class LoginController {

    @Autowired
    LinkGenerator linkGenerator

    @Autowired
    SSOStrategy ssoStrategy

    @Value('${security.core.defaultRedirectUri:/}')
    String defaultRedirect

    def index() {
        def path = params.get('path', defaultRedirect)

        def context = linkGenerator.contextPath
        // TODO What if there is a controller with the same name as the context path?
        if (path.startsWith(context)) {
            path -= context
        }

        def absPath = linkGenerator.link(absolute: true, uri: path)
        boolean auth = ssoStrategy.authenticate(request, response, false, absPath)

        if (!auth) {
            redirect(absolute: true, uri: path)
        }
    }
}
