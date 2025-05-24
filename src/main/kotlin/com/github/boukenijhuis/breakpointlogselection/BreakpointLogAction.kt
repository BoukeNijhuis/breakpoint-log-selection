package com.github.boukenijhuis.breakpointlogselection

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XSourcePositionImpl
// import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil // Not used anymore
import org.jetbrains.concurrency.AsyncPromise


class BreakpointLogAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun toggleLineBreakpointAsync(
        project: Project,
        position: XSourcePosition
    ): AsyncPromise<XLineBreakpoint<*>?> {
        val breakpointManager = XBreakpointManager.getInstance(project)
        val file = position.file
        val line = position.line
        val promise = AsyncPromise<XLineBreakpoint<*>?>()

        val lineBreakpointTypes = XBreakpointType.getRegisteredTypes().filterIsInstance<XLineBreakpointType<*>>()
        if (lineBreakpointTypes.isEmpty()) {
            promise.setError("No XLineBreakpointType registered.")
            return promise
        }

        var existingBreakpoint: XLineBreakpoint<*>? = null
        for (type in lineBreakpointTypes) {
            val bp = breakpointManager.findBreakpointAtLine(type, file, line)
            if (bp != null) {
                existingBreakpoint = bp
                break
            }
        }

        if (existingBreakpoint != null) {
            breakpointManager.removeBreakpoint(existingBreakpoint)
            promise.setResult(null) // Breakpoint removed
        } else {
            var suitableType: XLineBreakpointType<*>? = null
            for (type in lineBreakpointTypes) {
                // Ensure project is non-null for canPutAt if the type requires it, though XSourcePosition usually has it.
                if (type.canPutAt(file, line, project)) {
                    suitableType = type
                    break
                }
            }

            if (suitableType != null) {
                val properties = suitableType.createBreakpointProperties(file, line)
                val newBreakpoint = breakpointManager.addLineBreakpoint(
                    suitableType,
                    file,
                    line,
                    properties,
                    false // temporary = false
                )
                promise.setResult(newBreakpoint)
            } else {
                promise.setResult(null) // No suitable type found, resolve with null
            }
        }
        return promise
    }

    override fun actionPerformed(event: AnActionEvent) {
        // get current project and file
        val project = event.project ?: return
        val currentFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

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

        // increase the line number if the determined position does not support a breakpoint
        while (!isValidBreakpointLocation(project, currentFile, position) ) {
            position = XSourcePositionImpl.create(currentFile, position.line + 1)

            // when we move past the last line
            if (position.line + 1 > editor.document.lineCount) {
                return
            }

        }

        // always toggle (even if there is no selection)
        val breakpointPromise = toggleLineBreakpointAsync(project, position)

        // update the breakpoint with the log expression
        if (selectedText != null) { // No need to check if breakpointPromise is AsyncPromise
            breakpointPromise.then { bp -> // bp is XLineBreakpoint<*>?
                bp?.let { // Only configure if bp is not null (breakpoint was created or existed and wasn't removed for this path)
                    it.suspendPolicy = SuspendPolicy.NONE
                    it.logExpression = "\"$selectedText = [\" + $selectedText + \"]\""
                }
            }
        }
    }

    private fun isValidBreakpointLocation(
        project: Project,
        currentFile: VirtualFile,
        position: XSourcePosition
    ) = XDebuggerUtil.getInstance().canPutBreakpointAt(project, currentFile, position.line)
}