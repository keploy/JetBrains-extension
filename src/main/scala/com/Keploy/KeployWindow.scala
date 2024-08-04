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
    jsQuery.addHandler((appCommandAndPath: String ) => {
      println(s"appCommandAndPath received: $appCommandAndPath")
      val appCommand = appCommandAndPath.split("@@")(0)
      val path = appCommandAndPath.split("@@")(1)
        println("initializeConfig command received.")
        initializeConfig(appCommand, path)

      null;
    })
    client.addLoadHandler(new CefLoadHandlerAdapter {
      override def onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int): Unit = {
        if (frame.isMain) {
          println("Main frame loaded.")

          browser.executeJavaScript( // 4
            "window.initializeConfig = function(appCommandAndPath) {" +
              jsQuery.inject("appCommandAndPath") + // 5
              "};",
            frame.getURL(), 0
          );
          val js = """
            document.getElementById('initialiseConfigButton').addEventListener('click', function() {
             var appCommand = document.getElementById('configCommand').value;
          var path = document.getElementById('configPath').value;
          window.initializeConfig(appCommand + "@@" + path);
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

  private def initializeConfig(appCommand : String , path : String): Unit = {
    val folderPath = project.getBasePath
    var contentPath = path
    if (path == "") {
      contentPath= "./"
    }
    val configFilePath = Paths.get(folderPath, "keploy.yml")
    val content =
      s"""
        |path: "$contentPath"
        |appId: ""
        |command: "$appCommand"
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
            webView.loadURL("http://myapp/index.html") // Load index.html after successful initialization
          case Failure(exception) =>
            println(s"Failed to open config file: ${exception.getMessage}")
        }
      case Failure(exception) =>
        println(s"Failed to initialize config file: ${exception.getMessage}")
    }
  }

  private def openDocumentInEditor(doc: String): Unit = {
//  TODO: Implement
    //    val projectBasePath = project.getBasePath
//    val filePath = s"$projectBasePath/keploy.yml"
//    val virtualFile: VirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(s"file://$filePath")
//
//    if (virtualFile != null) {
//      virtualFile.setBinaryContent(doc.getBytes)
//      FileEditorManager.getInstance(project).openFile(virtualFile, true)
//      println("Document opened in the editor.")
//    } else {
//      println("Failed to find or create the virtual file.")
//    }
    println("Document opened in the editor.")
  }
}

