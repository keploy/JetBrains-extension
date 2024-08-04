package com.Keploy

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.{JBCefBrowser, JBCefClient, JBCefJSQuery}
import org.cef.CefApp
import org.cef.browser.{CefBrowser, CefFrame}
import org.cef.handler.CefLoadHandlerAdapter

import java.nio.file.{Files, Paths}
import javax.swing.JComponent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class KeployWindow(project: Project) {

  private lazy val webView: JBCefBrowser = {
    val browser = new JBCefBrowser()
    registerAppSchemeHandler()
    val url = if (isKeployConfigPresent) "http://myapp/config.html" else "http://myapp/config.html"
    browser.loadURL(url)
    Disposer.register(project, browser)

    val client: JBCefClient = browser.getJBCefClient()
    val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)
    println(jsQuery == null)
    jsQuery.addHandler((query: String) => {
      println(s"Query received: $query")
      if (query == "initializeConfig") {
        println("initializeConfig command received.")
        initializeConfig()
        //        jsQuery.createResponse("Config initialization request sent", 0)
      } else {
        //        jsQuery.createResponse("Unknown command", 1)
      }
      null;
    })
    client.addLoadHandler(new CefLoadHandlerAdapter {
      override def onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int): Unit = {
        if (frame.isMain) {
          println("Main frame loaded.")

          browser.executeJavaScript( // 4
            "window.initializeConfig = function(query) {" +
              jsQuery.inject("query") + // 5
              "};",
            frame.getURL(), 0
          );
          val js = """
            document.getElementById('initialiseConfigButton').addEventListener('click', function() {
  window.initializeConfig('initializeConfig');
              });
          """
          browser.executeJavaScript(js, frame.getURL, 0)
          println("JavaScript executed.")
        }
      }
    }, browser.getCefBrowser)


    println("KeployWindow initialized.")
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
    val exists = Files.exists(configPath)
    println(s"Keploy config present: $exists")
    exists
  }

  private def initializeConfig(): Unit = {
    val folderPath = project.getBasePath
    val configFilePath = Paths.get(folderPath, "keploy.yml")
    val content =
      """
        |path: "./"
        |appId: ""
        |command: ""
        |port: 0
        |proxyPort: 16789
        |dnsPort: 26789
        |debug: false
        |disableANSI: false
        |disableTele: false
        |inDocker: false
        |generateGithubActions: true
        |containerName: ""
        |networkName: ""
        |buildDelay: 30
        |test:
        |  selectedTests: {}
        |  globalNoise:
        |    global: {}
        |    test-sets: {}
        |  delay: 5
        |  apiTimeout: 5
        |  coverage: false
        |  goCoverage: false
        |  coverageReportPath: ""
        |  ignoreOrdering: true
        |  mongoPassword: "default@123"
        |  language: ""
        |  removeUnusedMocks: false
        |record:
        |  recordTimer: 0s
        |  filters: []
        |configPath: ""
        |bypassRules: []
        |cmdType: "native"
        |enableTesting: false
        |fallbackOnMiss: false
        |keployContainer: "keploy-v2"
        |keployNetwork: "keploy-network"
        |
        |# This config file has been initialized
        |
        |# Visit https://keploy.io/docs/running-keploy/configuration-file/ to learn about using Keploy through the configuration file.
      """.stripMargin

    Future {
      val writer = Files.newBufferedWriter(configFilePath)
      writer.write(content)
      writer.close()
    }.onComplete {
      case Success(_) =>
        println("Keploy config file initialized successfully.")
        val openTask = Future {
          val doc = Files.readAllLines(configFilePath).toArray.mkString("\n")
          doc
        }
        openTask.onComplete {
          case Success(doc) =>
            openDocumentInEditor(doc)
          case Failure(exception) =>
            println(s"Failed to open config file: ${exception.getMessage}")
        }
      case Failure(exception) =>
        println(s"Failed to initialize config file: ${exception.getMessage}")
    }
  }

  private def openDocumentInEditor(doc: String): Unit = {
    println("Document opened in the editor.")
  }
}

