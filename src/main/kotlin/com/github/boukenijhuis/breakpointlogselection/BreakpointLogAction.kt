package com.github.boukenijhuis.breakpointlogselection

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import org.jetbrains.concurrency.AsyncPromise


class BreakpointLogAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(event: AnActionEvent) {
        // get current project and file
        val project = event.project ?: return
        val currentFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = event.getRequiredData(CommonDataKeys.EDITOR)

        // determine the position variables
        val offset = editor.caretModel.offset
        val currentPosition = XSourcePositionImpl.createByOffset(currentFile, offset) as XSourcePosition
        val nextLinePosition = XSourcePositionImpl.create(currentFile, currentPosition.line + 1)

        // check the selection and determine the breakpoint position
        val selectedText = editor.selectionModel.selectedText
        var position = currentPosition
        if (selectedText != null) {
            position = nextLinePosition
        }

        // always toggle (even if there is no selection)
        val breakpoint =
            XBreakpointUtil.toggleLineBreakpoint(project, position, editor, false, false, true)

        // update the breakpoint with the log expression
        if (selectedText != null && breakpoint is AsyncPromise) {
            breakpoint.then {
                    it.suspendPolicy = SuspendPolicy.NONE
                    it.logExpression = "\"$selectedText = [\" + $selectedText + \"]\""

            }
        }
    }
}