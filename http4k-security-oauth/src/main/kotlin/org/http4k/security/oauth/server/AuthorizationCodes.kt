package org.http4k.security.oauth.server

import org.http4k.core.Uri
import java.time.Instant

/**
 * Provides a consistent way to manage authorization codes
 */
interface AuthorizationCodes {
    /**
     * Create new authorization code to be given to client after the user successfully authorize access
     * The generated authorization code needs to be associated with the clientId and redirectUri for later verification.
     * It should also be associated with a given expire date (recommended to be shorter than 10 minutes)
     */
    fun create(clientId: ClientId, redirectUri: Uri): AuthorizationCode

    /**
     * Retrieve the details of an authorization code
     */
    fun detailsFor(code: AuthorizationCode): AuthorizationCodeDetails

    /**
     * Destroys an authorization token after it's been used to generate an access token
     */
    fun destroy(authorizationCode: AuthorizationCode)
}

data class AuthorizationCodeDetails(
    val clientId: ClientId,
    val redirectUri: Uri,
    val expiresAt: Instant
)