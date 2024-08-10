package com.Keploy

import com.fasterxml.jackson._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.ui.jcef.{JBCefBrowser, JBCefClient, JBCefJSQuery}
import org.cef.CefApp
import org.cef.browser.{CefBrowser, CefFrame}
import org.cef.handler.CefLoadHandlerAdapter
import org.yaml.snakeyaml.Yaml

import java.io.File
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JComponent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}




case class KeployWindow(project: Project) {

  private lazy val webView: JBCefBrowser = {
    val browser = new JBCefBrowser()
    registerAppSchemeHandler()
    val url = if (isKeployConfigPresent) "http://myapp/index.html" else "http://myapp/config.html"
    browser.loadURL(url)
    Disposer.register(project, browser)

    val client: JBCefClient = browser.getJBCefClient()
    val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryPrevTests: JBCefJSQuery = JBCefJSQuery.create(browser)
    val jsQueryConfig : JBCefJSQuery = JBCefJSQuery.create(browser)
    jsQuery.addHandler((appCommandAndPath: String) => {
      val Array(appCommand, path) = appCommandAndPath.split("@@")
      initializeConfig(appCommand, path)
      null
    })

    jsQueryConfig.addHandler((_: String) => {

      openDocumentInEditor(Paths.get(project.getBasePath, "keploy.yml").toString)
      null
    })
    jsQueryPrevTests.addHandler((_: String) => {
      println("Displaying previous test results")
      val reportsFolderPath = Paths.get(project.getBasePath, "/keploy/reports")
      if (Files.exists(reportsFolderPath)) {
        val reportsFolder = new File(reportsFolderPath.toString)
        if (reportsFolder.listFiles().isEmpty) {
          previousTestResultsHandler(0, 0, 0, isError = true, "No test reports found.", null)
        } else {
          // Function to parse YAML
          val sortedReports = reportsFolder.listFiles().sortWith(_.lastModified() > _.lastModified())
          var totalSuccess = 0
          var totalFailure = 0
          var totalTests = 0
          val testResults = scala.collection.mutable.ListBuffer[Map[String, String]]()

          val yaml = new Yaml()
//          println("Reading test reports")
          sortedReports.foreach { testRunDir =>
            val testRunPath = Paths.get(reportsFolder.toString, testRunDir.getName)
            val testFiles = Files.list(testRunPath).iterator().asScala
              .filter(_.getFileName.toString.endsWith(".yaml"))
              .toList
            testFiles.foreach { testFile =>
              val testFilePath = testFile.toString
              val ios = Files.newInputStream(Paths.get(testFilePath))
//              println(s"Reading test file: $testFilePath")
//              println(fileContents)
              try {
                val mapper = new ObjectMapper().registerModules(DefaultScalaModule)
                val report = yaml.loadAs(ios, classOf[Any])
//                println(s"Parsed report: $report")
                val jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) // Formats YAML to a pretty printed JSON string - easy to read
                val jsonObj = mapper.readTree(jsonString)
//                println(s"JSON: $jsonObj")
//                println(s"Success: ${jsonObj.get("success")}," +
//                  s" Failure: ${jsonObj.get("failure")}," +
//                  s" Total: ${jsonObj.get("total")}")
                val success = jsonObj.get("success").asInt()
                val failure = jsonObj.get("failure").asInt()
                val total = jsonObj.get("total").asInt()
                totalSuccess += success
                totalFailure += failure
                totalTests += total
        val tests = jsonObj.get("tests")
                if (tests != null) {
                  tests.forEach { test =>
                    val req = test.get("req")
                    val timestamp = req.get("timestamp").asText().toLong
                    val date = new Date(timestamp)
                    testResults += Map(
                      "date" -> new SimpleDateFormat("dd/MM/yyyy").format(date),
                      "method" -> req.get("method").asText(),
                      "name" -> test.get("test_case_id").asText(),
                      "report" -> test.get("name").asText(),
                      "status" -> test.get("status").asText(),
                      "testCasePath" -> testFilePath
                    )
                  }
                }
              } catch {
                case e: Exception =>
                  println(s"Error parsing test file $testFilePath: ${e.getMessage}")
              }
            }
          }
//          println(s"Final Aggregated Results - Success: $totalSuccess, Failure: $totalFailure, Total: $totalTests")
          previousTestResultsHandler(totalSuccess, totalFailure, totalTests, isError = false, "Previous test results displayed.", testResults)

        }
      } else {
        previousTestResultsHandler(0, 0, 0, isError = true, "Run keploy test to generate test reports." , null)
      }
      null
    })

    client.addLoadHandler(new CefLoadHandlerAdapter {
      override def onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int): Unit = {
        if (frame.isMain) {
          browser.executeJavaScript(
            "window.initializeConfig = function(appCommandAndPath) {" +
              jsQuery.inject("appCommandAndPath") +
              "};",
            frame.getURL(), 0
          )



          browser.executeJavaScript(
            "window.historyPage = function() {" +
              jsQueryPrevTests.inject("historyPage") +
              "};",
            frame.getURL(), 0
          )
          browser.executeJavaScript(
            "window.openConfig = function() {" +
              jsQueryConfig.inject("openConfig") +
              "};",
            frame.getURL(), 0
          )
          val configInitializeJs =
            """
              document.getElementById('initialiseConfigButton').addEventListener('click', function() {
                var appCommand = document.getElementById('configCommand').value;
                var path = document.getElementById('configPath').value;
                window.initializeConfig(appCommand + "@@" + path);
              });
            """
          browser.executeJavaScript(configInitializeJs, frame.getURL, 0)

          val displayPreviousTestResultsJs =
            """
              function window.handleDisplayPreviousTestResults(message){
          document.getElementById('displayPreviousTestResults').style.display = 'none';


        const lastTestResultsDiv = document.getElementById('lastTestResults');
        const totalTestCasesDiv = document.getElementById('totalTestCases');
        const testSuiteNameDiv = document.getElementById('testSuiteName');
        const testCasesPassedDiv = document.getElementById('testCasesPassed');
        const testCasesFailedDiv = document.getElementById('testCasesFailed');

        // Clear previous content
        if (totalTestCasesDiv) { totalTestCasesDiv.innerHTML = ''; }
        if (testSuiteNameDiv) { testSuiteNameDiv.innerHTML = ''; }
        if (testCasesPassedDiv) { testCasesPassedDiv.innerHTML = ''; }
        if (testCasesFailedDiv) { testCasesFailedDiv.innerHTML = ''; }

        if (message.error === true) {

            if (lastTestResultsDiv) {
                const errorElement = document.createElement('p');
                errorElement.textContent = "No Test Runs Found";
                errorElement.classList.add("error");
                errorElement.id = "errorElement";
                lastTestResultsDiv.appendChild(errorElement);
            }
        } else {
            // Group tests by date
            const testsByDate = {};
            message.data.testResults.forEach(test => {
                const date = test.date;
                if (!testsByDate[date]) {
                    testsByDate[date] = [];
                }
                testsByDate[date].push(test);
            });


            const testCasesTotalElement = document.createElement('p');
            testCasesTotalElement.textContent = `Total Test Cases : ${message.data.total}`;
            if (totalTestCasesDiv) { totalTestCasesDiv.appendChild(testCasesTotalElement); }

            const testCasesPassedElement = document.createElement('p');
            testCasesPassedElement.textContent = `Test Cases Passed : ${message.data.success}`;
            if (testCasesPassedDiv) { testCasesPassedDiv.appendChild(testCasesPassedElement); }

            const testCasesFailedElement = document.createElement('p');
            testCasesFailedElement.textContent = `Test Cases Failed : ${message.data.failure}`;
            if (testCasesFailedDiv) { testCasesFailedDiv.appendChild(testCasesFailedElement); }

            // Create and append dropdown structure based on testsByDate
            const dropdownContainer = document.createElement('div');
            dropdownContainer.className = 'dropdown-container';

            for (const date in testsByDate) {
                if (testsByDate.hasOwnProperty(date)) {
                    const tests = testsByDate[date];

                    const dropdownHeader = document.createElement('div');
                    dropdownHeader.className = 'dropdown-header';

                    // Get current date
                    const currentDate = new Date();
                    const currentDateString = formatDate(currentDate);

                    // Get yesterday's date
                    const yesterday = new Date(currentDate);
                    yesterday.setDate(currentDate.getDate() - 1);
                    const yesterdayDateString = formatDate(yesterday);

                    if (currentDateString === date) {
                        dropdownHeader.textContent = `Today`;
                    } else if (yesterdayDateString === date) {
                        dropdownHeader.textContent = `Yesterday`;
                    } else {
                        dropdownHeader.textContent = `${date}`;
                    }

                    // Add dropdown icon
                    const dropdownIcon = document.createElement('span');
                    dropdownIcon.className = 'dropdown-icon';

                    dropdownHeader.appendChild(dropdownIcon);
                    dropdownHeader.onclick = () => {
                        const content = document.getElementById(`dropdown${date}`);
                        if (content) {
                            content.classList.toggle('show');
                            dropdownIcon.classList.toggle('open'); // Update icon based on dropdown state
                        }
                    };

                    const dropdownContent = document.createElement('div');
                    dropdownContent.id = `dropdown${date}`;
                    dropdownContent.className = 'dropdown-content';
                    tests.forEach((test, index) => {
                        // Append individual test details
                        const testMethod = document.createElement('div');
                        testMethod.textContent = `${test.method}`;
                        if (test.status === 'PASSED') {
                            testMethod.classList.add("testSuccess");
                        } else {
                            testMethod.classList.add("testError");
                        }
                        dropdownContent.appendChild(testMethod);

                        const testName = document.createElement('div');
                        testName.textContent = `${test.name}`;
                        testName.classList.add("testName");
                        dropdownContent.appendChild(testName);

                        testName.addEventListener('click', async () => {
                            vscode.postMessage({
                                type: "openTestFile",
                                value: test.testCasePath
                            });
                        });
                        testMethod.addEventListener('click', async () => {
                            vscode.postMessage({
                                type: "openTestFile",
                                value: test.testCasePath
                            });
                        });
                    });

                    dropdownContainer.appendChild(dropdownHeader);
                    dropdownContainer.appendChild(dropdownContent);
                }
            }

            if (lastTestResultsDiv) { lastTestResultsDiv.appendChild(dropdownContainer); }
        }
    }
            """
            browser.executeJavaScript(displayPreviousTestResultsJs, frame.getURL, 0)
          val historyPageJs =
            """
              document.getElementById('displayPreviousTestResults').addEventListener('click', function() {
                window.historyPage();
              });
            """
          browser.executeJavaScript(historyPageJs, frame.getURL, 0)
          val openConfigJs =
            """
              document.getElementById('openConfig').addEventListener('click', function() {
                window.openConfig();
              });
              """
            browser.executeJavaScript(openConfigJs, frame.getURL, 0)
        }
      }
    }, browser.getCefBrowser)

    browser
  }

  def content: JComponent = webView.getComponent

  private def registerAppSchemeHandler(): Unit = {
    CefApp.getInstance().registerSchemeHandlerFactory(
      "http",
      "myapp",
      new CustomSchemeHandlerFactory
    )
  }

  private def isKeployConfigPresent: Boolean = {
    val configPath = Paths.get(project.getBasePath, "keploy.yml")
    Files.exists(configPath)
  }

  private def initializeConfig(appCommand: String, path: String): Unit = {
    val folderPath = project.getBasePath
    val contentPath = if (path.isEmpty) "./" else path
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
        openDocumentInEditor(configFilePath.toString)
        webView.loadURL("http://myapp/index.html")
      case Failure(exception) =>
        println(s"Failed to initialize config file: ${exception.getMessage}")
    }
  }

  private def previousTestResultsHandler(success: Int, failure: Int, total: Int, isError: Boolean, message: String, testResults: Any): Unit = {
    val data = Map(
      "type" -> "aggregatedTestResults",
      "value" -> Map(
        "total" -> total,
        "success" -> success,
        "failure" -> failure,
        "error" -> isError,
        "message" -> message,
        "testResults" -> testResults
      )
    )

    // Convert the Scala Map to JSON
    val mapper = new ObjectMapper().registerModules(DefaultScalaModule)
    val jsonData = mapper.writeValueAsString(data)
    webView.getCefBrowser.executeJavaScript(
      s"""
           const lastTestResultsDiv = document.getElementById('lastTestResults');
        const totalTestCasesDiv = document.getElementById('totalTestCases');
        const testSuiteNameDiv = document.getElementById('testSuiteName');
        const testCasesPassedDiv = document.getElementById('testCasesPassed');
        const testCasesFailedDiv = document.getElementById('testCasesFailed');

        // Clear previous content
        if (totalTestCasesDiv) { totalTestCasesDiv.innerHTML = ''; }
        if (testSuiteNameDiv) { testSuiteNameDiv.innerHTML = ''; }
        if (testCasesPassedDiv) { testCasesPassedDiv.innerHTML = ''; }
        if (testCasesFailedDiv) { testCasesFailedDiv.innerHTML = ''; }
""" , webView.getCefBrowser.getURL, 0)
    println("Cleared UI")
    if (isError) {

      webView.getCefBrowser.executeJavaScript(
        s"""
            if (lastTestResultsDiv) {
                const errorElement = document.createElement('p');
                errorElement.textContent = "No Test Runs Found";
                errorElement.classList.add("error");
                errorElement.id = "errorElement";
                lastTestResultsDiv.appendChild(errorElement);
            }
        """, webView.getCefBrowser.getURL, 0)
      println("No test reports found")
    }else {
        webView.getCefBrowser.executeJavaScript(
            s"""
               """ , webView.getCefBrowser.getURL, 0)
    }

  }


  private def openDocumentInEditor(filePath: String): Unit = {
    ApplicationManager.getApplication().invokeLater(new Runnable {
      override def run(): Unit = {
        val file: VirtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file != null) {
          FileEditorManager.getInstance(project).openFile(file, true)
        } else {
          println(s"File not found: $filePath")
        }
      }
    })
  }
}
