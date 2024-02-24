package com.github.boukenijhuis.breakpointlogselection

import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy

class MyPluginTest : BasePlatformTestCase() {

    fun testBreakpointCreation() {
        myFixture.configureByFiles("Main.java")
        val manager = XDebuggerManager.getInstance(project).getBreakpointManager();

        val breakpointsBefore = manager.getAllBreakpoints().size

        var actionEvent = TestActionEvent(BreakpointLogAction())
        var action = BreakpointLogAction()
        action.actionPerformed(actionEvent)

        val breakpointsAfter = manager.getAllBreakpoints().size

        // there should be one breakpoint extra
        assertEquals("Expected exactly one created breakpoint.", 1, breakpointsAfter - breakpointsBefore)

        // check the created breakpoint
        val breakpoint = manager.allBreakpoints.last()
        assertEquals("Expected the breakpoint to not suspend.", breakpoint.suspendPolicy, SuspendPolicy.NONE)
        assertNotNull("Expected the breakpoint the have a log expression", breakpoint.logExpressionObject?.expression)

        // call the plugin again to remove the breakpoint
        action.actionPerformed(actionEvent)

        // there should be no breakpoint extra
        val breakpointsAfterDouble = manager.getAllBreakpoints().size
        assertEquals("Expected exactly zero created breakpoint.", 0, breakpointsAfterDouble - breakpointsBefore)

    }

    override fun getTestDataPath(): String {
        return "src/test/testData"
    }
}

