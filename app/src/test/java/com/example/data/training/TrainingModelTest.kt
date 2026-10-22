package com.example.data.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TrainingModelTest {
    private fun config() = TrainingRepository.defaults(ApplicationProvider.getApplicationContext<Application>())
    @Test fun allOriginalMissionsCompleteWithTheirOriginalSteps() {
        val config = config()
        assertNull(config.validate())
        assertEquals(6, config.missions.size)
        assertEquals(23, config.missions.sumOf { it.steps.size })
        config.missions.forEach { mission ->
            var state = TrainingState(mission.id)
            mission.steps.forEach { state = TrainingEngine.press(config, state, it.keyId) }
            assertTrue(mission.title, state.finished)
            assertEquals(0, state.errors)
            assertTrue(mission.id in state.completed)
        }
    }
    @Test fun visibilityModesAreExclusiveAndTestRequiresMaster() {
        val config = config()
        for (master in listOf(true, false)) assertFalse(config.copy(visibility = TrainingVisibility.OFF).visibleTo(master))
        assertTrue(config.copy(visibility = TrainingVisibility.TEST).visibleTo(true))
        assertFalse(config.copy(visibility = TrainingVisibility.TEST).visibleTo(false))
        assertTrue(config.copy(visibility = TrainingVisibility.ALL).visibleTo(false))
    }
    @Test fun learnWrongKeyKeepsStepWhileExamResetsTheMission() {
        val c = config(); val mission = c.missions.first()
        val initial = TrainingState(mission.id)
        val started = TrainingEngine.press(c, initial, mission.steps.first().keyId)
        val wrong = c.keys.first { it.label.isNotBlank() && it.id != mission.steps[1].keyId }.id
        val learn = TrainingEngine.press(c, started, wrong)
        assertEquals(1, learn.step); assertEquals(1, learn.errors)
        val exam = TrainingEngine.press(c, started.copy(mode = TrainingMode.EXAM), wrong)
        assertEquals(0, exam.step); assertEquals(1, exam.errors)
    }
    @Test fun editableCommandsAndDisplaysRoundTripAndWorkInFreeMode() {
        val original = config(); val key = original.keys.first { it.label == "1" }
        val changed = key.copy(label = "Digitar 9", action = KeyAction.APPEND, value = "9", operatorText = "Valor {entrada}", customerText = "Cliente {tecla}", operatorImage = "https://example.com/green.webp", customerImage = "https://example.com/customer.webp", overrideGuided = true)
        val c = original.copy(keys = original.keys.map { if (it.id == key.id) changed else it })
        assertEquals(c, TrainingCodec.decode(TrainingCodec.encode(c)))
        val state = TrainingEngine.press(c, TrainingState(c.missions.first().id, TrainingMode.FREE), key.id)
        assertEquals("9", state.input); assertEquals(key.id, state.displayKey)
        assertEquals("Valor 9", TrainingEngine.render(changed.operatorText, state, changed, c.missions.first()))
        val clear = c.keys.first { it.action == KeyAction.CLEAR }
        assertEquals("", TrainingEngine.press(c, state, clear.id).input)
        assertNotNull(c.copy(keys = c.keys.map { if (it.id == key.id) it.copy(operatorImage = "http://unsafe") else it }).validate())
    }
    @Test fun guidedOverrideBackAndResetApplyWithoutExecutingExternalCommands() {
        val c = config(); val mission = c.missions.first(); val first = mission.steps.first().keyId
        val overridden = c.copy(keys = c.keys.map { if (it.id == first) it.copy(overrideGuided = true, operatorText = "Minha mensagem") else it })
        val state = TrainingEngine.press(overridden, TrainingState(mission.id), first)
        assertEquals(first, state.displayKey)
        val second = mission.steps[1].keyId
        val back = c.copy(keys = c.keys.map { if (it.id == second) it.copy(action = KeyAction.BACK) else it })
        assertEquals(0, TrainingEngine.press(back, state, second).step)
        val reset = c.copy(keys = c.keys.map { if (it.id == second) it.copy(action = KeyAction.RESET) else it })
        assertEquals(TrainingState(mission.id), TrainingEngine.press(reset, state, second))
    }
}
