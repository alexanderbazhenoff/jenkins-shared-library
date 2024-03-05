<div align='center'>

# jenkins shared library

Frequently used code patterns that makes writing Jenkins pipelines easier.

![Super_Linter](https://github.com/alexanderbazhenoff/jenkins-shared-library/actions/workflows/super-linter.yml/badge.svg?branch=main)
![GitHub License](https://img.shields.io/github/license/alexanderbazhenoff/jenkins-shared-library)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](https://makeapullrequest.com)
</div>

## Usage

1. Connect this repository to your Jenkins shared library as described in
[`official documentation`](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#global-shared-libraries).
2. Write your Jenkins pipeline like:

    **To use library from** [`src/org`](src/org/alx):

    ```groovy
    #!/usr/bin/env groovy
    
    @Library('jenkins-shared-library')
    
    node('master') {
        CommonFunctions = new org.alx.commonFunctions() as Object
        
        // your pipeline code and call this object by 'CommonFunctions.<functionName>', e.g:
        CommonFunctions.cleanSshHostsFingerprints(env.IP_LIST.tokenize(' '))
        // where IP_LIST is a space separated string IP list which is defined by pipeline 
        // parameter IP_LIST
    }
    ```

    To use GitLab related functions (e.g. runAnsible) you should set GitCredentialsID variable, which can be defined in
`OrgAlxGlobals()` class:

    ```groovy
    @Library('jenkins-shared-library')
   
    node('master') {
        CommonFunctions = new org.alx.commonFunctions() as Object  // shout be placed before GlobalConstants 
        GlobalConstants = new org.alx.OrgAlxGlobals() as Object
        // Then use constants from OrgAlxGlobals        
    }
    ```

    **To use library from** [`vars`](vars):

    ```groovy
    @Library('jenkins-shared-library')
    // or @Library(['jenkins-shared-library', 'another-library']) _
    // if you need to use several libraries.
    
    node('master') {
        // your pipeline code then example usage of unstashParameter:
        String fileInWorkspace = unstashParameter "JENKINS_PIPELINE_FILE_PARAMETER_NAME"
        // Output file content
        sh String.format('cat %s', fileInWorkspace)
    }
    
    ```

    Please also keep in mind a differences between `vars` and `src` folder organizations
    (read [**this article**](http://tdongsi.github.io/blog/2017/12/26/class-in-jenkins-shared-library/) for details).

3. Read `Groovydoc` in the comments of file(s) (e.g:
[`src/org/alx/commonFunctions.groovy`](src/org/alx/commonFunctions.groovy)) for details.
4. (Optional) To use Git-related functions (e.g. `cloneGitToFolder()`, `installAnsibleGalaxyCollections()` or
`runAnsible()`) to clone from ssh URL you should set up **GitCredentialsID** variable in
[`commonFunctions.groovy`](src/org/alx/commonFunctions.groovy).
