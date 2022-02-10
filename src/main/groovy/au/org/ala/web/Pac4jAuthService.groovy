package au.org.ala.web

import org.pac4j.core.config.Config
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.UserProfile
import org.springframework.beans.factory.annotation.Autowired

class Pac4jAuthService implements IAuthService {

    // TODO Make these configurable?
    static final String ATTR_EMAIL = 'email'
    static final String ATTR_FIRST_NAME = 'firstname'
    static final String ATTR_LAST_NAME = 'sn'
    static final String ATTR_ROLES = 'role'

    @Autowired
    private final Config config

    @Autowired
    private final Pac4jContextProvider pac4jContextProvider

    @Autowired
    private final SessionStore sessionStore

    Pac4jAuthService(Config config, Pac4jContextProvider pac4jContextProvider, SessionStore sessionStore) {
        this.config = config
        this.pac4jContextProvider = pac4jContextProvider
        this.sessionStore = sessionStore
    }

    ProfileManager getProfileManager() {
        def context = pac4jContextProvider.webContext()
        final ProfileManager manager = new ProfileManager(context, sessionStore)
        manager.config = config
        return manager
    }

    UserProfile getUserProfile() {
        def manager = profileManager

        def value = null
        if (manager.authenticated) {
            final Optional<UserProfile> profile = manager.getProfile()
            if (profile.isPresent()) {
                value = profile.get()
            }
        }
        return value
    }

    String getAttribute(String attribute) {
        def manager = profileManager

        def value = null
        if (manager.authenticated) {
            final Optional<UserProfile> profile = manager.getProfile()
            if (profile.isPresent()) {
                def userProfile = profile.get()
                if (userProfile) {
                    value = userProfile.getAttribute(attribute)
                }
            }
        }
        return value
    }

    @Override
    String getEmail() {
        return getAttribute(ATTR_EMAIL)
    }

    @Override
    String getUserId() {
        def manager = profileManager
        def value = null
        if (manager.authenticated) {
            final Optional<UserProfile> profile = manager.getProfile()
            if (profile.isPresent()) {
                def userProfile = profile.get()
                if (userProfile) {
                    value = userProfile.username
                }
            }
        }
        return value
    }

    @Override
    String getDisplayName() {
        String firstname = getAttribute(ATTR_FIRST_NAME)
        String lastname = getAttribute(ATTR_LAST_NAME)
        String displayName = ""
        if (firstname && lastname) {
            displayName = String.format("%s %s", firstname, lastname)
        } else if (firstname || lastname) {
            displayName = String.format("%s", firstname ?: lastname)
        }
        return displayName
    }

    @Override
    String getFirstName() {
        return getAttribute(ATTR_FIRST_NAME)
    }

    @Override
    String getLastName() {
        return getAttribute(ATTR_LAST_NAME)
    }

    /**
     *
     * @param request Needs to be a {@link au.org.ala.cas.client.AlaHttpServletRequestWrapperFilter} or {@link org.jasig.cas.client.util.HttpServletRequestWrapperFilter}
     * @return The users roles in a set or an empty set if the user is not authenticated
     */
    Set<String> getUserRoles() {
        def userProfile = userProfile

        if (userProfile != null) {
            Object roles = userProfile.attributes.get(ATTR_ROLES);
            if (roles instanceof Collection) {
                return new HashSet<String>((Collection)roles);
            } else if (roles instanceof String) {
                String rolesString = (String) roles
                Set<String> retVal = new HashSet<String>()
                if (rolesString) {
                    for (String role: rolesString.split(",")) {
                        retVal.add(role.trim())
                    }
                }
                return retVal;
            }
        }
        return Collections.emptySet()
    }

    @Override
    boolean userInRole(String role) {
        return userRoles.contains(role)
    }

    @Override
    UserDetails userDetails() {
        def attr = userProfile?.attributes
        def details = null

        if (attr) {
            details = new UserDetails(
                    userId: attr?.userid?.toString(),
                    userName: attr?.email?.toString()?.toLowerCase(),
                    firstName: attr?.firstname?.toString() ?: "",
                    lastName: attr?.lastname?.toString() ?: "",
                    locked: attr?.locked?.toBoolean() ?: false,
                    organisation: attr?.organisation?.toString() ?: "",
                    city: attr?.country?.toString() ?: "",
                    state: attr?.state?.toString() ?: "",
                    country: attr?.country?.toString() ?: "",
                    roles: userRoles
            )
        }

        details
    }


}
