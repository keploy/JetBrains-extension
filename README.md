<div align="center">

[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen?logo=github)](CODE_OF_CONDUCT.md) 
[![Slack](https://img.shields.io/badge/Slack-Join%20Slack-blue?logo=slack)](https://join.slack.com/t/keploy/shared_invite/zt-12rfbvc01-o54cOG0X1G6eVJTuI_orSA)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

# Keploy

[Keploy](https://keploy.io) is a no-code testing platform that generates tests from API calls. It allows you to create end-to-end tests without writing a single line of code, making it easier to ensure the reliability of your APIs.

> Note:  This extension currently supports only Go, Node, Python and Java programming language.

## Features

#### Record and Replay TestCases. 
#### View Previous TestRun Result.
#### View and Edit Keploy Config File

## Installation

1. Install the Keploy extension from the [JetBrains Marketplace](https://plugins.jetbrains.com/).

2. Keploy CLI is present : - `curl --silent -O -L https://keploy.io/install.sh && source install.sh`

## Contribution Guide

### Prerequisites

Before you begin, ensure you have the following installed on your machine:

- **JDK (Java Development Kit)**: Version 11 or higher.
- **Gradle**: The project uses Gradle as its build system.

### Cloning the Repository

First, clone the repository to your local machine:

```bash
git clone https://github.com/keploy/keploy.git
cd keploy
```

### Building the Project

To build and run the project locally, follow these steps:

1. **Install Dependencies**: Ensure all the necessary dependencies are installed.

    ```bash
    ./gradlew clean build
    ```

2. **Run the IDE**: Start the IntelliJ IDEA instance with the plugin.

    ```bash
    ./gradlew runIde
    ```

3. **Packaging the Plugin**: To build a distributable version of the plugin:

    ```bash
    ./gradlew buildPlugin
    ```

The generated plugin will be available in the `build/distributions` directory.

4. From the debug console, you can see the output or errors if any.

### Make changes to Frontend

1. Run `npm run rollup` to compile your svelte files into js files present in `out/compiled` dir.

2. Make changes to your svelte code and the js files will be automatically re-compiled.


## Community Support

We'd love to collaborate with you to make Keploy even better. Here’s how you can get involved:

- **[Slack](https://join.slack.com/t/keploy/shared_invite/zt-12rfbvc01-o54cOG0X1G6eVJTuI_orSA)**: Join our Slack community to discuss and collaborate.
- **[GitHub Issues](https://github.com/keploy/keploy/issues)**: Report bugs, suggest new features, and track the development progress.

## License

Keploy is licensed under the [Apache License 2.0](https://opensource.org/licenses/Apache-2.0). See the LICENSE file for more details.
