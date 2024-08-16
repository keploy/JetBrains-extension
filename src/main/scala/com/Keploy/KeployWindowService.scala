package com.Keploy

import com.Keploy.KeployWindow
import com.intellij.openapi.project.Project

class KeployWindowService(val project: Project) {
  val KeployWindow : KeployWindow = new KeployWindow(project)
}
