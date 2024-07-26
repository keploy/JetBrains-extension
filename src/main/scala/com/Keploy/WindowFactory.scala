package com.Keploy

import com.Keploy.KeployWindowService
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}

class WindowFactory extends ToolWindowFactory {
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val keployWindow : KeployWindow= ServiceManager.getService(project, classOf[KeployWindowService]).KeployWindow
    val component     = toolWindow.getComponent
    component.getParent.add(keployWindow.content)
  }
}