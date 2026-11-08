package com.dhalam.ap

/** Refresh only on authentication failure, then retry once; never retry model/network errors. */
internal suspend fun <T> withRefreshedGoogleSession(
    current: AskBackendSession?,
    refresh: suspend () -> AskBackendSession,
    onExpired: () -> Unit,
    action: suspend (AskBackendSession) -> T
): T {
    if (current != null) {
        try { return action(current) }
        catch (_: AskSignInRequiredException) { onExpired() }
    }
    val renewed = refresh()
    return try { action(renewed) }
    catch (failure: AskSignInRequiredException) { onExpired(); throw failure }
}
