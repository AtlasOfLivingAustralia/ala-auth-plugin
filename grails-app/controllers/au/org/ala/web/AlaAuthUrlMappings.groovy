package au.org.ala.web

class AlaAuthUrlMappings {

    static mappings = {
        "/login" (controller: 'login', action: 'index', plugin: 'ala-auth')
    }
}