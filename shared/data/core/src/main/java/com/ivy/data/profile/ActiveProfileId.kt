package com.ivy.data.profile

import javax.inject.Qualifier

/**
 * The id of the profile this process is currently showing, as a plain [String].
 *
 * Bound once in `RoomDbModule`, which already resolves the active profile to
 * decide which database file to open. Feature modules inject this instead of
 * [ProfileRegistry]: they only ever need their own id to exclude their own rows,
 * and taking the whole registry re-resolves a type whose constructor references
 * SharedPrefs on every screen that does so.
 *
 * Bound as a String rather than [ProfileId] so no consumer needs the value class
 * on its classpath just to filter a query.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveProfileId
