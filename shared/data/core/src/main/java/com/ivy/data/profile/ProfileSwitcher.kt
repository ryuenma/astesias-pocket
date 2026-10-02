package com.ivy.data.profile

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Changes which profile the app is showing.
 *
 * ## Why switching restarts the process
 *
 * The obvious implementation - keep a mutable "current database" holder and have the
 * DAOs read through it - does not work with this codebase, and the failure is quiet.
 * `IvyRoomDatabase` is a Hilt `@Singleton`; the roughly twenty DAO providers are
 * unscoped, so each one wraps the singleton instance it was given. Those wrappers
 * capture the object at injection time. Swapping the object behind them leaves every
 * already-injected DAO pointing at the old handle: reads keep returning the previous
 * profile's rows, and writes hit a closed database. Neither surfaces at the switch.
 *
 * Closing the database and recreating the process is the version that cannot leak.
 * On relaunch the whole Hilt graph is rebuilt, `RoomDbModule` re-reads the active
 * profile, and every DAO is constructed against the new file. The visible cost is a
 * brief relaunch, which for a two-profile personal app is a fair trade for isolation
 * being a filesystem property rather than a convention.
 *
 * [ProcessPhoenix] restarts the process without the user seeing the launcher, so the
 * app returns straight to the home screen of the newly selected profile.
 */
@Singleton
class ProfileSwitcher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val registry: ProfileRegistry,
) {

    /**
     * Makes [target] the active profile and restarts into it.
     *
     * @throws UnknownProfileException if [target] is not registered on this device.
     * The preference is written only after the check, so a failed switch cannot
     * leave the app pointing at a profile that has no data.
     */
    fun switchTo(target: ProfileId) {
        if (!registry.exists(target)) throw UnknownProfileException(target)
        if (target == registry.activeProfileId()) return

        registry.setActiveProfile(target)
        restart()
    }

    /**
     * Registers [profile] and switches to it in one step, for the "add my SO's
     * profile" flow. Persists the profile list before the active pointer so the new
     * profile is resolvable when the process comes back up.
     */
    fun createAndSwitchTo(profile: Profile) {
        registry.register(profile)
        switchTo(profile.id)
    }

    /**
     * Relaunches into the newly active profile.
     *
     * `CLEAR_TASK` plus `NEW_TASK` puts the app in a clean task with no back stack
     * from the previous profile - without `CLEAR_TASK`, pressing back after a switch
     * would return to the *other* profile's screen, which is exactly the leak this
     * class exists to prevent. `exitProcess` then tears down the old graph; the
     * process may instead be reclaimed by the system, which has the same effect
     * because all of the app's state lives in that process.
     */
    private fun restart() {
        val launch = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?: error("cannot resolve launch intent for ${appContext.packageName}")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(launch)
        exitProcess(0)
    }

}
