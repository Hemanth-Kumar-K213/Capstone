package com.womensafety.system.safety

import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.ColleagueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ColleagueAwarenessTest {

    private val repository = ColleagueRepository()

    @Test
    fun testColleagueRetrievalAtWorkplace() = runBlocking {
        val colleagues = repository.getColleaguesAtWorkplace("test_workplace")
        assertNotNull(colleagues)
        assertTrue("Should have registered colleagues present", colleagues.isNotEmpty())
        assertTrue("Should include colleague status", colleagues.any { it.currentStatus == ColleagueStatus.AT_WORKPLACE })
    }

    @Test
    fun testSafetyBuddyAgreement() = runBlocking {
        val agreement = SafetyBuddyAgreement(
            user1Id = "user_primary",
            user2Id = "colleague_1",
            workplaceId = "test_workplace",
            mutualCheckIns = true
        )

        val success = repository.createBuddyAgreement(agreement)
        assertTrue(success)

        val buddy = repository.getCurrentSafetyBuddy("test_workplace")
        assertNotNull(buddy)
        assertTrue(buddy!!.isSafetyBuddy)
    }

    @Test
    fun testColleaguesLeavingSoon() = runBlocking {
        val leaving = repository.getColleaguesAlsoLeavingNow("test_workplace")
        assertNotNull(leaving)
        assertTrue(leaving.all { it.currentStatus == ColleagueStatus.LEAVING || it.currentStatus == ColleagueStatus.AT_WORKPLACE })
    }

    @Test
    fun testTransportSuggestionTypes() {
        val colleague = Colleague(name = "Ananya Rao")
        val buddySuggestion = TransportSuggestion.SharedWithBuddy(colleague)
        val cabSuggestion = TransportSuggestion.CompanyCab
        val rideSuggestion = TransportSuggestion.RideShare("Ola")

        assertEquals(TransportType.COLLEAGUE_SHARED, buddySuggestion.transportType)
        assertEquals(TransportType.COMPANY_CAB, cabSuggestion.transportType)
        assertEquals(TransportType.RIDESHARE_OLA, rideSuggestion.transportType)
        assertTrue(buddySuggestion.isSafetyVerified)
    }
}
