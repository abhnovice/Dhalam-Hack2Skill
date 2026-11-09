package com.dhalam.ap

import com.dhalam.ap.data.SecurityFact
import org.junit.Assert.*
import org.junit.Test

class LocalAskAnswerTest {
    private val scan = SecurityObservation(10L, null, null, emptyList(),
        PermissionInventory(3, listOf(PermissionReport("android.permission.CAMERA", listOf(
            PermissionApp("camera.app", "Camera App", true),
            PermissionApp("denied.app", "Denied App", false))))))

    private fun fact(id: Long, app: String, status: String = "granted", time: Long = 10L) =
        SecurityFact(id, "permission:$app:camera", "permission", app,
            "$app requested android.permission.CAMERA", status, time)

    @Test fun cameraQuestionUsesOnlyGrantedFactsFromCurrentScan() {
        val answer = localAskAnswer("which app can record a video without my knowledge",
            listOf(fact(1, "camera.app"), fact(2, "denied.app", "not_granted"),
                fact(3, "old.app", time = 9L)), scan)!!
        assertTrue(answer.answer.contains("Camera App (camera.app)"))
        assertFalse(answer.answer.contains("Denied App"))
        assertFalse(answer.answer.contains("old.app"))
        assertTrue(answer.answer.contains("cannot tell whether it recorded video"))
        assertEquals(listOf(1L), answer.findings.flatMap { it.sources }.map { it.id })
    }

    @Test fun unrelatedQuestionsStillUseTheBackend() {
        assertNull(localAskAnswer("Explain accessibility services", listOf(fact(1, "camera.app")), scan))
    }

    @Test fun inventoryCanAnswerBeforeFactsAreSaved() {
        val answer = localAskAnswer("Which apps have camera access?", emptyList(), scan)!!
        assertTrue(answer.answer.contains("Camera App (camera.app)"))
        assertTrue(answer.findings.isEmpty())
    }

    @Test fun missingInventoryDoesNotClaimNoCameraAccess() {
        assertNull(localAskAnswer("Which apps have camera access?", emptyList(), scan.copy(permissionInventory = null)))
    }
}
