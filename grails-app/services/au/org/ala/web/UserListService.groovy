package au.org.ala.web

import au.org.ala.userdetails.UserDetailsClient
import grails.plugin.cache.Cacheable

/**
 * This service has one method that returns a large list of objects containing data about ALA users.
 *
 * This method has been split from the AuthService because it is an expensive call, and is used internally by other auth service methods, and so should
 * be cached. Service methods called by other methods on the same service do not get cached (because the caching is implemented via proxy wrappers),
 * so we stick it in a different service
 */
class UserListService {

    static transactional = false

    def grailsApplication
    UserDetailsClient userDetailsClient

    /**
     * @deprecated use the AuthService.getUserDetailsById instead
     * @return All the UserDetails, without extended properties
     */
    @Cacheable("userListCache")
    List<UserDetails> getFullUserList() {
        checkConfig()
        def response = userDetailsClient.getUserListFull().execute()
        if (response.successful) {
            return response.body()
        } else {
            throw new RuntimeException("Error response from UserListFull service: ${response.code()} ${response.message()}")
        }
    }

    private void checkConfig() {
        if (!grailsApplication.config.getProperty('userDetails.url')) {
            log.error "Required config not found: userDetails.url - please add to Config.groovy"
        }
    }

}
