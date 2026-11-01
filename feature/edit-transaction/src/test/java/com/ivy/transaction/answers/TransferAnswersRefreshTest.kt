package com.ivy.transaction.answers

import com.ivy.domain.usecase.transfer.TransferConfirmationRepository.AnswerPullResult
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * An empty answers list means one of two completely different things, and the
 * screen used to say the same sentence for both:
 *
 *  - the server was read and nobody has answered yet (a fact, and good news);
 *  - the server was never read, so nobody knows (not news at all).
 *
 * The second is what made an accepted transfer look ignored: the pull result was
 * discarded, the list was empty, and the screen said "no answers yet" with complete
 * confidence about something it had never checked.
 */
class TransferAnswersRefreshTest {

    @Test
    fun `a server that answered with nothing is still a successful check`() {
        // Pulled(0, 0, 0, 0) is the "nothing has been answered" case. Treating it
        // as a failure would replace one lie with the opposite one, and nag the
        // owner about a server that is working perfectly.
        AnswerPullResult.Pulled(recorded = 0, alreadyKnown = 0, notOurs = 0, unusable = 0)
            .toRefresh() shouldBe AnswersRefresh.Succeeded
    }

    @Test
    fun `a server that answered with answers is a successful check`() {
        AnswerPullResult.Pulled(recorded = 2, alreadyKnown = 1, notOurs = 0, unusable = 0)
            .toRefresh() shouldBe AnswersRefresh.Succeeded
    }

    @Test
    fun `an unreadable server is not a successful check`() {
        AnswerPullResult.Unreachable.toRefresh() shouldBe AnswersRefresh.Unreachable
    }

    @Test
    fun `a device with no server says so rather than claiming nobody answered`() {
        AnswerPullResult.NotConfigured.toRefresh() shouldBe AnswersRefresh.NotConfigured
    }
}
