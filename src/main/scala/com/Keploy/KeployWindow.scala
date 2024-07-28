package com.Keploy

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JComponent
import org.cef.CefApp
import java.nio.file.{Files, Paths}

case class KeployWindow(project: Project) {
  private lazy val webView: JBCefBrowser = {
    val browser = new JBCefBrowser()
    registerAppSchemeHandler()
    val url = if (isKeployConfigPresent) "http://myapp/index.html" else "http://myapp/config.html"
    browser.loadURL(url)
    Disposer.register(project, browser)
    browser
  }

  def content: JComponent = webView.getComponent

  private def registerAppSchemeHandler(): Unit = {
    CefApp
      .getInstance()
      .registerSchemeHandlerFactory(
        "http",
        "myapp",
        new CustomSchemeHandlerFactory
      )
  }

  private def isKeployConfigPresent: Boolean = {
    val configPath = Paths.get(project.getBasePath, "keploy.yml")
    Files.exists(configPath)
  }
}