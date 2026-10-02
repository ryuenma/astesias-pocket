package com.ivy.poll.impl.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import arrow.core.Either
import com.ivy.data.datastore.IvyDataStore
import com.ivy.domain.usecase.android.DeviceId
import com.ivy.poll.data.PollRepository
import com.ivy.poll.data.model.PollId
import com.ivy.poll.data.model.PollOptionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

// Fork change: this used to write the vote to Ivy's Firestore ("ivy-widget" project),
// sending the device id and choice to Ivy's infrastructure. Astesia's Pocket does not
// ship Ivy's backend, so the vote is recorded locally only. The vote card that reached
// this code already expired on 2025-07-28, so in practice this is unreachable either
// way -- but the network call is gone rather than merely dead.

class PollRepositoryImpl @Inject constructor(
  private val dataStore: IvyDataStore
) : PollRepository {
  override suspend fun hasVoted(poll: PollId): Boolean {
    return dataStore.data.map { it[votedKey(poll)] ?: false }.first()
  }

  override suspend fun setVoted(poll: PollId, voted: Boolean) {
    dataStore.edit {
      it[votedKey(poll)] = voted
    }
  }

  override suspend fun vote(
    deviceId: DeviceId,
    poll: PollId,
    option: PollOptionId
  ): Either<String, Unit> {
    Timber.i("Poll vote recorded locally: poll=%s option=%s", poll.id, option.value)
    setVoted(poll, true)
    return Either.Right(Unit)
  }

  private fun votedKey(poll: PollId): Preferences.Key<Boolean> {
    return booleanPreferencesKey("poll.${poll.id}_voted")
  }
}