package com.iu.radioapp.journey

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.iu.radioapp.MainActivity
import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.data.remote.s2archive.FakeArchiveDataSource
import com.iu.radioapp.data.remote.s3requests.FakeRequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeRatingEventDataSource
import com.iu.radioapp.di.AdjustableClock
import com.iu.radioapp.di.TestLocalDataModule
import com.iu.radioapp.ui.navigation.NavTags
import com.iu.radioapp.ui.navigation.TopLevel
import com.iu.radioapp.ui.rating.RatingTags
import com.iu.radioapp.work.WorkManagerDeliveryScheduler
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/** One journey through the real app; only the station systems are fakes and the network is [networkBack]. */
abstract class JourneyTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule: ComposeTestRule = createEmptyComposeRule()

    @Inject lateinit var playout: FakePlayoutDataSource
    @Inject lateinit var archive: FakeArchiveDataSource
    @Inject lateinit var requests: FakeRequestsDataSource
    @Inject lateinit var feedback: FakeFeedbackDataSource
    @Inject lateinit var ratingEvents: FakeRatingEventDataSource
    @Inject lateinit var clock: AdjustableClock
    @Inject lateinit var workerFactory: HiltWorkerFactory

    protected val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUpApp() {
        hiltRule.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(workerFactory).setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDownApp() {
        scenario?.close()
        context.cacheDir.listFiles { file -> file.name.startsWith(TestLocalDataModule.PREFERENCES_PREFIX) }
            ?.forEach { it.delete() }
    }

    protected fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun openArea(area: TopLevel) {
        composeRule.onNodeWithTag(NavTags.tab(area)).performClick()
    }

    protected fun openRating(tab: String) {
        waitForText("Sample Song")
        openArea(TopLevel.RATING)
        composeRule.onNode(hasText(tab) and isSelectable()).performClick()
    }

    protected fun submitRating(value: Int) {
        waitForTag(RatingTags.value(value))
        composeRule.onNodeWithTag(RatingTags.value(value)).performClick()
        composeRule.onNodeWithTag(RatingTags.SUBMIT).performClick()
    }

    protected fun networkBack() {
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        // Queuing runs on the ViewModel's scope, so the run may not be enqueued yet.
        composeRule.waitUntil(TIMEOUT_MS) { pendingDeliveryRuns().isNotEmpty() }
        pendingDeliveryRuns().forEach { driver.setAllConstraintsMet(it.id) }
    }

    private fun pendingDeliveryRuns() =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerDeliveryScheduler.UNIQUE_WORK_NAME).get()
            .filterNot { it.state.isFinished }

    protected fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = TIMEOUT_MS) {
        try {
            composeRule.waitUntil(timeoutMs) {
                composeRule.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            composeRule.onAllNodes(isRoot()).onFirst().printToLog("JourneyTest")
            throw e
        }
    }

    protected fun waitForTag(tag: String, timeoutMs: Long = TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun hasNoText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty()

    protected companion object {
        const val TIMEOUT_MS = 5_000L
        // A track change has to show up within 15 seconds.
        const val NEXT_POLL_MS = 15_000L
        const val ALWAYS = 1_000
    }
}
